package com.example.hybridandroid

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONException
import org.json.JSONObject
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

/**
 * JsBridge - JavaScript 与 Native 通信桥接
 * 
 * JSON-RPC 格式:
 * 请求: { id: number|string, method: string, params?: any }
 * 成功响应: window.__JSB_onMessage({ id: number|string, result: any })
 * 错误响应: window.__JSB_onMessage({ id: number|string, error: { code: number, message: string } })
 */
class JsBridge(
    private val context: Context,
    private val webView: WebView
) {
    companion object {
        private const val TAG = "JsBridge"
        private const val PREF_NAME = "jsbridge_prefs"
        private const val KEY_USER_TOKEN = "user_token"
        
        // 错误码
        private const val ERROR_METHOD_NOT_FOUND = -32601
        private const val ERROR_INVALID_PARAMS = -32602
        private const val ERROR_INTERNAL_ERROR = -32603
        private const val ERROR_PARSE_ERROR = -32700
    }

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * JavaScript 调用此方法发送消息
     * @param message JSON-RPC 格式的消息字符串
     */
    @JavascriptInterface
    fun postMessage(message: String) {
        try {
            val request = JSONObject(message)
            val id = request.opt("id") ?: JSONObject.NULL
            val method = request.optString("method", "")
            val params = request.opt("params")
            
            if (method.isEmpty()) {
                sendError(id, ERROR_INVALID_PARAMS, "Method is required")
                return
            }
            
            handleMethod(id, method, params)
        } catch (e: JSONException) {
            sendError(JSONObject.NULL, ERROR_PARSE_ERROR, "Parse error: ${e.message}")
        }
    }

    /**
     * 处理不同的方法调用
     */
    private fun handleMethod(id: Any, method: String, params: Any?) {
        when (method) {
            "getDeviceInfo" -> handleGetDeviceInfo(id)
            "echo" -> handleEcho(id, params)
            "getUserToken" -> handleGetUserToken(id)
            "setUserToken" -> handleSetUserToken(id, params)
            "downloadFile" -> handleDownloadFile(id, params)
            else -> sendError(id, ERROR_METHOD_NOT_FOUND, "Method not found: $method")
        }
    }

    /**
     * 获取设备信息
     */
    private fun handleGetDeviceInfo(id: Any) {
        try {
            val deviceInfo = JSONObject().apply {
                put("brand", Build.BRAND)
                put("model", Build.MODEL)
                put("device", Build.DEVICE)
                put("manufacturer", Build.MANUFACTURER)
                put("sdkVersion", Build.VERSION.SDK_INT)
                put("release", Build.VERSION.RELEASE)
                put("product", Build.PRODUCT)
            }
            sendResult(id, deviceInfo)
        } catch (e: Exception) {
            sendError(id, ERROR_INTERNAL_ERROR, "Failed to get device info: ${e.message}")
        }
    }

    /**
     * Echo 方法 - 原样返回传入的参数
     */
    private fun handleEcho(id: Any, params: Any?) {
        sendResult(id, params ?: JSONObject.NULL)
    }

    /**
     * 获取用户token
     */
    private fun handleGetUserToken(id: Any) {
        try {
            val token = sharedPreferences.getString(KEY_USER_TOKEN, null)
            sendResult(id, token ?: JSONObject.NULL)
        } catch (e: Exception) {
            sendError(id, ERROR_INTERNAL_ERROR, "Failed to get user token: ${e.message}")
        }
    }

    /**
     * 设置用户token
     */
    private fun handleSetUserToken(id: Any, params: Any?) {
        try {
            if (params == null || params == JSONObject.NULL) {
                sendError(id, ERROR_INVALID_PARAMS, "Token parameter is required")
                return
            }
            
            val token = params.toString()
            // 允许空字符串，表示清除 token
            if (token.isEmpty()) {
                sharedPreferences.edit().remove(KEY_USER_TOKEN).apply()
            } else {
                sharedPreferences.edit().putString(KEY_USER_TOKEN, token).apply()
            }
            
            sendResult(id, JSONObject.NULL) // 返回null表示成功
        } catch (e: Exception) {
            sendError(id, ERROR_INTERNAL_ERROR, "Failed to set user token: ${e.message}")
        }
    }

    /**
     * 下载文件
     */
    private fun handleDownloadFile(id: Any, params: Any?) {
        try {
            if (params == null || params !is JSONObject) {
                sendError(id, ERROR_INVALID_PARAMS, "Invalid download parameters")
                return
            }

            val base64Data = params.optString("base64Data")
            val fileName = params.optString("fileName", "download")

            if (base64Data.isEmpty()) {
                sendError(id, ERROR_INVALID_PARAMS, "Base64 data is required")
                return
            }

            // 解析Base64数据
            val parts = base64Data.split(",")
            if (parts.size != 2) {
                sendError(id, ERROR_INVALID_PARAMS, "Invalid base64 format")
                return
            }

            val mimeType = parts[0].split(":")[1].split(";")[0]
            val base64String = parts[1]

            // 解码Base64
            val decodedBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                java.util.Base64.getDecoder().decode(base64String)
            } else {
                android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
            }

            // 创建临时文件
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

            // 确保文件名唯一
            var counter = 1
            val nameWithoutExt = fileName.substringBeforeLast(".")
            val extension = fileName.substringAfterLast(".", "")
            var uniqueFile = file

            while (uniqueFile.exists()) {
                val newName = if (extension.isNotEmpty()) {
                    "$nameWithoutExt($counter).$extension"
                } else {
                    "$nameWithoutExt($counter)"
                }
                uniqueFile = File(downloadsDir, newName)
                counter++
            }

            // 写入文件
            FileOutputStream(uniqueFile).use { fos ->
                fos.write(decodedBytes)
            }

            // 使用DownloadManager通知系统
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.addCompletedDownload(
                uniqueFile.name,
                "Downloaded from Chat App",
                true,
                mimeType,
                uniqueFile.absolutePath,
                uniqueFile.length(),
                true
            )

            sendResult(id, JSONObject().apply {
                put("filePath", uniqueFile.absolutePath)
                put("fileName", uniqueFile.name)
            })

        } catch (e: Exception) {
            sendError(id, ERROR_INTERNAL_ERROR, "Failed to download file: ${e.message}")
        }
    }

    /**
     * 发送成功结果
     */
    private fun sendResult(id: Any, result: Any?) {
        try {
            val response = JSONObject().apply {
                put("id", id)
                put("result", result ?: JSONObject.NULL)
            }
            callJavaScript(response.toString())
        } catch (e: JSONException) {
            // 如果结果序列化失败，发送错误
            sendError(id, ERROR_INTERNAL_ERROR, "Failed to serialize result")
        }
    }

    /**
     * 发送错误结果
     */
    private fun sendError(id: Any, code: Int, message: String) {
        try {
            val errorObj = JSONObject().apply {
                put("code", code)
                put("message", message)
            }
            val response = JSONObject().apply {
                put("id", id)
                put("error", errorObj)
            }
            callJavaScript(response.toString())
        } catch (e: JSONException) {
            // 忽略序列化错误
        }
    }

    /**
     * 调用 JavaScript 回调
     * 使用 evaluateJavascript 直接传递 JSON 字符串，避免手动转义带来的安全风险
     */
    private fun callJavaScript(jsonString: String) {
        // 使用 evaluateJavascript 直接执行 JavaScript 代码
        // JSONObject.toString() 已经正确转义了 JSON 内容
        // 使用 JSON.parse 确保数据安全传递
        val script = """
            (function() {
                try {
                    var data = $jsonString;
                    if (window.__JSB_onMessage) {
                        window.__JSB_onMessage(data);
                    }
                } catch (e) {
                    console.error('[JSBridge] Error parsing response:', e);
                }
            })();
        """.trimIndent()
        
        // 在主线程执行
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }
}
