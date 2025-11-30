package com.moynext.yt_explode.videos

import com.moynext.yt_explode.channels.ChannelId
import java.util.Date

data class Video(
  val id: VideoId,
  val title: String,
  val author: String,
  val channelId: ChannelId,
  val uploadDate: Date?,
  val uploadDateRaw: String?,
  val description: String?,
  val duration: String?,
  val thumbnails: ThumbnailSet?,
  val keywords: List<String>?,
  val engagement: Engagement?,
  val isLive: Boolean,
  val source: String?,
  val viewCount: Long,
  val channelThumbnail: String
)
