package net.chilltec.tempo.Activities

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.support.v4.view.GravityCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.PopupMenu
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_album_browser.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_song_browser.*
import kotlinx.android.synthetic.main.song_item.view.*
import net.chilltec.tempo.Services.DatabaseService
import net.chilltec.tempo.Services.MediaService
import net.chilltec.tempo.R
import net.chilltec.tempo.Adapters.SongBrowserAdapter
import net.chilltec.tempo.R.id.songItemAddToPlaylist

class SongBrowserActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var songList: IntArray
    private val TAG = "SongBrowserActivity"

    private var mp: MediaService? = null
    private var isBound: Boolean = false
    private val mpConnection= object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaService.LocalBinder
            mp = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    private var db: DatabaseService? = null
    private val dbConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DatabaseService.LocalBinder
            db = binder.getService()
            loadAdapter() //Must be called after the database connection is established
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    private val ref = this //to pass to the adapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_browser)
        setSupportActionBar(songToolbar)
        var toolbar = supportActionBar
        toolbar?.title = if(intent.hasExtra("title")){ intent.getStringExtra("title") }
                            else{ "Artists" }
        toolbar?.subtitle = if(intent.hasExtra("subtitle")){ intent.getStringExtra("subtitle") }
                            else{ "" }
        toolbar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
        }

        //Bind the MediaService & DatabaseService
        val mpIntent = Intent(this, MediaService::class.java)
        bindService(mpIntent, mpConnection, Context.BIND_AUTO_CREATE)

        var dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)

        //init intents
        fun openArtistBrowser(){
            val allArtists = db?.getAllArtistIds() //May be null!
            val intent = Intent(this, ArtistBrowserActivity::class.java)
            intent.putExtra("artistList", allArtists)
            intent.putExtra("title", "All Artists")
            startActivity(intent)
        }
        fun openAlbumBrowser(){
            val allAlbums = db?.getAllAlbumIds() //May be null!
            val intent = Intent(this, AlbumBrowserActivity::class.java)
            intent.putExtra("albumList", allAlbums)
            intent.putExtra("title", "All Albums")
            startActivity(intent)
        }
        fun openSongBrowser(){
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
        fun openPlayer(){
            val intent = Intent(this, PlayerActivity::class.java)
            startActivity(intent)
        }
        //end init intents

        //Init NavDrawer onClick listeners
        song_navview.setNavigationItemSelectedListener { menuItem ->
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

    fun loadAdapter(){
        //Called after the database connection is esablished
        songList= intent.getIntArrayExtra("songList")
        val artistsDB = db?.getArtistsDB() ?: arrayOf()
        val albumsDB = db?.getAlbumsDB() ?: arrayOf()
        val songsDB = db?.getSongsDB() ?: arrayOf()

        if(artistsDB.isEmpty() || albumsDB.isEmpty() || songsDB.isEmpty()){
            endActivity()
        }

        viewManager = LinearLayoutManager(this)
        viewAdapter = SongBrowserAdapter(artistsDB, albumsDB, songsDB, songList, ref)

        recyclerView = SongBrowser.apply{
            //Only if changes do not effect size
            setHasFixedSize(true)

            //Linear layout
            layoutManager = viewManager

            //Pass viewAdapter
            adapter = viewAdapter
        }
    }

    override fun onPause(){
        super.onPause()

    }

    override fun onResume(){
        super.onResume()
        //Bind the MediaService & DatabaseService
        val mpIntent = Intent(this, MediaService::class.java)
        bindService(mpIntent, mpConnection, Context.BIND_AUTO_CREATE)

        var dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy(){
        super.onDestroy()
        unbindService(dbConnection)
        unbindService(mpConnection)
        Log.i(TAG, "Unbinding...")
    }

    fun onClickCalled(holder: SongBrowserAdapter.SongItemHolder){
        val songId: Int = holder.song_item.songID.text.toString().toInt()
        mp?.setSongList(songList)
        mp?.playSongById(songId, true)
    }

    fun onLongClickCalled(holder: SongBrowserAdapter.SongItemHolder): Boolean{
        val songId: Int = holder.song_item.songID.text.toString().toInt()
        Log.i("SongBrowserActivity", "Long Click $songId")
        createSongPopup(holder)
        return true
    }

    fun createSongPopup(holder: SongBrowserAdapter.SongItemHolder){
        val menu = PopupMenu(this, holder.itemView)
        menu.inflate(R.menu.song_item_menu)
        menu.show()
        menu.setOnMenuItemClickListener{
            when(it.itemId){
                songItemAddToPlaylist -> {
                    playlistPopup(holder)
                    true
                }
                else -> {
                    false
                }
            }
        }
    }

    fun playlistPopup(holder: SongBrowserAdapter.SongItemHolder){
        //Show a popup menu with all the playlists
        val songId = holder.song_item.songID.text.toString().toInt()
        val songDir = db?.getSongDirBySongId(songId) ?: ""
        if(songDir.length >= 0){
            Log.i(TAG,"Attempting to add $songDir to playlist")
            val playlistMenu = PopupMenu(this, holder.itemView)
            val playlistList = db?.getAllPlaylistIds() ?: intArrayOf()
            Log.i(TAG, "Num Playlists: ${playlistList.size}")
            for(playlistID in playlistList){
                val playlistName = db?.getPlaylistNameByPlaylistId(playlistID) ?: "test"
                playlistMenu.menu.add(playlistName)
            }

            playlistMenu.show()
            playlistMenu.setOnMenuItemClickListener { menuItem ->
                //TODO: Add onClick listener to add song to playlist
                val playlistName = menuItem.title.toString()
                val playlistID = db?.getPlaylistIDByPlaylistName(playlistName) ?: 0
                val songID = holder.song_item.songID.text.toString().toInt()
                if(playlistID >= 1 && songID >= 1){
                    db?.addSongToPlaylist(playlistID, songID)
                }
                true
            }
        }
    }

    fun endActivity(){
        this.unbindService(mpConnection)
        this.unbindService(dbConnection)
        finish()
    }

    //init toolbar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem?) = when(item?.itemId) {
        android.R.id.home -> {
            song_navdrawer.openDrawer(GravityCompat.START)
            true
        }
        R.id.mainPlayer -> {
            //Open the player
            val intent = Intent(this, PlayerActivity::class.java)
            startActivity(intent)
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
