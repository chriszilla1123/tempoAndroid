package net.chilltec.tempo.Activities

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
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_song_browser.*
import kotlinx.android.synthetic.main.song_item.view.*
import net.chilltec.tempo.Services.DatabaseService
import net.chilltec.tempo.Services.MediaService
import net.chilltec.tempo.R
import net.chilltec.tempo.Adapters.SongBrowserAdapter

class SongBrowserActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var songList: IntArray

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

        //Bind the MediaService & DatabaseService
        val mpIntent = Intent(this, MediaService::class.java)
        bindService(mpIntent, mpConnection, Context.BIND_AUTO_CREATE)

        var dbIntent = Intent(this, DatabaseService::class.java)
        bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)
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

    fun onClickCalled(holder: SongBrowserAdapter.SongItemHolder){
        val songId: Int = holder.song_item.songID.text.toString().toInt()
        mp?.setSongList(songList)
        mp?.playSongById(songId, true)
    }

    fun onLongClickCalled(holder: SongBrowserAdapter.SongItemHolder): Boolean{
        val songId: Int = holder.song_item.songID.text.toString().toInt()
        Log.i("SongBrowserActivity", "Long Click $songId")
        return true
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
