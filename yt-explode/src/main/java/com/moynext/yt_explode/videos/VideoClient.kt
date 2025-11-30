package com.moynext.yt_explode.videos

import com.moynext.yt_explode.channels.ChannelId
import com.moynext.yt_explode.client.YTHttpClient
import com.moynext.yt_explode.videos.closed_captions.ClosedCaptionClient
import com.moynext.yt_explode.videos.comments.CommentsClient

class VideoClient(
  private val httpClient: YTHttpClient
) {


  val closedCaptions: ClosedCaptionClient = ClosedCaptionClient(httpClient)

  val commentsClient: CommentsClient = CommentsClient(httpClient)
  val comments: CommentsClient get() = commentsClient

  suspend fun get(videoId: Any): Video {
    val videoIdObj = VideoId.fromString(videoId)
    return getVideoFromWatchPage(videoIdObj)
  }

  private suspend fun getVideoFromWatchPage(videoId: VideoId): Video {
    val url = "https://www.youtube.com/watch?v=${videoId.value}"

    try {
      val response = httpClient.getString(url)

      val title = extractTitle(response)
      val author = extractAuthor(response)
      val channelId = extractChannelId(response)
      val uploadDate = extractUploadDate(response)
      val uploadDateRaw = extractUploadDateRaw(response)
      val description = extractDescription(response)
      val duration = extractDuration(response)
      val keywords = extractKeywords(response)
      val engagement = extractEngagement(response)
      val isLive = extractIsLive(response)

      return Video(
        id = videoId,
        title = title ?: "Unknown Title",
        author = author ?: "Unknown Author",
        channelId = ChannelId(channelId ?: "unknown_channel"),
        uploadDate = uploadDate,
        uploadDateRaw = uploadDateRaw,
        description = description,
        duration = duration,
        thumbnails = ThumbnailSet(videoId.value),
        keywords = keywords,
        engagement = engagement,
        isLive = isLive,
        source = response,
        viewCount = 0,
        ""
      )
    } catch (e: Exception) {
      return Video(
        id = videoId,
        title = "Error loading video",
        author = "Unknown Author",
        channelId = ChannelId("unknown_channel"),
        uploadDate = null,
        uploadDateRaw = null,
        description = null,
        duration = null,
        thumbnails = ThumbnailSet(videoId.value),
        keywords = null,
        engagement = null,
        isLive = false,
        source = null,
        0, ""
      )
    }
  }

  private fun extractTitle(html: String): String? {
    val titleRegex = Regex("""<title[^>]*>([^<]+)</title>""")
    return titleRegex.find(html)?.groupValues?.get(1)?.replace(" - YouTube", "")?.trim()
  }

  private fun extractAuthor(html: String): String? {
    val authorRegex = Regex("""<meta name="author" content="([^"]+)""")
    return authorRegex.find(html)?.groupValues?.get(1)
  }

  private fun extractChannelId(html: String): String? {
    val channelRegex =
      Regex("""<link itemprop="url" href="https://www\.youtube\.com/channel/([^"]+)""")
    return channelRegex.find(html)?.groupValues?.get(1)
  }

  private fun extractUploadDate(html: String): java.util.Date? {
    val dateRegex = Regex("""<meta itemprop="uploadDate" content="([^"]+)""")
    val dateString = dateRegex.find(html)?.groupValues?.get(1)
    return try {
      dateString?.let {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        formatter.parse(it)
      }
    } catch (e: Exception) {
      try {
        dateString?.let {
          val formatter2 = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
          formatter2.parse(it)
        }
      } catch (e2: Exception) {
        null
      }
    }
  }

  private fun extractUploadDateRaw(html: String): String? {
    val dateRegex = Regex("""<meta itemprop="uploadDate" content="([^"]+)""")
    return dateRegex.find(html)?.groupValues?.get(1)
  }

  private fun extractDescription(html: String): String? {
    val descRegex = Regex("""<meta name="description" content="([^"]+)""")
    return descRegex.find(html)?.groupValues?.get(1)
  }

  private fun extractDuration(html: String): String? {
    val durationRegex = Regex("""<meta itemprop="duration" content="PT(\d+)M(\d+)S""")
    val match = durationRegex.find(html)
    return match?.let { "${it.groupValues[1]}:${it.groupValues[2]}" }
  }

  private fun extractKeywords(html: String): List<String>? {
    val keywordsRegex = Regex("""<meta name="keywords" content="([^"]+)""")
    val keywordsString = keywordsRegex.find(html)?.groupValues?.get(1)
    return keywordsString?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
  }

  private fun extractEngagement(html: String): Engagement? {
    val viewCountRegex = Regex("""<meta itemprop="interactionCount" content="UserPlays:(\d+)""")
    val likeCountRegex = Regex("""<meta itemprop="interactionCount" content="UserLikes:(\d+)""")

    val viewCount = viewCountRegex.find(html)?.groupValues?.get(1)?.toLongOrNull()
    val likeCount = likeCountRegex.find(html)?.groupValues?.get(1)?.toLongOrNull()

    return if (viewCount != null || likeCount != null) {
      Engagement(viewCount, likeCount, null)
    } else null
  }

  private fun extractIsLive(html: String): Boolean {
    return html.contains("isLiveContent\":true") || html.contains("\"liveBroadcastDetails\"")
  }

  suspend fun getRelatedVideos(video: Video): RelatedVideosList? {
    for (i in 0..2) {
      val client = RelatedVideosClient.get(httpClient, video)
      if (client != null) {
        return RelatedVideosList(client.getRelatedVideos(), client, httpClient)
      }
    }
    return null
  }
}
