package net.chilltec.tempo.services

import android.app.*
import android.content.*
import android.graphics.Color
import android.media.*
import android.net.wifi.WifiManager
import android.os.*
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import net.chilltec.tempo.activities.PlayerActivity
import net.chilltec.tempo.R
import okhttp3.*
import org.jetbrains.anko.runOnUiThread
import org.jetbrains.anko.toast
import java.io.*
import java.lang.Exception
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

class MediaService : Service() {

    private var mp = MediaPlayer()
    private val binder = LocalBinder()
    private lateinit var ms: MediaSessionCompat
    val http = OkHttpClient()
    private var baseUrl: String = ""
    private var songSet: IntArray = intArrayOf()
    private var shuffleSet: IntArray = intArrayOf()
    private var playedSet: IntArray = intArrayOf()
    private var songQueue: IntArray = intArrayOf()
    private var curSong: Int = -1
    private var nextSong: Int = -1
    private var downloadList: MutableList<Int> = mutableListOf()

    //Flags
    private var mpIsSafe: Boolean = true
    private var curDownloading: Boolean = false
    private var isStreaming: Boolean = false
    private var dbIsInit: Boolean = false
    private var shuffleEnabled: Boolean = false
    private var repeatEnabled: Boolean = false
    //private var hasRepeated: Boolean = false
    //private var streamingEnabled: Boolean = true

    var db: DatabaseService? = null
    private val dbConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DatabaseService.LocalBinder
            db = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    override fun onCreate() {
        Log.i(TAG, "Media Service Created")
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        baseUrl = preferences.getString("server_base_url", "") ?: ""
        Log.i(TAG, "Using base URL: $baseUrl")
        //Bind to DatabaseService
        val dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)

