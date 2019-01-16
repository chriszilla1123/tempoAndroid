package net.chilltec.tempo.Services

import android.app.Service
import android.content.Intent
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

        //Start Initialize Databases

        val artistURL = "$baseURL/getArtists"
        val artistRequest = Request.Builder().url(artistURL).build()
        http.newCall(artistRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException){}
            override fun onResponse(call: Call, response: Response){
                var respString = response.body()?.string()
                artistsFile.writeText(respString.toString())
            }
        })
        val albumURL = "$baseURL/getAlbums"
        val albumRequest = Request.Builder().url(albumURL).build()
        http.newCall(albumRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException){}
            override fun onResponse(call: Call, response: Response){
                var respString = response.body()?.string()
                albumsFile.writeText(respString.toString())
            }
        })
        val songURL = "$baseURL/getSongs"
        val songRequest = Request.Builder().url(songURL).build()
        http.newCall(songRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException){}
            override fun onResponse(call: Call, response: Response){
                var respString = response.body()?.string()
                songsFile.writeText(respString.toString())
            }
        })

        artistsDB = gson.fromJson<Array<Artist>>(artistsFile.readText(), object: TypeToken<Array<Artist>>(){}.type)
        albumsDB = gson.fromJson<Array<Album>>(albumsFile.readText(), object: TypeToken<Array<Album>>(){}.type)
        songsDB = gson.fromJson<Array<Song>>(songsFile.readText(), object: TypeToken<Array<Song>>(){}.type)
        isInitialized = true
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
    fun getArtistBySongId(id: Int): String? {
        if(id < 1) return null
        val artistID = songsDB[id-1].artist
        return artistsDB[artistID-1].artist
    }
    fun getAlbumBySongID(id: Int): String? {
        if(id < 1) return null
        val albumID = songsDB[id-1].album
        return albumsDB[albumID-1].album
    }
    fun getTitleBySongID(id: Int): String? {
        if(id < 1) return null
        return songsDB[id-1].title
    }
    //End info by Song ID

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
}
