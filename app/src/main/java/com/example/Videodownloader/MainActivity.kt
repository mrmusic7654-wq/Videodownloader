package com.example.videodownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.videodownloader.utils.NetworkUtils
import com.example.videodownloader.utils.PermissionUtils

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private var currentDownloadUrl: String = ""
    private var isExtracting = false
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize DownloadManager
        DownloadManager.initialize(applicationContext)
        
        setupWebView()
        checkPermissionsAndLoad()
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent) {
        // Handle shared URLs from other apps
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    val url = uri.toString()
                    if (url.isNotEmpty()) {
                        loadUrlInWebView(url)
                    }
                }
            }
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    sharedText?.let { text ->
                        if (text.startsWith("http")) {
                            loadUrlInWebView(text)
                        }
                    }
                }
            }
        }
    }
    
    private fun loadUrlInWebView(url: String) {
        webView.evaluateJavascript("""
            document.getElementById('videoUrl').value = '$url';
            document.getElementById('extractBtn').click();
        """.trimIndent(), null)
    }
    
    private fun setupWebView() {
        webView = WebView(this)
        setContentView(webView)
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadsImagesAutomatically = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setAppCacheEnabled(true)
            
            // User agent to avoid being blocked
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                
                // Handle download links
                if (url.contains(".mp4") || url.contains(".webm") || url.contains(".mp3") ||
                    url.contains("download") || url.contains("y2mate.com/download")) {
                    showDownloadDialog(url)
                    return true
                }
                
                // Handle y2mate direct downloads
                if (url.contains("y2mate.com") && url.contains("download")) {
                    showDownloadDialog(url)
                    return true
                }
                
                return false
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectJavaScriptInterface()
            }
            
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Toast.makeText(this@MainActivity, "Error: $description", Toast.LENGTH_SHORT).show()
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                // Log web console messages for debugging
                android.util.Log.d("WebView", "${consoleMessage.message()} (${consoleMessage.lineNumber()})")
                return true
            }
        }
    }
    
    private fun injectJavaScriptInterface() {
        // Add JavaScript interface for native communication
        webView.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun downloadVideo(url: String) {
                runOnUiThread {
                    showDownloadDialog(url)
                }
            }
            
            @JavascriptInterface
            fun showToast(message: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
            
            @JavascriptInterface
            fun log(message: String) {
                android.util.Log.d("WebView-JS", message)
            }
        }, "Android")
        
        // Inject JavaScript to intercept all download attempts
        val interceptScript = """
            (function() {
                // Override window.open for download links
                const originalOpen = window.open;
                window.open = function(url, name, specs) {
                    if (url && (url.includes('.mp4') || url.includes('.webm') || url.includes('.mp3') || url.includes('download'))) {
                        Android.downloadVideo(url);
                        return null;
                    }
                    return originalOpen.call(this, url, name, specs);
                };
                
                // Intercept all clicks on download buttons
                document.addEventListener('click', function(e) {
                    let target = e.target;
                    while (target && target !== document) {
                        if (target.tagName === 'A' || target.classList?.contains('download-btn') || target.classList?.contains('format-item')) {
                            let url = target.href || target.getAttribute('data-url') || target.getAttribute('data-href');
                            if (url && (url.includes('.mp4') || url.includes('.webm') || url.includes('.mp3') || url.includes('download'))) {
                                e.preventDefault();
                                Android.downloadVideo(url);
                                return false;
                            }
                        }
                        target = target.parentNode;
                    }
                });
                
                console.log('Download interceptor injected successfully');
                Android.log('Download interceptor injected');
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(interceptScript, null)
    }
    
    private fun checkPermissionsAndLoad() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ needs notification permission
                val permissions = mutableListOf<String>()
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                
                if (permissions.isNotEmpty()) {
                    ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
                } else {
                    loadVideoDownloader()
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10+ only needs internet
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.INTERNET), PERMISSION_REQUEST_CODE)
                } else {
                    loadVideoDownloader()
                }
            }
            else -> {
                // Android 9 and below need storage permission
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.INTERNET
                        ),
                        PERMISSION_REQUEST_CODE
                    )
                } else {
                    loadVideoDownloader()
                }
            }
        }
    }
    
    private fun showDownloadDialog(url: String) {
        currentDownloadUrl = url
        
        AlertDialog.Builder(this)
            .setTitle("Download Video")
            .setMessage("Do you want to download this video?")
            .setPositiveButton("Download") { _, _ ->
                startDownload(url)
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Open in Browser") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }
            .show()
    }
    
    private fun startDownload(url: String) {
        // Check network connectivity
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show()
            return
        }
        
        // Check if using mobile data and warn
        if (!NetworkUtils.isWifiConnected(this)) {
            AlertDialog.Builder(this)
                .setTitle("Mobile Data")
                .setMessage("You are using mobile data. Downloading may consume significant data. Continue?")
                .setPositiveButton("Continue") { _, _ ->
                    proceedWithDownload(url)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            proceedWithDownload(url)
        }
    }
    
    private fun proceedWithDownload(url: String) {
        Toast.makeText(this, "Starting download in background...", Toast.LENGTH_SHORT).show()
        
        // Add to download queue
        DownloadManager.addToQueue(url)
        
        // Show notification about background download
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
                // Notification will be shown by DownloadService
            }
        }
    }
    
    private fun loadVideoDownloader() {
        val htmlContent = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
                <title>Video Downloader Pro</title>
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        min-height: 100vh;
                        padding: 20px;
                    }
                    
                    .container {
                        max-width: 900px;
                        margin: 0 auto;
                        background: white;
                        border-radius: 20px;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                        overflow: hidden;
                        animation: slideUp 0.5s ease;
                    }
                    
                    @keyframes slideUp {
                        from {
                            opacity: 0;
                            transform: translateY(30px);
                        }
                        to {
                            opacity: 1;
                            transform: translateY(0);
                        }
                    }
                    
                    .header {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        padding: 30px;
                        text-align: center;
                    }
                    
                    .header h1 {
                        font-size: 2em;
                        margin-bottom: 10px;
                    }
                    
                    .header p {
                        opacity: 0.9;
                        font-size: 14px;
                    }
                    
                    .content {
                        padding: 30px;
                    }
                    
                    .url-input-wrapper {
                        display: flex;
                        gap: 10px;
                        margin-bottom: 20px;
                        flex-wrap: wrap;
                    }
                    
                    input[type="text"] {
                        flex: 1;
                        padding: 15px;
                        border: 2px solid #e0e0e0;
                        border-radius: 12px;
                        font-size: 14px;
                        font-family: monospace;
                    }
                    
                    input[type="text"]:focus {
                        outline: none;
                        border-color: #667eea;
                    }
                    
                    button {
                        padding: 15px 30px;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        border: none;
                        border-radius: 12px;
                        font-weight: bold;
                        cursor: pointer;
                        transition: transform 0.2s;
                    }
                    
                    button:hover {
                        transform: translateY(-2px);
                    }
                    
                    button:disabled {
                        opacity: 0.6;
                        cursor: not-allowed;
                    }
                    
                    .method-buttons {
                        display: flex;
                        gap: 10px;
                        margin-bottom: 20px;
                        flex-wrap: wrap;
                    }
                    
                    .method-btn {
                        flex: 1;
                        padding: 12px;
                        background: #f0f0f0;
                        border: 2px solid #e0e0e0;
                        border-radius: 10px;
                        text-align: center;
                        cursor: pointer;
                        font-weight: 500;
                        transition: all 0.2s;
                    }
                    
                    .method-btn.active {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        border-color: transparent;
                    }
                    
                    .progress-section {
                        margin: 20px 0;
                        display: none;
                    }
                    
                    .progress-bar {
                        width: 100%;
                        height: 30px;
                        background: #f0f0f0;
                        border-radius: 15px;
                        overflow: hidden;
                    }
                    
                    .progress-fill {
                        height: 100%;
                        background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
                        width: 0%;
                        transition: width 0.3s;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        color: white;
                        font-size: 12px;
                    }
                    
                    .status {
                        margin: 15px 0;
                        padding: 12px;
                        border-radius: 10px;
                        text-align: center;
                        font-size: 14px;
                    }
                    
                    .status.success {
                        background: #d4edda;
                        color: #155724;
                    }
                    
                    .status.error {
                        background: #f8d7da;
                        color: #721c24;
                    }
                    
                    .status.info {
                        background: #d1ecf1;
                        color: #0c5460;
                    }
                    
                    .status.warning {
                        background: #fff3cd;
                        color: #856404;
                    }
                    
                    .video-info {
                        margin: 20px 0;
                        padding: 20px;
                        background: #f8f9fa;
                        border-radius: 12px;
                        display: none;
                    }
                    
                    .video-info img {
                        max-width: 100%;
                        border-radius: 10px;
                        margin-top: 10px;
                    }
                    
                    .format-list {
                        margin-top: 15px;
                        max-height: 400px;
                        overflow-y: auto;
                    }
                    
                    .format-item {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        padding: 12px;
                        background: white;
                        border-radius: 8px;
                        margin-bottom: 8px;
                        cursor: pointer;
                        border: 1px solid #e0e0e0;
                        transition: all 0.2s;
                    }
                    
                    .format-item:hover {
                        border-color: #667eea;
                        transform: translateX(5px);
                    }
                    
                    .download-btn {
                        padding: 8px 16px;
                        background: #28a745;
                        color: white;
                        border: none;
                        border-radius: 6px;
                        cursor: pointer;
                    }
                    
                    .tips {
                        margin-top: 20px;
                        padding: 15px;
                        background: #fff3cd;
                        border-radius: 12px;
                        font-size: 13px;
                    }
                    
                    .tips ul {
                        margin-left: 20px;
                        margin-top: 8px;
                    }
                    
                    .tips li {
                        margin: 5px 0;
                    }
                    
                    .loading {
                        display: inline-block;
                        width: 20px;
                        height: 20px;
                        border: 3px solid #f3f3f3;
                        border-top: 3px solid #667eea;
                        border-radius: 50%;
                        animation: spin 1s linear infinite;
                    }
                    
                    @keyframes spin {
                        0% { transform: rotate(0deg); }
                        100% { transform: rotate(360deg); }
                    }
                    
                    @media (max-width: 600px) {
                        .content {
                            padding: 20px;
                        }
                        .url-input-wrapper {
                            flex-direction: column;
                        }
                        .method-btn {
                            font-size: 12px;
                            padding: 8px;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🎬 Video Downloader Pro</h1>
                        <p>Download videos from YouTube, Facebook, Instagram, TikTok, Twitter, and 1000+ sites</p>
                        <p style="font-size: 12px; margin-top: 8px;">✨ Works entirely in this app! Videos save to Downloads folder</p>
                    </div>
                    <div class="content">
                        <div class="method-buttons" id="methodButtons">
                            <div class="method-btn active" data-method="y2mate">🎵 Y2mate (Best)</div>
                            <div class="method-btn" data-method="piped">🌊 Piped API</div>
                            <div class="method-btn" data-method="direct">📁 Direct URL</div>
                        </div>
                        
                        <div class="url-input-wrapper">
                            <input type="text" id="videoUrl" placeholder="Paste any video link... (YouTube, Facebook, TikTok, etc.)" />
                            <button id="extractBtn">🔍 Extract Video</button>
                        </div>
                        
                        <div class="progress-section" id="progressSection">
                            <div class="progress-bar">
                                <div class="progress-fill" id="progressFill">0%</div>
                            </div>
                            <div class="status info" id="status"></div>
                        </div>
                        
                        <div class="video-info" id="videoInfo">
                            <h3>📺 Video Information</h3>
                            <div id="infoContent"></div>
                            <div id="formatList"></div>
                        </div>
                        
                        <div class="tips">
                            <h3>💡 How to Download:</h3>
                            <ul>
                                <li><strong>🎵 Y2mate Method:</strong> Best for YouTube - extracts download links</li>
                                <li><strong>🌊 Piped API:</strong> Privacy-friendly YouTube API with direct links</li>
                                <li><strong>📁 Direct URL:</strong> For direct MP4/WEBM/MP3 links</li>
                                <li><strong>📋 Auto-detect:</strong> URLs from clipboard are automatically detected</li>
                                <li><strong>📱 Share:</strong> Share videos from other apps directly to this app</li>
                                <li><strong>⚠️ Note:</strong> If one method fails, try another!</li>
                            </ul>
                        </div>
                    </div>
                </div>
                
                <script>
                    let currentMethod = 'y2mate';
                    let isExtracting = false;
                    
                    // Method selector
                    document.querySelectorAll('.method-btn').forEach(btn => {
                        btn.addEventListener('click', () => {
                            document.querySelectorAll('.method-btn').forEach(b => b.classList.remove('active'));
                            btn.classList.add('active');
                            currentMethod = btn.dataset.method;
                            showStatus(`Switched to ${btn.textContent} method`, 'info');
                            document.getElementById('videoInfo').style.display = 'none';
                        });
                    });
                    
                    function showStatus(message, type) {
                        const statusDiv = document.getElementById('status');
                        statusDiv.textContent = message;
                        statusDiv.className = `status ${type}`;
                        document.getElementById('progressSection').style.display = 'block';
                        setTimeout(() => {
                            if (document.getElementById('status').textContent === message) {
                                document.getElementById('progressSection').style.display = 'none';
                            }
                        }, 4000);
                    }
                    
                    function updateProgress(percent, message) {
                        const progressFill = document.getElementById('progressFill');
                        progressFill.style.width = percent + '%';
                        progressFill.textContent = Math.round(percent) + '%';
                        showStatus(message, 'info');
                    }
                    
                    function extractVideoId(url) {
                        const patterns = [
                            /(?:youtube\.com\/watch\?v=)([^&]+)/,
                            /(?:youtu\.be\/)([^?]+)/,
                            /(?:youtube\.com\/embed\/)([^?]+)/,
                            /(?:youtube\.com\/shorts\/)([^?]+)/
                        ];
                        for (let pattern of patterns) {
                            const match = url.match(pattern);
                            if (match) return match[1];
                        }
                        return null;
                    }
                    
                    async function extractVideo() {
                        const url = document.getElementById('videoUrl').value.trim();
                        if (!url) {
                            showStatus('Please enter a video URL', 'error');
                            return;
                        }
                        
                        if (isExtracting) {
                            showStatus('Already extracting...', 'warning');
                            return;
                        }
                        
                        isExtracting = true;
                        document.getElementById('extractBtn').disabled = true;
                        document.getElementById('videoInfo').style.display = 'none';
                        document.getElementById('formatList').innerHTML = '';
                        updateProgress(0, 'Starting extraction...');
                        
                        try {
                            let result = null;
                            
                            if (currentMethod === 'y2mate') {
                                updateProgress(30, 'Connecting to Y2mate...');
                                const videoId = extractVideoId(url);
                                if (videoId) {
                                    // Open Y2mate in a popup for the user
                                    const y2mateUrl = 'https://www.y2mate.com/youtube/' + videoId;
                                    showStatus('Opening Y2mate in new window...', 'info');
                                    window.open(y2mateUrl, '_blank');
                                    result = {
                                        success: true,
                                        data: {
                                            title: 'YouTube Video',
                                            thumbnail: 'https://img.youtube.com/vi/' + videoId + '/hqdefault.jpg',
                                            formats: [
                                                { note: 'Open in Y2mate to download', url: y2mateUrl, ext: 'html' },
                                                { note: 'MP4 - Best Quality', url: y2mateUrl, ext: 'mp4' },
                                                { note: 'MP3 Audio', url: y2mateUrl, ext: 'mp3' }
                                            ]
                                        }
                                    };
                                } else {
                                    result = { success: false, error: 'Not a valid YouTube URL' };
                                }
                            } else if (currentMethod === 'piped') {
                                updateProgress(30, 'Connecting to Piped API...');
                                const videoId = extractVideoId(url);
                                if (videoId) {
                                    try {
                                        const response = await fetch('https://pipedapi.kavin.rocks/streams/' + videoId);
                                        if (response.ok) {
                                            const data = await response.json();
                                            const formats = [];
                                            if (data.videoStreams && data.videoStreams.length > 0) {
                                                data.videoStreams.forEach(s => {
                                                    if (s.url) {
                                                        formats.push({ 
                                                            note: s.quality + ' - ' + (s.format || 'MP4'), 
                                                            url: s.url, 
                                                            ext: 'mp4',
                                                            size: s.contentLength
                                                        });
                                                    }
                                                });
                                            }
                                            if (data.audioStreams && data.audioStreams.length > 0) {
                                                data.audioStreams.forEach(s => {
                                                    if (s.url) {
                                                        formats.push({ 
                                                            note: 'Audio - ' + (s.bitrate || '128') + 'kbps', 
                                                            url: s.url, 
                                                            ext: 'mp3',
                                                            size: s.contentLength
                                                        });
                                                    }
                                                });
                                            }
                                            result = {
                                                success: true,
                                                data: {
                                                    title: data.title,
                                                    thumbnail: data.thumbnailUrl,
                                                    duration: data.duration,
                                                    formats: formats
                                                }
                                            };
                                        } else {
                                            result = { success: false, error: 'Piped API failed' };
                                        }
                                    } catch (e) {
                                        result = { success: false, error: 'Network error: ' + e.message };
                                    }
                                } else {
                                    result = { success: false, error: 'Not a valid YouTube URL' };
                                }
                            } else if (currentMethod === 'direct') {
                                updateProgress(30, 'Checking direct URL...');
                                const videoExtensions = ['mp4', 'webm', 'avi', 'mov', 'mkv', 'mp3', 'm4a'];
                                const isDirect = videoExtensions.some(ext => url.toLowerCase().includes(ext));
                                if (isDirect) {
                                    result = {
                                        success: true,
                                        data: {
                                            title: url.split('/').pop() || 'Video',
                                            formats: [{ note: 'Direct Video', url: url, ext: url.split('.').pop() }]
                                        }
                                    };
                                } else {
                                    result = { success: false, error: 'Not a direct video URL' };
                                }
                            }
                            
                            if (result && result.success) {
                                updateProgress(100, 'Extraction complete!');
                                displayVideoInfo(result.data);
                                showStatus('✅ Ready to download! Click on any format.', 'success');
                            } else {
                                showStatus(result?.error || 'Extraction failed. Try another method.', 'error');
                            }
                        } catch (error) {
                            showStatus('Error: ' + error.message, 'error');
                        } finally {
                            isExtracting = false;
                            document.getElementById('extractBtn').disabled = false;
                        }
                    }
                    
                    function formatFileSize(bytes) {
                        if (!bytes) return '';
                        const sizes = ['B', 'KB', 'MB', 'GB'];
                        const i = Math.floor(Math.log(bytes) / Math.log(1024));
                        return (bytes / Math.pow(1024, i)).toFixed(2) + ' ' + sizes[i];
                    }
                    
                    function displayVideoInfo(data) {
                        const infoDiv = document.getElementById('videoInfo');
                        const infoContent = document.getElementById('infoContent');
                        const formatList = document.getElementById('formatList');
                        
                        let thumbnailHtml = '';
                        if (data.thumbnail) {
                            thumbnailHtml = '<img src="' + data.thumbnail + '" alt="Thumbnail">';
                        }
                        
                        const durationText = data.duration ? ` (${Math.floor(data.duration / 60)}:${(data.duration % 60).toString().padStart(2, '0')})` : '';
                        
                        infoContent.innerHTML = '<p><strong>🎬 Title:</strong> ' + (data.title || 'Unknown') + durationText + '</p>' + thumbnailHtml;
                        
                        formatList.innerHTML = '<h4>📥 Available Formats:</h4>';
                        
                        if (data.formats && data.formats.length > 0) {
                            data.formats.forEach(format => {
                                const sizeText = format.size ? ` (${formatFileSize(format.size)})` : '';
                                const div = document.createElement('div');
                                div.className = 'format-item';
                                div.innerHTML = `
                                    <div>
                                        <strong>${format.note}</strong>
                                        <div style="font-size: 12px; color: #666;">${format.ext.toUpperCase()}${sizeText}</div>
                                    </div>
                                    <button class="download-btn" onclick="event.stopPropagation(); downloadVideo('${format.url.replace(/'/g, "\\'")}', '${format.note.replace(/'/g, "\\'")}')">Download</button>
                                `;
                                div.onclick = () => downloadVideo(format.url, format.note);
                                formatList.appendChild(div);
                            });
                        } else {
                            formatList.innerHTML = '<p>No formats available</p>';
                        }
                        
                        infoDiv.style.display = 'block';
                    }
                    
                    function downloadVideo(url, note) {
                        if (!url) {
                            showStatus('No download URL available', 'error');
                            return;
                        }
                        
                        showStatus('Starting download: ' + note, 'info');
                        
                        // Try native Android download first
                        if (window.Android && window.Android.downloadVideo) {
                            window.Android.downloadVideo(url);
                        } else {
                            // Fallback: open in new tab
                            window.open(url, '_blank');
                            showStatus('Opened in browser', 'warning');
                        }
                    }
                    
                    // Event listeners
                    document.getElementById('extractBtn').onclick = extractVideo;
                    document.getElementById('videoUrl').addEventListener('keypress', function(e) {
                        if (e.key === 'Enter') extractVideo();
                    });
                    
                    // Auto-detect clipboard
                    setTimeout(async () => {
                        try {
                            const text = await navigator.clipboard.readText();
                            if (text && (text.includes('youtube.com') || text.includes('youtu.be') || 
                                        text.includes('facebook.com') || text.includes('instagram.com') ||
                                        text.includes('tiktok.com') || text.includes('twitter.com'))) {
                           const input = document.getElementById('videoUrl');
                                if (!input.value) {
                                    input.value = text;
                                    showStatus('📋 URL detected from clipboard! Click Extract Video', 'info');
                                }
                            }
                        } catch (err) {
                            // Clipboard permission denied
                            console.log('Clipboard access denied');
                        }
                    }, 1000);
                    
                    // Check for URL from intent (passed via Android)
                    if (window.Android && window.Android.getIntentUrl) {
                        const intentUrl = window.Android.getIntentUrl();
                        if (intentUrl) {
                            document.getElementById('videoUrl').value = intentUrl;
                            setTimeout(() => extractVideo(), 500);
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        
        webView.loadDataWithBaseURL("https://y2mate.com/", htmlContent, "text/html", "UTF-8", null)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
                    loadVideoDownloader()
                } else {
                    Toast.makeText(this, "Storage permission needed to download videos", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}             
 
