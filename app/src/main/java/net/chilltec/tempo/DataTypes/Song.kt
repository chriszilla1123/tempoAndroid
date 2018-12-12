package net.chilltec.tempo.DataTypes

data class Song(val artist: Int,
                val album: Int,
                val title: String,
                val fileType: String,
                val directory: String,
                val id: Int) {
    override fun toString() = "$title (id=$id)"
}