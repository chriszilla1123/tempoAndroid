package net.chilltec.tempo.adapters

import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import kotlinx.android.synthetic.main.playlist_item.view.*
import net.chilltec.tempo.activities.PlaylistBrowserActivity
import net.chilltec.tempo.dataTypes.Playlist
import net.chilltec.tempo.R

class PlaylistBrowserAdapter(
    private val playlistsDB: Array<Playlist>,
    private val playlistList: IntArray,
    val context: PlaylistBrowserActivity
) : RecyclerView.Adapter<PlaylistBrowserAdapter.PlaylistItemHolder>() {

    class PlaylistItemHolder(val playlist_item: ConstraintLayout
    ) : RecyclerView.ViewHolder(playlist_item)

    //New View
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistItemHolder {
        val playlistElement = LayoutInflater.from(parent.context)
            .inflate(R.layout.playlist_item, parent, false) as ConstraintLayout
        return PlaylistItemHolder(playlistElement)
    }

    override fun onBindViewHolder(holder: PlaylistItemHolder, position: Int) {
        val playlistIndex = playlistList[position] - 1

        holder.playlist_item.playlistID.text = (playlistIndex + 1).toString()
        holder.playlist_item.playlistLabel.text = playlistsDB[playlistIndex].playlist

        holder.playlist_item.setOnClickListener{
            //Pass the holder to the activity to handle the onClick event
            context.onClickHandler(holder)
        }
        holder.playlist_item.setOnLongClickListener {
            context.onLongClickHandler(holder)
        }
    }

    //Return the size of the dataset, the number of playlists
    override fun getItemCount() = playlistList.size
}