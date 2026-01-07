package com.moynext.yt_explode.client

import com.moynext.yt_explode.exceptions.FatalFailureException
import com.moynext.yt_explode.exceptions.HttpClientClosedException
import com.moynext.yt_explode.exceptions.RequestLimitExceededException
import com.moynext.yt_explode.exceptions.TransientFailureException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

class YTHttpClient(
  private val httpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()
) {
  @Volatile
  private var closed = false

  val isClosed: Boolean get() = closed

  companion object {
    private val defaultHeaders = mapOf(
      "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.18 Safari/537.36",
      "cookie" to "CONSENT=YES+cb",
      "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",
      "accept-language" to "en-US,en;q=0.5"
    )
  }

  val headers: Map<String, String> get() = defaultHeaders

  private fun validateResponse(response: Response, statusCode: Int) {
    if (closed) return

    val request = response.request
    val url = request.url

    if (url.host.endsWith(".google.com") && url.encodedPath.startsWith("/sorry/")) {
      throw RequestLimitExceededException("Request limit exceeded", response)
    }

    if (statusCode >= 500) {
      throw TransientFailureException("Server error", response)
    }

    if (statusCode == 429) {
      throw RequestLimitExceededException("Rate limit exceeded", response)
    }

    if (statusCode >= 400) {
      throw FatalFailureException("Client error", response)
    }
  }

  suspend fun getString(
    url: String,
    headers: Map<String, String> = emptyMap(),
    validate: Boolean = true
  ): String {
    val response = get(url, headers, validate)
    if (closed) throw HttpClientClosedException()
    return withContext(Dispatchers.IO) {
      response.body?.string() ?: ""
    }
  }

  suspend fun get(
    url: String,
    headers: Map<String, String> = emptyMap(),
    validate: Boolean = false
  ): Response {
    if (closed) throw HttpClientClosedException()

    val requestBuilder = Request.Builder()
      .url(url)
      .get()

    val allHeaders = this.headers + headers
    allHeaders.forEach { (key, value) ->
      requestBuilder.addHeader(key, value)
    }

    val response = withContext(Dispatchers.IO) {
      httpClient.newCall(requestBuilder.build()).execute()
    }

    if (closed) throw HttpClientClosedException()

    if (validate) {
      validateResponse(response, response.code)
    }

    return response
  }

  suspend fun post(
    url: String,
    headers: Map<String, String> = emptyMap(),
    body: String? = null,
    validate: Boolean = false
  ): Response {
    if (closed) throw HttpClientClosedException()

    val requestBuilder = Request.Builder()
      .url(url)

    val allHeaders = this.headers + headers
    allHeaders.forEach { (key, value) ->
      requestBuilder.addHeader(key, value)
    }

    if (body != null) {
      val mediaType = "application/json".toMediaType()
      requestBuilder.post(body.toRequestBody(mediaType))
    } else {
      requestBuilder.post(RequestBody.create(null, ""))
    }

    val response = withContext(Dispatchers.IO) {
      httpClient.newCall(requestBuilder.build()).execute()
    }

    if (closed) throw HttpClientClosedException()

    if (validate) {
      validateResponse(response, response.code)
    }

    return response
  }

  suspend fun postString(
    url: String,
    body: Map<String, Any>? = null,
    headers: Map<String, String> = emptyMap(),
    validate: Boolean = true
  ): String {
    val response = post(
      url = url,
      headers = headers,
      body = if (body != null) Json.encodeToString(body) else null,
      validate = validate
    )

    if (closed) throw HttpClientClosedException()

    return withContext(Dispatchers.IO) {
      response.body?.string() ?: ""
    }
  }

  suspend fun getContentLength(
    url: String,
    headers: Map<String, String> = emptyMap(),
    validate: Boolean = true
  ): Long? {
    val requestBuilder = Request.Builder()
      .url(url)
      .head()

    val allHeaders = this.headers + headers
    allHeaders.forEach { (key, value) ->
      requestBuilder.addHeader(key, value)
    }

    val response = withContext(Dispatchers.IO) {
      httpClient.newCall(requestBuilder.build()).execute()
    }

    if (closed) throw HttpClientClosedException()

    if (validate) {
      validateResponse(response, response.code)
    }

    return response.header("content-length")?.toLongOrNull()
  }

  suspend fun sendContinuation(
    action: String,
    token: String,
    headers: Map<String, String> = emptyMap()
  ): JsonObject = sendPost(action, mapOf("continuation" to token), headers)

  suspend fun sendPost(
    action: String,
    data: Map<String, Any>,
    headers: Map<String, String> = emptyMap()
  ): JsonObject {
    require(action in listOf("next", "browse", "search")) { "Invalid action: $action" }

    val url =
      "https://www.youtube.com/youtubei/v1/$action?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

    val body = buildJsonObject {
      putJsonObject("context") {
        putJsonObject("client") {
          put("browserName", "Chrome")
          put("browserVersion", "105.0.0.0")
          put("clientFormFactor", "UNKNOWN_FORM_FACTOR")
          put("clientName", "WEB")
          put("clientVersion", "2.20220921.00.00")
        }
      }
      data.forEach { (key, value) ->
        when (value) {
          is String -> put(key, value)
          is Number -> put(key, value)
          is Boolean -> put(key, value)
          else -> put(key, value.toString())
        }
      }
    }

    var lastException: Exception? = null
    repeat(3) { attempt ->
      try {
        val response = post(url, headers, Json.encodeToString(body), true)
        if (closed) throw HttpClientClosedException()
        val bodyString = withContext(Dispatchers.IO) {
          response.body?.string() ?: "{}"
        }
        return Json.parseToJsonElement(bodyString).jsonObject
      } catch (e: Exception) {
        lastException = e
      }
    }
    throw lastException ?: Exception("Retry failed")
  }

  fun close() {
    closed = true
    httpClient.dispatcher.executorService.shutdown()
  }
}

class HlsManifest {
  companion object {
    fun parseVideoSegments(videoIndex: String): List<HlsSegment> {
      return emptyList()
    }
  }
}

data class HlsSegment(
  val url: String
)
