package com.example.videodownloader.models

data class VideoInfo(
    val title: String,
    val url: String,
    val thumbnail: String? = null,
    val duration: Int? = null,
    val formats: List<VideoFormat> = emptyList()
)

data class VideoFormat(
    val quality: String,
    val url: String,
    val format: String,
    val size: Long? = null
)
