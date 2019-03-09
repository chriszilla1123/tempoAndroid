package net.chilltec.tempo.DataTypes

data class PlaylistSong(val id: Int,
                    val playlist: Int,
                    val songId: Int) {
    override fun toString() = "$playlist (id=$id)"
}