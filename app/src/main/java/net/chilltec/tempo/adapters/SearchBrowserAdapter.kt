package net.chilltec.tempo.adapters

import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.album_item.view.*
import kotlinx.android.synthetic.main.artist_item.view.*
import kotlinx.android.synthetic.main.search_title.view.*
import kotlinx.android.synthetic.main.song_item.view.*
import net.chilltec.tempo.activities.SearchBrowserActivity
import net.chilltec.tempo.dataTypes.Album
import net.chilltec.tempo.dataTypes.Artist
import net.chilltec.tempo.R
import net.chilltec.tempo.dataTypes.Song

class SearchBrowserAdapter(val artistsDB: Array<Artist>,
                           val albumsDB: Array<Album>,
                           val songsDB: Array<Song>,
                           val artistList: IntArray,
                           val albumList: IntArray,
                           val songList: IntArray,
                           val context: SearchBrowserActivity
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(){

    class ArtistItemHolder(val artist_item: ConstraintLayout): RecyclerView.ViewHolder(artist_item)
    class AlbumItemHolder(val album_item: ConstraintLayout): RecyclerView.ViewHolder(album_item)
    class SongItemHolder(val song_item: ConstraintLayout): RecyclerView.ViewHolder(song_item)
    //For holding search titles
    class TitleHolder(val title_item: ConstraintLayout): RecyclerView.ViewHolder(title_item)

    private val  artistItem = 0
    private val albumItem = 1
    private val songItem = 2
    private val artistTitle = 3
    private val albumTitle = 4
    private val songTitle = 5

    override fun getItemViewType(position: Int): Int {
        //Determine whether the passed item is an artist, album, or song
        //If there are nonzero artists, albums, or songs, each category will have a lable
        //Lables are counted as an item with a position
        var pos = position
        val artistOffset = if(artistList.isNotEmpty()) 1 else 0
        val albumOffset = if(albumList.isNotEmpty()) 1 else 0

        if(artistList.isNotEmpty() && position == 0){
            return artistTitle
        }
        if(albumList.isNotEmpty() && 0 == (position - artistList.size - artistOffset)){
            return albumTitle
        }
        if(songList.isNotEmpty() && 0 == (position - artistList.size - albumList.size - artistOffset - albumOffset)){
            return songTitle
        }
        if(artistList.isNotEmpty()) pos--
        if(pos < artistList.size) {
            return artistItem
        }
        if(albumList.isNotEmpty()) pos--
        if((pos - artistList.size) < albumList.size){
            return albumItem
        }
        if(songList.isNotEmpty()) pos--
        if((pos - artistList.size - albumList.size) < songList.size){
            return songItem
        }
        return -1
    }

    //New View
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view: View
        when(viewType){
            artistItem -> {
                view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.artist_item, parent, false) as ConstraintLayout
                return ArtistItemHolder(view)
            }
            albumItem -> {
                view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.album_item, parent, false) as ConstraintLayout
                return AlbumItemHolder(view)
            }
            songItem -> {
                view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.song_item, parent, false) as ConstraintLayout
                return SongItemHolder(view)
            }
            artistTitle, albumTitle, songTitle -> {
                view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.search_title, parent, false) as ConstraintLayout
                return TitleHolder(view)
            }
            else -> {
                //Change to null
                view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.song_item, parent, false) as ConstraintLayout
                return SongItemHolder(view)
            }
        }
    }

    //Add content to each element
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        //Account for type lables, +1 if there are artists, albums, or songs
        when(holder.itemViewType){
            artistItem -> {
                val artistHolder =
                    ArtistItemHolder(holder.itemView as ConstraintLayout)
                val pos = position - 1 //for the artist label
                val artistIndex = artistList[pos] - 1
                artistHolder.artist_item.artistID.text = (artistIndex + 1).toString()
                artistHolder.artist_item.artistLabel.text = artistsDB[artistIndex].artist

                artistHolder.artist_item.setOnClickListener{
                    //Pass the holder to the activity to handle to onClick event
                    context.artistOnClickHandler(artistHolder)
                }

                artistHolder.artist_item.setOnLongClickListener {
                    context.artistOnLongClickHandler(artistHolder)
                    true
                }
            }

            albumItem -> {
                val albumHolder =
                    AlbumItemHolder(holder.itemView as ConstraintLayout)
                var pos = position - 1 //for the album lable
                if(artistList.isNotEmpty()) pos-- //for the possible artist lable
                val albumIndex = albumList[pos - artistList.size] - 1
                val artistIndex = albumsDB[albumIndex].artist - 1

                albumHolder.album_item.albumID.text = (albumIndex + 1).toString()
                albumHolder.album_item.albumLabel.text = albumsDB[albumIndex].album
                albumHolder.album_item.albumArtistLabel.text = artistsDB[artistIndex].artist

                albumHolder.album_item.setOnClickListener{
                    //Pass the holder to the activity to handle to onClick event
                    context.albumOnClickHandler(albumHolder)
                }

                albumHolder.album_item.setOnLongClickListener{
                    context.albumOnLongClickHandler(albumHolder)
                    true
                }
            }

            songItem -> {
                val songHolder =
                    SongItemHolder(holder.itemView as ConstraintLayout)
                var pos = position - 1 //For the song lable
                if(artistList.isNotEmpty()) pos-- //For the possible artist label
                if(albumList.isNotEmpty()) pos-- //For the possible album label
                val songIndex: Int = songList[pos - artistList.size - albumList.size] - 1

                val artistIndex = songsDB[songIndex].artist - 1
                val albumIndex = songsDB[songIndex].album - 1

                songHolder.song_item.songID.text = (songIndex + 1).toString()
                songHolder.song_item.songTitleLabel.text = songsDB[songIndex].title
                songHolder.song_item.songArtistLabel.text = artistsDB[artistIndex].artist
                songHolder.song_item.songAlbumLabel.text = albumsDB[albumIndex].album

                songHolder.song_item.setOnClickListener{
                    //Pass the holder back to the activity
                    context.songOnClickHandler(songHolder)
                }

                songHolder.song_item.setOnLongClickListener{
                    context.songOnLongClickHandler(songHolder)
                    true
                }
            }

            artistTitle -> {
                val titleHolder =
                    TitleHolder(holder.itemView as ConstraintLayout)
                titleHolder.title_item.searchTitleLabel.text =  R.string.artists.toString()
            }
            albumTitle -> {
                val titleHolder =
                    TitleHolder(holder.itemView as ConstraintLayout)
                titleHolder.title_item.searchTitleLabel.text = R.string.albums.toString()
            }
            songTitle -> {
                val titleHolder =
                    TitleHolder(holder.itemView as ConstraintLayout)
                titleHolder.title_item.searchTitleLabel.text = R.string.songs.toString()
            }
        }


    }

    //Return the size of the dataset, the number of artists
    //Because titles count as items, add one to size if there are more than 0: artists, albums, and songs
    override fun getItemCount(): Int {
        var size = artistList.size + albumList.size + songList.size
        if(artistList.isNotEmpty()) size++
        if(albumList.isNotEmpty()) size++
        if(songList.isNotEmpty()) size++
        return size
    }
}