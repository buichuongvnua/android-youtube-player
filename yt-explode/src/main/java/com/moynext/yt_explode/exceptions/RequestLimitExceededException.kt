package com.moynext.yt_explode.exceptions

import okhttp3.Response

class RequestLimitExceededException(
    message: String = "Request limit exceeded",
    val response: Response? = null
) : Exception(message)
