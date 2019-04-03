package net.chilltec.tempo.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.download_queue_browser.*
import net.chilltec.tempo.adapters.DownloadQueueAdapter
import net.chilltec.tempo.R
import net.chilltec.tempo.services.DatabaseService
import net.chilltec.tempo.services.MediaService
import org.jetbrains.anko.support.v4.runOnUiThread

class DownloadsFragment : Fragment() {
    var isDBConnected: Boolean = false
    var isMPConnected: Boolean = false
    var shouldKeepThreadAlive: Boolean = false
    lateinit var ref: Context

    var db: DatabaseService? = null
    private val dbConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DatabaseService.LocalBinder
            db = binder.getService()
            isDBConnected = true

            //Initialize MediaService after DatabaseService is connected
            val mpIntent = Intent(ref, MediaService::class.java)
            ref.bindService(mpIntent, mediaServiceConnection, Context.BIND_AUTO_CREATE)
            //End initialize MediaService
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isDBConnected = false
        }
    }

    var mp: MediaService? = null
    val mediaServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaService.LocalBinder
            mp = binder.getService()
            isMPConnected = true
            shouldKeepThreadAlive = true
            updateContent() //Ensure that the Database and Media services are conencted
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isMPConnected = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //Grap the parent context reference
        ref = container?.context ?: return null

        //Initialize Database and bind to the service
        val dbIntent = Intent(ref, DatabaseService::class.java)
        ref.bindService(dbIntent, dbConnection, Context.BIND_AUTO_CREATE)
        //End initialize database

        return inflater.inflate(R.layout.download_queue_browser, container, false)
    }

    override fun onPause() {
        shouldKeepThreadAlive = false
        super.onPause()
    }

    fun updateContent() {
        //Adds or updates the current download status
        //Must be called only while MediaService and DatabaseService are connected
        Thread(Runnable {
            while (isDBConnected && isMPConnected && shouldKeepThreadAlive) {
                val downloadList = mp?.getCacheQueue() ?: intArrayOf()
                if(downloadList.isEmpty()){
                    runOnUiThread { downloadEmptyMessageBox.visibility = View.VISIBLE }

                }
                else {
                    runOnUiThread { downloadEmptyMessageBox.visibility = View.INVISIBLE }
                }

                val artistsDB = db?.getArtistsDB() ?: arrayOf()
                val albumsDB = db?.getAlbumsDB() ?: arrayOf()
                val songsDB = db?.getSongsDB() ?: arrayOf()

                val viewManager = LinearLayoutManager(this.context)
                val viewAdapter = DownloadQueueAdapter(artistsDB, albumsDB, songsDB, downloadList, this)

                runOnUiThread {
                    downloadsItemHolder.apply {
                        setHasFixedSize(true)
                        layoutManager = viewManager
                        adapter = viewAdapter
                    }
                }

                Thread.sleep(1000)
            }
        }).start()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onItemClickListener(holder: DownloadQueueAdapter.DownloadQueueItemHolder) {

    }

    @Suppress("UNUSED_PARAMETER")
    fun onItemLongClickListener(holder: DownloadQueueAdapter.DownloadQueueItemHolder) {

    }
}