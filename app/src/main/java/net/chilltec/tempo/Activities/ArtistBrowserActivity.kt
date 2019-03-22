package net.chilltec.tempo.Activities

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v4.view.GravityCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.PopupMenu
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem

import kotlinx.android.synthetic.main.activity_artist_browser.*
import kotlinx.android.synthetic.main.artist_item.view.*
import net.chilltec.tempo.*
import net.chilltec.tempo.Adapters.ArtistBrowserAdapter
import net.chilltec.tempo.DataTypes.Album
import net.chilltec.tempo.DataTypes.Artist
import net.chilltec.tempo.Services.DatabaseService
import net.chilltec.tempo.Services.MediaService

class ArtistBrowserActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var artistsDB: Array<Artist>
    private lateinit var albumsDB: Array<Album>
    private val ref = this
    private val TAG = "ArtistBrowserActivity"

    private var db: DatabaseService? = null
    private val dbConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "Connected to db")
            val binder = service as DatabaseService.LocalBinder
            db = binder.getService()
            loadAdapter() //Must be called after the database mpConnection is established
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    var mp: MediaService? = null
    val mpConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaService.LocalBinder
            mp = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artist_browser)
        setSupportActionBar(artistToolbar)
        var toolbar = supportActionBar

        toolbar?.title = if(intent.hasExtra("title")){ intent.getStringExtra("title") }
                            else{ "Artists" }
        toolbar?.subtitle = if(intent.hasExtra("subtitle")){ intent.getStringExtra("subtitle") }
                            else{ "" }
        toolbar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
        }


        if(intent.hasExtra("title")){
            toolbar?.title = intent.getStringExtra("title")
        }
        else{
            toolbar?.title = "Albums"
        }

        //Bind the DatabaseService
        var dbIntent = Intent(this, DatabaseService::class.java)
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
        artist_navview.setNavigationItemSelectedListener { menuItem ->
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
        var artistList = intent.getIntArrayExtra("artistList")
        artistsDB = db?.getArtistsDB() ?: arrayOf()
        albumsDB = db?.getAlbumsDB() ?: arrayOf()
        viewManager = GridLayoutManager(this, 2)
        //viewManager = LinearLayoutManager(this)
        viewAdapter = ArtistBrowserAdapter(artistsDB, artistList, ref)

        recyclerView = ArtistBrowser.apply{
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
        //Bind the DatabaseService
        val dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)
        //Bind the MediaService
        val bindIntent = Intent(this, MediaService::class.java)
        bindService(bindIntent, mpConnection, Context.BIND_AUTO_CREATE)
    }

    fun onClickHandler(holder: ArtistBrowserAdapter.ArtistItemHolder){
        //Start the AlbumBrowser with albums from the clicked artist
        var artistID: Int = holder.artist_item.artistID.text.toString().toInt()
        var artistName: String = holder.artist_item.artistLable.text.toString()
        var albumsMutableList = mutableListOf<Int>()
        for(album in albumsDB){
            if(album.artist == artistID){
                albumsMutableList.add(album.id)
            }
        }
        var albumsList: IntArray = albumsMutableList.toIntArray()
        val intent = Intent(this, AlbumBrowserActivity::class.java)
        intent.putExtra("albumList", albumsList)
        intent.putExtra("title", artistName)
        intent.putExtra("subtitle", "All albums")
        startActivity(intent)
    }

    fun onLongClickHandler(holder: ArtistBrowserAdapter.ArtistItemHolder): Boolean {
        var artistID: Int = holder.artist_item.artistID.text.toString().toInt()
        Log.i("ArtistBrowserActivity", "Long click artist $artistID")

        //
        val popup = PopupMenu(this, holder.itemView)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(R.menu.artist_item_menu, popup.menu)
        popup.show()
        popup.setOnMenuItemClickListener { menuItem ->
            val id = menuItem.itemId
            when(id){
                R.id.artistItemMenuPlayAll -> {
                    val songID: Int? = db?.getSongIdByArtistId(artistID)
                    val songList: IntArray? = db?.getSongListByArtistId(artistID)
                    if(songID != null && songID != -1 && songList != null && songList.isNotEmpty()){
                        mp?.setSongList(songList)
                        mp?.playSongById(songID, true)
                    }
                }
            }
            true
        }
        //

        return true
    }

    //init toolbar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem?) = when(item?.itemId) {
        android.R.id.home -> {
            artist_navdrawer.openDrawer(GravityCompat.START)
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
}
