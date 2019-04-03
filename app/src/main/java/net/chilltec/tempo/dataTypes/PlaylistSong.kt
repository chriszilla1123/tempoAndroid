package net.chilltec.tempo.dataTypes

data class PlaylistSong(val id: Int,
                    val playlist: Int,
                    val songId: Int) {
    override fun toString() = "$playlist (id=$id)"
}