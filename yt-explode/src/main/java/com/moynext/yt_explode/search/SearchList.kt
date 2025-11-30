package com.moynext.yt_explode.search

import com.moynext.yt_explode.client.YTHttpClient

class SearchList(
    private val searchResults: List<SearchResult>,
    private val page: SearchPage,
    private val httpClient: YTHttpClient
) {
    val content: List<SearchResult> get() = searchResults
    val relatedVideos: List<SearchResult> get() = page.relatedVideos
    val estimatedResults: Int get() = page.estimatedResults
    
    suspend fun nextPage(): SearchList? {
        val nextPage = page.nextPage(httpClient) ?: return null
        return SearchList(nextPage.searchContent, nextPage, httpClient)
    }
}