package com.example.hybridandroid

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/**
 * MainActivity - WebView 宿主
 * 
 * 开发模式: 加载 http://10.0.2.2:3000 (Android 模拟器访问本机的地址)
 * 生产模式: 加载 file:///android_asset/index.html
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var jsBridge: JsBridge

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        
        // 初始化 JsBridge
        jsBridge = JsBridge(this, webView)
        
        // 配置 WebView
        setupWebView()
        
        // 加载页面
        loadPage()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            // 启用 JavaScript
            javaScriptEnabled = true
            // 启用 DOM storage
            domStorageEnabled = true
            // 允许文件访问
            allowFileAccess = true
            // 允许内容访问
            allowContentAccess = true
        }
        
        // 设置 WebViewClient
        webView.webViewClient = WebViewClient()
        
        // 设置 WebChromeClient（用于 console.log 等）
        webView.webChromeClient = WebChromeClient()
        
        // 添加 JavaScript 接口
        webView.addJavascriptInterface(jsBridge, "AndroidBridge")
        
        // 启用调试（chrome://inspect）
        WebView.setWebContentsDebuggingEnabled(true)
    }

    private fun loadPage() {
        if (BuildConfig.IS_DEV_MODE) {
            // 开发模式：加载开发服务器
            webView.loadUrl(BuildConfig.DEV_URL)
        } else {
            // 生产模式：加载本地 assets
            webView.loadUrl("file:///android_asset/index.html")
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
