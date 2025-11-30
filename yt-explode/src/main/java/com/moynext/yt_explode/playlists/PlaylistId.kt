package com.moynext.yt_explode.playlists

data class PlaylistId(
    val value: String
) {
    override fun toString(): String = value
    
    companion object {
        fun fromString(id: Any): PlaylistId {
            return when (id) {
                is PlaylistId -> id
                is String -> PlaylistId(id)
                else -> PlaylistId(id.toString())
            }
        }
    }
}
