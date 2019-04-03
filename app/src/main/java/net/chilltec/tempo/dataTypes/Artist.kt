package net.chilltec.tempo.dataTypes

data class Artist(val id: Int,
                  val artist: String,
                  val numSongs: Int,
                  val numAlbumns: Int,
                  val picture: String) {
    override fun toString() = "$artist (id=$id)"
}