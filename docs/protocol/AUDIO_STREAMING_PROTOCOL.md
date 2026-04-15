# FSCast Audio Streaming Protocol

FSCast PCM 音频流传输通信协议，用于车机端与手机端（Android/鸿蒙）之间的实时音频传输和播放控制。

**版本**：1.0  
**端口**：UDP 19876（发现）、TCP 19880（音频流 + 控制）  
**编码**：PCM 16-bit raw audio  
**最大同时连接设备**：5 个

---

## 1. 设备发现（UDP）

### 端口：19876

### 手机 → 车机：发现请求

```
广播 "FSCAST_DISCOVER" 到 255.255.255.255:19876
```

### 车机 → 手机：发现响应

```json
{
  "type": "discover_response",
  "name": "FSCast",
  "ip": "192.168.1.100",
  "port": 19880,
  "version": 1
}
```

---

## 2. 连接建立（TCP）

### 手机 → 车机：TCP 连接

手机连接到 `车机IP:19880`。

### 车机 → 手机：握手

连接建立后，车机主动发送握手消息：

```json
{ "type": "hello", "version": 1 }
```

### 手机 → 车机：握手确认

```json
{
  "type": "hello_ack",
  "device_name": "华为 Mate60",
  "platform": "harmony",
  "version": 1
}
```

`platform` 取值：`"android"` 或 `"harmony"`

---

## 3. 帧格式（TCP 数据流）

所有二进制帧以 **1 字节帧类型** 开头，后跟帧数据。

| 帧类型 | 值 | 方向 | 说明 |
|--------|-----|------|------|
| 心跳 | `0x00` | 双向 | 每 3 秒发送一次 |
| 格式头 | `0x01` | 车机→手机 | 音频格式（连接后发送一次） |
| PCM 数据 | `0x02` | 车机→手机 | PCM 音频帧 |
| 格式变化 | `0x03` | 车机→手机 | 音频格式变化（换视频源） |
| 状态同步 | `0x10` | 车机→手机 | JSON 播放状态（每秒） |
| 命令结果 | `0x11` | 车机→手机 | JSON 命令执行结果 |
| 输出变化 | `0x12` | 车机→手机 | JSON 音频输出切换通知 |
| 控制命令 | `0x20` | 手机→车机 | JSON 控制命令 |

### 3.1 心跳帧 `0x00`

```
[0x00]
```

无附加数据。收到后应回复心跳帧。5 秒无心跳则判定连接断开。

### 3.2 格式头帧 `0x01`

```
[0x01] [4B 采样率] [4B 声道数] [4B 编码格式]
```

| 字段 | 大小 | 说明 |
|------|------|------|
| 采样率 | 4 bytes (int32 BE) | 如 44100, 48000 |
| 声道数 | 4 bytes (int32 BE) | 1=单声道, 2=立体声 |
| 编码格式 | 4 bytes (int32 BE) | 2=PCM_16BIT |

连接建立后，音频格式头发送一次。

### 3.3 PCM 数据帧 `0x02`

```
[0x02] [4B 数据长度 N] [N 字节 PCM 数据]
```

| 字段 | 大小 | 说明 |
|------|------|------|
| 数据长度 | 4 bytes (int32 BE) | PCM 数据字节数 |
| PCM 数据 | N bytes | 原始 PCM 16-bit 交错数据 |

注意：PCM 数据只发送给**当前选中的设备**，未选中的设备不接收此帧。

### 3.4 格式变化帧 `0x03`

```
[0x03] [4B 采样率] [4B 声道数] [4B 编码格式]
```

与 `0x01` 格式相同。当视频源切换导致音频格式变化时发送。手机端收到后应重建 AudioTrack/AudioRenderer。

### 3.5 状态同步帧 `0x10`

```
[0x10] [4B JSON长度] [N 字节 JSON]
```

发送给**所有已连接设备**（每秒一次）：

