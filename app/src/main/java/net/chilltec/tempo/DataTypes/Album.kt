package net.chilltec.tempo.DataTypes

data class Album(val id: Int,
                 val artist: Int,
                 val album: String,
                 val numSongs: Int,
                 val albumArt: String) {
    override fun toString() = "$album (id=$id)"
}