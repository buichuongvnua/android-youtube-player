package com.moynext.yt_explode.channels

import com.moynext.yt_explode.common.Thumbnail
import java.time.LocalDateTime

data class ChannelAbout(
    val description: String,
    val viewCount: Long,
    val joinDate: LocalDateTime?,
    val title: String,
    val thumbnails: List<Thumbnail>,
    val country: String?,
    val channelLinks: List<ChannelLink>
)

data class ChannelLink(
    val title: String,
    val url: String
)
