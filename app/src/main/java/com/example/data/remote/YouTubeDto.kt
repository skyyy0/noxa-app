package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YouTubeSearchResponse(
    @Json(name = "items") val items: List<YouTubeSearchItem>? = null,
    @Json(name = "nextPageToken") val nextPageToken: String? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeSearchItem(
    @Json(name = "id") val id: YouTubeResourceId? = null,
    @Json(name = "snippet") val snippet: YouTubeSnippet? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeResourceId(
    @Json(name = "kind") val kind: String? = null,
    @Json(name = "videoId") val videoId: String? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeVideoListResponse(
    @Json(name = "items") val items: List<YouTubeVideoItem>? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeVideoItem(
    @Json(name = "id") val id: String? = null,
    @Json(name = "snippet") val snippet: YouTubeSnippet? = null,
    @Json(name = "contentDetails") val contentDetails: YouTubeContentDetails? = null,
    @Json(name = "statistics") val statistics: YouTubeStatistics? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeSnippet(
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "channelTitle") val channelTitle: String? = null,
    @Json(name = "channelId") val channelId: String? = null,
    @Json(name = "thumbnails") val thumbnails: YouTubeThumbnails? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeThumbnails(
    @Json(name = "default") val defaultThumb: YouTubeThumbnailInfo? = null,
    @Json(name = "medium") val medium: YouTubeThumbnailInfo? = null,
    @Json(name = "high") val high: YouTubeThumbnailInfo? = null,
    @Json(name = "standard") val standard: YouTubeThumbnailInfo? = null,
    @Json(name = "maxres") val maxres: YouTubeThumbnailInfo? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeThumbnailInfo(
    @Json(name = "url") val url: String? = null,
    @Json(name = "width") val width: Int? = null,
    @Json(name = "height") val height: Int? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeContentDetails(
    @Json(name = "duration") val duration: String? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeStatistics(
    @Json(name = "viewCount") val viewCount: String? = null,
    @Json(name = "likeCount") val likeCount: String? = null
)
