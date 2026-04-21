# PCM音频捕获播放问题排查文档

## 问题描述

**症状**：
- 车机扬声器播放（ExoPlayer直接输出）= 完全正常 ✅
- PCM捕获+AudioTrack播放 = 有问题 ❌
  - **语速慢时**：声音相对清楚
  - **语速快时**：变成"快进/电流音"，听不清晰
  - 音画保持同步

**排除**：ExoPlayer本身没问题，问题在PCM捕获/播放流程

---

## 数据流分析

```
ExoPlayer播放视频
  ↓
MediaCodecAudioRenderer解码
  ↓
StreamingAudioSink.handleBuffer()拦截PCM数据
  ├─ [质疑点1] buffer的position/limit是否正确？
  ├─ [质疑点2] 读取的ByteBuffer是纯PCM还是有元数据？
  ├─ [质疑点3] buffer.remaining()是否等于实际的PCM数据量？
  ↓
复制到PcmRingBuffer（环形缓冲区）
  ├─ 使用buffer.get(tempArray)复制
  ├─ 保存position，读取后恢复
  ↓
本地测试线程从PcmRingBuffer读取
  ├─ 读取28288字节/次
  ├─ 间隔约160ms（匹配期望播放时长）
  ↓
AudioTrack播放
  ├─ 配置：44100Hz, 立体声, PCM_16BIT
  ├─ MODE_STREAM模式
```

---

## 已验证的正确项 ✅

1. **音频格式**：
   - 采样率：44100Hz ✓
   - 声道：2（立体声）✓
   - 编码：PCM_16BIT ✓
   - 字节序：LITTLE_ENDIAN ✓

2. **读取速度控制**：
   - 平均速度：176201B/s
   - 理论速度：176400B/s
   - 误差：仅0.1%（几乎完美）✓

3. **采样值范围**：
   - 观察值：-5310 到 5753
   - 16位范围：-32768 到 32767
   - 在合理范围内 ✓

4. **缓冲区状态**：
   - 使用率：约84%（稳定）✓
   - 无溢出/underrun ✓

---

## 关键日志发现

### 1. handleBuffer调用间隔（不稳定）
```
handleBuffer#4: 实际间隔=7ms, 期望间隔≈23ms, 差异=-16ms
handleBuffer#5: 实际间隔=3ms, 期望间隔≈23ms, 差异=-20ms
handleBuffer#10: 实际间隔=86ms, 期望间隔≈23ms, 差异=63ms
```
**结论**：ExoPlayer不是匀速调用handleBuffer（0ms到87ms波动）

### 2. 本地测试读取间隔（稳定）
```
本地测试#0: 读取=28288B, 间隔=0ms, 期望播放=160ms
本地测试#1: 读取=28288B, 间隔=160ms, 期望播放=160ms
本地测试#2: 读取=28288B, 间隔=160ms, 期望播放=160ms
```
**结论**：读取速度控制完美（在移除Sleep之前）

### 3. PCM采样值（正常）
```
handleBuffer#2: 前8个16位采样值: [0]=-855, [1]=-4582, [2]=-976, [3]=-4503...
统计 - 采样数=2048, 平均=-368, 最大绝对值=5753
```
**结论**：采样值在合理范围内

### 4. ByteBuffer属性
```
remaining=4096, position=0, limit=4096, capacity=1048576
isDirect=true, order=LITTLE_ENDIAN
```
**结论**：ByteBuffer属性正常

---

## 核心怀疑点

### 怀疑1：ByteBuffer可能有元数据/帧头
**现象**：
- 语速慢时清楚（数据量小，元数据影响小）
- 语速快时异常（数据量大，元数据导致错位）

**验证方法**：
- 对比`buffer.remaining()`和`encodedSampleCount`
- 检查ByteBuffer的实际可读数据

### 怀疑2：数据复制时序问题
**当前代码**：
```kotlin
val savedPosition = buffer.position()
buffer.get(tempArray)  // position会移动
buffer.position(savedPosition)  // 恢复position
```

**质疑**：
- 在`buffer.get()`和`super.handleBuffer()`之间，ByteBuffer状态可能被ExoPlayer修改
- 如果在复制期间position变化，可能导致数据不一致

**验证方法**：
- 使用`buffer.duplicate()`而不是`get()`
- 验证复制前后的ByteBuffer状态

### 怀疑3：AudioTrack写入时机
**现象**：
- 有Sleep时：有"快进"效应
- 无Sleep时：变成"电流音"

**分析**：
- Sleep控制读取速度 → 导致AudioTrack缓冲区波动 → 产生杂音
- 无Sleep → 读取太快 → AudioTrack缓冲区溢出 → 电流音

**根本问题**：需要让AudioTrack.write()自己控制速度，但环形缓冲区可能导致数据不连续

### 怀疑4：语速快时的音频特性
**分析**：
- 语速快 = 高频成分多 = 能量集中在高频
- 如果高频处理有问题，会导致失真

**可能原因**：
- 采样精度不足（但已确认16bit正确）
- 数据丢失导致高频信息缺失
- 字节序错误导致高频失真

---

## 测试历史

### Test 1：直接读取播放（无速度控制）
**结果**：声音非常不正常，"发电报干扰声"
**日志**：平均速度180967B/s（略快于理论176400B/s）
**结论**：读取速度不均匀导致缓冲区波动

### Test 2：添加Sleep速度控制
**结果**：声音"像快进倍速播放"
**日志**：间隔160ms完美匹配期望
**结论**：Sleep导致AudioTrack缓冲区问题

### Test 3：移除Sleep
**结果**：电流音，更加不清晰
**结论**：读取太快导致缓冲区问题

### Test 4：用户观察（最重要发现）
**结果**：
- 语速慢时清楚
- 语速快时变"快进"
**结论**：问题与音频内容/数据量相关

---

## 待验证项

1. **ByteBuffer数据完整性**：
   - [ ] encodedSampleCount vs remaining/2 是否匹配？
   - [ ] ByteBuffer是否有隐藏的元数据/帧头？
   - [ ] position/limit是否始终正确？

2. **数据复制方法**：
   - [ ] 使用`duplicate()`代替`get()`
   - [ ] 验证复制后的数据一致性

3. **AudioTrack配置**：
   - [ ] 尝试不同的缓冲区大小
   - [ ] 验证ENCODING_PCM_16BIT是否正确

4. **字节序问题**：
   - [ ] 虽然显示LITTLE_ENDIAN，但实际数据是否需要转换？

---

## 下一步调试计划

### 优先级1：验证ByteBuffer数据完整性
添加日志：
```kotlin
val actualSampleCount = remaining / 2
Log.d(TAG, "encodedSampleCount=$encodedSampleCount, actual=$actualSampleCount, match=${encodedSampleCount == actualSampleCount}")
```

### 优先级2：改用duplicate()复制数据
```kotlin
val duplicate = buffer.duplicate()
val tempArray = ByteArray(remaining)
duplicate.get(tempArray)
```

### 优先级3：分析语速快时的具体差异
捕获语速快和语速慢时的：
- handleBuffer调用频率
- 每次的数据量
- 采样值分布

---

## 创建时间
2025-04-16 16:05

## 最后更新
待定...
