package com.moynext.yt_explode

import com.moynext.yt_explode.client.YTHttpClient
import com.moynext.yt_explode.search.SearchClient
import com.moynext.yt_explode.videos.VideoClient
import com.moynext.yt_explode.playlists.PlaylistClient
import com.moynext.yt_explode.channels.ChannelClient

class YTExplode(
    private val httpClient: YTHttpClient = YTHttpClient()
) {
    val search: SearchClient = SearchClient(httpClient)
    val videos: VideoClient = VideoClient(httpClient)
    val playlists: PlaylistClient = PlaylistClient(httpClient)
    val channels: ChannelClient = ChannelClient(httpClient)
    
    fun close() {
        httpClient.close()
    }
}
