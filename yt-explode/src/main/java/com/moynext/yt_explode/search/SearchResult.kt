package com.moynext.yt_explode.search

import com.moynext.yt_explode.channels.ChannelId
import com.moynext.yt_explode.common.Thumbnail
import com.moynext.yt_explode.playlists.PlaylistId
import com.moynext.yt_explode.videos.VideoId

sealed class SearchResult {
  data class Video(
    val id: VideoId,
    val title: String,
    val author: String,
    val description: String,
    val duration: String,
    val viewCount: Long,
    val thumbnails: List<Thumbnail>,
    val uploadDate: String?,
    val isLive: Boolean,
    val channelId: String,
    val channelThumbnail: String
  ) : SearchResult()

  data class Playlist(
    val id: PlaylistId,
    val title: String,
    val videoCount: Int,
    val thumbnails: List<Thumbnail>
  ) : SearchResult()

  data class Channel(
    val id: ChannelId,
    val name: String,
    val description: String,
    val videoCount: Int,
    val thumbnails: List<Thumbnail>
  ) : SearchResult()
}
