package com.moynext.yt_explode.channels

data class ChannelId(
    val value: String
) {
    override fun toString(): String = value
    
    companion object {
        fun fromString(id: Any): ChannelId {
            return when (id) {
                is ChannelId -> id
                is String -> ChannelId(id)
                else -> ChannelId(id.toString())
            }
        }
    }
}
