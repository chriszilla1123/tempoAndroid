package net.chilltec.tempo.Activities

import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
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
import android.view.View
import android.widget.SeekBar
import kotlinx.android.synthetic.main.activity_player.*
import kotlinx.android.synthetic.main.album_item.*
import kotlinx.android.synthetic.main.song_queue_item.view.*
import net.chilltec.tempo.*
import net.chilltec.tempo.Adapters.SongBrowserAdapter
import net.chilltec.tempo.Adapters.SongQueueAdapter
import net.chilltec.tempo.DataTypes.Album
import net.chilltec.tempo.DataTypes.Artist
import net.chilltec.tempo.DataTypes.Song
import net.chilltec.tempo.Services.DatabaseService
import net.chilltec.tempo.Services.MediaService
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.sdk27.coroutines.onTouch
import java.util.concurrent.TimeUnit

class PlayerActivity : AppCompatActivity() {
    private lateinit var artistsDB: Array<Artist>
    private lateinit var albumsDB: Array<Album>
    private lateinit var songsDB: Array<Song>
    private var isActive: Boolean = false
    val TAG = "PlayerActivity"
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
    //Connect to this only after DatabaseService connection is establshed
    //onServiceConnected is only called after both MP and DB connections are established.
    val mpConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaService.LocalBinder
            mp = binder.getService()
            isBound = true
            updateSongLables()
            updatePlayButton()
            loadQueueAdapter()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    val songUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "BROADCAST_SONG_CHANGED" -> {
                    Log.i(TAG, "Received broadcast")
                    updateSongLables()
                    loadQueueAdapter()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isActive = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        setSupportActionBar(playerToolbar)
        var toolbar = supportActionBar
        toolbar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
        }

        var myActivity = findViewById<ConstraintLayout>(R.id.playerLayout)
        myActivity.requestFocus()

        //Bind DatabaseService
        var dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                //Progress is in seconds
                //toast("Progress is ${seekBar?.progress}%")
                val displayMinutes = (progress / 60).toString()
                val displaySeconds = (progress % 60).toString()
                if(displaySeconds.length == 1){
                    //prepend 0
                    playerCurTimeLable.text = "$displayMinutes:0$displaySeconds"
                }
                else{
                    //Don't need to prepend 0
                    playerCurTimeLable.text = "$displayMinutes:$displaySeconds"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                //toast("Progress is ${seekBar?.progress}%")
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                //toast("Progress is ${seekBar?.progress}%")
                var progress = if (seekBar?.progress != null) {
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

        fun openPlayer() {
            val intent = Intent(this, PlayerActivity::class.java)
            startActivity(intent)
        }
        //end init intents

        //Init NavDrawer onClick listeners
        player_navview.setNavigationItemSelectedListener { menuItem ->
            val id = menuItem.itemId
            when (id) {
                R.id.nav_artists -> {
                    openArtistBrowser()
                }
                R.id.nav_albums -> {
                    openAlbumBrowser()
                }
                R.id.nav_songs -> {
                    openSongBrowser()
                }
                R.id.nav_player -> {
                    openPlayer()
                }
            }
            true
        }
        //End Init NavDrawer onClick listeners
    }

    override fun onPause() {
        isActive = false
        super.onPause()
        unregisterReceiver(songUpdateReceiver)
        unbindService(dbConnection)
        unbindService(mpConnection)
        //finish()
    }

    override fun onResume() {
        isActive = true
        super.onResume()
        registerReceiver(songUpdateReceiver, IntentFilter("BROADCAST_SONG_CHANGED"))
        //Bind the MediaService
        val bindIntent = Intent(this, MediaService::class.java)
        bindService(bindIntent, mpConnection, Context.BIND_AUTO_CREATE)
        //Bind DatabaseService
        var dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)

        //Update song times in another thread
        Thread(Runnable {
            while(isActive){
                updateTimer()
                Thread.sleep(1000)
            }
        }).start()

        //Control onClickDown and onClickUp listeners
        val pressedColor = Color.rgb(80, 80, 80)
        playerButtonPrev.onTouch { v, event ->
            when(event.action){
                MotionEvent.ACTION_DOWN -> {
                    playerButtonPrev.setColorFilter(pressedColor)
                }
                MotionEvent.ACTION_UP -> {
                    playerButtonPrev.clearColorFilter()
                    mp?.control_prev()
                }
            }
        }
        playerButtonPlay.onTouch { v, event ->
            when(event.action){
                MotionEvent.ACTION_DOWN -> {
                    playerButtonPlay.setColorFilter(pressedColor)
                }
                MotionEvent.ACTION_UP -> {
                    playerButtonPlay.clearColorFilter()
                    mp?.control_play()
                    updatePlayButton()
                }
            }
        }
        playerButtonNext.onTouch { v, event ->
            when(event.action){
                MotionEvent.ACTION_DOWN -> {
                    playerButtonNext.setColorFilter(pressedColor)
                }
                MotionEvent.ACTION_UP -> {
                    playerButtonNext.clearColorFilter()
                    mp?.control_next()
                }
            }
        }

        //Setup queueLayout
        queueLayout.panelHeight = (queueLayout.panelHeight * 1.1).toInt()
        queueLayout.shadowHeight = 0
        queueLayout.setScrollableView(songQueueBrowser)
    }

