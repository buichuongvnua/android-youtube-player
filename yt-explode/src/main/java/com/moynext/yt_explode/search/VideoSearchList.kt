package com.moynext.yt_explode.search

import com.moynext.yt_explode.client.YTHttpClient
import com.moynext.yt_explode.videos.Video

class VideoSearchList(
  private val videos: List<Video>,
  private val page: SearchPage,
  private val httpClient: YTHttpClient
) {
  val content: List<Video> get() = videos
  val relatedVideos: List<SearchResult> get() = page.relatedVideos
  val estimatedResults: Int get() = page.estimatedResults

  suspend fun nextPage(): VideoSearchList? {
    val nextPage = page.nextPage(httpClient) ?: return null

    val nextVideos = nextPage.searchContent
      .filterIsInstance<SearchResult.Video>()
      .map { searchVideo ->
        Video(
          id = searchVideo.id,
          title = searchVideo.title,
          author = searchVideo.author,
          channelId = com.moynext.yt_explode.channels.ChannelId(searchVideo.channelId),
          uploadDate = null,
          uploadDateRaw = searchVideo.uploadDate,
          description = searchVideo.description,
          duration = searchVideo.duration,
          thumbnails = com.moynext.yt_explode.videos.ThumbnailSet(searchVideo.id.value),
          keywords = null,
          engagement = com.moynext.yt_explode.videos.Engagement(searchVideo.viewCount, null, null),
          isLive = searchVideo.isLive,
          "",
          searchVideo.viewCount,
          searchVideo.channelThumbnail
        )
      }

    return VideoSearchList(nextVideos, nextPage, httpClient)
  }
}
