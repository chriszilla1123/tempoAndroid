package net.chilltec.tempo.Activities

import android.app.AlertDialog
import android.app.SearchManager
import android.content.*
import kotlinx.android.synthetic.main. activity_main.*
import android.os.*
import android.support.v4.view.GravityCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.SearchView
import android.view.Menu
import android.view.MenuItem
import net.chilltec.tempo.R
import net.chilltec.tempo.R.id.*
import net.chilltec.tempo.services.DatabaseService
import net.chilltec.tempo.services.MediaService
import android.os.StrictMode
import net.chilltec.tempo.BuildConfig
import net.chilltec.tempo.Utils.DownloadsFragment


class MainActivity : AppCompatActivity()  {
    val context = this

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



        buttonAllArtists.setOnClickListener{
            openArtistBrowser()
        }
        buttonAllAlbums.setOnClickListener{
            openAlbumBrowser()
        }
        buttonAllSongs.setOnClickListener{
            openSongBrowser()
        }
        buttonPlaylists.setOnClickListener{
            openPlaylistBrowser()
        }
        buttonPlayer.setOnClickListener{
            openPlayer()
        }

        //Init NavDrawer onClick listeners
        main_navview.setNavigationItemSelectedListener { menuItem ->
            val id = menuItem.itemId
            when(id){
                nav_artists -> { openArtistBrowser() }
                nav_albums -> { openAlbumBrowser() }
                nav_songs -> { openSongBrowser() }
                nav_playlists -> { openPlaylistBrowser() }
                nav_player -> { openPlayer() }
            }
            true
        }
        //End Init NavDrawer onClick listeners

        //Enable strict mode
        if(BuildConfig.DEBUG){
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()   // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build()
            )
        }
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
            openPlayer()
            true
        }
        R.id.mainSettings -> {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            true
        }
        R.id.mainDownloads -> {
            supportFragmentManager.beginTransaction()
                .add(android.R.id.content, DownloadsFragment())
                .addToBackStack("DownloadFragment").commit()
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

    //Open other activities
    fun openArtistBrowser(){
        val isReady: Boolean = db?.isDatabaseReady() ?: false
        if(isReady){
            val allArtists = db?.getAllArtistIds() //May be null!
            val intent = Intent(this, ArtistBrowserActivity::class.java)
            intent.putExtra("artistList", allArtists)
            intent.putExtra("title", "All Artists")
            startActivity(intent)
        }
        else{ showDBErrorMessage() }
    }
    fun openAlbumBrowser(){
        val isReady: Boolean = db?.isDatabaseReady() ?: false
        if(isReady){
            val allAlbums = db?.getAllAlbumIds() //May be null!
            val intent = Intent(this, AlbumBrowserActivity::class.java)
            intent.putExtra("albumList", allAlbums)
            intent.putExtra("title", "All Albums")
            startActivity(intent)
        }
        else{ showDBErrorMessage() }
    }
    fun openSongBrowser(){
        val isReady: Boolean = db?.isDatabaseReady() ?: false
        if(isReady){
            val allSongs = db?.getAllSongIds() //May be null!
            val intent = Intent(this, SongBrowserActivity::class.java)
            intent.putExtra("songList", allSongs)
            intent.putExtra("title", "All Songs")
            startActivity(intent)
        }
        else{ showDBErrorMessage() }
    }
    fun openPlaylistBrowser(){
        val isReady: Boolean = db?.isDatabaseReady() ?: false
        if(isReady){
            val allPlaylists = db?.getAllPlaylistIds()
            val intent = Intent(this, PlaylistBrowserActivity::class.java)
            intent.putExtra("playlistList", allPlaylists)
            intent.putExtra("title", "All Playlists")
            startActivity(intent)
        }
        else{ showDBErrorMessage() }
    }
    fun openPlayer(){
        val isReady: Boolean = db?.isDatabaseReady() ?: false
        if(isReady){
            val intent = Intent(this, PlayerActivity::class.java)
            startActivity(intent)
        }
        else{ showDBErrorMessage() }
    }
    //End open other activities

    //Alert dialog
    fun showDBErrorMessage(){
        //Shows an alert, must be closed by user
        val msg = "Error: Unable to initilize database. Is the server available?"
        val alert = AlertDialog.Builder(this).setMessage(msg)
        alert.setPositiveButton("Okay") { a,b -> } //Function is requried, so set to empty
        alert.create().show()
    }
}
