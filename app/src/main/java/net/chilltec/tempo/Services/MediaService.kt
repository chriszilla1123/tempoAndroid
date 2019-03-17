package net.chilltec.tempo.Services

import android.app.*
import android.content.*
import android.graphics.Color
import android.media.*
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.*
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import net.chilltec.tempo.Activities.PlayerActivity
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
    private val baseUrl = "http://www.chrisco.top/api"
    private val TAG = "MediaService"
    private var songSet: IntArray = intArrayOf()
    private var shuffleSet: IntArray = intArrayOf()
    private var playedSet: IntArray = intArrayOf()
    private var songQueue: IntArray = intArrayOf()
    private var curSong: Int = -1
    private var nextSong: Int = -1
    private var cacheQueue: Queue<Int> = LinkedList()

    //Flags
    private var mpIsSafe: Boolean = true
    private var curDownloading: Boolean = false
    private var isStreaming: Boolean = false
    private var dbIsInit: Boolean = false
    private var shuffleEnabled: Boolean = false
    private var repeatEnabled: Boolean = false
    private var hasRepeated: Boolean = false
    private var streamingEnabled: Boolean = true

    //Constants
    val BROADCAST_SONG_UPDATE = "BROADCAST_SONG_CHANGED"
    val BROADCAST_PLAY_PAUSE = "BROADCAST_PLAY_PAUSE"
    val BROADCAST_SHUFFLE_UPDATE = "BROADCAST_SHUFFLE_UPDATE"
    val BROADCAST_REPEAT_UPDATE = "BROADCAST_REPEAT_UPDATE"

    var db: DatabaseService? = null
    val DBconnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DatabaseService.LocalBinder
            db = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    override fun onCreate() {
        Log.i(TAG, "Media Service Created")

        //Bind to DatabaseService
        var DBintent = Intent(this, DatabaseService::class.java)
        bindService(DBintent, DBconnection, Context.BIND_AUTO_CREATE)

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
        unbindService(DBconnection)
    }
    inner class LocalBinder : Binder() {
        fun getService(): MediaService {
            return this@MediaService
        }
    }
    fun playerInit() {
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
                control_next()
            }
            setOnErrorListener { mp, what, extra ->
                Log.i(TAG, "onError")
                Log.i(TAG, "${what.toString()}, $extra")
                mp.reset()
                true
            }
        }
    }
    fun playSongById(songID: Int, force: Boolean = false) {
        //Plays a given song, and pre-loads the next song, if applicable
        //If force is true, the cacheQueue will be cleared and songID pushed to the front
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
                    cacheQueue.clear()
                    cacheQueue.add(curSong)
                    getInternetSong()
                }
                else if(curSong !in cacheQueue){
                    cacheQueue.add(curSong)
                    if(!curDownloading) getInternetSong()
                }
            }
            if(!isCached(nextSong)){
                cacheQueue.add(nextSong)
                if(!curDownloading) getInternetSong()
            }
        }).start()
    }
    private fun getInternetSong() {
        //Plays song from the internet, either by downloading it to cache or streaming.
        if (cacheQueue.isEmpty()) return
        curDownloading = true
        val songId = cacheQueue.remove()
        if(songId < 1) return
        val songFile = File(cacheDir, "$songId.song")
        if (songFile.exists() && songFile.length() > 0) {
            //Already downloaded, download next item in cacheQueue if applicable
            if (cacheQueue.isNotEmpty()) { getInternetSong() }
            return
        }
        val isWifiConnected = getWifiStatus()
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
                if (respBytes.size == 0) {
                    Log.i(TAG, "ERROR downloading song $songId to cache")
                } else {
                    songFile.writeBytes(respBytes)
                    Log.i(TAG, "Successfully cached song $songId")
                    if (cacheQueue.isNotEmpty()) getInternetSong()
                    else curDownloading = false
                }
            }
        })
    }
    fun streamSong(id: Int){
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
    fun playSongFromFile(file: File): Boolean{
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
    fun getSongFile(id: Int): File? {
        //Returns the file for the given song, if it exists
        val songFileLoc = "$id.song"
        val songFile = File(cacheDir, songFileLoc)
        if(songFile.exists()){ return songFile }
        else { return null }
    }
    fun isCached(id: Int): Boolean{
        //Returns true if the given songID has a downloaded file in cache
        val songFileLoc = "$id.song"
        val songFile = File(cacheDir, songFileLoc)
        return songFile.exists()
    }
    //Cache info
    fun getCachedSongList(): IntArray{
        //Disc access, call this from inside non-UI thread
        var songList = mutableListOf<Int>()
        cacheDir.walkTopDown().forEach {
            if(it.exists() &&  it.extension == "song"){
                var songID = it.nameWithoutExtension.toIntOrNull() ?: -1
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
            var deletedFiles: Int = 0
            var sizeInBytes: Long = 0
            cacheDir.walkTopDown().forEach {
                if (it.exists() && it.name != curFile && it.name != nextFile) {
                    sizeInBytes += it.length()
                    it.delete()
                    deletedFiles++
                }
            }
            var sizeInMb: Int = (sizeInBytes.toDouble() / 1000000.toDouble()).roundToInt()

            this.runOnUiThread {
                toast("Cleared ${sizeInMb}MB from $deletedFiles files in cache")
            }
            Log.i(TAG, "Cleared ${sizeInMb}MB from $deletedFiles files in cache")
        }).start()
    }
    //End Cache Info
    fun updateCurNextSong(id: Int) {
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
    fun getWifiStatus(): Boolean {
        //Returns true if wifi is connected, otherwise false.
        val connManager = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = connManager.activeNetworkInfo
        return (activeNetwork?.type == ConnectivityManager.TYPE_WIFI)
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
    fun shuffler(list: IntArray): IntArray {
        //Shuffles a given IntArray containing song IDs
        var listCopy = list.toMutableList()
        var shuffledList = mutableListOf<Int>()
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

    //Broadcasts
    fun sendSongUpdateBroadcast() {
        Log.i(TAG, "Sending song update request")
        Intent().also { intent ->
            intent.action = BROADCAST_SONG_UPDATE
            sendBroadcast(intent)
        }
    }

    fun sendPlayPauseBroadcast() {
        Log.i(TAG, "Sending PlayPause Notification")
        Intent().also { intent ->
            intent.action = BROADCAST_PLAY_PAUSE
            sendBroadcast(intent)
        }
    }

    fun sendShuffleUpdateBroadcast() {
        Log.i(TAG, "Sending Shuffle Update Notification")
        Intent().also { intent ->
            intent.action = BROADCAST_SHUFFLE_UPDATE
            sendBroadcast(intent)
        }
    }

    fun sendRepeatUpdateBroadcast() {
        Log.i(TAG, "Sending Repeat Update Notification")
        Intent().also { intent ->
            intent.action = BROADCAST_REPEAT_UPDATE
            sendBroadcast(intent)
        }
    }
    //End Broadcasts

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

    fun getSongList(): IntArray {
        //Returns the songs that are in the current song set.
        //This is not necessarily the order songs will be played in.
        return this.songSet
    }

    fun getSongQueue(): IntArray {
        //Returns the songs in the current song set, in the order they will be played.
        return this.songQueue
    }

    fun setProgress(time: Int) {
        //Sets the current song to the given time, in milliseconds
        //Accepts a value between 0 and mp.duration
        try{
            var curDuration = getCurrentDuration()
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
        try {
            return mp.currentPosition
        } catch (e: Exception) {
            return 0
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
        var duration: Int = 0
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
    fun control_prev() {
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

    fun control_play() {
        //Functionality for all play/pause buttons across tempo. Handles both playing
        //and pausing.
        Log.i(TAG, "Control_Play")
        if (mp.isPlaying) mp.pause()
        else mp.start()
        updateNotification()
        sendPlayPauseBroadcast()
    }

    fun control_pause() {
        //Handles only pausing, does nothing if the player is already paused.
        //Used to handle bluetooth / headphone disconnections
        if (mp.isPlaying) mp.pause()
        updateNotification()
    }

    fun control_next() {
        Log.i(TAG, "Control_Next")

        if (mpIsSafe) mp.reset()
        else return

        var toPlay: Int = nextSong
        var songQueueIndex: Int = songQueue.indexOf(toPlay)

        if (toPlay != -1) {
            if (songQueue.size > (songQueueIndex + 1)) {
                nextSong = songQueue[songQueueIndex + 1]
            } else {
                nextSong = -1
            }
            playSongById(toPlay, true)
            curSong = toPlay
        } else {
            Log.i(TAG, "Hit end of queue")
        }
    }

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
            control_pause()
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
            val actionDown = KeyEvent.ACTION_DOWN
            var event: KeyEvent? = mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            val keyCode = event?.keyCode ?: 0

            if (event?.action == actionUp) return true

            if (keyCode == codePlay || keyCode == codePause || keyCode == codePlayPause) {
                control_play()
            } else if (keyCode == codeNext) {
                control_next()
            } else if (keyCode == codePrev) {
                control_prev()
            } else if (keyCode == codeStop) {
                control_pause()
                stopForeground(true)
            }
            return super.onMediaButtonEvent(mediaButtonEvent)
        }
    }

    private fun noisyReceiverInit() {
        var filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(noiseReceiver, filter)
    }

    private fun msInit() {
        var mediaReceiverComponent = ComponentName(applicationContext, MediaButtonReceiver::class.java)
        ms = MediaSessionCompat(applicationContext, "TempoSessionTag", mediaReceiverComponent, null)
        ms.setCallback(msCallback)
        ms.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS)

        var mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.setClass(this, MediaButtonReceiver::class.java)
        var pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0)
        ms.setMediaButtonReceiver(pendingIntent)
    }

    fun setMetadata(id: Int) {
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

    fun updateNotification() {
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

    fun createNotificationChannel(channelID: String, channelName: String): String {
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
}