        playerInit()
        noisyReceiverInit()
        msInit()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startID: Int): Int {
        Log.i(TAG, "Media Service Started")
        MediaButtonReceiver.handleIntent(ms, intent)
        return START_STICKY
    }
    override fun onBind(intent: Intent): IBinder? {
        Log.i(TAG, "Media Service Bound")
        return binder
    }
    override fun onDestroy() {
        Log.i(TAG, "Service onDestroy")
        mp.release()
        unregisterReceiver(noiseReceiver)
        unbindService(dbConnection)
    }
    inner class LocalBinder : Binder() {
        fun getService(): MediaService {
            return this@MediaService
        }
    }
    private fun playerInit() {
        //Sets a new MediaPlayer with the initial options.
        mpIsSafe = false
        mp.release()
        mp = MediaPlayer()
        mpIsSafe = true

        val attr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        mp.setAudioAttributes(attr)
        mp.apply {
            setOnPreparedListener {
                Log.i(TAG, "Song prepared")
                mp.start()
                sendSongUpdateBroadcast()
                updateNotification()
            }
            setOnCompletionListener {
                Log.i(TAG, "onComplete")
                controlNext()
            }
            setOnErrorListener { mp, what, extra ->
                Log.i(TAG, "onError")
                Log.i(TAG, "$what, $extra")
                mp.reset()
                true
            }
        }
    }
    fun playSongById(songID: Int, force: Boolean = false) {
        //Plays a given song, and pre-loads the next song, if applicable
        //If force is true, the songID will be added to the front of the list.
        //  This is only used when a user directly requests a song by clicking.
        Thread(Runnable {
            if (songID <= 0) {
                Log.i(TAG, "ERROR: Attempted to play invalid song id: $songID")
                return@Runnable
            }
            updateCurNextSong(songID)
            setMetadata(songID)
            if(isCached(curSong)) {
                Log.i(TAG, "Playing song $songID from Local Cache")
                //Get file, and return if it doesn't exist
                val songFile = getSongFile(songID) ?: return@Runnable
                playSongFromFile(songFile)
            }
            else{
                //Start streaming the song, then download it to cache
                streamSong(songID)
                Log.i(TAG, "Playing song $songID from Remote Stream")
                if(force){
                    downloadList.add(0, curSong)
                    downloadSong()
                }
                else if(curSong !in downloadList){
                    downloadList.add(curSong)
                    if(!curDownloading) downloadSong()
                }
            }
            if(!isCached(nextSong)){
                downloadList.add(nextSong)
                if(!curDownloading) downloadSong()
            }
        }).start()
    }
    private fun downloadSong() {
        //Downloads a song.
        if (downloadList.isEmpty()) return
        curDownloading = true
        val songId: Int = downloadList.removeAt(0)
        if(songId < 1) return
        val songFile = File(cacheDir, "$songId.song")
        if (songFile.exists() && songFile.length() > 0) {
            //Already downloaded, download next item in downloadList if applicable
            if (downloadList.isNotEmpty()) { downloadSong() }
            return
        }
        val isWifiConnected = isWifiConnected()
        val songUrl: String = if (isWifiConnected) { "$baseUrl/getSongById/$songId" }
                                else { "$baseUrl/getLowSongById/$songId" }
        if (songFile.exists()) { songFile.delete() }
        Log.i(TAG, "Requesting song $songId from $songUrl to Local Cache")
        this.runOnUiThread {
            toast("Downloading song...")
        }
        val curRequest = Request.Builder().url(songUrl).build()
        http.newCall(curRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val respBytes = response.body()?.bytes() ?: byteArrayOf()
                response.body()?.close()
                if (respBytes.isEmpty() || response.code() != 200) {
                    Log.i(TAG, "ERROR downloading song $songId to cache. code: ${response.code()}")
                }
                else {
                    songFile.writeBytes(respBytes)
                    Log.i(TAG, "Successfully cached song $songId")
                    if (downloadList.isNotEmpty()) downloadSong()
                    else curDownloading = false
                }
            }
        })
    }
    fun addSongToCacheQueue(id: Int){
        if(id < 1) return
        Log.i(TAG, "")
        downloadList.add(id)
        Thread(Runnable{
            if(!curDownloading){ downloadSong() }
        }).start()
    }
    fun getCacheQueue(): IntArray{
        //Returns a list of all songs in the download queue, may be empty
        return downloadList.toIntArray()
    }
    private fun streamSong(id: Int){
        //Plays a song by ID through streaming mode.
        //Media playback controls are not available in this mode
        //Must set isStreaming flag to true
        val url = "$baseUrl/getSongById/$id"
        try {
            isStreaming = true
            playerInit()
            mp.setDataSource(url)
            mp.prepareAsync()
        } catch(e: Exception){
            Log.i(TAG, "Streaming Failed")
            e.printStackTrace()
        }
    }
    private fun playSongFromFile(file: File): Boolean{
        //Plays a song from a local file
        //Must clear the isStreaming flag to false, to enable media controls.
        try{
            isStreaming = false
            playerInit()
            mp.setDataSource(file.absolutePath)
            mp.prepareAsync()
        } catch(e: Exception) {
            return false
        }
        return true
    }
    private fun getSongFile(id: Int): File? {
        //Returns the file for the given song, if it exists
        val songFileLoc = "$id.song"
        val songFile = File(cacheDir, songFileLoc)
        return if(songFile.exists()){
            songFile
        } else {
            null
        }
    }
    private fun isCached(id: Int): Boolean{
        //Returns true if the given songID has a downloaded file in cache
        val songFileLoc = "$id.song"
        val songFile = File(cacheDir, songFileLoc)
        return songFile.exists()
    }
    //Cache info
    fun getCachedSongList(): IntArray{
        //Disc access, call this from inside non-UI thread
        val songList = mutableListOf<Int>()
        cacheDir.walkTopDown().forEach {
            if(it.exists() &&  it.extension == "song"){
                val songID = it.nameWithoutExtension.toIntOrNull() ?: -1
                if(songID > 0){
                    songList.add(songID)
                }
            }
        }
        return songList.toIntArray()
    }
    fun clearCache() {
        //Deletes all song files that have been downloaded to cache
        Thread(Runnable {
            val curFile = "$curSong.song"
            val nextFile = "$nextSong.song"
            var deletedFiles = 0
            var sizeInBytes: Long = 0
            cacheDir.walkTopDown().forEach {
                if (it.exists() && it.name != curFile && it.name != nextFile) {
                    sizeInBytes += it.length()
                    it.delete()
                    deletedFiles++
                }
            }
            val sizeInMb: Int = (sizeInBytes.toDouble() / 1000000.toDouble()).roundToInt()

            this.runOnUiThread {
                toast("Cleared ${sizeInMb}MB from $deletedFiles files in cache")
            }
            Log.i(TAG, "Cleared ${sizeInMb}MB from $deletedFiles files in cache")
        }).start()
    }
    //End Cache Info
    private fun updateCurNextSong(id: Int) {
        //Sets the given id as the curSong id and updates nextSong
        if (songQueue.isEmpty()) return
        if (id in songQueue) {
            curSong = id
            val index = songQueue.indexOf(id)
            if ((index + 1) <= songQueue.lastIndex) {
                nextSong = songQueue[index + 1]
            }
        } else {
            //Error, set first song in queue to curSong
            curSong = songQueue[0]
            if (1 <= songQueue.lastIndex) {
                nextSong = songQueue[1]
            }
        }
    }
    private fun isWifiConnected(): Boolean {
        var isWifiConnected = false
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if(wifiManager.isWifiEnabled){
            val wifiInfo = wifiManager.connectionInfo
            if(wifiInfo.networkId != -1) isWifiConnected = true
        }
        return isWifiConnected
    }
    fun toggleShuffle() {
        if (shuffleEnabled) {
            songQueue = songSet
            shuffleEnabled = false
        } else {
            songQueue = shuffleSet
            shuffleEnabled = true
        }
        updateCurNextSong(curSong)
        sendShuffleUpdateBroadcast()
    }
    private fun shuffler(list: IntArray): IntArray {
        //Shuffles a given IntArray containing song IDs
        val listCopy = list.toMutableList()
        val shuffledList = mutableListOf<Int>()
        val random = Random()
        while (listCopy.size > 0) {
            val randInt = random.nextInt().absoluteValue
            val index = randInt.rem(listCopy.size)
            shuffledList.add(listCopy.removeAt(index))
        }
        //Log.i(TAG, "oldSet: ${list.toMutableList()}")
        //Log.i(TAG, "Shuffled: $shuffledList")
        return shuffledList.toIntArray()
    }
    fun toggleRepeat() {
        this.repeatEnabled = !this.repeatEnabled
        sendRepeatUpdateBroadcast()
    }
    fun getShuffleStatus(): Boolean = this.shuffleEnabled
    fun getRepeatStatus(): Boolean = this.repeatEnabled

    fun setSongList(list: IntArray) {
        //Accepts an array of Integer song IDs. The can corrispond to songs from the current album, artist, or playlist
        songSet = list.copyOf()
        shuffleSet = shuffler(list)
        if (shuffleEnabled) {
            songSet = shuffleSet
        } else {
            songQueue = songSet
        }
        //Clear the playedSet
        playedSet = intArrayOf()
    }

    fun getSongQueue(): IntArray {
        //Returns the songs in the current song set, in the order they will be played.
        return this.songQueue
    }

    fun setProgress(time: Int) {
        //Sets the current song to the given time, in milliseconds
        //Accepts a value between 0 and mp.duration
        try{
            val curDuration = getCurrentDuration()
            if (time < 0 || time > curDuration) return
            if (curDuration == -1) return
            mp.seekTo(time)
        } catch(e: Exception){
            Log.i(TAG, "Can't seek right now")
        }
    }

    fun getProgress(): Int {
        //Returns the current song's integer progress in milliseconds.
        //A return value of 0 indicates the song has just started,
        // or an error has occured.
        return try {
            mp.currentPosition
        } catch (e: Exception) {
            0
        }
    }

    fun getCurSong(): Int {
        //Returns the Song ID of the currently playing song
        return curSong
    }

    fun getCurrentDuration(): Int {
        //Safely request the current duration in milliseconds, with state and error checking.
        //Return 0 on error
        //This is the only place mp.duration or mp.getDuration should be called
        var duration = 0
        try {
            duration = mp.duration
        } catch (e: IllegalStateException) {
            Log.i(TAG, "Illegal State Exception caught")
            Log.i(TAG, e.localizedMessage)
        }

        return duration
    }

    fun isPlaying(): Boolean {
        return try {
            mp.isPlaying
        } catch (e: Exception) {
            false
        }
    }

    //Media Controls
    fun controlPrev() {
        Log.i(TAG, "Control_Prev")

        //Restart song if more than 2 seconds into it
        if (mp.currentPosition > 2000) {
            if (getCurrentDuration() != -1) {
                mp.seekTo(1)
            } else {
                playSongById(curSong)
            }
        } else {
            //Otherwise go to previous song
            mp.reset()
            val curIndex = songQueue.indexOf(curSong)
            var toPlay = -1
            if (curIndex >= 1) toPlay = songQueue[curIndex - 1]
            if (toPlay != -1) {
                nextSong = curSong
                curSong = toPlay
                playSongById(toPlay, true)
            } else {
                Log.i(TAG, "Hit beginning of queue")
            }
        }
    }

    fun controlPlay() {
        //Functionality for all play/pause buttons across tempo. Handles both playing
        //and pausing.
        Log.i(TAG, "Control_Play")
        if (mp.isPlaying) mp.pause()
        else mp.start()
        updateNotification()
        sendPlayPauseBroadcast()
    }

    fun controlPause() {
        //Handles only pausing, does nothing if the player is already paused.
        //Used to handle bluetooth / headphone disconnections
        if (mp.isPlaying) mp.pause()
        updateNotification()
    }

    fun controlNext() {
        Log.i(TAG, "Control_Next")

        if (mpIsSafe) mp.reset()
        else return

        val toPlay: Int = nextSong
        val songQueueIndex: Int = songQueue.indexOf(toPlay)

        if (toPlay != -1) {
            nextSong = if(songQueue.size > (songQueueIndex + 1))
                { songQueue[songQueueIndex + 1] }
                else { -1 }
            playSongById(toPlay, true)
            curSong = toPlay
        } else {
            Log.i(TAG, "Hit end of queue")
        }
    }

    //Broadcasts
    private fun sendSongUpdateBroadcast() {
        Log.i(TAG, "Sending song update request")
        Intent().also { intent ->
            intent.action = BROADCAST_SONG_UPDATE
            sendBroadcast(intent)
        }
    }

    private fun sendPlayPauseBroadcast() {
        Log.i(TAG, "Sending PlayPause Notification")
        Intent().also { intent ->
            intent.action = BROADCAST_PLAY_PAUSE
            sendBroadcast(intent)
        }
    }

    private fun sendShuffleUpdateBroadcast() {
        Log.i(TAG, "Sending Shuffle Update Notification")
        Intent().also { intent ->
            intent.action = BROADCAST_SHUFFLE_UPDATE
            sendBroadcast(intent)
        }
    }

    private fun sendRepeatUpdateBroadcast() {
        Log.i(TAG, "Sending Repeat Update Notification")
        Intent().also { intent ->
            intent.action = BROADCAST_REPEAT_UPDATE
            sendBroadcast(intent)
        }
    }
    //End Broadcasts

    //Administrative Functions
    fun rescanLibrary() {
        Log.i(TAG, "Requesting the server to rescan library files")
        val url = "$baseUrl/rescanLibrary"
        val request = Request.Builder().url(url).build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.body()?.close()
                db?.updateDatabases()
            }
        })
    }
    fun rescanPlaylists() {
        Log.i(TAG, "Requesting the server to rescan playlists")
        val url = "$baseUrl/rescanPlaylists"
        val request = Request.Builder().url(url).build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.body()?.close()
                db?.updatePlaylistDatabases()
            }
        })
    }

    //MediaSession
    private var noiseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i(TAG, "Noise received, pausing playback")
            controlPause()
        }
    }
    private var msCallback: MediaSessionCompat.Callback = object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            //Handles media button events
            val codePlay = KeyEvent.KEYCODE_MEDIA_PLAY
            val codePause = KeyEvent.KEYCODE_MEDIA_PAUSE
            val codePlayPause = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            val codeNext = KeyEvent.KEYCODE_MEDIA_NEXT
            val codePrev = KeyEvent.KEYCODE_MEDIA_PREVIOUS
            val codeStop = KeyEvent.KEYCODE_MEDIA_STOP
            val actionUp = KeyEvent.ACTION_UP
            //val actionDown = KeyEvent.ACTION_DOWN
            val event: KeyEvent? = mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            val keyCode = event?.keyCode ?: 0

            if (event?.action == actionUp) return true

            if (keyCode == codePlay || keyCode == codePause || keyCode == codePlayPause) {
                controlPlay()
            } else if (keyCode == codeNext) {
                controlNext()
            } else if (keyCode == codePrev) {
                controlPrev()
            } else if (keyCode == codeStop) {
                controlPause()
                stopForeground(true)
            }
            return super.onMediaButtonEvent(mediaButtonEvent)
        }
    }

    private fun noisyReceiverInit() {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(noiseReceiver, filter)
    }

    private fun msInit() {
        val mediaReceiverComponent = ComponentName(applicationContext, MediaButtonReceiver::class.java)
        ms = MediaSessionCompat(applicationContext, "TempoSessionTag", mediaReceiverComponent, null)
        ms.setCallback(msCallback)
        ms.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS)

        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.setClass(this, MediaButtonReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0)
        ms.setMediaButtonReceiver(pendingIntent)
    }

    private fun setMetadata(id: Int) {
        //Accepts a SongID and sets media metadata
        Thread(Runnable {
            if (!dbIsInit) Thread.sleep(100)

            val metadata = db?.getMetadataBySongId(id)
            if (metadata != null) {
                ms.setMetadata(metadata)
                createNotification()
            }
        }).start()
    }

    private fun createNotification() {
        //Only called after metadata is set in this.setMetadata(int)
        val controller = ms.controller
        val mediaMetadata = controller.metadata
        //val description = mediaMetadata.description
        val ref = this
        val channelID =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { //Needed for Oreo +
                createNotificationChannel("a", "Player")
            } else ""
        val builder = NotificationCompat.Builder(this, channelID).apply {
            setContentTitle(mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
            setContentText(mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION))
            //setSubText(description.description)
            try {
                setLargeIcon(mediaMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ART))
            } catch (e: Exception) {
            }
            //Opens the player when the notification is clicked.
            val intent = Intent(ref, PlayerActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(ref, 0, intent, 0)
            setContentIntent(pendingIntent)

            //Stop playback when  the notification is swiped or deleted
            setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    ref,
                    PlaybackStateCompat.ACTION_STOP)
            )
            //Enable lockscreen controls
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            //Set app icon
            setSmallIcon(R.drawable.ic_white_notification)
            color = ContextCompat.getColor(ref, R.color.darkBackgroundOverImage)

            //Actions, displayed in order as created
            addAction( //"Previous" media control
                NotificationCompat.Action(  //Icon, Text, Action
                    R.drawable.notification_control_prev,
                    "Previous Song",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        ref,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                )
            )
            addAction( //"Play/Pause" media control
                NotificationCompat.Action(  //Icon, Text, Action
                    if (isPlaying()) {
                        R.drawable.notification_control_pause
                    } else {
                        R.drawable.notification_control_play
                    },
                    if (isPlaying()) {
                        "Pause"
                    } else {
                        "Play"
                    },
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        ref,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                )
            )
            addAction( //"Next" media control
                NotificationCompat.Action(  //Icon, Text, Action
                    R.drawable.notification_control_next,
                    "Next Song",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        ref,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    )
                )
            )
            addAction( //Close button, should stop media player
                NotificationCompat.Action( //Icon, Text, Action
                    R.drawable.notification_close_icon,
                    "Close App",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        ref,
                        PlaybackStateCompat.ACTION_STOP
                    )
                )
            )
            setStyle(
                android.support.v4.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(ms.sessionToken)
                    //Show first 3 actions always (media controls)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            ref,
                            PlaybackStateCompat.ACTION_STOP
                        )
                    )
            )
        }
        startForeground(NotificationCompat.PRIORITY_HIGH, builder.build())
    }

    private fun updateNotification() {
        val controller = ms.controller
        val metadata = controller.metadata
        try {
            val metadataID = metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
            if (curSong.toString() == metadataID) {
                //Update the notification
                createNotification()
            } else {
                //Set metadata and create the notification
                setMetadata(curSong)
            }
        } catch (e: Exception) {
            return
        }
    }

    private fun createNotificationChannel(channelID: String, channelName: String): String {
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                channelID, channelName,
                NotificationManager.IMPORTANCE_NONE
            )
        } else {
            return ""
        }
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelID
    }

    companion object {
        //Constants
        const val BROADCAST_SONG_UPDATE = "BROADCAST_SONG_CHANGED"
        const val BROADCAST_PLAY_PAUSE = "BROADCAST_PLAY_PAUSE"
        const val BROADCAST_SHUFFLE_UPDATE = "BROADCAST_SHUFFLE_UPDATE"
        const val BROADCAST_REPEAT_UPDATE = "BROADCAST_REPEAT_UPDATE"
        const val TAG = "MediaService"
    }
}
