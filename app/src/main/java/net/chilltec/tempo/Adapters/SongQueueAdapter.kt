package net.chilltec.tempo.Adapters

import android.graphics.Color
import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.song_queue_item.view.*
import net.chilltec.tempo.Activities.PlayerActivity
import net.chilltec.tempo.DataTypes.Album
import net.chilltec.tempo.DataTypes.Artist
import net.chilltec.tempo.DataTypes.Song
import net.chilltec.tempo.R

class SongQueueAdapter(val artistsDB: Array<Artist>,
                         val albumsDB: Array<Album>,
                         val songsDB: Array<Song>,
                         val songList: IntArray,
                         val nowPlaying: Int,
                         val context: PlayerActivity
)
    : RecyclerView.Adapter<SongQueueAdapter.SongQueueItemHolder>(){

    val songQueueItemView = R.layout.song_queue_item
    class SongQueueItemHolder(val song_queue_item: ConstraintLayout): RecyclerView.ViewHolder(song_queue_item)

    //New View
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongQueueItemHolder {
        val  songElement = LayoutInflater.from(parent.context)
            .inflate(R.layout.song_queue_item, parent, false) as ConstraintLayout
        return SongQueueItemHolder(songElement)
    }

    //Replace existing view
    override fun onBindViewHolder(holder: SongQueueItemHolder, position: Int){
        //Add content to each element
        val songIndex: Int = songList[position] - 1
        val songID: Int = songIndex + 1

        val artistIndex = songsDB[songIndex].artist - 1
        val albumIndex = songsDB[songIndex].album - 1

        holder.song_queue_item.songID.text = songID.toString()
        holder.song_queue_item.songTitleLable.text = songsDB[songIndex].title
        holder.song_queue_item.songArtistLable.text = artistsDB[artistIndex].artist
        holder.song_queue_item.songAlbumLable.text = albumsDB[albumIndex].album

        holder.song_queue_item.setOnClickListener{
            //Pass the holder back to the activity
            context.onClickCalled(holder)
        }

        holder.song_queue_item.setOnLongClickListener{
            context.onLongClickCalled(holder)
            true
        }

        //Change text color for the currently playing song
        if(songID == nowPlaying){
            val highlightColor = Color.GREEN
            holder.song_queue_item.songTitleLable.setTextColor(highlightColor)
            holder.song_queue_item.songQueuePlayIcon.visibility = View.VISIBLE
        }
    }

    //Return the size of the dataset, the number of songs
    override fun getItemCount() = songList.size
}