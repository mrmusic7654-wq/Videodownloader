package com.example.videodownloader

import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            webView = WebView(this)
            setContentView(webView)
            
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            
            // Add JavaScript interface with proper import
            webView.addJavascriptInterface(object {
                @JavascriptInterface
                fun loadUrl(url: String) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Loading: $url", Toast.LENGTH_SHORT).show()
                        webView.loadUrl(url)
                    }
                }
            }, "Android")
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    Toast.makeText(this@MainActivity, "Page loaded", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Load HTML from assets
            webView.loadUrl("file:///android_asset/index.html")
            Toast.makeText(this, "App started!", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            val errorText = android.widget.TextView(this)
            errorText.text = "Error: ${e.message}"
            errorText.setTextColor(android.graphics.Color.RED)
            errorText.setPadding(32, 32, 32, 32)
            setContentView(errorText)
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
