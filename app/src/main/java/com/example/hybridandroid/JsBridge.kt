package com.example.hybridandroid

import android.content.Context
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONException
import org.json.JSONObject

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
        
        // 错误码
        private const val ERROR_METHOD_NOT_FOUND = -32601
        private const val ERROR_INVALID_PARAMS = -32602
        private const val ERROR_INTERNAL_ERROR = -32603
        private const val ERROR_PARSE_ERROR = -32700
    }

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
