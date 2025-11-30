package com.moynext.yt_explode.videos

data class VideoId(
    val value: String
) {
    override fun toString(): String = value
    
    companion object {
        fun fromString(id: Any): VideoId {
            return when (id) {
                is VideoId -> id
                is String -> VideoId(id)
                else -> VideoId(id.toString())
            }
        }
    }
}
