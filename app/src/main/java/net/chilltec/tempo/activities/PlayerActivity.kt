package net.chilltec.tempo.activities

import android.annotation.SuppressLint
import android.content.*
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.support.constraint.ConstraintLayout
import android.support.v4.content.res.ResourcesCompat
import android.support.v4.view.GravityCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.SeekBar
import kotlinx.android.synthetic.main.activity_player.*
import kotlinx.android.synthetic.main.song_queue_item.view.*
import net.chilltec.tempo.*
import net.chilltec.tempo.adapters.SongQueueAdapter
import net.chilltec.tempo.services.DatabaseService
import net.chilltec.tempo.services.MediaService
import org.jetbrains.anko.sdk27.coroutines.onTouch

class PlayerActivity : AppCompatActivity() {
    private var isActive: Boolean = false
    val context = this

    private var db: DatabaseService? = null
    var mp: MediaService? = null
    var isBound: Boolean = false
    private val dbConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DatabaseService.LocalBinder
            db = binder.getService()
            //Then, connect to MediaService
            val bindIntent = Intent(context, MediaService::class.java)
            bindService(bindIntent, mpConnection, Context.BIND_AUTO_CREATE)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }
    //Connect to this only after DatabaseService connection is established
    //onServiceConnected is only called after both MP and DB connections are established.
    val mpConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaService.LocalBinder
            mp = binder.getService()
            isBound = true
            updateSongLabels()
            updatePlayButton()
            updateShuffleButton()
            updateRepeatButton()
            updateSongQueue()
            //Register Receiver
            registerReceiver(playerUpdateReceiver, IntentFilter(MediaService.BROADCAST_SONG_UPDATE))
            registerReceiver(playerUpdateReceiver, IntentFilter(MediaService.BROADCAST_PLAY_PAUSE))
            registerReceiver(playerUpdateReceiver, IntentFilter(MediaService.BROADCAST_SHUFFLE_UPDATE))
            registerReceiver(playerUpdateReceiver, IntentFilter(MediaService.BROADCAST_REPEAT_UPDATE))
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }
    val playerUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MediaService.BROADCAST_SONG_UPDATE -> {
                    Log.i(TAG, "Received song update broadcast")
                    updateSongLabels()
                    updateSongQueue()
                }
                MediaService.BROADCAST_PLAY_PAUSE -> {
                    Log.i(TAG, "Received song play pause broadcast")
                    updatePlayButton()
                }
                MediaService.BROADCAST_SHUFFLE_UPDATE -> {
                    Log.i(TAG, "Received Shuffle Update Broadcast")
                    updateShuffleButton()
                    updateSongQueue()
                }
                MediaService.BROADCAST_REPEAT_UPDATE -> {
                    Log.i(TAG, "Received Repeat Update Broadcast")
                    updateRepeatButton()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isActive = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        setSupportActionBar(playerToolbar)
        val toolbar = supportActionBar
        toolbar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
        }
        val myActivity = findViewById<ConstraintLayout>(R.id.playerLayout)
        myActivity.requestFocus()

        //Bind DatabaseService
        val dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                //Progress is in seconds
                //toast("Progress is ${seekBar?.progress}%")
                val displayMinutes = (progress / 60).toString()
                val displaySeconds = (progress % 60).toString()
                if (displaySeconds.length == 1) {
                    //prepend 0
                    playerCurTimeLable.text = "$displayMinutes:0$displaySeconds"
                } else {
                    //Don't need to prepend 0
                    playerCurTimeLable.text = "$displayMinutes:$displaySeconds"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                //toast("Progress is ${seekBar?.progress}%")
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                //toast("Progress is ${seekBar?.progress}%")
                val progress = if (seekBar?.progress != null) {
                    seekBar.progress * 1000
                } else -1
                Log.i(TAG, "Setting time to $progress ")
                if (progress != -1) mp?.setProgress(progress)
            }

        })
        //init intents
        fun openArtistBrowser() {
            val allArtists = db?.getAllArtistIds() //May be null!
            val intent = Intent(this, ArtistBrowserActivity::class.java)
            intent.putExtra("artistList", allArtists)
            intent.putExtra("title", "All Artists")
            startActivity(intent)
        }
        fun openAlbumBrowser() {
            val allAlbums = db?.getAllAlbumIds() //May be null!
            val intent = Intent(this, AlbumBrowserActivity::class.java)
            intent.putExtra("albumList", allAlbums)
            intent.putExtra("title", "All Albums")
            startActivity(intent)
        }
        fun openSongBrowser() {
            val allSongs = db?.getAllSongIds() //May be null!
            val intent = Intent(this, SongBrowserActivity::class.java)
            intent.putExtra("songList", allSongs)
            intent.putExtra("title", "All Songs")
            startActivity(intent)
        }
        fun openPlaylistBrowser(){
            val allPlaylists = db?.getAllPlaylistIds()
            val intent = Intent(this, PlaylistBrowserActivity::class.java)
            intent.putExtra("playlistList", allPlaylists)
            intent.putExtra("title", "All Playlists")
            startActivity(intent)
        }
        fun openPlayer() {
            val intent = Intent(this, PlayerActivity::class.java)
            startActivity(intent)
        }
        //end init intents

        //Init NavDrawer onClick listeners
        player_navview.setNavigationItemSelectedListener { menuItem ->
            val id = menuItem.itemId
            when(id){
                R.id.nav_artists -> { openArtistBrowser() }
                R.id.nav_albums -> { openAlbumBrowser() }
                R.id.nav_songs -> { openSongBrowser() }
                R.id.nav_playlists -> { openPlaylistBrowser() }
                R.id.nav_player -> { openPlayer() }
            }
            true
        }
        //End Init NavDrawer onClick listeners
    }
    override fun onPause() {
        isActive = false
        super.onPause()
    }
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(playerUpdateReceiver)
        unbindService(dbConnection)
        unbindService(mpConnection)
    }
    override fun onResume() {
        isActive = true
        super.onResume()
        //Bind the MediaService
        val bindIntent = Intent(this, MediaService::class.java)
        bindService(bindIntent, mpConnection, Context.BIND_AUTO_CREATE)
        //Bind DatabaseService
        val dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)

        //Update song times in another thread
        Thread(Runnable {
            while (isActive) {
                updateTimer()
                Thread.sleep(1000)
            }
        }).start()

        //Control onClickDown and onClickUp listeners
        val pressedColor = Color.rgb(80, 80, 80)
        playerButtonPrev.onTouch { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    playerButtonPrev.setColorFilter(pressedColor)
                }
                MotionEvent.ACTION_UP -> {
                    playerButtonPrev.clearColorFilter()
                    mp?.controlPrev()
                }
            }
        }
        playerButtonPlay.onTouch { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    playerButtonPlay.setColorFilter(pressedColor)
                }
                MotionEvent.ACTION_UP -> {
                    playerButtonPlay.clearColorFilter()
                    mp?.controlPlay()
                    updatePlayButton()
                }
            }
        }
        playerButtonNext.onTouch { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    playerButtonNext.setColorFilter(pressedColor)
                }
                MotionEvent.ACTION_UP -> {
                    playerButtonNext.clearColorFilter()
                    mp?.controlNext()
                }
            }
        }
        playerShuffleButton.onTouch { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    playerShuffleButton.setColorFilter(pressedColor)
                }
                MotionEvent.ACTION_UP -> {
                    playerShuffleButton.clearColorFilter()
                    mp?.toggleShuffle()
                }
            }
        }
        playerRepeatButton.onTouch { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    mp?.toggleRepeat()
                }
            }
        }

        //Setup queueLayout
        queueLayout.panelHeight = (queueLayout.panelHeight * 1.1).toInt()
        queueLayout.shadowHeight = 0
        queueLayout.setScrollableView(songQueueBrowser)
    }
    @SuppressLint("SetTextI18n")
    private fun updateSongLabels() {
        //Called whenever the song is updated
        val songID: Int = mp?.getCurSong() ?: -1
        //val artistID: Int = db?.getArtistIdBySongId(songID) ?: -1
        //val albumID: Int = db?.getAlbumIdBySongId(songID) ?: -1
        val songArtist: String = db?.getArtistBySongId(songID) ?: ""
        val songAlbum: String = db?.getAlbumBySongId(songID) ?: ""
        val songTitle: String = db?.getTitleBySongId(songID) ?: ""
        val songDuration: Int = mp?.getCurrentDuration() ?: 100

        if (songArtist.isEmpty()) {
            playerArtistAlbumLable.text = ""
        } else {
            playerArtistAlbumLable.text = "$songArtist - $songAlbum"
        }
        playerTitleLable.text = songTitle

        val songDurationInSeconds = songDuration / 1000 //Convert from milliseconds
        val durationDisplayMinutes = (songDurationInSeconds / 60).toString()
        val durationDisplaySeconds = (songDurationInSeconds % 60).toString()
        val durationDisplay: String //Prepend 0, if necessary
        durationDisplay = if (durationDisplaySeconds.length == 1) {
            "$durationDisplayMinutes:0$durationDisplaySeconds"
        } else "$durationDisplayMinutes:$durationDisplaySeconds"

        seekBar.max = songDurationInSeconds
        playerMaxTimeLable.text = durationDisplay

        updatePlayButton()
        getArtwork()
    }
    private fun getArtwork(){
        //Loads album artwork for the currently playing song, if available.
        Thread(Runnable{
            val songID: Int = mp?.getCurSong() ?: -1
            val hasArtwork = db?.songHasArtwork(songID) ?: false
            if (hasArtwork) {
                val albumArtUri = db?.getArtworkUriBySongId(songID)
                if (albumArtUri != null) {
                    playerArtwork.setImageURI(albumArtUri)
                }
            }
            else {
                playerArtwork.setImageResource(R.drawable.ic_album_black_24dp)
            }
        }).start()
    }
    private fun updatePlayButton() {
        //Sets play button text to "PLAY" is song is paused
        //Sets play button to "PAUSE" is song is playing
        val isPlaying = mp?.isPlaying() ?: false

        if (!isPlaying) {
            val playIcon = ResourcesCompat.getDrawable(resources, R.drawable.player_control_play, null)
            playerButtonPlay.setImageDrawable(playIcon)
        } else {
            val pauseIcon = ResourcesCompat.getDrawable(resources, R.drawable.player_control_pause, null)
            playerButtonPlay.setImageDrawable(pauseIcon)
        }
    }

    private fun updateShuffleButton() {
        val isEnabled = mp?.getShuffleStatus() ?: false
        if (isEnabled) {
            val enabledIcon = ResourcesCompat.getDrawable(resources, R.drawable.shuffle_icon_pressed, null)
            playerShuffleButton.setImageDrawable(enabledIcon)
        } else {
            val disabledIcon = ResourcesCompat.getDrawable(resources, R.drawable.shuffle_icon, null)
            playerShuffleButton.setImageDrawable(disabledIcon)
        }
    }

    private fun updateRepeatButton() {
        val isEnabled = mp?.getRepeatStatus() ?: false
        if (isEnabled) {
            val enabledIcon = ResourcesCompat.getDrawable(resources, R.drawable.repeat_icon_pressed, null)
            playerRepeatButton.setImageDrawable(enabledIcon)
        } else {
            val disabledIcon = ResourcesCompat.getDrawable(resources, R.drawable.repeat_icon, null)
            playerRepeatButton.setImageDrawable(disabledIcon)
        }
    }

    private fun updateTimer() {
        //If a song is currently playing, updates the playerCurTimeLabel
        //Should run in a loop running on its own thread.
        val curTimeInMillis: Int = mp?.getProgress() ?: 0
        val curTimeInSeconds: Int = curTimeInMillis / 1000
        val curDisplayMinutes: String = (curTimeInSeconds / 60).toString()
        var curDisplaySeconds: String = (curTimeInSeconds % 60).toString()
        if (curDisplaySeconds.length == 1) {
            //prepend a 0 if the seconds is less than 10
            curDisplaySeconds = "0$curDisplaySeconds"
        }
        val curSongLabel = "$curDisplayMinutes:$curDisplaySeconds"
        //Log.i(TAG, "Cur time: $curSongLabel")
        playerCurTimeLable.post {
            playerCurTimeLable.text = curSongLabel
        }

        //Update the seekbar
        seekBar.post {
            //seekBar.max is set to the duration, in seconds
            seekBar.progress = curTimeInSeconds
        }
    }

    //Song Queue
    fun updateSongQueue() {
        //Called after the database connection is esablished
        lateinit var viewAdapter: RecyclerView.Adapter<*>
        lateinit var viewManager: RecyclerView.LayoutManager
        val songQueue: IntArray = mp?.getSongQueue() ?: intArrayOf()

        val artistsDB = db?.getArtistsDB() ?: arrayOf()
        val albumsDB = db?.getAlbumsDB() ?: arrayOf()
        val songsDB = db?.getSongsDB() ?: arrayOf()
        val songID: Int = mp?.getCurSong() ?: -1

        if (artistsDB.isEmpty() || albumsDB.isEmpty() || songsDB.isEmpty()) {
            //endActivity()
        }

        viewManager = LinearLayoutManager(this)
        viewAdapter = SongQueueAdapter(artistsDB, albumsDB, songsDB, songQueue, songID, this)

        songQueueBrowser.apply {
            //Only if changes do not effect size
            setHasFixedSize(true)

            //Linear layout
            layoutManager = viewManager

            //Pass viewAdapter
            adapter = viewAdapter
        }
    }

    fun onClickCalled(holder: SongQueueAdapter.SongQueueItemHolder) {
        val songId: Int = holder.song_queue_item.songID.text.toString().toInt()
        mp?.playSongById(songId, true)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onLongClickCalled(holder: SongQueueAdapter.SongQueueItemHolder) {

    }
    //End Song Queue

    //init toolbar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?) = when (item?.itemId) {
        android.R.id.home -> {
            player_navdrawer.openDrawer(GravityCompat.START)
            true
        }
        R.id.mainClearCache -> {
            mp?.clearCache()
            true
        }
        R.id.mainSettings -> {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            true
        }
        R.id.mainRescanPlaylists -> {
            mp?.rescanPlaylists()
            true
        }
        R.id.mainRescanLibrary -> {
            mp?.rescanLibrary()
            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }
    //end init toolbar

    companion object {
        const val TAG = "PlayerActivity"
    }
}