    private fun updateSongLables() {
        //Called whenever the song is updated
        val songID: Int = mp?.getCurSong() ?: -1
        val artistID: Int = db?.getArtistIdBySongId(songID) ?: -1
        val albumID: Int = db?.getAlbumIdBySongId(songID) ?: -1
        val songArtist: String = db?.getArtistBySongId(songID) ?: ""
        val songAlbum: String = db?.getAlbumBySongId(songID) ?: ""
        val songTitle: String = db?.getTitleBySongId(songID) ?: ""
        val songDuration: Int = mp?.getCurrentDuration() ?: 100
        val hasArtwork = db?.songHasArtwork(songID) ?: false

        if(songArtist.length == 0) {
            playerArtistAlbumLable.text = ""
        }
        else {
            playerArtistAlbumLable.text = "$songArtist - $songAlbum"
        }
        playerTitleLable.text = songTitle

        val songDurationInSeconds = songDuration / 1000 //Convert from milliseconds
        val durationDisplayMinutes = (songDurationInSeconds / 60).toString()
        val durationDisplaySeconds = (songDurationInSeconds % 60).toString()
        val durationDisplay: String //Prepend 0, if necessary
        if(durationDisplaySeconds.length == 1){
            durationDisplay = "$durationDisplayMinutes:0$durationDisplaySeconds"
        }
        else durationDisplay ="$durationDisplayMinutes:$durationDisplaySeconds"

        seekBar.max = songDurationInSeconds
        playerMaxTimeLable.text = durationDisplay

        if(hasArtwork){
            val albumArtUri = db?.getArtworkUriBySongId(songID)
            if(albumArtUri != null){
                playerArtwork.setImageURI(albumArtUri)
            }
        }
        else{
            playerArtwork.setImageResource(R.drawable.ic_album_black_24dp)
        }

        updatePlayButton()
    }

    private fun updatePlayButton() {
        //Sets play button text to "PLAY" is song is paused
        //Sets play button to "PAUSE" is song is playing
        var isPlaying = mp?.isPlaying() ?: false
        val playIcon = ResourcesCompat.getDrawable(resources, R.drawable.player_control_play, null)
        val pauseIcon = ResourcesCompat.getDrawable(resources, R.drawable.player_control_pause, null)

        if (!isPlaying) playerButtonPlay.setImageDrawable(playIcon)
        else playerButtonPlay.setImageDrawable(pauseIcon)
    }

    private fun updateTimer() {
        //If a song is currently playing, updates the playerCurTimeLable
        //Should run in a loop running on its own thread.
        val curTimeInMillis: Int = mp?.getProgress() ?: 0
        val curTimeInSeconds: Int = curTimeInMillis / 1000
        val curDisplayMinutes: String = (curTimeInSeconds / 60).toString()
        var curDisplaySeconds: String = (curTimeInSeconds % 60).toString()
        if(curDisplaySeconds.length == 1) {
            //prepend a 0 if the seconds is less than 10
            curDisplaySeconds = "0$curDisplaySeconds"
        }
        val curSongLable = "$curDisplayMinutes:$curDisplaySeconds"
        //Log.i(TAG, "Cur time: $curSongLable")
        playerCurTimeLable.post {
            playerCurTimeLable.text = curSongLable
        }

        //Update the seekbar
        seekBar.post {
            //seekBar.max is set to the duration, in seconds
            seekBar.progress = curTimeInSeconds
        }
    }

    //Song Queue
    fun loadQueueAdapter(){
        //Called after the database connection is esablished
        lateinit var recyclerView: RecyclerView
        lateinit var viewAdapter: RecyclerView.Adapter<*>
        lateinit var viewManager: RecyclerView.LayoutManager
        var songQueue: IntArray = mp?.getSongList() ?: intArrayOf()

        val artistsDB = db?.getArtistsDB() ?: arrayOf()
        val albumsDB = db?.getAlbumsDB() ?: arrayOf()
        val songsDB = db?.getSongsDB() ?: arrayOf()
        val songID: Int = mp?.getCurSong() ?: -1

        if(artistsDB.isEmpty() || albumsDB.isEmpty() || songsDB.isEmpty()){
            //endActivity()
        }

        viewManager = LinearLayoutManager(this)
        viewAdapter = SongQueueAdapter(artistsDB, albumsDB, songsDB, songQueue, songID, this)

        recyclerView = songQueueBrowser.apply{
            //Only if changes do not effect size
            setHasFixedSize(true)

            //Linear layout
            layoutManager = viewManager

            //Pass viewAdapter
            adapter = viewAdapter
        }
    }

    fun onClickCalled(holder: SongQueueAdapter.SongQueueItemHolder){
        val songId: Int = holder.song_queue_item.songID.text.toString().toInt()
        mp?.playSongById(songId, true)
    }

    fun onLongClickCalled(holder: SongQueueAdapter.SongQueueItemHolder){

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
        R.id.mainRescanLibrary -> {
            mp?.rescanLibrary()
            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }
    //end init toolbar
}
