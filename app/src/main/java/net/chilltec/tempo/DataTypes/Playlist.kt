package net.chilltec.tempo.DataTypes

data class Playlist(val id: Int,
                 val playlist: String,
                 val playlistArt: String) {
    override fun toString() = "$playlist (id=$id)"
}