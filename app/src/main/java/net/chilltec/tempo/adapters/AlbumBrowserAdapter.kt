package net.chilltec.tempo.adapters

import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import kotlinx.android.synthetic.main.album_item.view.*
import net.chilltec.tempo.activities.AlbumBrowserActivity
import net.chilltec.tempo.dataTypes.Album
import net.chilltec.tempo.dataTypes.Artist
import net.chilltec.tempo.R

class AlbumBrowserAdapter(val artistsDB: Array<Artist>,
                          val albumsDB: Array<Album>,
                          val albumList: IntArray,
                          val context: AlbumBrowserActivity
): RecyclerView.Adapter<AlbumBrowserAdapter.AlbumItemHolder>(){

    class AlbumItemHolder(val album_item: ConstraintLayout): RecyclerView.ViewHolder(album_item)

    //Create the holder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumItemHolder {
        val albumElement = LayoutInflater.from(parent.context)
            .inflate(R.layout.album_item, parent, false) as ConstraintLayout
        return AlbumItemHolder(albumElement)
    }

    override fun onBindViewHolder(holder: AlbumItemHolder, position: Int) {
        val albumIndex = albumList[position] - 1
        val artistIndex = albumsDB[albumIndex].artist - 1
        val albumID = albumIndex + 1

        holder.album_item.albumID.text = (albumIndex + 1).toString()
        holder.album_item.albumLabel.text = albumsDB[albumIndex].album
        holder.album_item.albumArtistLabel.text = artistsDB[artistIndex].artist

        holder.album_item.setOnClickListener{
            //Pass the holder to the activity to handle to onClick event
            context.onClickHandler(holder)
        }

        holder.album_item.setOnLongClickListener{
            context.onLongClickHandler(holder)
        }
        holder.album_item.albumMenu.setOnClickListener {
            context.albumMenuHandler(holder, albumID)
        }
        holder.album_item.albumLabel.bringToFront()
        context.setAlbumArtwork(holder, albumID)
    }

    //Return the size of the dataset, the number of albums
    override fun getItemCount() = albumList.size

    //Fixes a bug causing recyclerview elements to move after scrolling down and back up.
    override fun getItemViewType(position: Int) = position
}