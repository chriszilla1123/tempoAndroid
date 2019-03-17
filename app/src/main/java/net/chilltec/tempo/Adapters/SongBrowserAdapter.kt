package net.chilltec.tempo.Adapters

import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import kotlinx.android.synthetic.main.song_item.view.*
import net.chilltec.tempo.Activities.SongBrowserActivity
import net.chilltec.tempo.DataTypes.Album
import net.chilltec.tempo.DataTypes.Artist
import net.chilltec.tempo.R
import net.chilltec.tempo.DataTypes.Song

class SongBrowserAdapter(val artistsDB: Array<Artist>,
                         val albumsDB: Array<Album>,
                         val songsDB: Array<Song>,
                         val songList: IntArray,
                         val context: SongBrowserActivity
)
                        : RecyclerView.Adapter<SongBrowserAdapter.SongItemHolder>(){

    class SongItemHolder(val song_item: ConstraintLayout): RecyclerView.ViewHolder(song_item)

    //New View
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongItemHolder {
        val  songElement = LayoutInflater.from(parent.context)
            .inflate(R.layout.song_item, parent, false) as ConstraintLayout
        return SongItemHolder(songElement)
    }

    //Replace existing view
    override fun onBindViewHolder(holder: SongItemHolder, position: Int){
        //Add content to each element
        val songIndex: Int = songList[position] - 1

        val artistIndex = songsDB[songIndex].artist - 1
        val albumIndex = songsDB[songIndex].album - 1

        holder.song_item.songID.text = (songIndex + 1).toString()
        holder.song_item.songTitleLable.text = songsDB[songIndex].title
        holder.song_item.songArtistLable.text = artistsDB[artistIndex].artist
        holder.song_item.songAlbumLable.text = albumsDB[albumIndex].album

        holder.song_item.setOnClickListener{ context.onClickCalled(holder) }
        holder.song_item.setOnLongClickListener{ context.onLongClickCalled(holder) }

        //Song Download Icon color/onClick
        context.showIsSongDownloaded(holder) //Sets icon to green if it's cached
        holder.song_item.songDownloadIcon.bringToFront()
        holder.song_item.songDownloadIcon.setOnClickListener{ context.onClickSongDownloadIcon(holder) }
    }

    //Return the size of the dataset, the number of songs
    override fun getItemCount() = songList.size
}