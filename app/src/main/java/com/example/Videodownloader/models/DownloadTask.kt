package com.example.videodownloader.models

import java.util.UUID

data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val fileName: String,
    val title: String = "",
    var progress: Int = 0,
    var status: DownloadStatus = DownloadStatus.PENDING
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED
}
