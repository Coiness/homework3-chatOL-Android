package com.example.hybridandroid

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import android.webkit.ValueCallback
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ActivityNotFoundException

/**
 * MainActivity - WebView 宿主
 * 
 * 开发模式: 加载 http://10.0.2.2:3000 (Android 模拟器访问本机的地址)
 * 生产模式: 加载 file:///android_asset/index.html
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var jsBridge: JsBridge
    
    // 用于处理文件上传的回调
    private var uploadMessage: ValueCallback<Array<Uri>>? = null

    // 注册文件选择器
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadMessage?.onReceiveValue(arrayOf(uri))
        } else {
            uploadMessage?.onReceiveValue(null)
        }
        uploadMessage = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        
        // 初始化 JsBridge
        jsBridge = JsBridge(this, webView)
        
        // 配置 WebView
        setupWebView()
        
        // 设置返回键处理
        setupBackPressedHandler()
        
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
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // 取消之前的回调
                if (uploadMessage != null) {
                    uploadMessage?.onReceiveValue(null)
                    uploadMessage = null
                }

                uploadMessage = filePathCallback

                try {
                    // 获取网页请求的文件类型
                    var mimeType = "*/*"
                    if (fileChooserParams != null && fileChooserParams.acceptTypes.isNotEmpty()) {
                        val acceptType = fileChooserParams.acceptTypes[0]
                        if (acceptType.isNotEmpty()) {
                            mimeType = acceptType
                        }
                    }
                    
                    // 启动文件选择器
                    fileChooserLauncher.launch(mimeType)
                } catch (e: ActivityNotFoundException) {
                    uploadMessage = null
                    return false
                }
                return true
            }
        }
        
        // 添加 JavaScript 接口
        webView.addJavascriptInterface(jsBridge, "AndroidBridge")
        
        // 仅在调试模式下启用 WebView 调试（chrome://inspect）
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
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

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
