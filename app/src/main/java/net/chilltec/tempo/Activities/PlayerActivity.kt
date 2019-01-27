package net.chilltec.tempo.Activities

import android.content.*
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.support.constraint.ConstraintLayout
import android.support.v4.view.GravityCompat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import kotlinx.android.synthetic.main.activity_player.*
import net.chilltec.tempo.*
import net.chilltec.tempo.DataTypes.Album
import net.chilltec.tempo.DataTypes.Artist
import net.chilltec.tempo.DataTypes.Song
import net.chilltec.tempo.Services.DatabaseService
import net.chilltec.tempo.Services.MediaService

class PlayerActivity : AppCompatActivity() {
    private lateinit var artistsDB: Array<Artist>
    private lateinit var albumsDB: Array<Album>
    private lateinit var songsDB: Array<Song>
    val TAG = "PlayerActivityTag"

    private var db: DatabaseService? = null
    private val dbConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DatabaseService.LocalBinder
            db = binder.getService()
            updateSongLables() //Must be called after the database mpConnection is established
            updatePlayButton()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    var mp: MediaService? = null
    var isBound: Boolean = false
    val mpConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaService.LocalBinder
            mp = binder.getService()
            isBound = true

            //update buttonPlay
            updatePlayButton()
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
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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

        //Bind the MediaService
        val bindIntent = Intent(this, MediaService::class.java)
        bindService(bindIntent, mpConnection, Context.BIND_AUTO_CREATE)
        //Bind DatabaseService
        var dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)

        //Initialize the seekbar
        initSeekbar()

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                //toast("Progress is ${seekBar?.progress}%")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                //toast("Progress is ${seekBar?.progress}%")
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                //toast("Progress is ${seekBar?.progress}%")
                var progress = if (seekBar?.progress != null) seekBar.progress else -1
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
        super.onPause()
        unregisterReceiver(songUpdateReceiver)
        unbindService(dbConnection)
        unbindService(mpConnection)
        //finish()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(songUpdateReceiver, IntentFilter("BROADCAST_SONG_CHANGED"))
        //Bind the MediaService
        val bindIntent = Intent(this, MediaService::class.java)
        bindService(bindIntent, mpConnection, Context.BIND_AUTO_CREATE)
        //Bind DatabaseService
        var dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)
    }

    fun onClick_playButton(v: View) {
        mp?.control_play()
        updatePlayButton()
    }

    fun onClick_nextButton(v: View) {
        mp?.control_next()
    }

    fun onClick_prevButton(v: View) { //Set this to the onclick event in the player activity
        mp?.control_prev()
    }

    private fun updateSongLables() {
        //Make this update when a new song starts.
        val songID: Int = mp?.getCurSong() ?: -1
        val artistID: Int = db?.getArtistIdBySongId(songID) ?: -1
        val albumID: Int = db?.getAlbumIdBySongId(songID) ?: -1
        val songArtist: String = db?.getArtistBySongId(songID) ?: ""
        val songAlbum: String = db?.getAlbumBySongId(songID) ?: ""
        val songTitle: String = db?.getTitleBySongId(songID) ?: ""
        val songDuration: Int = mp?.getCurrentDuration() ?: 100
        val hasArtwork = db?.songHasArtwork(songID) ?: false

        playerArtistLable.text = songArtist
        playerAlbumLable.text = songAlbum
        playerTitleLable.text = songTitle
        seekBar.max = songDuration
        if(hasArtwork){
            val albumArtUri = db?.getArtworkUriBySongId(songID)
            if(albumArtUri != null){
                playerArtwork.setImageURI(albumArtUri)
            }
        }
        else{
            playerArtwork.setImageResource(R.drawable.ic_album_black_24dp)
        }
    }

    private fun updatePlayButton() {
        //Sets play button text to "PLAY" is song is paused
        //Sets play button to "PAUSE" is song is playing
        var isPlaying = mp?.isPlaying() ?: false
        if (!isPlaying) buttonPlay.text = "PLAY"
        else buttonPlay.text = "PAUSE"
    }

    private fun initSeekbar() {
        var handler = Handler()
        lateinit var updateProgress: Runnable

        updateProgress = Runnable {
            var curProgress = mp?.getProgress() ?: -1
            if (curProgress == -1) return@Runnable
            seekBar.progress = curProgress
            handler.postDelayed(updateProgress, 1000)
        }
        handler.postDelayed(updateProgress, 1000)
    }

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
