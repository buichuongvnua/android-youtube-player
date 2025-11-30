package com.moynext.yt_explode.channels

data class ChannelHandle(
    val value: String
) {
    override fun toString(): String = value
    
    companion object {
        fun fromString(handle: Any): ChannelHandle {
            return when (handle) {
                is ChannelHandle -> handle
                is String -> ChannelHandle(handle)
                else -> ChannelHandle(handle.toString())
            }
        }
    }
}
