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
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_album_browser.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.album_item.view.*
import net.chilltec.tempo.*
import net.chilltec.tempo.Adapters.AlbumBrowserAdapter
import net.chilltec.tempo.DataTypes.Album
import net.chilltec.tempo.DataTypes.Artist
import net.chilltec.tempo.DataTypes.Song
import net.chilltec.tempo.Services.DatabaseService
import net.chilltec.tempo.Services.MediaService

class AlbumBrowserActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var artistsDB: Array<Artist>
    private lateinit var albumsDB: Array<Album>
    private lateinit var songsDB: Array<Song>

    private val ref = this

    private var db: DatabaseService? = null
    private val dbConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
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
        setContentView(R.layout.activity_album_browser)
        setSupportActionBar(albumToolbar)
        var toolbar = supportActionBar
        toolbar?.title = if(intent.hasExtra("title")){ intent.getStringExtra("title") }
                            else{ "Albums" }
        toolbar?.subtitle = if(intent.hasExtra("subtitle")){ intent.getStringExtra("subtitle") }
                                else{ "" }
        toolbar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
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
        fun openPlayer(){
            val intent = Intent(this, PlayerActivity::class.java)
            startActivity(intent)
        }
        //end init intents

        //Init NavDrawer onClick listeners
        album_navview.setNavigationItemSelectedListener { menuItem ->
            val id = menuItem.itemId
            when(id){
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

    fun loadAdapter(){
        val albumList = intent.getIntArrayExtra("albumList")
        artistsDB = db?.getArtistsDB() ?: arrayOf()
        albumsDB = db?.getAlbumsDB() ?: arrayOf()
        songsDB = db?.getSongsDB() ?: arrayOf()

        if(artistsDB.isEmpty() || albumsDB.isEmpty() || songsDB.isEmpty()){
            endActivity()
        }

        viewManager = LinearLayoutManager(this)
        viewAdapter = AlbumBrowserAdapter(artistsDB, albumsDB, albumList, ref)

        recyclerView = AlbumBrowser.apply{
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
        unbindService(dbConnection)
        unbindService(mpConnection)
    }

    override fun onResume(){
        super.onResume()
        //Bind the DatabaseService
        var dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)
        //Bind the MediaService
        val bindIntent = Intent(this, MediaService::class.java)
        bindService(bindIntent, mpConnection, Context.BIND_AUTO_CREATE)
    }

    private fun endActivity(){
        this.unbindService(dbConnection)
        finish()
    }

    fun onClickHandler(holder: AlbumBrowserAdapter.AlbumItemHolder){
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

    fun onLongClickHandler(holder: AlbumBrowserAdapter.AlbumItemHolder): Boolean{
        var albumID: Int = holder.album_item.albumID.text.toString().toInt()
        Log.i("AlbumBrowserActivity", "Long click album $albumID")
        return true
    }

    //init toolbar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem?) = when(item?.itemId) {
        android.R.id.home -> {
            album_navdrawer.openDrawer(GravityCompat.START)
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
