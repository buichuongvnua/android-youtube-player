package com.moynext.yt_explode.exceptions

import okhttp3.Response

class FatalFailureException(
    message: String = "Fatal failure occurred",
    val response: Response? = null
) : Exception(message)
