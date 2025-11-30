package com.moynext.yt_explode.exceptions

import okhttp3.Response

class TransientFailureException(
    message: String = "Transient failure occurred",
    val response: Response? = null
) : Exception(message)
