/**
 * JSBridge - JavaScript 与 Android Native 通信库
 * 
 * 使用 JSON-RPC 格式进行通信:
 * 请求格式: { id: number, method: string, params?: any }
 * 成功响应: { id: number, result: any }
 * 错误响应: { id: number, error: { code: number, message: string } }
 */

// 用括号括起来，立即调用。同时，避免污染全局变量。
// 匿名函数，没有名字，把当前的全局对象作为global传给函数，定义完立即执行
(function(global) {
    'use strict';
    
    // 请求 ID 计数器
    var requestId = 0;
    
    // 存储待处理的回调
    var pendingCallbacks = {};
    
    // 默认超时时间 (毫秒)
    var DEFAULT_TIMEOUT = 30000;
    
    /**
     * JSBridge 对象
     */
    var JSBridge = {

        // 核心层，负责设置超时器、把对象转换为JSON字符串、真正调用安卓底层
        /**
         * 调用 Native 方法
         * @param {string} method - 方法名
         * @param {any} params - 参数
         * @param {number} timeout - 超时时间 (毫秒), 默认 30000
         * @returns {Promise} - 返回 Promise
         */
        call: function(method, params, timeout) {
            return new Promise(function(resolve, reject) {
                // 检查 Android 桥接是否可用
                if (typeof AndroidBridge === 'undefined') {
                    reject({
                        code: -1,
                        message: 'AndroidBridge is not available'
                    });
                    return;
                }
                
                // 生成唯一请求 ID
                var id = ++requestId;
                
                // 设置超时
                var timeoutMs = timeout || DEFAULT_TIMEOUT;
                var timeoutId = setTimeout(function() {
                    if (pendingCallbacks[id]) {
                        delete pendingCallbacks[id];
                        reject({
                            code: -2,
                            message: 'Request timeout'
                        });
                    }
                }, timeoutMs);
                
                // 存储回调
                pendingCallbacks[id] = {
                    resolve: resolve,
                    reject: reject,
                    timeoutId: timeoutId
                };
                
                // 构建请求消息
                var message = JSON.stringify({
                    id: id,
                    method: method,
                    params: params
                });
                
                // 发送到 Native
                try {
                    AndroidBridge.postMessage(message);
                } catch (e) {
                    clearTimeout(timeoutId);
                    delete pendingCallbacks[id];
                    reject({
                        code: -3,
                        message: 'Failed to send message: ' + e.message
                    });
                }
            });
        },
        

        // 业务层 封装常用方法
        /**
         * 获取设备信息 (便捷方法)
         * @returns {Promise}
         */
        getDeviceInfo: function() {
            return this.call('getDeviceInfo');
        },
        
        /**
         * Echo 测试 (便捷方法)
         * @param {any} data - 要回显的数据
         * @returns {Promise}
         */
        echo: function(data) {
            return this.call('echo', data);
        },
        
        // 工具层
        /**
         * 检查 JSBridge 是否可用
         * @returns {boolean}
         */
        isAvailable: function() {
            return typeof AndroidBridge !== 'undefined';
        },
        
        /**
         * 获取待处理请求数量
         * @returns {number}
         */
        getPendingCount: function() {
            return Object.keys(pendingCallbacks).length;
        }
    };
    
    /**
     * Native 回调处理函数
     * 由 Android 端调用: window.__JSB_onMessage({ id, result } | { id, error })
     * @param {Object} response - 响应对象
     */
    // Native对Web请求的回复
    global.__JSB_onMessage = function(response) {

        // 回复无效
        if (!response || typeof response.id === 'undefined') {
            console.warn('[JSBridge] Invalid response:', response);
            return;
        }
        
        // 回调无效
        var callback = pendingCallbacks[response.id];
        if (!callback) {
            console.warn('[JSBridge] No callback found for id:', response.id);
            return;
        }
        
        // 清理
        clearTimeout(callback.timeoutId);
        delete pendingCallbacks[response.id];
        
        // 处理响应
        if (response.error) {
            callback.reject(response.error);
        } else {
            callback.resolve(response.result);
        }
    };
    
    // 导出 JSBridge
    global.JSBridge = JSBridge;
    
    // 兼容 CommonJS
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = JSBridge;
    }
    
    console.log('[JSBridge] Initialized, available:', JSBridge.isAvailable());
    
})(typeof window !== 'undefined' ? window : this);

/**
 * 流程
 * 1. Web 发起Request
 * 2. Web 调用 call 方法
 * 3. call方法，生成请求id、设置超时、存储回调、构建请求消息并发送给Native
 * 4. call方法返回的是Promise，所以Web异步执行，干其他事去了
 * 5. Android 收到消息，处理消息，返回结果
 * 6. Android 端调用: window.__JSB_onMessage({ id, result } | { id, error }) 返回消息
 * 7. Web 端的 __JSB_onMessage接受到 response：{id，result} | {id,error}
 * 8. 根据 response 内部的id，查找回调，清理回调超时定时器，根据reslut/error来reslove/reject上面的Promise（call返回的那个）
 * 9. 由于这个是异步，所以web可以await收到消息，拿到数据/报错
 */


