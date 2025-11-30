package com.moynext.yt_explode.channels

data class Channel(
    val id: ChannelId,
    val title: String,
    val logoUrl: String,
    val bannerUrl: String,
    val subscribersCount: Long?
) {
    val url: String get() = "https://www.youtube.com/channel/${id.value}"
}