```json
{
  "type": "state_update",
  "is_playing": true,
  "position_ms": 12345,
  "duration_ms": 300000,
  "title": "哔哩哔哩",
  "buffered_position_ms": 13000,
  "audio_output": "phone",
  "selected_device": "192.168.1.105:54321"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| is_playing | bool | 是否正在播放 |
| position_ms | long | 当前播放位置（毫秒） |
| duration_ms | long | 总时长（毫秒），-1 表示未知 |
| title | string | 当前播放标题 |
| buffered_position_ms | long | 已缓冲位置（毫秒） |
| audio_output | string | "phone" 或 "speaker" |
| selected_device | string | 当前接收音频的设备 ID，null 表示扬声器 |

### 3.6 命令结果帧 `0x11`

```
[0x11] [4B JSON长度] [N 字节 JSON]
```

```json
{ "type": "command_result", "action": "play", "success": true }
{ "type": "command_result", "action": "seek", "success": true, "position_ms": 60000 }
{ "type": "command_result", "action": "set_audio_output", "success": true, "output": "phone" }
{ "type": "command_result", "action": "stop", "success": false, "error": "not_playing" }
```

### 3.7 输出变化通知帧 `0x12`

```
[0x12] [4B JSON长度] [N 字节 JSON]
```

车机主动推送，通知手机其音频输出状态发生变化：

```json
{ "type": "audio_output_changed", "output": "phone" }    // 你被选中了
{ "type": "audio_output_changed", "output": "speaker" }  // 你被取消了
```

**手机端处理**：
- 收到 `output: "phone"` → 开始 AudioTrack/AudioRenderer 播放
- 收到 `output: "speaker"` → 暂停 AudioTrack/AudioRenderer

### 3.8 控制命令帧 `0x20`

```
[0x20] [4B JSON长度] [N 字节 JSON]
```

所有已连接设备均可发送控制命令：

#### 播放
```json
{ "type": "command", "action": "play" }
```

#### 暂停
```json
{ "type": "command", "action": "pause" }
```

#### 停止（退出投屏）
```json
{ "type": "command", "action": "stop" }
```

#### 拖动进度
```json
{ "type": "command", "action": "seek", "position_ms": 60000 }
```

#### 请求切换到手机播放
```json
{ "type": "command", "action": "set_audio_output", "output": "phone" }
```
车机收到后自动将该设备设为音频输出目标。

#### 请求切换到车机扬声器
```json
{ "type": "command", "action": "set_audio_output", "output": "speaker" }
```

#### 请求当前状态
```json
{ "type": "command", "action": "get_state" }
```
车机立即回复一条状态同步帧 `0x10`。

#### 断开连接
```json
{ "type": "command", "action": "disconnect" }
```

---

## 4. 多设备支持

### 设备标识

每个连接设备的唯一 ID 格式：`IP:PORT`（如 `192.168.1.105:54321`）

### 行为规则

| 规则 | 说明 |
|------|------|
| PCM 数据 | 仅发送给当前选中的设备 |
| 状态同步 | 发送给所有已连接设备 |
| 控制命令 | 所有设备均可发送，车机统一处理 |
| 输出变化通知 | 仅发送给受影响的设备（被选中/被取消） |
| 命令结果 | 仅回复给发送命令的设备 |

### 设备切换流程

```
1. 用户在车机端选择新设备（或手机端请求 set_audio_output: phone）
2. 车机发送 0x12 给旧设备：{ "output": "speaker" }
3. 车机发送 0x12 给新设备：{ "output": "phone" }
4. 车机发送 0x01 格式头给新设备（如果是首次选中）
5. PCM 数据流切换到新设备的 Socket
6. 切换延迟 < 100ms
```

### 设备断开处理

```
1. 心跳超时（5秒无响应）或 Socket 异常
2. 从设备列表移除
3. 如果是当前选中设备：
   a. 自动回退到车机扬声器
   b. 停止 PCM 拦截
   c. VideoPresentation.setMuted(false)
4. 通知 UI 刷新设备列表
```

---

## 5. 错误处理

| 错误 | 处理 |
|------|------|
| 连接断开 | 自动重连（手机端指数退避，最大 30 秒） |
| 心跳超时 | 关闭连接，触发断开处理 |
| 格式不识别 | 断开连接，记录日志 |
| PCM 溢出 | PcmRingBuffer 丢弃最旧数据 |
| TCP 背压 | TCP 内置流控，自动降速 |

---

## 6. 常量定义

| 常量 | 值 | 说明 |
|------|-----|------|
| UDP_PORT | 19876 | 设备发现端口 |
| TCP_PORT | 19880 | 音频流 + 控制端口 |
| PROTOCOL_VERSION | 1 | 协议版本 |
| MAX_CLIENTS | 5 | 最大同时连接设备数 |
| HEARTBEAT_INTERVAL_MS | 3000 | 心跳间隔 |
| HEARTBEAT_TIMEOUT_MS | 5000 | 心跳超时 |
| STATE_SYNC_INTERVAL_MS | 1000 | 状态同步间隔 |
| RING_BUFFER_SIZE | 176400 | PCM 环形缓冲区大小（~500ms @ 44.1kHz stereo 16bit） |
| MAGIC_NUMBER | 0x46534341 | "FSCA" ASCII |
