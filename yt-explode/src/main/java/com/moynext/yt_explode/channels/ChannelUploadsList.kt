package com.moynext.yt_explode.channels

import com.moynext.yt_explode.client.YTHttpClient
import com.moynext.yt_explode.videos.Video
import com.moynext.yt_explode.common.BasePagedList

class ChannelUploadsList(
    base: List<Video>,
    val channelTitle: String,
    val channelId: ChannelId,
    private val page: Any?, // Placeholder for page object
    private val httpClient: YTHttpClient
) : BasePagedList<Video>(base) {
    
    override suspend fun nextPage(): ChannelUploadsList? {
        // TODO: Implement next page logic
        return null
    }
}
