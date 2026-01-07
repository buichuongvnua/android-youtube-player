package com.moynext.yt_explode.search

import com.moynext.yt_explode.client.YTHttpClient
import com.moynext.yt_explode.videos.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class SearchClient(
  private val httpClient: YTHttpClient
) {

  suspend fun search(
    searchQuery: String,
    filter: SearchFilter = TypeFilters.VIDEO
  ): VideoSearchList {
    if (searchQuery.isBlank()) {
      throw IllegalArgumentException("Search query cannot be empty")
    }

    val page = SearchPage.get(httpClient, searchQuery, filter)

    val videos = page.searchContent
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
          source = null,
          viewCount = searchVideo.viewCount,
          channelThumbnail = searchVideo.channelThumbnail
        )
      }

    return VideoSearchList(videos, page, httpClient)
  }

  suspend fun searchContent(
    searchQuery: String,
    filter: SearchFilter = SearchFilter.EMPTY
  ): SearchList {
    if (searchQuery.isBlank()) {
      throw IllegalArgumentException("Search query cannot be empty")
    }

    val page = SearchPage.get(httpClient, searchQuery, filter)
    return SearchList(page.searchContent, page, httpClient)
  }

  suspend fun getQuerySuggestions(query: String): List<String> {
    val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
    val url =
      "https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&hl=en&gl=en&q=$encodedQuery&callback=func"

    val response = httpClient.get(url)
    val body = withContext(Dispatchers.IO) {
      response.body?.string() ?: return@withContext null
    } ?: return emptyList()

    val startIndex = body.indexOf("func(")
    if (startIndex == -1) return emptyList()

    body.substring(startIndex + 5, body.length - 1)

    try {
      // Parse JSON response to get suggestions
      // This is a simplified implementation
      return emptyList()
    } catch (e: Exception) {
      return emptyList()
    }
  }

  suspend fun searchRaw(
    searchQuery: String,
    filter: SearchFilter = SearchFilter.EMPTY
  ): SearchQuery {
    if (searchQuery.isBlank()) {
      throw IllegalArgumentException("Search query cannot be empty")
    }

    return SearchQuery.search(httpClient, searchQuery, filter)
  }
}
