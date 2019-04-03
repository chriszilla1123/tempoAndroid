package net.chilltec.tempo.dataTypes

data class Playlist(val id: Int,
                 val playlist: String,
                 val playlistArt: String) {
    override fun toString() = "$playlist (id=$id)"
}