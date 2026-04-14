# 蓝牙音频测试指南

## 测试工具说明

这是一个专门的蓝牙音频测试工具，用于验证车机系统对蓝牙音频输出的支持情况。

## 测试前准备

1. **确保蓝牙耳机已配对**
   - 打开车机蓝牙设置
   - 确认 `HONOR Earbuds X6` 已配对
   - 蓝牙耳机处于开机状态

2. **启动日志监控**
   ```bash
   adb logcat -c
   adb logcat -s BluetoothAudioTest:* *:S
   ```

## 测试步骤

### 步骤1：启动测试应用

在车机上找到并打开 "蓝牙音频测试" 应用

### 步骤2：依次执行测试

#### 测试1：检查蓝牙权限
- 点击 "测试1: 检查蓝牙权限" 按钮
- 检查日志中的权限状态
- **预期结果**：所有蓝牙权限都已授予

#### 测试2：获取已配对设备
- 点击 "测试2: 获取已配对设备" 按钮
- 检查日志中的设备列表
- **预期结果**：能看到 `HONOR Earbuds X6` 设备

#### 测试3：检查A2DP状态
- 点击 "测试3: 检查A2DP状态" 按钮
- 检查日志中的A2DP连接状态
- **预期结果**：设备可能显示为 "未连接" 或 "已连接"

#### 测试4：获取音频设备
- 点击 "测试4: 获取音频设备" 按钮
- 检查日志中的可用音频输出设备
- **预期结果**：应该能看到至少扬声器和一个蓝牙设备（如果已连接）

#### 测试5：激活A2DP连接
- 点击 "测试5: 激活A2DP连接" 按钮
- 检查日志中的连接尝试结果
- **预期结果**：
  - 如果系统允许：显示 "连接调用结果: true"
  - 如果系统限制：显示错误信息（这是我们要验证的）

#### 测试6：播放测试音频（系统默认）
- 点击 "测试6: 播放测试音频（系统默认）" 按钮
- **戴上蓝牙耳机**
- 检查是否听到音频
- **预期结果**：音频从车机扬声器播放（系统默认路由）

#### 测试7：播放测试音频（ExoPlayer路由）
- 点击 "测试7: 播放测试音频（ExoPlayer路由）" 按钮
- **戴上蓝牙耳机**
- 检查是否听到音频
- **预期结果**：
  - 如果API可用：音频从蓝牙耳机播放
  - 如果系统限制：音频仍然从扬声器播放

## 日志分析

### 正常情况的日志示例

```
BluetoothAudioTest: ========== 测试1: 检查蓝牙权限 ==========
BluetoothAudioTest: 权限检查结果:
BluetoothAudioTest:   android.permission.BLUETOOTH: true
BluetoothAudioTest:   android.permission.BLUETOOTH_CONNECT: true
BluetoothAudioTest:   android.permission.MODIFY_AUDIO_ROUTING: false (预期为false)
```

### A2DP连接成功的日志示例

```
BluetoothAudioTest: 尝试激活设备: HONOR Earbuds X6
BluetoothAudioTest:   当前状态: 未连接
BluetoothAudioTest:  尝试连接...
BluetoothAudioTest:  连接调用结果: true
BluetoothAudioTest:  2秒后状态: 已连接
```

### 系统限制的日志示例

```
BluetoothAudioTest:  尝试连接...
BluetoothAudioTest:  连接失败: Bluetooth permission check failed
BluetoothAudioTest:  可能原因: 系统限制或权限不足
```

## 预期测试结果

基于车机系统的特性分析，预期结果可能是：

1. ✅ **权限检查**：所有蓝牙权限都已授予
2. ✅ **设备检测**：能检测到已配对的蓝牙耳机
3. ⚠️ **A2DP激活**：可能失败，因为车机系统有音频路由限制
4. ⚠️ **音频路由**：
   - 测试6：音频从扬声器播放（预期）
   - 测试7：取决于ExoPlayer API是否被系统限制

## 测试后操作

1. **保存日志**
   ```bash
   adb logcat -d > bluetooth_test_log.txt
   ```

2. **分析结果**
   - 如果测试7成功：音频从蓝牙耳机播放 → 系统支持ExoPlayer音频路由
   - 如果测试7失败：音频从扬声器播放 → 系统限制了音频路由

3. **报告结果**
   将测试结果和日志文件发送给开发者

## 常见问题

### Q1: 应用闪退怎么办？
A: 检查是否授予了所有蓝牙权限。在设置中手动授予权限。

### Q2: 听不到测试音频？
A:
- 检查车机音量是否开启
- 检查蓝牙耳机是否正常工作
- 查看日志中的错误信息

### Q3: 如何停止测试音频？
A: 测试音频会自动播放10秒后停止，或关闭应用。

### Q4: 可以在测试过程中连接/断开蓝牙耳机吗？
A: 不建议。请在所有测试完成后再操作蓝牙设备。

## 技术说明

### 测试原理

1. **权限测试**：检查应用拥有的蓝牙相关权限
2. **设备检测**：通过BluetoothAdapter获取已配对设备
3. **A2DP状态**：通过BluetoothA2dp服务检查连接状态
4. **音频设备**：通过AudioManager获取可用的音频输出设备
5. **A2DP激活**：尝试连接A2DP设备（可能被系统限制）
6. **ExoPlayer路由**：使用ExoPlayer的setPreferredDevice API设置音频输出

### API版本要求

- Android 6.0 (API 23): AudioDeviceInfo
- Android 8.0 (API 26): setPreferredDevice
- Android 12 (API 31): BLUETOOTH_CONNECT权限

### 系统限制

车机系统可能的限制：
- 禁用A2DP Sink连接
- 限制应用修改音频路由
- 限制ExoPlayer的音频设备选择
