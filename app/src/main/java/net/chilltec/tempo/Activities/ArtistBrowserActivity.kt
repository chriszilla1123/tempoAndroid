package net.chilltec.tempo.Activities

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Menu
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
        setContentView(R.layout.activity_artist_browser)
        setSupportActionBar(artistToolbar)
        var toolbar = supportActionBar

        toolbar?.title = if(intent.hasExtra("title")){ intent.getStringExtra("title") }
                            else{ "Artists" }
        toolbar?.subtitle = if(intent.hasExtra("subtitle")){ intent.getStringExtra("subtitle") }
                            else{ "" }


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
        var dbIntent = Intent(this, DatabaseService::class.java)
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
        return true
    }

    //init toolbar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem?) = when(item?.itemId) {
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
        else -> {
            super.onOptionsItemSelected(item)
        }
    }
    //end init toolbar
}
