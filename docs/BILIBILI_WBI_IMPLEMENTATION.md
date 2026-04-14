# Bilibili WBI签名实现文档

## 概述

本项目已成功实现Bilibili WBI签名功能，用于解决Bilibili视频投屏时的959错误问题。

## 实现的文件

### 1. BilibiliWbiSigner.kt
**路径**: `app/src/main/java/com/example/floatingscreencasting/dlna/BilibiliWbiSigner.kt`

这是WBI签名的核心实现类，包含以下功能：

- **密钥获取**: 从Bilibili nav API获取实时的img_key和sub_key
- **密钥缓存**: 密钥缓存1小时，避免频繁请求
- **签名计算**: 使用64位混淆表(MIXIN_KEY_ENC_TAB)生成mixin_key
- **URL签名**: 自动为Bilibili API URL添加w_rid和wts参数

### 2. VideoProxyServer.kt (已更新)
**路径**: `app/src/main/java/com/example/floatingscreencasting/dlna/VideoProxyServer.kt`

更新内容：
- 自动检测Bilibili URL
- 对API请求自动应用WBI签名
- 设置Bilibili专用的请求头
- 预加载WBI密钥

## WBI签名算法

### 算法流程

1. **获取密钥**:
   ```
   GET https://api.bilibili.com/x/web-interface/nav
   ```
   返回:
   ```json
   {
     "data": {
       "wbi_img": {
         "img_url": "https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png",
         "sub_url": "https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f01bf08b70ac45.png"
       }
     }
   }
   ```

2. **提取密钥**:
   - img_key: `7cd084941338484aae1ad9425b84077c`
   - sub_key: `4932caff0ff746eab6f01bf08b70ac45`

3. **生成mixin_key**:
   - 拼接: `img_key + sub_key`
   - 使用混淆表重排字符顺序
   - 截取前32位

4. **计算签名**:
   - 对请求参数按键名排序
   - 添加当前时间戳wts
   - 拼接mixin_key后计算MD5
   - 得到w_rid

5. **添加签名参数**:
   - 将w_rid和wts添加到请求URL

### 混淆表 (MIXIN_KEY_ENC_TAB)

```kotlin
intArrayOf(
    46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
    33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
    61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
    36, 20, 34, 44, 52
)
```

## 使用方法

### 自动签名

VideoProxyServer会自动检测Bilibili URL并应用签名：

```kotlin
// 在VideoPresentation中
val finalUrl = proxyServer?.toProxyUrl(uri) ?: uri
// 如果uri包含"bilibili.com"，会自动使用WBI签名
```

### 手动签名

如果需要手动签名URL：

```kotlin
lifecycleScope.launch {
    val result = BilibiliWbiSigner.signUrl(originalUrl)
    if (result.isSuccess) {
        val signedUrl = result.getOrNull()
        // 使用signedUrl
    }
}
```

### 预加载密钥

在应用启动时预加载WBI密钥：

```kotlin
lifecycleScope.launch {
    val result = BilibiliWbiSigner.preloadKeys()
    if (result.isSuccess) {
        Log.d(TAG, "WBI密钥预加载成功")
    }
}
```

## 依赖项

已添加到 `app/build.gradle.kts`:

```kotlin
plugins {
    kotlin("plugin.serialization") version "2.0.21"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
```

## 日志输出

WBI签名相关的日志标签：

- `BilibiliWbiSigner`: WBI签名核心逻辑
- `VideoProxyServer`: 代理服务器和签名应用

关键日志：
```
BilibiliWbiSigner: 从Bilibili API获取WBI密钥
BilibiliWbiSigner: 成功获取WBI密钥: imgKey=xxx, subKey=xxx
VideoProxyServer: 检测到Bilibili URL，使用WBI签名处理
VideoProxyServer: 检测到Bilibili API请求，应用WBI签名
BilibiliWbiSigner: 签名成功
```

## 限制和注意事项

### 1. 密钥有效期
- WBI密钥每日更新
- 当前缓存1小时，需要定期刷新

### 2. 算法变化
- Bilibili可能会更新签名算法
- 需要持续跟踪bilibili-API-collect项目

### 3. 法律风险
- 逆向工程可能违反服务条款
- 建议仅用于个人学习和研究

### 4. 性能影响
- 首次签名需要网络请求获取密钥
- 建议在应用启动时预加载

## 测试

### 验证WBI签名是否正常工作

1. 查看日志确认密钥获取成功
2. 尝试投屏Bilibili视频
3. 检查是否还有959错误

### 测试命令

```bash
# 编译并安装
./gradlew installDebug

# 查看日志
adb logcat | grep -E "BilibiliWbiSigner|VideoProxyServer"
```

## 参考文档

- [WBI签名文档](https://github.com/pskdje/bilibili-API-collect/blob/main/docs/misc/sign/wbi.md)
- [bilibili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect)

## 更新记录

### 2026-04-14
- 初始实现WBI签名功能
- 添加密钥缓存机制
- 集成到VideoProxyServer
- 添加预加载功能

## 故障排除

### 问题1: WBI密钥获取失败
**解决方案**:
- 检查网络连接
- 确认API URL是否正确
- 查看日志中的错误信息

### 问题2: 签名后仍然959错误
**可能原因**:
- 算法已更新
- 请求参数不完整
- 用户Cookie缺失

**解决方案**:
- 检查bilibili-API-collect最新文档
- 对比官方APP的请求

### 问题3: 视频播放失败
**可能原因**:
- 视频URL过期
- 需要额外的鉴权（如VIP）

**解决方案**:
- 尝试其他视频
- 检查视频权限设置
