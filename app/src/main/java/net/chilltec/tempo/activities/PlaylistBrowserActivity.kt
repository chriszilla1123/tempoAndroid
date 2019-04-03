package net.chilltec.tempo.activities

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v4.view.GravityCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.PopupMenu
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_playlist_browser.*
import kotlinx.android.synthetic.main.playlist_item.view.*
import net.chilltec.tempo.R
import net.chilltec.tempo.R.id.playlistItemMenuDownloadPlaylist
import net.chilltec.tempo.adapters.PlaylistBrowserAdapter
import net.chilltec.tempo.dataTypes.Album
import net.chilltec.tempo.dataTypes.Artist
import net.chilltec.tempo.dataTypes.Playlist
import net.chilltec.tempo.services.DatabaseService
import net.chilltec.tempo.services.MediaService

class PlaylistBrowserActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var playlistsDB: Array<Playlist>
    private lateinit var artistsDB: Array<Artist>
    private lateinit var albumsDB: Array<Album>
    //private lateinit var songsDB: Array<Song>
    private val ref = this
    private var isDBConnected: Boolean = false

    private var db: DatabaseService? = null
    private val dbConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "Connected to db")
            val binder = service as DatabaseService.LocalBinder
            db = binder.getService()
            isDBConnected = true
            loadAdapter() //Must be called after the database mpConnection is established
        }
        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    var mp: MediaService? = null
    private val mpConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaService.LocalBinder
            mp = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_browser)
        setSupportActionBar(playlistToolbar)
        val toolbar = supportActionBar

        toolbar?.title = if(intent.hasExtra("title")){ intent.getStringExtra("title") }
        else{ "Playlists" }
        toolbar?.subtitle = if(intent.hasExtra("subtitle")){ intent.getStringExtra("subtitle") }
        else{ "" }
        toolbar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
        }

        //Bind the DatabaseService
        val dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)
        //Bind the MediaService
        val bindIntent = Intent(this, MediaService::class.java)
        bindService(bindIntent, mpConnection, Context.BIND_AUTO_CREATE)

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
        playlist_navview.setNavigationItemSelectedListener { menuItem ->
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
        val playlistList = intent.getIntArrayExtra("playlistList")
        playlistsDB = db?.getPlaylistsDB() ?: arrayOf()
        artistsDB = db?.getArtistsDB() ?: arrayOf()
        albumsDB = db?.getAlbumsDB() ?: arrayOf()
        //viewManager = GridLayoutManager(this, 2)
        viewManager = LinearLayoutManager(this)
        viewAdapter = PlaylistBrowserAdapter(playlistsDB, playlistList, ref)

        recyclerView = PlaylistBrowser.apply{
            //Only if changes do not effect size
            setHasFixedSize(true)

            //Grid Layout
            layoutManager = viewManager

            //Pass viewAdapter
            adapter = viewAdapter
        }
    }

    override fun onPause(){
        super.onPause()
        unbindService(dbConnection)
        unbindService(mpConnection)
    }

    override fun onResume(){
        super.onResume()
        val dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)
        //Bind the MediaService
        val bindIntent = Intent(this, MediaService::class.java)
        bindService(bindIntent, mpConnection, Context.BIND_AUTO_CREATE)
    }

    fun onClickHandler(holder: PlaylistBrowserAdapter.PlaylistItemHolder){
        //Start the SongBrowser with songs from the clicked playlist
        Thread(Runnable{
            val playlistID: Int = holder.playlist_item.playlistID.text.toString().toInt()
            val playlistName: String = holder.playlist_item.playlistLabel.text.toString()
            val songsList = db?.getSongListByPlaylistId(playlistID) ?: intArrayOf()
            val intent = Intent(this, SongBrowserActivity::class.java)
            intent.putExtra("songList", songsList)
            intent.putExtra("title", playlistName)
            intent.putExtra("subtitle", "Playlist")
            startActivity(intent)
        }).start()
    }

    fun onLongClickHandler(holder: PlaylistBrowserAdapter.PlaylistItemHolder): Boolean {
        val playlistID: Int = holder.playlist_item.playlistID.text.toString().toInt()
        playlistMenuHandler(holder, playlistID)
        return true
    }

    //init toolbar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem?) = when(item?.itemId) {
        android.R.id.home -> {
            playlist_navdrawer.openDrawer(GravityCompat.START)
            true
        }
        R.id.mainPlayer -> {
            //Open the player
            val intent = Intent(this, PlayerActivity::class.java)
            startActivity(intent)
            true
        }
        R.id.mainSettings -> {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            true
        }
        R.id.mainClearCache -> {
            mp?.clearCache()
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

    //Playlist popup menu
    private fun playlistMenuHandler(holder: PlaylistBrowserAdapter.PlaylistItemHolder, playlistID: Int){
        val menu = PopupMenu(this, holder.itemView)
        menu.inflate(R.menu.playlist_item_menu)
        menu.show()
        menu.setOnMenuItemClickListener {
            when(it.itemId){
                playlistItemMenuDownloadPlaylist -> {
                    Thread(Runnable{
                        if(!isDBConnected) { Thread.sleep(10) }
                        val songList = db?.getSongListByPlaylistId(playlistID) ?: intArrayOf()
                        if(songList.isNotEmpty()){
                            for(songID: Int in songList){
                                mp?.addSongToCacheQueue(songID)
                            }
                        }
                    }).start()
                    true
                }
                else -> {
                    false
                }
            }
        }
    }

    companion object {
        const val TAG = "PlaylistBrowserActivity"
    }
}