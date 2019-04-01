package net.chilltec.tempo.Adapters

import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import kotlinx.android.synthetic.main.song_queue_item.view.*
import net.chilltec.tempo.DataTypes.Album
import net.chilltec.tempo.DataTypes.Artist
import net.chilltec.tempo.DataTypes.Song
import net.chilltec.tempo.R
import net.chilltec.tempo.Utils.DownloadsFragment

class DownloadQueueAdapter(val artistsDB: Array<Artist>,
                           val albumsDB: Array<Album>,
                           val songsDB: Array<Song>,
                           val songList: IntArray,
                           val context: DownloadsFragment
)
    : RecyclerView.Adapter<DownloadQueueAdapter.DownloadQueueItemHolder>(){

    class DownloadQueueItemHolder(val download_queue_item: ConstraintLayout): RecyclerView.ViewHolder(download_queue_item)

    //New View
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadQueueItemHolder {
        val  songElement = LayoutInflater.from(parent.context)
            .inflate(R.layout.download_queue_item, parent, false) as ConstraintLayout
        return DownloadQueueItemHolder(songElement)
    }

    //Replace existing view
    override fun onBindViewHolder(holder: DownloadQueueItemHolder, position: Int){
        //Add content to each element
        val songIndex: Int = songList[position] - 1
        val songID: Int = songIndex + 1

        val artistIndex = songsDB[songIndex].artist - 1
        val albumIndex = songsDB[songIndex].album - 1

        holder.download_queue_item.songID.text = songID.toString()
        holder.download_queue_item.songTitleLable.text = songsDB[songIndex].title
        holder.download_queue_item.songArtistLable.text = artistsDB[artistIndex].artist
        holder.download_queue_item.songAlbumLable.text = albumsDB[albumIndex].album

        holder.download_queue_item.setOnClickListener{
            //Pass the holder back to the activity
            context.onItemClickListener(holder)
        }

        holder.download_queue_item.setOnLongClickListener{
            context.onItemLongClickListener(holder)
            true
        }
    }

    //Return the size of the dataset, the number of songs
    override fun getItemCount() = songList.size

    override fun getItemViewType(position: Int) = position
}