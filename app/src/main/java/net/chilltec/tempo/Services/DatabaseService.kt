package net.chilltec.tempo.Services

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.*
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.chilltec.tempo.DataTypes.Album
import net.chilltec.tempo.DataTypes.Artist
import net.chilltec.tempo.DataTypes.Song
import okhttp3.*
import java.io.File
import java.io.IOException
import java.nio.file.Files.isDirectory



class DatabaseService : Service() {
    private val binder = LocalBinder()
    private val TAG = "DatabaseService"

    private lateinit var artistsDB: Array<Artist>
    private lateinit var albumsDB: Array<Album>
    private lateinit var songsDB: Array<Song>
    private val baseURL = "http://www.chrisco.top/api"
    private val artistsFileLoc = "artists.db"
    private val albumsFileLoc = "albums.db"
    private val songsFileLoc = "songs.db"
    private val databaseTimestampFileLoc = "databaseTimestamp.txt"
    private var isInitialized: Boolean = false

    override fun onCreate(){
        Log.i(TAG, "Database Service Started")
        //Load the database from the server
        val http = OkHttpClient()
        val gson = Gson()
        val filesDir = this.filesDir
        val artistsFile = File(filesDir, artistsFileLoc)
        val albumsFile = File(filesDir, albumsFileLoc)
        val songsFile = File(filesDir, songsFileLoc)
        val databaseTimestampFile = File(filesDir, databaseTimestampFileLoc)

        fun initDatabases(){
            //Must only be called after database is downloaded.
            artistsDB = gson.fromJson<Array<Artist>>(artistsFile.readText(), object: TypeToken<Array<Artist>>(){}.type)
            albumsDB = gson.fromJson<Array<Album>>(albumsFile.readText(), object: TypeToken<Array<Album>>(){}.type)
            songsDB = gson.fromJson<Array<Song>>(songsFile.readText(), object: TypeToken<Array<Song>>(){}.type)
            isInitialized = true
            Log.i(TAG,"Database is initilized")
        }

        //Start Initialize Databases
        //Check if an update is required
        //Get timestamp for the local database
        if(!databaseTimestampFile.exists()){
            databaseTimestampFile.createNewFile()
            databaseTimestampFile.writeText("0")
        }
        val localTimestamp = databaseTimestampFile.readText()

        //Get timestamp for the remote database
        val remoteTimestampUrl = "$baseURL/getLastUpdate"
        val request = Request.Builder().url(remoteTimestampUrl).build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException){}
            override fun onResponse(call: Call, response: Response){
                var remoteTimestamp = response.body()?.string()
                //An update is only required if the timestamps do not match
                var needsUpdate = remoteTimestamp != localTimestamp

                if(!needsUpdate){
                    Log.i(TAG, "Local database is up to date")
                    initDatabases()
                }
                else{ //Update the database
                    Log.i(TAG, "Updating the local database")
                    val artistURL = "$baseURL/getArtists"
                    val artistRequest = Request.Builder().url(artistURL).build()
                    http.newCall(artistRequest).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException){}
                        override fun onResponse(call: Call, response: Response){
                            var respString = response.body()?.string()
                            artistsFile.writeText(respString.toString())
                            artistsDB = gson.fromJson<Array<Artist>>(artistsFile.readText(), object: TypeToken<Array<Artist>>(){}.type)
                        }
                    })
                    val albumURL = "$baseURL/getAlbums"
                    val albumRequest = Request.Builder().url(albumURL).build()
                    http.newCall(albumRequest).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException){}
                        override fun onResponse(call: Call, response: Response){
                            var respString = response.body()?.string()
                            albumsFile.writeText(respString.toString())
                            albumsDB = gson.fromJson<Array<Album>>(albumsFile.readText(), object: TypeToken<Array<Album>>(){}.type)
                            getAlbumArt()
                        }
                    })
                    val songURL = "$baseURL/getSongs"
                    val songRequest = Request.Builder().url(songURL).build()
                    http.newCall(songRequest).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException){}
                        override fun onResponse(call: Call, response: Response){
                            var respString = response.body()?.string()
                            songsFile.writeText(respString.toString())
                            songsDB = gson.fromJson<Array<Song>>(songsFile.readText(), object: TypeToken<Array<Song>>(){}.type)
                            isInitialized = true
                        }
                    })

                    val url = "$baseURL/getLastUpdate"
                    val request = Request.Builder().url(url).build()
                    http.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException){}
                        override fun onResponse(call: Call, response: Response){
                            var timestamp = response.body()?.string() ?: "0"

                            val databaseTimestampFile = File(filesDir, databaseTimestampFileLoc)
                            if(!databaseTimestampFile.exists()){
                                databaseTimestampFile.createNewFile()
                            }
                            databaseTimestampFile.writeText(timestamp)
                        }
                    })
                }
            }
        })
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    inner class LocalBinder: Binder() {
        fun getService(): DatabaseService {
            return this@DatabaseService
        }
    }

    //Return full database file functions
    fun getArtistsDB(): Array<Artist> {
        return artistsDB
    }
    fun getAlbumsDB(): Array<Album> {
        return albumsDB
    }
    fun getSongsDB(): Array<Song> {
        return songsDB
    }
    //End full database file functions

    //Database entry counting functions
    fun numArtists(): Int {
        if(!isInitialized) return -1
        return artistsDB.size
    }
    fun numAlbums(): Int {
        if(!isInitialized) return -1
        return albumsDB.size
    }
    fun numSongs(): Int {
        if(!isInitialized) return -1
        return songsDB.size
    }
    //End database entry counting functions

    //Database range functions'
    fun getAllArtistIds(): IntArray {
        val numArtists = numArtists()
        val artistList = IntArray(numArtists)
        for(i in 1..numArtists){
            artistList[i-1] = i
        }
        return artistList
    }
    fun getAllAlbumIds(): IntArray {
        val numAlbums = numAlbums()
        val albumList = IntArray(numAlbums)
        for(i in 1..numAlbums){
            albumList[i-1] = i
        }
        return albumList
    }
    fun getAllSongIds(): IntArray {
        val numSongs = numSongs()
        var songList = IntArray(numSongs)
        for(i in 1..numSongs){
            songList[i-1] = i
        }
        return songList
    }
    //End database range functions

    //Info by Song ID
    fun getArtistIdBySongId(id: Int): Int? {
        if(id < 1) return null
        return songsDB[id-1].artist
    }
    fun getAlbumIdBySongId(id: Int): Int? {
        if(id < 1) return null
        return songsDB[id-1].album
    }
    fun getArtistBySongId(id: Int): String? {
        if(id < 1) return null
        val artistID = songsDB[id-1].artist
        return artistsDB[artistID-1].artist
    }
    fun getAlbumBySongId(id: Int): String? {
        if(id < 1) return null
        val albumID = songsDB[id-1].album
        return albumsDB[albumID-1].album
    }
    fun getTitleBySongId(id: Int): String? {
        if(id < 1) return null
        return songsDB[id-1].title
    }
    fun songHasArtwork(id: Int): Boolean{
        //Returns true if the given song's album has artwork
        val albumID = songsDB[id-1].album
        val artDirLoc = filesDir.absolutePath + File.separator + "artwork"
        val artDir = File(artDirLoc)
        if(!artDir.exists()){
            return false
        }
        val albumArtLoc = artDirLoc + File.separator + "$albumID.art"
        val albumArtFile = File(albumArtLoc)
        return albumArtFile.exists()
    }
    fun getArtworkUriBySongId(id: Int): Uri? {
        val albumID = songsDB[id-1].album
        val artDirLoc = filesDir.absolutePath + File.separator + "artwork"
        val artDir = File(artDirLoc)
        if(!artDir.exists()){
            return null
        }
        val albumArtLoc = artDirLoc + File.separator + "$albumID.art"
        val albumArtFile = File(albumArtLoc)
        return Uri.fromFile(albumArtFile)
    }
    //End info by Song ID

    //Info by Album ID
    fun albumHasArtwork(id: Int): Boolean{
        val artDirLoc = filesDir.absolutePath + File.separator + "artwork"
        val artDir = File(artDirLoc)
        if(!artDir.exists()){
            return false
        }
        val albumArtLoc = artDirLoc + File.separator + "$id.art"
        val albumArtFile = File(albumArtLoc)
        return albumArtFile.exists()
    }

    fun getArtworkUriByAlbumId(id: Int): Uri? {
        //Returns the File object for the album artwork
        val artDirLoc = filesDir.absolutePath + File.separator + "artwork"
        val artDir = File(artDirLoc)
        if(!artDir.exists()){
            return null
        }
        val albumArtLoc = artDirLoc + File.separator + "$id.art"
        val albumArtFile = File(albumArtLoc)
        return Uri.fromFile(albumArtFile)
    }
    //End Info by Album ID

    //Info by Artist ID
    fun getSongIdByArtistId(id: Int): Int{
        //Returns the first song by the given artist, referenced by artist ID
        for(song in songsDB){
            if(song.artist == id) return song.id
        }
        return -1 //No song found
    }
    fun getSongListByArtistId(id: Int): IntArray{
        //Returns the list of all song IDs by a given artist, referenced by artist ID
        var songList = mutableListOf<Int>()
        for(song in songsDB){
            if(song.artist == id) songList.add(song.id)
        }
        return songList.toIntArray()
    }
    //End Info by Artist ID

    //Search
    fun search(searchTerm: String): Triple<IntArray, IntArray, IntArray>{
        val mutArtistList = mutableListOf<Int>()
        val mutAlbumList = mutableListOf<Int>()
        val mutSongList = mutableListOf<Int>()
        for(artist in artistsDB){
            if(artist.artist.contains(searchTerm, true))
                mutArtistList.add(artist.id)
        }
        for(album in albumsDB){
            if(album.album.contains(searchTerm, true))
                mutAlbumList.add(album.id)
        }
        for(song in songsDB){
            if(song.title.contains(searchTerm, true))
                mutSongList.add(song.id)
        }
        val artistList =  mutArtistList.toIntArray()
        val albumList = mutAlbumList.toIntArray()
        val songList = mutSongList.toIntArray()

        return Triple(artistList, albumList, songList)
    }

    //Download images
    fun getAlbumArt(): Boolean{
        //Must be called after database is downloaded and initialized
        //if(!isInitialized) return false
        val http = OkHttpClient()

        Log.i(TAG, "Downloading album artwork")

        val artDirLoc = filesDir.absolutePath + File.separator + "artwork"
        val artDir = File(artDirLoc)
        if(!artDir.exists()){
            artDir.mkdir()
        }
        clearDirctory(artDir)

        val albumIDs: ArrayList<Int> //IDs of only albums that have art
        for(album in albumsDB){
            if(album.albumArt == ""){
                //Log.i(TAG, "${album.album} has no art")
            }
            else{
                //Log.i(TAG, "Downloading art for ${album.album}")
                val artFileLoc = artDirLoc + File.separator + "${album.id}.art"
                val artFile = File(artFileLoc)
                artFile.createNewFile()
                val url = "$baseURL/getAlbumArtById/${album.id}"
                val request = Request.Builder().url(url).build()
                http.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException){}
                    override fun onResponse(call: Call, response: Response){
                        var respBytes = response.body()?.bytes() ?: byteArrayOf()
                        if(respBytes.size == 0){
                            Log.i(TAG, "ERROR downloading artwork for album id ${album.id}")
                        }
                        else{
                            artFile.writeBytes(respBytes)
                            Log.i(TAG, "Successfully downloaded artwork for album ${album.id}")
                        }

                    }
                })
            }
        }
        return true
    }

    fun clearDirctory(dir: File){
        if(dir.isDirectory) {
            val children = dir.list()
            for (i in children.indices) {
                File(dir, children[i]).delete()
            }
        }
    }
}
