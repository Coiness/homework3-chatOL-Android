# Hybrid Android

一个基于 WebView 的 Android Hybrid 应用框架，支持 JavaScript 与 Native 的双向通信。

## 📋 目录

- [功能特性](#功能特性)
- [项目结构](#项目结构)
- [环境配置](#环境配置)
- [快速开始](#快速开始)
- [JSBridge API](#jsbridge-api)
- [开发模式与生产模式](#开发模式与生产模式)
- [调试指南](#调试指南)
- [构建发布](#构建发布)
- [安全注意事项](#安全注意事项)

## 🚀 功能特性

- **WebView 宿主**: 使用 Android WebView 作为 Web 内容的容器
- **JSBridge 通信**: 基于 JSON-RPC 格式的 JavaScript 与 Native 双向通信
- **开发/生产模式**: 支持开发模式（加载本地开发服务器）和生产模式（加载本地 assets）
- **内置方法**: 提供 `getDeviceInfo` 和 `echo` 等常用方法
- **调试支持**: 支持 Chrome DevTools 远程调试

## 📁 项目结构

```
hybrid-android/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/hybridandroid/
│   │   │   ├── MainActivity.kt      # 主 Activity，WebView 宿主
│   │   │   └── JsBridge.kt          # JSBridge 实现
│   │   ├── assets/
│   │   │   ├── index.html           # 演示页面
│   │   │   └── js/
│   │   │       └── jsb.js           # JavaScript 端 JSBridge 库
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml
│   │   │   └── values/
│   │   │       ├── strings.xml
│   │   │       ├── colors.xml
│   │   │       └── themes.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── scripts/
│   └── copy-web.js                  # 复制 Web 构建文件脚本
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 🔧 环境配置

### 系统要求

- **Android Studio**: Arctic Fox (2020.3.1) 或更高版本
- **JDK**: 11 或更高版本
- **Android SDK**: API 24 (Android 7.0) 或更高版本
- **Gradle**: 8.2 或更高版本
- **Node.js**: 14.0+ (用于运行 copy-web.js 脚本)

### 安装步骤

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd hybrid-android
   ```

2. **打开 Android Studio**
   - 启动 Android Studio
   - 选择 "Open an Existing Project"
   - 导航到项目目录并打开

3. **同步 Gradle**
   - Android Studio 会自动检测 Gradle 配置
   - 点击 "Sync Now" 同步依赖

4. **配置模拟器或连接设备**
   - 创建 AVD (Android Virtual Device) 或连接物理设备
   - 确保 USB 调试已启用（物理设备）

## 🏃 快速开始

### 运行应用

1. 在 Android Studio 中选择目标设备
2. 点击运行按钮 (▶) 或按 `Shift + F10`
3. 应用将自动安装并启动

### 切换开发/生产模式

- **开发模式 (Debug Build)**: 加载 `http://10.0.2.2:3000`
- **生产模式 (Release Build)**: 加载 `file:///android_asset/index.html`

在 Android Studio 中通过 Build Variants 面板切换 debug/release 构建类型。

## 📡 JSBridge API

### 通信协议

JSBridge 使用 JSON-RPC 格式进行通信：

**请求格式:**
```json
{
  "id": 1,
  "method": "getDeviceInfo",
  "params": {}
}
```

**成功响应:**
```json
{
  "id": 1,
  "result": { "brand": "Google", "model": "Pixel" }
}
```

**错误响应:**
```json
{
  "id": 1,
  "error": { "code": -32601, "message": "Method not found" }
}
```

### JavaScript 端使用

引入 `js/jsb.js` 后，使用 `JSBridge.call()` 方法：

```javascript
// 获取设备信息
const deviceInfo = await JSBridge.call('getDeviceInfo');
console.log(deviceInfo);
// { brand: "Google", model: "Pixel", ... }

// Echo 测试
const result = await JSBridge.call('echo', { message: 'Hello' });
console.log(result);
// { message: "Hello" }

// 便捷方法
const info = await JSBridge.getDeviceInfo();
const echoResult = await JSBridge.echo({ data: 'test' });
```

### 内置方法

| 方法名 | 参数 | 返回值 | 描述 |
|--------|------|--------|------|
| `getDeviceInfo` | 无 | `DeviceInfo` 对象 | 获取设备信息 |
| `echo` | 任意对象 | 原样返回输入 | 用于测试通信 |

**DeviceInfo 对象:**
```typescript
interface DeviceInfo {
  brand: string;       // 品牌
  model: string;       // 型号
  device: string;      // 设备名
  manufacturer: string; // 制造商
  sdkVersion: number;  // SDK 版本
  release: string;     // Android 版本
  product: string;     // 产品名
}
```

### 扩展 JSBridge

在 `JsBridge.kt` 中添加新方法：

```kotlin
private fun handleMethod(id: Any, method: String, params: Any?) {
    when (method) {
        "getDeviceInfo" -> handleGetDeviceInfo(id)
        "echo" -> handleEcho(id, params)
        // 添加新方法
        "myMethod" -> handleMyMethod(id, params)
        else -> sendError(id, ERROR_METHOD_NOT_FOUND, "Method not found: $method")
    }
}

private fun handleMyMethod(id: Any, params: Any?) {
    // 实现自定义逻辑
    val result = JSONObject().apply {
        put("status", "success")
    }
    sendResult(id, result)
}
```

## 🔄 开发模式与生产模式

### 开发模式

开发模式下，WebView 加载本地开发服务器 (`http://10.0.2.2:3000`)。

**配置步骤:**

1. 启动 Web 开发服务器（例如 React、Vue 开发服务器）
   ```bash
   cd your-web-project
   npm run dev  # 通常监听 localhost:3000
   ```

2. 在 Android Studio 中选择 "debug" 构建变体

3. 运行应用，WebView 将加载开发服务器

**注意**: `10.0.2.2` 是 Android 模拟器访问主机 localhost 的特殊 IP 地址。

### 生产模式

生产模式下，WebView 加载打包到 assets 中的 Web 文件。

**配置步骤:**

1. 构建 Web 项目
   ```bash
   cd your-web-project
   npm run build
   ```

2. 使用脚本复制构建文件
   ```bash
   node scripts/copy-web.js ../your-web-project/dist
   ```

3. 在 Android Studio 中选择 "release" 构建变体

4. 构建并运行应用

## 🐛 调试指南

### Chrome DevTools 远程调试

1. **启用调试**
   - 代码中已启用: `WebView.setWebContentsDebuggingEnabled(true)`

2. **连接设备**
   - 使用 USB 连接 Android 设备或启动模拟器
   - 确保设备已被 adb 识别: `adb devices`

3. **打开 Chrome DevTools**
   - 在电脑上打开 Chrome 浏览器
   - 访问 `chrome://inspect`
   - 在 "Remote Target" 下找到你的 WebView
   - 点击 "inspect" 打开 DevTools

4. **调试功能**
   - Elements: 查看和修改 DOM
   - Console: 查看日志和执行 JavaScript
   - Network: 监控网络请求
   - Sources: 设置断点调试

### 常见问题排查

**Q: WebView 显示空白页面**
- 检查网络权限是否已添加
- 检查开发服务器是否正在运行
- 使用 Chrome DevTools 查看控制台错误

**Q: JSBridge 调用无响应**
- 确认 `jsb.js` 已正确加载
- 检查 `AndroidBridge` 是否可用: `console.log(typeof AndroidBridge)`
- 查看 Android Studio Logcat 中的错误

**Q: 开发模式连接失败**
- 确认开发服务器监听正确端口
- 检查 `android:usesCleartextTraffic="true"` 是否已设置
- 尝试在模拟器浏览器中直接访问 `http://10.0.2.2:3000`

## 📦 构建发布

### Debug 构建

```bash
./gradlew assembleDebug
```

输出: `app/build/outputs/apk/debug/app-debug.apk`

### Release 构建

1. 配置签名（在 `app/build.gradle.kts` 中）
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file("your-keystore.jks")
           storePassword = "your-password"
           keyAlias = "your-alias"
           keyPassword = "your-key-password"
       }
   }
   ```

2. 构建
   ```bash
   ./gradlew assembleRelease
   ```

输出: `app/build/outputs/apk/release/app-release.apk`

### 使用 copy-web.js 脚本

```bash
# 复制 Web 构建文件到 assets
node scripts/copy-web.js <web-build-dir>

# 示例
node scripts/copy-web.js ../my-web-app/dist
node scripts/copy-web.js /path/to/web/build
```

## ⚠️ 安全注意事项

### 1. JavaScript 接口安全

- `@JavascriptInterface` 注解的方法会暴露给 JavaScript
- **只暴露必要的方法**，避免敏感操作
- 对所有输入进行验证和清理
- 考虑添加来源验证

```kotlin
@JavascriptInterface
fun postMessage(message: String) {
    // 验证消息格式
    // 限制可调用的方法
    // 不要执行敏感操作
}
```

### 2. 网络安全

- 生产环境**禁用** `usesCleartextTraffic`
- 使用 HTTPS 加载远程内容
- 配置网络安全策略 (`network_security_config.xml`)

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">your-domain.com</domain>
    </domain-config>
</network-security-config>
```

### 3. WebView 安全配置

```kotlin
webView.settings.apply {
    // 禁用不必要的功能
    allowFileAccessFromFileURLs = false
    allowUniversalAccessFromFileURLs = false
    
    // 生产环境禁用调试
    if (!BuildConfig.DEBUG) {
        WebView.setWebContentsDebuggingEnabled(false)
    }
}
```

### 4. 内容安全

- 仅加载可信来源的内容
- 考虑实现 Content Security Policy (CSP)
- 避免加载用户提供的 URL

### 5. 生产检查清单

- [ ] 禁用 WebView 调试
- [ ] 禁用明文流量
- [ ] 验证所有 JavaScript 接口输入
- [ ] 使用 ProGuard 混淆代码
- [ ] 移除敏感日志输出
- [ ] 配置网络安全策略
- [ ] 签名 APK

## 📄 许可证

MIT License

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

如有问题或建议，请提交 Issue。
