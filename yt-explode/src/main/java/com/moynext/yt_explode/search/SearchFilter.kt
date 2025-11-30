package com.moynext.yt_explode.search

data class SearchFilter(
    val value: String
) {
    companion object {
        val EMPTY = SearchFilter("")
    }
}

object FeatureFilters {
    val LIVE = SearchFilter("EgJAAQ%253D%253D")
    val V4K = SearchFilter("EgJwAQ%253D%253D")
    val HD = SearchFilter("EgIgAQ%253D%253D")
    val SUBTITLES = SearchFilter("EgIoAQ%253D%253D")
    val CREATIVE_COMMONS = SearchFilter("EgIwAQ%253D%253D")
    val V360 = SearchFilter("EgJ4AQ%253D%253D")
    val VR180 = SearchFilter("EgPQAQE%253D")
    val V3D = SearchFilter("EgI4AQ%253D%253D")
    val HDR = SearchFilter("EgPIAQE%253D")
    val LOCATION = SearchFilter("EgO4AQE%253D")
    val PURCHASED = SearchFilter("EgJIAQ%253D%253D")
}

object UploadDateFilter {
    val LAST_HOUR = SearchFilter("EgIIAQ%253D%253D")
    val TODAY = SearchFilter("EgIIAg%253D%253D")
    val LAST_WEEK = SearchFilter("EgIIAw%253D%253D")
    val LAST_MONTH = SearchFilter("EgIIBA%253D%253D")
    val LAST_YEAR = SearchFilter("EgIIBQ%253D%253D")
}

object TypeFilters {
    val VIDEO = SearchFilter("EgIQAQ%253D%253D")
    val CHANNEL = SearchFilter("EgIQAg%253D%253D")
    val PLAYLIST = SearchFilter("EgIQAw%253D%253D")
    val MOVIE = SearchFilter("EgIQBA%253D%253D")
    val SHOW = SearchFilter("EgIQBQ%253D%253D")
}

object DurationFilters {
    val SHORT = SearchFilter("EgIYAQ%253D%253D")
    val LONG = SearchFilter("EgIYAg%253D%253D")
}

object SortFilters {
    val RELEVANCE = SearchFilter("CAASAhAB")
    val UPLOAD_DATE = SearchFilter("CAI%253D")
    val VIEW_COUNT = SearchFilter("CAM%253D")
    val RATING = SearchFilter("CAE%253D")
}
