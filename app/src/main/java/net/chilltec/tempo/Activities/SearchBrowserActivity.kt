package net.chilltec.tempo.Activities

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_search_browser.*
import kotlinx.android.synthetic.main.album_item.view.*
import kotlinx.android.synthetic.main.artist_item.view.*
import kotlinx.android.synthetic.main.song_item.view.*
import net.chilltec.tempo.Adapters.SearchBrowserAdapter
import net.chilltec.tempo.DataTypes.Album
import net.chilltec.tempo.DataTypes.Artist
import net.chilltec.tempo.DataTypes.Song
import net.chilltec.tempo.R
import net.chilltec.tempo.Services.DatabaseService
import net.chilltec.tempo.Services.MediaService

class SearchBrowserActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var artistList: IntArray
    private lateinit var albumList: IntArray
    private lateinit var songList: IntArray
    private lateinit var artistsDB: Array<Artist>
    private lateinit var albumsDB: Array<Album>
    private lateinit var songsDB: Array<Song>
    private var searchTerm = ""

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
        setContentView(R.layout.activity_search_browser)
        setSupportActionBar(searchToolbar)
        var toolbar = supportActionBar
        toolbar?.title = if(intent.hasExtra("title")){ intent.getStringExtra("title") }
            else{ "Search Results" }
        toolbar?.subtitle = if(intent.hasExtra("subtitle")){ intent.getStringExtra("subtitle") }
            else{ "" }
        toolbar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_black_24dp)
        }

        //Bind the MediaService & DatabaseService
        val mpIntent = Intent(this, MediaService::class.java)
        bindService(mpIntent, mpConnection, Context.BIND_AUTO_CREATE)

        var dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)

        //If created by Intent
        handleIntent(intent)

        //Get search term
        if(Intent.ACTION_SEARCH == intent.action){
            searchTerm = intent.getStringExtra(SearchManager.QUERY)
        }

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
        search_navview.setNavigationItemSelectedListener { menuItem ->
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
        //Called after the database connection is established.
        artistsDB = db?.getArtistsDB() ?: arrayOf()
        albumsDB = db?.getAlbumsDB() ?: arrayOf()
        songsDB = db?.getSongsDB() ?: arrayOf()
        val result: Triple<IntArray, IntArray, IntArray>? = db?.search(searchTerm)
        artistList = result?.first ?: intArrayOf()
        albumList = result?.second ?: intArrayOf()
        songList = result?.third ?: intArrayOf()

        if(artistsDB.isEmpty() || albumsDB.isEmpty() || songsDB.isEmpty()){
            endActivity()
        }

        viewManager = LinearLayoutManager(this)
        viewAdapter = SearchBrowserAdapter(
            artistsDB, albumsDB, songsDB,
            artistList, albumList, songList, ref
        )

        recyclerView = searchBrowser.apply{
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }
    }

    override fun onNewIntent(intent: Intent){
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent){
        if(Intent.ACTION_SEARCH == intent.action){
            Log.i("SearchService", intent.getStringExtra(SearchManager.QUERY))
        }
    }

    override fun onPause(){
        super.onPause()
        unbindService(dbConnection)
        unbindService(mpConnection)
    }

    override fun onResume(){
        super.onResume()
        //Bind the MediaService & DatabaseService
        val mpIntent = Intent(this, MediaService::class.java)
        bindService(mpIntent, mpConnection, Context.BIND_AUTO_CREATE)

        var dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)
    }

    fun endActivity(){

    }

    //Click Handlers
    fun artistOnClickHandler(holder: SearchBrowserAdapter.ArtistItemHolder){
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
    fun artistOnLongClickHandler(holder: SearchBrowserAdapter.ArtistItemHolder){

    }
    fun albumOnClickHandler(holder: SearchBrowserAdapter.AlbumItemHolder){
        //Start the SongBrowser with songs from the clicked album
        var albumID: Int = holder.album_item.albumID.text.toString().toInt()
        var albumName: String = holder.album_item.albumLable.text.toString()
        var artistName: String = holder.album_item.albumArtistLable.text.toString()
        var songsMutableList = mutableListOf<Int>()
        for(song in songsDB){
            if(song.album == albumID){
                songsMutableList.add(song.id)
            }
        }
        var songsList: IntArray = songsMutableList.toIntArray()
        val intent = Intent(this, SongBrowserActivity::class.java)
        intent.putExtra("songList", songsList)
        intent.putExtra("title", albumName)
        intent.putExtra("subtitle", artistName)
        startActivity(intent)
    }
    fun albumOnLongClickHandler(holder: SearchBrowserAdapter.AlbumItemHolder){

    }
    fun songOnClickHandler(holder: SearchBrowserAdapter.SongItemHolder){
        val songId: Int = holder.song_item.songID.text.toString().toInt()
        mp?.setSongList(songList)
        mp?.playSongById(songId, true)
    }
    fun songOnLongClickHandler(holder: SearchBrowserAdapter.SongItemHolder){

    }
}