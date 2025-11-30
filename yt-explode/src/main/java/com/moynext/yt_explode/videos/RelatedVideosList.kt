package com.moynext.yt_explode.videos

import com.moynext.yt_explode.channels.ChannelId
import com.moynext.yt_explode.client.YTHttpClient
import com.moynext.yt_explode.common.BasePagedList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RelatedVideosList(
  base: List<Video>,
  private val client: RelatedVideosClient,
  private val httpClient: YTHttpClient
) : BasePagedList<Video>(base) {

  override suspend fun nextPage(): RelatedVideosList? {
    val nextClient = client.nextPage(httpClient)
    return nextClient?.let { RelatedVideosList(it.getRelatedVideos(), it, httpClient) }
  }
}

class RelatedVideosClient(
  private val contents: List<Map<String, Any>>,
  private val rootJson: Map<String, Any>? = null
) {

  fun getRelatedVideos(): List<Video> {
    println("RelatedVideosClient: Parsing ${contents.size} content items")
    val videos = mutableListOf<Video>()

    for ((index, content) in contents.withIndex()) {
      val video = parseVideoFromContent(content)
      if (video != null) {
        videos.add(video)
        println("RelatedVideosClient: Parsed video $index: ${video.title}")
      } else {
        println("RelatedVideosClient: Failed to parse video $index")
      }
    }

    println("RelatedVideosClient: Successfully parsed ${videos.size} videos out of ${contents.size} content items")
    return videos
  }

  private fun parseVideoFromContent(content: Map<String, Any>): Video? {
    val compactVideo = content["compactVideoRenderer"] as? Map<String, Any>
    val lockupView = content["lockupViewModel"] as? Map<String, Any>

    return when {
      compactVideo != null -> parseCompactVideo(compactVideo)
      lockupView != null -> parseLockupView(lockupView)
      else -> null
    }
  }

  private fun getChannelThumbnail(data: Map<String, Any>): String {
    return try {
      val longByline = data["longBylineText"] as? Map<String, Any>
      val runs = longByline?.get("runs") as? List<Map<String, Any>>
      val firstRun = runs?.firstOrNull()
      val thumbnail = firstRun?.get("thumbnail") as? Map<String, Any>
      val thumbnails = thumbnail?.get("thumbnails") as? List<Map<String, Any>>
      val firstThumbnail = thumbnails?.firstOrNull()
      val url = firstThumbnail?.get("url") as? String
      url ?: ""
    } catch (e: Exception) {
      ""
    }
  }

  private fun getChannelThumbnailFromLockup(data: Map<String, Any>): String {
    return try {
      val metadata = data["metadata"] as? Map<String, Any>
      val lockupMetadata = metadata?.get("lockupMetadataViewModel") as? Map<String, Any>
      val image = lockupMetadata?.get("image") as? Map<String, Any>
      val decoratedAvatar = image?.get("decoratedAvatarViewModel") as? Map<String, Any>
      val avatar = decoratedAvatar?.get("avatar") as? Map<String, Any>
      val thumbnails = avatar?.get("thumbnails") as? List<Map<String, Any>>
      val firstThumbnail = thumbnails?.firstOrNull()
      val url = firstThumbnail?.get("url") as? String
      url ?: ""
    } catch (e: Exception) {
      ""
    }
  }

  private fun parseCompactVideo(data: Map<String, Any>): Video? {
    val videoId = data["videoId"] as? String
    val title = (data["title"] as? Map<String, Any>)?.get("simpleText") as? String
    val longByline = data["longBylineText"] as? Map<String, Any>
    val author =
      (longByline?.get("runs") as? List<Map<String, Any>>)?.firstOrNull()?.get("text") as? String
    val channelId = (longByline?.get("runs") as? List<Map<String, Any>>)?.firstOrNull()
      ?.get("navigationEndpoint") as? Map<String, Any>
    val browseEndpoint = channelId?.get("browseEndpoint") as? Map<String, Any>
    val channelIdValue = browseEndpoint?.get("browseId") as? String

    val publishedTime =
      (data["publishedTimeText"] as? Map<String, Any>)?.get("simpleText") as? String
    val length = (data["lengthText"] as? Map<String, Any>)?.get("simpleText") as? String
    val viewCount = (data["viewCountText"] as? Map<String, Any>)?.get("simpleText") as? String
    val channelThumbnail = getChannelThumbnail(data)

    if (videoId == null || title == null || author == null || channelIdValue == null) {
      return null
    }

    return Video(
      id = VideoId(videoId),
      title = title,
      author = author,
      channelId = ChannelId(channelIdValue),
      uploadDate = publishedTime?.let { parseDateTime(it) },
      uploadDateRaw = publishedTime,
      description = null,
      duration = length,
      thumbnails = ThumbnailSet(videoId),
      keywords = null,
      engagement = viewCount?.let { Engagement(parseViewCount(it), null, null) },
      isLive = length == "LIVE",
      source = null,
      viewCount = 0,
      channelThumbnail = channelThumbnail
    )
  }

  private fun parseLockupView(data: Map<String, Any>): Video? {
    val rendererContext = data["rendererContext"] as? Map<String, Any>
    val commandContext = rendererContext?.get("commandContext") as? Map<String, Any>
    val onTap = commandContext?.get("onTap") as? Map<String, Any>
    val innertubeCommand = onTap?.get("innertubeCommand") as? Map<String, Any>
    val watchEndpoint = innertubeCommand?.get("watchEndpoint") as? Map<String, Any>
    val videoId = watchEndpoint?.get("videoId") as? String

    val metadata = data["metadata"] as? Map<String, Any>
    val lockupMetadata = metadata?.get("lockupMetadataViewModel") as? Map<String, Any>
    val titleData = lockupMetadata?.get("title") as? Map<String, Any>
    val title = titleData?.get("content") as? String

    val image = lockupMetadata?.get("image") as? Map<String, Any>
    val decoratedAvatar = image?.get("decoratedAvatarViewModel") as? Map<String, Any>
    val avatarContext = decoratedAvatar?.get("rendererContext") as? Map<String, Any>
    val avatarCommand = avatarContext?.get("commandContext") as? Map<String, Any>
    val avatarOnTap = avatarCommand?.get("onTap") as? Map<String, Any>
    val avatarInnertube = avatarOnTap?.get("innertubeCommand") as? Map<String, Any>
    val browseEndpoint = avatarInnertube?.get("browseEndpoint") as? Map<String, Any>
    val channelId = browseEndpoint?.get("browseId") as? String

    if (videoId == null || title == null || channelId == null) {
      return null
    }

    val contentImage = data["contentImage"] as? Map<String, Any>
    val thumbnail = contentImage?.get("thumbnailViewModel") as? Map<String, Any>
    val overlays = thumbnail?.get("overlays") as? List<Map<String, Any>>
    val overlay = overlays?.firstOrNull()
    val badge = overlay?.get("thumbnailOverlayBadgeViewModel") as? Map<String, Any>
    val thumbnailBadges = badge?.get("thumbnailBadges") as? List<Map<String, Any>>
    val thumbnailBadge = thumbnailBadges?.firstOrNull()
    val badgeViewModel = thumbnailBadge?.get("thumbnailBadgeViewModel") as? Map<String, Any>
    val text = badgeViewModel?.get("text") as? String
    val duration = text

    val metadataContent = lockupMetadata?.get("metadata") as? Map<String, Any>
    val contentMetadata = metadataContent?.get("contentMetadataViewModel") as? Map<String, Any>
    val metadataRows = contentMetadata?.get("metadataRows") as? List<Map<String, Any>>
    val secondRow = metadataRows?.getOrNull(1)
    val metadataParts = secondRow?.get("metadataParts") as? List<Map<String, Any>>
    val uploadDatePart = metadataParts?.getOrNull(1)
    val uploadDateText = uploadDatePart?.get("text") as? Map<String, Any>
    val uploadDate = uploadDateText?.get("content") as? String

    val viewsPart = metadataParts?.getOrNull(0)
    val viewsText = viewsPart?.get("text") as? Map<String, Any>
    val views = viewsText?.get("content") as? String

    val firstRow = metadataRows?.getOrNull(0)
    val authorParts = firstRow?.get("metadataParts") as? List<Map<String, Any>>
    val authorPart = authorParts?.getOrNull(0)
    val authorText = authorPart?.get("text") as? Map<String, Any>
    val author = authorText?.get("content") as? String
    val channelThumbnail = getChannelThumbnailFromLockup(data)

    return Video(
      id = VideoId(videoId),
      title = title,
      author = author ?: "",
      channelId = ChannelId(channelId),
      uploadDate = uploadDate?.let { parseDateTime(it) },
      uploadDateRaw = uploadDate,
      description = null,
      duration = duration,
      thumbnails = ThumbnailSet(videoId),
      keywords = null,
      engagement = views?.let { Engagement(parseViewCount(it), null, null) },
      isLive = duration == "LIVE",
      source = null,
      viewCount = 0,
      channelThumbnail = channelThumbnail
    )
  }

  private fun parseDateTime(dateString: String): Date? {
    return try {
      val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
      formatter.parse(dateString)
    } catch (e: Exception) {
      try {
        val formatter2 = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        formatter2.parse(dateString)
      } catch (e2: Exception) {
        null
      }
    }
  }

  private fun parseViewCount(viewCountString: String): Long? {
    return try {
      viewCountString.replace(Regex("[^0-9]"), "").toLongOrNull()
    } catch (e: Exception) {
      null
    }
  }

  fun getContinuationToken(): String? {
    println("RelatedVideosClient: Looking for continuation token in ${contents.size} items")

    return try {
      // Method 1: Check if we have contents structure (like Dart code)
      if (rootJson?.containsKey("contents") == true) {
        val contents = rootJson["contents"] as? Map<String, Any>
        val twoColumnSearchResultsRenderer =
          contents?.get("twoColumnSearchResultsRenderer") as? Map<String, Any>
        val primaryContents =
          twoColumnSearchResultsRenderer?.get("primaryContents") as? Map<String, Any>
        val sectionListRenderer = primaryContents?.get("sectionListRenderer") as? Map<String, Any>
        val contentsList = sectionListRenderer?.get("contents") as? List<Map<String, Any>>

        if (contentsList != null && contentsList.size > 1) {
          // Try to get item at index 1 (like Dart: elementAtSafe(1))
          val itemAt1 = contentsList.getOrNull(1)
          if (itemAt1 != null) {
            val continuationItemRenderer = itemAt1["continuationItemRenderer"] as? Map<String, Any>
            if (continuationItemRenderer != null) {
              val continuationEndpoint =
                continuationItemRenderer["continuationEndpoint"] as? Map<String, Any>
              if (continuationEndpoint != null) {
                val continuationCommand =
                  continuationEndpoint["continuationCommand"] as? Map<String, Any>
                if (continuationCommand != null) {
                  val token = continuationCommand["token"] as? String
                  if (token != null) {
                    println("RelatedVideosClient: Found continuation token in contents[1]")
                    return token
                  }
                }
              }
            }
          }
        }
      }

      // Method 2: Check for onResponseReceivedCommands (like Dart code)
      if (rootJson?.containsKey("onResponseReceivedCommands") == true) {
        val onResponseReceivedCommands =
          rootJson["onResponseReceivedCommands"] as? List<Map<String, Any>>
        if (onResponseReceivedCommands != null && onResponseReceivedCommands.isNotEmpty()) {
          val firstCommand = onResponseReceivedCommands.first()
          val appendContinuationItemsAction =
            firstCommand["appendContinuationItemsAction"] as? List<Map<String, Any>>
          if (appendContinuationItemsAction != null && appendContinuationItemsAction.isNotEmpty()) {
            val firstAction = appendContinuationItemsAction.first()
            val continuationItems = firstAction["continuationItems"] as? List<Map<String, Any>>
            if (continuationItems != null && continuationItems.size > 1) {
              val itemAt1 = continuationItems.getOrNull(1)
              if (itemAt1 != null) {
                val continuationItemRenderer =
                  itemAt1["continuationItemRenderer"] as? Map<String, Any>
                if (continuationItemRenderer != null) {
                  val continuationEndpoint =
                    continuationItemRenderer["continuationEndpoint"] as? Map<String, Any>
                  if (continuationEndpoint != null) {
                    val continuationCommand =
                      continuationEndpoint["continuationCommand"] as? Map<String, Any>
                    if (continuationCommand != null) {
                      val token = continuationCommand["token"] as? String
                      if (token != null) {
                        println("RelatedVideosClient: Found continuation token in onResponseReceivedCommands")
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

      // Fallback: Look for any continuation item in our contents
      for (item in contents) {
        val continuationItemRenderer = item["continuationItemRenderer"] as? Map<String, Any>
        if (continuationItemRenderer != null) {
          val continuationEndpoint =
            continuationItemRenderer["continuationEndpoint"] as? Map<String, Any>
          if (continuationEndpoint != null) {
            val continuationCommand =
              continuationEndpoint["continuationCommand"] as? Map<String, Any>
            if (continuationCommand != null) {
              val token = continuationCommand["token"] as? String
              if (token != null) {
                println("RelatedVideosClient: Found continuation token in fallback search")
                return token
              }
            }
          }
        }
      }

      println("RelatedVideosClient: No continuation token found")
      null
    } catch (e: Exception) {
      println("RelatedVideosClient: Error extracting continuation token: ${e.message}")
      null
    }
  }

  suspend fun nextPage(httpClient: YTHttpClient): RelatedVideosClient? {
    val continuation = getContinuationToken() ?: return null

    try {
      val response = httpClient.sendPost("next", mapOf("continuation" to continuation))
      val onResponseReceived = response["onResponseReceivedEndpoints"] as? List<Map<String, Any>>
      val appendAction =
        onResponseReceived?.firstOrNull()?.get("appendContinuationItemsAction") as? Map<String, Any>
      val continuationItems = appendAction?.get("continuationItems") as? List<Map<String, Any>>

      return if (continuationItems != null) {
        RelatedVideosClient(continuationItems, null)
      } else null
    } catch (e: Exception) {
      return null
    }
  }

  companion object {
    suspend fun get(httpClient: YTHttpClient, video: Video): RelatedVideosClient? {
      return try {
        val url = "https://www.youtube.com/watch?v=${video.id.value}"
        val response = httpClient.getString(url)
        val contents = extractRelatedVideosContent(response)
        val rootJson = extractRootJson(response)

        if (contents != null) {
          RelatedVideosClient(contents, rootJson)
        } else null
      } catch (e: Exception) {
        null
      }
    }

    private fun extractRelatedVideosContent(html: String): List<Map<String, Any>>? {
      return try {
        println("RelatedVideosClient: Extracting related videos content from HTML")
        val jsonRegex = Regex("var ytInitialData = (\\{.*?\\});")
        val match = jsonRegex.find(html)
        if (match != null) {
          val jsonString = match.groupValues[1]
          println("RelatedVideosClient: Found ytInitialData, length: ${jsonString.length}")
          val json = Json.parseToJsonElement(jsonString).jsonObject

          // Navigate through the JSON structure
          val contents = json["contents"]?.jsonObject
          val twoColumnWatchNext = contents?.get("twoColumnWatchNextResults")?.jsonObject
          val secondaryResults = twoColumnWatchNext?.get("secondaryResults")?.jsonObject
          val secondaryResultsInner = secondaryResults?.get("secondaryResults")?.jsonObject
          val results = secondaryResultsInner?.get("results")?.jsonArray

          println("RelatedVideosClient: Results found: ${results != null}, size: ${results?.size}")

          if (results != null) {
            // Find itemSectionRenderer in results
            val itemSectionRenderer = results.find { item ->
              item.jsonObject["itemSectionRenderer"] != null
            }

            println("RelatedVideosClient: ItemSectionRenderer found: ${itemSectionRenderer != null}")

            if (itemSectionRenderer != null) {
              val itemSectionContents = itemSectionRenderer.jsonObject
                .get("itemSectionRenderer")?.jsonObject
                ?.get("contents")?.jsonArray

              if (itemSectionContents != null) {
                println("RelatedVideosClient: Found ${itemSectionContents.size} related videos in itemSectionRenderer")
                return itemSectionContents.map { it.jsonObject.toMap() }
              }
            }

            // Fallback: if no itemSectionRenderer found, try to parse results directly
            println("RelatedVideosClient: Using fallback, parsing ${results.size} results directly")
            return results.map { it.jsonObject.toMap() }
          }
        }
        null
      } catch (e: Exception) {
        println("Error parsing related videos content: ${e.message}")
        e.printStackTrace()
        null
      }
    }

    private fun extractRootJson(html: String): Map<String, Any>? {
      return try {
        println("RelatedVideosClient: Extracting root JSON from HTML")
        val jsonRegex = Regex("var ytInitialData = (\\{.*?\\});")
        val match = jsonRegex.find(html)
        if (match != null) {
          val jsonString = match.groupValues[1]
          val json = Json.parseToJsonElement(jsonString).jsonObject
          return json.toMap()
        }
        null
      } catch (e: Exception) {
        println("Error parsing root JSON: ${e.message}")
        null
      }
    }
  }
}

private fun JsonObject.toMap(): Map<String, Any> {
  val map = mutableMapOf<String, Any>()
  entries.forEach { (key, value) ->
    when (value) {
      is kotlinx.serialization.json.JsonPrimitive -> map[key] = value.content
      is JsonObject -> map[key] = value.toMap()
      is kotlinx.serialization.json.JsonArray -> map[key] = value.jsonArray.map {
        when (it) {
          is kotlinx.serialization.json.JsonPrimitive -> it.content
          is JsonObject -> it.toMap()
          is kotlinx.serialization.json.JsonArray -> it.jsonArray.map { item ->
            when (item) {
              is kotlinx.serialization.json.JsonPrimitive -> item.content
              is JsonObject -> item.toMap()
              else -> item.toString()
            }
          }

          else -> it.toString()
        }
      }

      else -> map[key] = value.toString()
    }
  }
  return map
}
