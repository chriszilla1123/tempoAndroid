package net.chilltec.tempo.adapters

import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import kotlinx.android.synthetic.main.artist_item.view.*
import net.chilltec.tempo.activities.ArtistBrowserActivity
import net.chilltec.tempo.dataTypes.Artist
import net.chilltec.tempo.R

class ArtistBrowserAdapter(val artistsDB: Array<Artist>,
                           val artistList: IntArray,
                           val context: ArtistBrowserActivity
) : RecyclerView.Adapter<ArtistBrowserAdapter.ArtistItemHolder>(){

    class ArtistItemHolder(val artist_item: ConstraintLayout): RecyclerView.ViewHolder(artist_item)

    //New View
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistItemHolder {
        val  artistElement = LayoutInflater.from(parent.context)
            .inflate(R.layout.artist_item, parent, false) as ConstraintLayout
        return ArtistItemHolder(artistElement)
    }

    //Add content to each element
    override fun onBindViewHolder(holder: ArtistItemHolder, position: Int) {
        val artistIndex = artistList[position] - 1

        holder.artist_item.artistID.text = (artistIndex + 1).toString()
        holder.artist_item.artistLable.text = artistsDB[artistIndex].artist

        holder.artist_item.setOnClickListener{
            //Pass the holder to the activity to handle the onClick event
            context.onClickHandler(holder)
        }

        holder.artist_item.setOnLongClickListener {
            context.onLongClickHandler(holder)
        }
    }

    //Return the size of the dataset, the number of artists
    override fun getItemCount() = artistList.size

}