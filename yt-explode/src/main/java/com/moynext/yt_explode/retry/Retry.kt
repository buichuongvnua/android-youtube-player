package com.moynext.yt_explode.retry

suspend fun <T> retry(
    client: Any,
    operation: suspend () -> T,
    maxRetries: Int = 3,
    delayMs: Long = 1000
): T {
    var lastException: Exception? = null
    
    repeat(maxRetries) { attempt ->
        try {
            return operation()
        } catch (e: Exception) {
            lastException = e
            if (attempt < maxRetries - 1) {
                kotlinx.coroutines.delay(delayMs * (attempt + 1))
            }
        }
    }
    
    throw lastException ?: Exception("Retry failed")
}
