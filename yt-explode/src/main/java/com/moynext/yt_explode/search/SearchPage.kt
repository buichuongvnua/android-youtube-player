package com.moynext.yt_explode.search

import com.moynext.yt_explode.channels.ChannelId
import com.moynext.yt_explode.client.YTHttpClient
import com.moynext.yt_explode.common.Thumbnail
import com.moynext.yt_explode.playlists.PlaylistId
import com.moynext.yt_explode.videos.VideoId
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class SearchPage private constructor(
  private val httpClient: YTHttpClient,
  val searchQuery: String,
  val filter: SearchFilter,
  val searchContent: List<SearchResult>,
  val relatedVideos: List<SearchResult>,
  val estimatedResults: Int,
  private val continuationToken: String? = null
) {
  
  companion object {
    private fun getSimpleText(obj: JSONObject, key: String): String {
      return if (obj.has(key) && !obj.isNull(key)) {
        try {
          obj.getString(key)
        } catch (e: Exception) {
          ""
        }
      } else ""
    }
    
    private fun getTextFromRuns(obj: JSONObject, key: String): String {
      return if (obj.has(key) && !obj.isNull(key)) {
        try {
          val runs = obj.getJSONArray(key)
          if (runs.length() > 0) {
            runs.getJSONObject(0).getString("text")
          } else ""
        } catch (e: Exception) {
          ""
        }
      } else ""
    }
    
    private fun getChannelIdFromOwnerText(obj: JSONObject): String {
      return try {
        val ownerText = obj.getJSONObject("ownerText")
        if (ownerText.has("runs")) {
          val runs = ownerText.getJSONArray("runs")
          if (runs.length() > 0) {
            val firstRun = runs.getJSONObject(0)
            if (firstRun.has("navigationEndpoint")) {
              val navEndpoint = firstRun.getJSONObject("navigationEndpoint")
              if (navEndpoint.has("browseEndpoint")) {
                navEndpoint.getJSONObject("browseEndpoint").getString("browseId")
              } else ""
            } else ""
          } else ""
        } else ""
      } catch (e: Exception) {
        ""
      }
    }
    
    private fun getChannelThumbnail(obj: JSONObject): String {
      return try {
        if (obj.has("channelThumbnailSupportedRenderers")) {
          val channelThumbnailRenderer = obj.getJSONObject("channelThumbnailSupportedRenderers")
          if (channelThumbnailRenderer.has("channelThumbnailWithLinkRenderer")) {
            val thumbnailRenderer = channelThumbnailRenderer.getJSONObject("channelThumbnailWithLinkRenderer")
            if (thumbnailRenderer.has("thumbnail")) {
              val thumbnails = parseThumbnails(thumbnailRenderer.getJSONObject("thumbnail"))
              if (thumbnails.isNotEmpty()) {
                return thumbnails.first().url
              }
            }
          }
        }
        
        if (obj.has("ownerText")) {
          val ownerText = obj.getJSONObject("ownerText")
          if (ownerText.has("runs")) {
            val runs = ownerText.getJSONArray("runs")
            if (runs.length() > 0) {
              val firstRun = runs.getJSONObject(0)
              if (firstRun.has("thumbnail")) {
                val thumbnails = parseThumbnails(firstRun.getJSONObject("thumbnail"))
                if (thumbnails.isNotEmpty()) {
                  return thumbnails.first().url
                }
              }
            }
          }
        }
        
        ""
      } catch (e: Exception) {
        ""
      }
    }
    suspend fun get(
      httpClient: YTHttpClient,
      searchQuery: String,
      filter: SearchFilter
    ): SearchPage {
      if (searchQuery.isBlank()) {
        throw IllegalArgumentException("Search query cannot be empty")
      }

      val encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8.toString())
      val filterParam = if (filter.value.isNotEmpty()) "&sp=${filter.value}" else ""

      val url = "https://www.youtube.com/results?search_query=$encodedQuery$filterParam"

      val response = httpClient.get(url)
      if (!response.isSuccessful) {
        throw Exception("Failed to get search page: HTTP ${response.code}")
      }

      val body = response.body?.string() ?: throw Exception("Failed to get search page body")

      val searchContent = mutableListOf<SearchResult>()
      val relatedVideos = mutableListOf<SearchResult>()
      var estimatedResults = 0
      var continuationToken: String? = null

      val ytInitialDataPattern = Pattern.compile("var ytInitialData = (\\{.*?\\});")
      val matcher = ytInitialDataPattern.matcher(body)

      if (matcher.find()) {
        try {
          val jsonStr = matcher.group(1)
          val json = JSONObject(jsonStr)

          val contents = json
            .getJSONObject("contents")
            .getJSONObject("twoColumnSearchResultsRenderer")
            .getJSONObject("primaryContents")
            .getJSONObject("sectionListRenderer")
            .getJSONArray("contents")

          for (i in 0 until contents.length()) {
            val content = contents.getJSONObject(i)
            if (content.has("itemSectionRenderer")) {
              val items = content
                .getJSONObject("itemSectionRenderer")
                .getJSONArray("contents")

              for (j in 0 until items.length()) {
                val item = items.getJSONObject(j)
                parseSearchResult(item, searchContent, relatedVideos)
              }
            }
          }

          val estimatedResultsText = extractEstimatedResults(body)
          estimatedResults = parseEstimatedResults(estimatedResultsText)

          continuationToken = extractContinuationToken(json)

        } catch (e: Exception) {
          throw Exception("Failed to parse search results: ${e.message}")
        }
      }

      return SearchPage(
        httpClient = httpClient,
        searchQuery = searchQuery,
        filter = filter,
        searchContent = searchContent,
        relatedVideos = relatedVideos,
        estimatedResults = estimatedResults,
        continuationToken = continuationToken
      )
    }

    private fun parseSearchResult(
      item: JSONObject,
      searchContent: MutableList<SearchResult>,
      relatedVideos: MutableList<SearchResult>
    ) {
      when {
        item.has("videoRenderer") -> {
          val video = parseVideoRenderer(item.getJSONObject("videoRenderer"))
          searchContent.add(video)
        }

        item.has("playlistRenderer") -> {
          val playlist = parsePlaylistRenderer(item.getJSONObject("playlistRenderer"))
          searchContent.add(playlist)
        }

        item.has("channelRenderer") -> {
          val channel = parseChannelRenderer(item.getJSONObject("channelRenderer"))
          searchContent.add(channel)
        }

        item.has("radioRenderer") -> {
          val playlist = parseRadioRenderer(item.getJSONObject("radioRenderer"))
          searchContent.add(playlist)
        }

        item.has("movieRenderer") -> {
          val video = parseMovieRenderer(item.getJSONObject("movieRenderer"))
          searchContent.add(video)
        }

        item.has("showRenderer") -> {
          val video = parseShowRenderer(item.getJSONObject("showRenderer"))
          searchContent.add(video)
        }
        
      }
    }

    private fun parseVideoRenderer(renderer: JSONObject): SearchResult.Video {
      val videoId = renderer.optString("videoId", "")
      val title = if (renderer.has("title")) {
        getTextFromRuns(renderer.getJSONObject("title"), "runs")
      } else ""

      val author = if (renderer.has("ownerText")) {
        getTextFromRuns(renderer.getJSONObject("ownerText"), "runs")
      } else ""

      val channelId = if (renderer.has("ownerText")) {
        getChannelIdFromOwnerText(renderer)
      } else ""

      val description = if (renderer.has("descriptionSnippet")) {
        getTextFromRuns(renderer.getJSONObject("descriptionSnippet"), "runs")
      } else ""

      val duration = if (renderer.has("lengthText")) {
        getSimpleText(renderer.getJSONObject("lengthText"), "simpleText")
      } else ""

      val viewCount = if (renderer.has("viewCountText")) {
        val viewCountText = getSimpleText(renderer.getJSONObject("viewCountText"), "simpleText")
        parseViewCount(viewCountText)
      } else 0L

      val thumbnails = if (renderer.has("thumbnail")) {
        parseThumbnails(renderer.getJSONObject("thumbnail"))
      } else emptyList()

      val channelThumbnail = getChannelThumbnail(renderer)

      val uploadDate = if (renderer.has("publishedTimeText")) {
        val uploadTimeText = getSimpleText(renderer.getJSONObject("publishedTimeText"), "simpleText")
        if (uploadTimeText.isNotEmpty()) uploadTimeText else null
      } else null

      val isLive = try {
        renderer.has("badges") && renderer
          .getJSONArray("badges")
          .getJSONObject(0)
          .getJSONObject("metadataBadgeRenderer")
          .getString("label") == "LIVE"
      } catch (e: Exception) {
        false
      }

      return SearchResult.Video(
        id = VideoId(videoId),
        title = title,
        author = author,
        description = description,
        duration = duration,
        viewCount = viewCount,
        thumbnails = thumbnails,
        uploadDate = uploadDate,
        isLive = isLive,
        channelId = channelId,
        channelThumbnail = channelThumbnail
      )
    }

    private fun parsePlaylistRenderer(renderer: JSONObject): SearchResult.Playlist {
      val playlistId = renderer.optString("playlistId", "")
      val title = if (renderer.has("title")) {
        getSimpleText(renderer.getJSONObject("title"), "simpleText")
      } else ""

      val videoCount = if (renderer.has("videoCountText")) {
        try {
          val videoCountText = renderer.getJSONObject("videoCountText")
          val countText = if (videoCountText.has("runs")) {
            getTextFromRuns(videoCountText, "runs")
          } else {
            getSimpleText(videoCountText, "simpleText")
          }
          parseVideoCount(countText)
        } catch (e: Exception) {
          0
        }
      } else 0

      val thumbnails = if (renderer.has("thumbnail")) {
        parseThumbnails(renderer.getJSONObject("thumbnail"))
      } else emptyList()

      return SearchResult.Playlist(
        id = PlaylistId(playlistId),
        title = title,
        videoCount = videoCount,
        thumbnails = thumbnails
      )
    }

    private fun parseChannelRenderer(renderer: JSONObject): SearchResult.Channel {
      val channelId = renderer.optString("channelId", "")
      val name = if (renderer.has("title")) {
        getSimpleText(renderer.getJSONObject("title"), "simpleText")
      } else ""

      val description = if (renderer.has("descriptionSnippet")) {
        getSimpleText(renderer.getJSONObject("descriptionSnippet"), "simpleText")
      } else ""

      val videoCount = if (renderer.has("videoCountText")) {
        try {
          val videoCountText = renderer.getJSONObject("videoCountText")
          val countText = if (videoCountText.has("runs")) {
            getTextFromRuns(videoCountText, "runs")
          } else {
            getSimpleText(videoCountText, "simpleText")
          }
          parseVideoCount(countText)
        } catch (e: Exception) {
          0
        }
      } else 0

      val thumbnails = if (renderer.has("thumbnail")) {
        parseThumbnails(renderer.getJSONObject("thumbnail"))
      } else emptyList()

      return SearchResult.Channel(
        id = ChannelId(channelId),
        name = name,
        description = description,
        videoCount = videoCount,
        thumbnails = thumbnails
      )
    }

    private fun parseRadioRenderer(renderer: JSONObject): SearchResult.Playlist {
      val playlistId = renderer.optString("playlistId", "")
      val title = if (renderer.has("title")) {
        getSimpleText(renderer.getJSONObject("title"), "simpleText")
      } else ""

      val videoCount = if (renderer.has("videoCountText")) {
        try {
          val videoCountText = renderer.getJSONObject("videoCountText")
          val countText = if (videoCountText.has("runs")) {
            getTextFromRuns(videoCountText, "runs")
          } else {
            getSimpleText(videoCountText, "simpleText")
          }
          parseVideoCount(countText)
        } catch (e: Exception) {
          0
        }
      } else 0

      val thumbnails = if (renderer.has("thumbnail")) {
        parseThumbnails(renderer.getJSONObject("thumbnail"))
      } else emptyList()

      return SearchResult.Playlist(
        id = PlaylistId(playlistId),
        title = title,
        videoCount = videoCount,
        thumbnails = thumbnails
      )
    }

    private fun parseMovieRenderer(renderer: JSONObject): SearchResult.Video {
      val videoId = renderer.optString("videoId", "")
      val title = if (renderer.has("title")) {
        getSimpleText(renderer.getJSONObject("title"), "simpleText")
      } else ""

      val author = if (renderer.has("ownerText")) {
        getSimpleText(renderer.getJSONObject("ownerText"), "simpleText")
      } else ""

      val channelId = try {
        if (renderer.has("ownerText")) {
          val ownerText = renderer.getJSONObject("ownerText")
          if (ownerText.has("runs")) {
            val runs = ownerText.getJSONObject("runs")
            if (runs.has("0")) {
              val firstRun = runs.getJSONObject("0")
              if (firstRun.has("navigationEndpoint")) {
                val navEndpoint = firstRun.getJSONObject("navigationEndpoint")
                if (navEndpoint.has("browseEndpoint")) {
                  navEndpoint.getJSONObject("browseEndpoint").getString("browseId")
                } else ""
              } else ""
            } else ""
          } else ""
        } else ""
      } catch (e: Exception) {
        ""
      }

      val description = if (renderer.has("descriptionSnippet")) {
        getSimpleText(renderer.getJSONObject("descriptionSnippet"), "simpleText")
      } else ""

      val duration = if (renderer.has("lengthText")) {
        getSimpleText(renderer.getJSONObject("lengthText"), "simpleText")
      } else ""

      val viewCount = if (renderer.has("viewCountText")) {
        val viewCountText = getSimpleText(renderer.getJSONObject("viewCountText"), "simpleText")
        parseViewCount(viewCountText)
      } else 0L

      val thumbnails = if (renderer.has("thumbnail")) {
        parseThumbnails(renderer.getJSONObject("thumbnail"))
      } else emptyList()

      val channelThumbnail = getChannelThumbnail(renderer)

      val uploadDate = if (renderer.has("publishedTimeText")) {
        val uploadTimeText = getSimpleText(renderer.getJSONObject("publishedTimeText"), "simpleText")
        if (uploadTimeText.isNotEmpty()) uploadTimeText else null
      } else null

      return SearchResult.Video(
        id = VideoId(videoId),
        title = title,
        author = author,
        description = description,
        duration = duration,
        viewCount = viewCount,
        thumbnails = thumbnails,
        uploadDate = uploadDate,
        isLive = false,
        channelId = channelId,
        channelThumbnail = channelThumbnail
      )
    }

    private fun parseShowRenderer(renderer: JSONObject): SearchResult.Video {
      val videoId = renderer.optString("videoId", "")
      val title = if (renderer.has("title")) {
        getSimpleText(renderer.getJSONObject("title"), "simpleText")
      } else ""

      val author = if (renderer.has("ownerText")) {
        getSimpleText(renderer.getJSONObject("ownerText"), "simpleText")
      } else ""

      val channelId = try {
        if (renderer.has("ownerText")) {
          val ownerText = renderer.getJSONObject("ownerText")
          if (ownerText.has("runs")) {
            val runs = ownerText.getJSONObject("runs")
            if (runs.has("0")) {
              val firstRun = runs.getJSONObject("0")
              if (firstRun.has("navigationEndpoint")) {
                val navEndpoint = firstRun.getJSONObject("navigationEndpoint")
                if (navEndpoint.has("browseEndpoint")) {
                  navEndpoint.getJSONObject("browseEndpoint").getString("browseId")
                } else ""
              } else ""
            } else ""
          } else ""
        } else ""
      } catch (e: Exception) {
        ""
      }

      val description = if (renderer.has("descriptionSnippet")) {
        getSimpleText(renderer.getJSONObject("descriptionSnippet"), "simpleText")
      } else ""

      val duration = if (renderer.has("lengthText")) {
        getSimpleText(renderer.getJSONObject("lengthText"), "simpleText")
      } else ""

      val viewCount = if (renderer.has("viewCountText")) {
        val viewCountText = getSimpleText(renderer.getJSONObject("viewCountText"), "simpleText")
        parseViewCount(viewCountText)
      } else 0L

      val thumbnails = if (renderer.has("thumbnail")) {
        parseThumbnails(renderer.getJSONObject("thumbnail"))
      } else emptyList()

      val channelThumbnail = getChannelThumbnail(renderer)

      val uploadDate = if (renderer.has("publishedTimeText")) {
        val uploadTimeText = getSimpleText(renderer.getJSONObject("publishedTimeText"), "simpleText")
        if (uploadTimeText.isNotEmpty()) uploadTimeText else null
      } else null

      return SearchResult.Video(
        id = VideoId(videoId),
        title = title,
        author = author,
        description = description,
        duration = duration,
        viewCount = viewCount,
        thumbnails = thumbnails,
        uploadDate = uploadDate,
        isLive = false,
        channelId = channelId,
        channelThumbnail = channelThumbnail
      )
    }

    private fun parseThumbnails(thumbnail: JSONObject): List<Thumbnail> {
      val thumbnails = mutableListOf<Thumbnail>()

      if (thumbnail.has("thumbnails")) {
        try {
          val thumbnailArray = thumbnail.getJSONArray("thumbnails")
          for (i in 0 until thumbnailArray.length()) {
            val thumb = thumbnailArray.getJSONObject(i)
            thumbnails.add(
              Thumbnail(
                url = thumb.optString("url", ""),
                width = thumb.optInt("width", 0),
                height = thumb.optInt("height", 0)
              )
            )
          }
        } catch (e: Exception) {
          // Ignore thumbnail parsing errors
        }
      }

      return thumbnails
    }

    private fun parseViewCount(viewCountText: String): Long {
      val pattern = Pattern.compile("([\\d,]+)")
      val matcher = pattern.matcher(viewCountText)
      return if (matcher.find()) {
        matcher.group(1).replace(",", "").toLongOrNull() ?: 0L
      } else 0L
    }

    private fun parseVideoCount(videoCountText: String): Int {
      val pattern = Pattern.compile("([\\d,]+)")
      val matcher = pattern.matcher(videoCountText)
      return if (matcher.find()) {
        matcher.group(1).replace(",", "").toIntOrNull() ?: 0
      } else 0
    }

    private fun extractEstimatedResults(body: String): String {
      val pattern = Pattern.compile("About ([\\d,]+) results")
      val matcher = pattern.matcher(body)
      return if (matcher.find()) {
        matcher.group(1)
      } else ""
    }

    private fun parseEstimatedResults(estimatedResultsText: String): Int {
      return if (estimatedResultsText.isNotEmpty()) {
        estimatedResultsText.replace(",", "").toIntOrNull() ?: 0
      } else 0
    }

    private fun extractContinuationToken(json: JSONObject): String? {
      return try {
        val contents = json
          .getJSONObject("contents")
          .getJSONObject("twoColumnSearchResultsRenderer")
          .getJSONObject("primaryContents")
          .getJSONObject("sectionListRenderer")
          .getJSONArray("contents")

        for (i in 0 until contents.length()) {
          val content = contents.getJSONObject(i)
          if (content.has("continuationItemRenderer")) {
            return content
              .getJSONObject("continuationItemRenderer")
              .getJSONObject("continuationEndpoint")
              .getJSONObject("continuationCommand")
              .getString("token")
          }
        }
        null
      } catch (e: Exception) {
        null
      }
    }
  }

  suspend fun nextPage(httpClient: YTHttpClient): SearchPage? {
    if (continuationToken == null) return null

    val url =
      "https://www.youtube.com/youtubei/v1/search?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

    val requestBody = JSONObject().apply {
      put("context", JSONObject().apply {
        put("client", JSONObject().apply {
          put("clientName", "WEB")
          put("clientVersion", "2.20210721.00.00")
        })
      })
      put("continuation", continuationToken)
    }

    val response = httpClient.post(url, body = requestBody.toString())
    if (!response.isSuccessful) {
      return null
    }

    val body = response.body?.string() ?: return null

    try {
      val json = JSONObject(body)
      val searchContent = mutableListOf<SearchResult>()
      val relatedVideos = mutableListOf<SearchResult>()

      val contentItems = getContentContext(json)
      if (contentItems != null) {
        for (item in contentItems) {
          parseSearchResult(item, searchContent, relatedVideos)
        }
      }

      val newContinuationToken = extractContinuationTokenFromContinuation(json)

      return SearchPage(
        httpClient = httpClient,
        searchQuery = searchQuery,
        filter = filter,
        searchContent = searchContent,
        relatedVideos = relatedVideos,
        estimatedResults = estimatedResults,
        continuationToken = newContinuationToken
      )
    } catch (e: Exception) {
      return null
    }
  }

  private fun getContentContext(root: JSONObject): List<JSONObject>? {
    if (root.has("contents")) {
      val contents = root.optJSONObject("contents")
      if (contents != null) {
        val twoColumnSearchResultsRenderer = contents.optJSONObject("twoColumnSearchResultsRenderer")
        if (twoColumnSearchResultsRenderer != null) {
          val primaryContents = twoColumnSearchResultsRenderer.optJSONObject("primaryContents")
          if (primaryContents != null) {
            val sectionListRenderer = primaryContents.optJSONObject("sectionListRenderer")
            if (sectionListRenderer != null) {
              val sectionContents = sectionListRenderer.optJSONArray("contents")
              if (sectionContents != null && sectionContents.length() > 0) {
                val firstSection = sectionContents.optJSONObject(0)
                if (firstSection != null) {
                  val itemSectionRenderer = firstSection.optJSONObject("itemSectionRenderer")
                  if (itemSectionRenderer != null) {
                    val itemContents = itemSectionRenderer.optJSONArray("contents")
                    if (itemContents != null) {
                      val result = mutableListOf<JSONObject>()
                      for (i in 0 until itemContents.length()) {
                        val item = itemContents.optJSONObject(i)
                        if (item != null) {
                          result.add(item)
                        }
                      }
                      return result
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    
    if (root.has("onResponseReceivedCommands")) {
      val onResponseReceivedCommands = root.optJSONArray("onResponseReceivedCommands")
      if (onResponseReceivedCommands != null && onResponseReceivedCommands.length() > 0) {
        val firstCommand = onResponseReceivedCommands.optJSONObject(0)
        if (firstCommand != null) {
          val appendContinuationItemsAction = firstCommand.optJSONObject("appendContinuationItemsAction")
          if (appendContinuationItemsAction != null) {
            val continuationItems = appendContinuationItemsAction.optJSONArray("continuationItems")
            if (continuationItems != null && continuationItems.length() > 0) {
              val firstContinuationItem = continuationItems.optJSONObject(0)
              if (firstContinuationItem != null) {
                val itemSectionRenderer = firstContinuationItem.optJSONObject("itemSectionRenderer")
                if (itemSectionRenderer != null) {
                  val itemContents = itemSectionRenderer.optJSONArray("contents")
                  if (itemContents != null) {
                    val result = mutableListOf<JSONObject>()
                    for (i in 0 until itemContents.length()) {
                      val item = itemContents.optJSONObject(i)
                      if (item != null) {
                        result.add(item)
                      }
                    }
                    return result
                  }
                }
              }
            }
          }
        }
      }
    }
    
    return null
  }

  private fun extractContinuationTokenFromContinuation(json: JSONObject): String? {
    return try {
      // Method 1: Check if we have contents structure (like Dart code)
      if (json.has("contents")) {
        val contents = json.optJSONObject("contents")
        val twoColumnSearchResultsRenderer = contents?.optJSONObject("twoColumnSearchResultsRenderer")
        val primaryContents = twoColumnSearchResultsRenderer?.optJSONObject("primaryContents")
        val sectionListRenderer = primaryContents?.optJSONObject("sectionListRenderer")
        val contentsList = sectionListRenderer?.optJSONArray("contents")
        
        if (contentsList != null && contentsList.length() > 1) {
          // Try to get item at index 1 (like Dart: elementAtSafe(1))
          val itemAt1 = contentsList.optJSONObject(1)
          if (itemAt1 != null && itemAt1.has("continuationItemRenderer")) {
            val continuationItemRenderer = itemAt1.optJSONObject("continuationItemRenderer")
            if (continuationItemRenderer != null) {
              val continuationEndpoint = continuationItemRenderer.optJSONObject("continuationEndpoint")
              if (continuationEndpoint != null) {
                val continuationCommand = continuationEndpoint.optJSONObject("continuationCommand")
                if (continuationCommand != null) {
                  val token = continuationCommand.optString("token", null)
                  if (token != null && token.isNotEmpty()) {
                    return token
                  }
                }
              }
            }
          }
        }
      }
      
      // Method 2: Check for onResponseReceivedCommands (like Dart code)
      if (json.has("onResponseReceivedCommands")) {
        val onResponseReceivedCommands = json.optJSONObject("onResponseReceivedCommands")
        if (onResponseReceivedCommands != null) {
          val appendContinuationItemsAction = onResponseReceivedCommands.optJSONArray("appendContinuationItemsAction")
          if (appendContinuationItemsAction != null && appendContinuationItemsAction.length() > 0) {
            val firstAction = appendContinuationItemsAction.optJSONObject(0)
            if (firstAction != null) {
              val continuationItems = firstAction.optJSONArray("continuationItems")
              if (continuationItems != null && continuationItems.length() > 1) {
                // Try to get item at index 1 (like Dart: elementAtSafe(1))
                val itemAt1 = continuationItems.optJSONObject(1)
                if (itemAt1 != null && itemAt1.has("continuationItemRenderer")) {
                  val continuationItemRenderer = itemAt1.optJSONObject("continuationItemRenderer")
                  if (continuationItemRenderer != null) {
                    val continuationEndpoint = continuationItemRenderer.optJSONObject("continuationEndpoint")
                    if (continuationEndpoint != null) {
                      val continuationCommand = continuationEndpoint.optJSONObject("continuationCommand")
                      if (continuationCommand != null) {
                        val token = continuationCommand.optString("token", null)
                        if (token != null && token.isNotEmpty()) {
                          return token
                        }
                      }
                    }
                  }
                }
              }
              
              // Fallback: Look for any continuation item
              if (continuationItems != null) {
                for (i in 0 until continuationItems.length()) {
                  val item = continuationItems.optJSONObject(i)
                  if (item != null && item.has("continuationItemRenderer")) {
                    val continuationItemRenderer = item.optJSONObject("continuationItemRenderer")
                    if (continuationItemRenderer != null) {
                      val continuationEndpoint = continuationItemRenderer.optJSONObject("continuationEndpoint")
                      if (continuationEndpoint != null) {
                        val continuationCommand = continuationEndpoint.optJSONObject("continuationCommand")
                        if (continuationCommand != null) {
                          val token = continuationCommand.optString("token", null)
                          if (token != null && token.isNotEmpty()) {
                            return token
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      
      null
    } catch (e: Exception) {
      null
    }
  }
}
