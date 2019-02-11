package net.chilltec.tempo.Adapters

import android.net.Uri
import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.album_item.view.*
import net.chilltec.tempo.Activities.AlbumBrowserActivity
import net.chilltec.tempo.DataTypes.Album
import net.chilltec.tempo.DataTypes.Artist
import net.chilltec.tempo.R
import java.io.File



class AlbumBrowserAdapter(val artistsDB: Array<Artist>,
                          val albumsDB: Array<Album>,
                          val albumList: IntArray,
                          val albumArtList: List<File?>, //albumArtList may be empty, check before using.
                          val context: AlbumBrowserActivity
): RecyclerView.Adapter<AlbumBrowserAdapter.AlbumItemHolder>(){

    class AlbumItemHolder(val album_item: ConstraintLayout): RecyclerView.ViewHolder(album_item)

    var TAG = "AlbumBrowserAdapter" // For debugging

    //Create the holder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumItemHolder {
        val albumElement = LayoutInflater.from(parent.context)
            .inflate(R.layout.album_item, parent, false) as ConstraintLayout
        return AlbumItemHolder(albumElement)
    }

    override fun onBindViewHolder(holder: AlbumItemHolder, position: Int) {
        val albumIndex = albumList[position] - 1
        val artistIndex = albumsDB[albumIndex].artist - 1
        val albumID = albumsDB[albumIndex].id

        holder.album_item.albumID.text = (albumIndex + 1).toString()
        holder.album_item.albumLable.text = albumsDB[albumIndex].album
        holder.album_item.albumArtistLable.text = artistsDB[artistIndex].artist

        Thread(Runnable {
            if(albumArtList.isNotEmpty()){
                //holder.album_item.albumArt.setImageURI(Uri.fromFile(albumArtList[albumIndex]))
                val file = albumArtList[albumIndex]
                if(file != null){
                    holder.album_item.post{
                        Picasso.get().load(file).fit().centerCrop().into(holder.album_item.albumArt)
                    }
                }
            }
        }).start()

        holder.album_item.setOnClickListener{
            //Pass the holder to the activity to handle to onClick event
            context.onClickHandler(holder)
        }

        holder.album_item.setOnLongClickListener{
            context.onLongClickHandler(holder)
        }
        holder.album_item.albumLable.bringToFront()
    }

    //Return the size of the dataset, the number of albums
    override fun getItemCount() = albumList.size

    //Fixes a bug causing recyclerview elements to move after scrolling down and back up.
    override fun getItemViewType(position: Int) = position
}