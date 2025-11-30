package com.moynext.yt_explode.playlists

import com.moynext.yt_explode.videos.Engagement
import com.moynext.yt_explode.videos.ThumbnailSet

data class Playlist(
    val id: PlaylistId,
    val title: String,
    val author: String,
    val description: String,
    val thumbnails: ThumbnailSet,
    val engagement: Engagement,
    val videoCount: Int?
) {
    val url: String get() = "https://www.youtube.com/playlist?list=${id.value}"
}
