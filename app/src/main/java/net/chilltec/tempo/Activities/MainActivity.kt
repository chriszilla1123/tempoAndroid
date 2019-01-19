package net.chilltec.tempo.Activities

import android.app.SearchManager
import android.content.*
import kotlinx.android.synthetic.main. activity_main.*
import android.os.*
import android.support.v4.view.GravityCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.SearchView
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import net.chilltec.tempo.R
import net.chilltec.tempo.R.id.*
import net.chilltec.tempo.Services.DatabaseService
import net.chilltec.tempo.Services.MediaService

class MainActivity : AppCompatActivity()  {

    var mp: MediaService? = null
    val mediaServiceConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaService.LocalBinder
            mp = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    var db: DatabaseService? = null
    val DBconnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DatabaseService.LocalBinder
            db = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Toolbar Init
        setSupportActionBar(mainToolbar)
        var toolbar = supportActionBar
        toolbar?.title = "Tempo"
        toolbar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
        }
        //End Toolbar Init

        //Initialize mediaPlayer and pass library
        var MPintent = Intent(this, MediaService::class.java)
        startService(intent)
        bindService(MPintent, mediaServiceConnection, Context.BIND_AUTO_CREATE)
        //End initialize mediaPlayer

        //Initialize Database and bind to the service
        var DBintent = Intent(this, DatabaseService::class.java)
        startService(DBintent)
        bindService(DBintent, DBconnection, Context.BIND_AUTO_CREATE)
        //End initialize database

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

        buttonAllArtists.setOnClickListener{
            openArtistBrowser()
        }
        buttonAllAlbums.setOnClickListener{
            openAlbumBrowser()
        }
        buttonAllSongs.setOnClickListener{
            openSongBrowser()
        }
        buttonPlayer.setOnClickListener{
            openPlayer()
        }

        //Init NavDrawer onClick listeners
        main_navview.setNavigationItemSelectedListener { menuItem ->
            val id = menuItem.itemId
            when(id){
                nav_artists -> {
                    openArtistBrowser()
                }
                nav_albums -> {
                    openAlbumBrowser()
                }
                nav_songs -> {
                    openSongBrowser()
                }
                nav_player -> {
                    openPlayer()
                }
            }
            true
        }
        //End Init NavDrawer onClick listeners
    }

    override fun onDestroy(){
        super.onDestroy()
        unbindService(mediaServiceConnection)
        unbindService(DBconnection)
    }

    //init toolbar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager

        (menu?.findItem(R.id.mainSearch)?.actionView as SearchView)
            .setSearchableInfo(
                searchManager.getSearchableInfo(
                    ComponentName(this, SearchBrowserActivity::class.java)))
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem?) = when(item?.itemId) {
        android.R.id.home -> {
            main_navdrawer.openDrawer(GravityCompat.START)
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

    fun initNavBar(){
        val navView = main_navdrawer
        navView.setOnClickListener {
            Log.i("MainMenu", it.toString())
            true
        }
    }

    fun onClick_artistNav(){

    }
}
