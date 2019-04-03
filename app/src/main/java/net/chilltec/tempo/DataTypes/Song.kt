package net.chilltec.tempo.DataTypes

data class Song(val id: Int,
                val artist: Int,
                val album: Int,
                val title: String,
                val fileType: String,
                val fileSize: Int,
                val directory: String) {
    override fun toString() = "$title (id=$id)"
}