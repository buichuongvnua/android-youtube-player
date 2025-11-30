package com.moynext.yt_explode.search

import com.moynext.yt_explode.client.YTHttpClient

class SearchQuery(
    private val httpClient: YTHttpClient,
    val searchQuery: String,
    private val page: SearchPage
) {
    companion object {
        suspend fun search(
            httpClient: YTHttpClient,
            searchQuery: String,
            filter: SearchFilter = SearchFilter.EMPTY
        ): SearchQuery {
            if (searchQuery.isBlank()) {
                throw IllegalArgumentException("Search query cannot be empty")
            }
            
            val page = SearchPage.get(httpClient, searchQuery, filter)
            return SearchQuery(httpClient, searchQuery, page)
        }
    }
    
    suspend fun nextPage(): SearchQuery? {
        val nextPage = page.nextPage(httpClient) ?: return null
        return SearchQuery(httpClient, searchQuery, nextPage)
    }
    
    val content: List<SearchResult> get() = page.searchContent
    val relatedVideos: List<SearchResult> get() = page.relatedVideos
    val estimatedResults: Int get() = page.estimatedResults
}
