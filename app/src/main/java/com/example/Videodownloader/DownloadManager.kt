package com.example.videodownloader

import android.content.Context
import android.content.Intent
import java.util.concurrent.ConcurrentLinkedQueue

object DownloadManager {
    
    private val downloadQueue = ConcurrentLinkedQueue<DownloadTask>()
    private var isDownloading = false
    private lateinit var context: Context
    
    fun initialize(context: Context) {
        this.context = context.applicationContext
    }
    
    fun addToQueue(url: String, fileName: String? = null) {
        val task = DownloadTask(url, fileName ?: "video_${System.currentTimeMillis()}.mp4")
        downloadQueue.add(task)
        processQueue()
    }
    
    private fun processQueue() {
        if (isDownloading || downloadQueue.isEmpty()) return
        
        isDownloading = true
        val task = downloadQueue.poll()
        
        val intent = Intent(context, DownloadService::class.java).apply {
            putExtra("download_url", task.url)
            putExtra("file_name", task.fileName)
        }
        context.startService(intent)
        
        // Reset flag after delay (service will handle its own lifecycle)
        android.os.Handler(context.mainLooper).postDelayed({
            isDownloading = false
            processQueue()
        }, 1000)
    }
    
    fun getQueueSize(): Int = downloadQueue.size
    
    data class DownloadTask(val url: String, val fileName: String)
}
