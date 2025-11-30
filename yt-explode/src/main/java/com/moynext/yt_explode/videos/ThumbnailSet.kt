package com.moynext.yt_explode.videos

import com.moynext.yt_explode.common.Thumbnail

class ThumbnailSet(
    private val videoId: String
) {
    fun getLowResUrl(): String = "https://img.youtube.com/vi/$videoId/default.jpg"
    fun getMediumResUrl(): String = "https://img.youtube.com/vi/$videoId/mqdefault.jpg"
    fun getHighResUrl(): String = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    fun getStandardResUrl(): String = "https://img.youtube.com/vi/$videoId/sddefault.jpg"
    fun getMaxResUrl(): String = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
    
    fun getThumbnails(): List<Thumbnail> = listOf(
        Thumbnail(getLowResUrl(), 120, 90),
        Thumbnail(getMediumResUrl(), 320, 180),
        Thumbnail(getHighResUrl(), 480, 360),
        Thumbnail(getStandardResUrl(), 640, 480),
        Thumbnail(getMaxResUrl(), 1280, 720)
    )
}
