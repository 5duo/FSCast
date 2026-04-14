# 车机蓝牙音频输出研究

## 研究目标
让FloatingScreenCasting投屏应用的音频通过蓝牙耳机（HONOR Earbuds X6）播放，而不是从车载扬声器播放。

## 研究时间
2026年4月14日

## 车机环境
- 设备: 岚图梦想家
- 蓝牙: HONOR Earbuds X6 已连接
- 系统: 基于Android的定制车机系统

## 尝试的方法

### 1. 正常Android音频API
**测试方法：**
- SCO (Synchronous Connection-Oriented) 音频通道
- MODE_IN_COMMUNICATION 通话模式
- AudioManager.setCommunicationDevice() 强制蓝牙设备
- AudioTrack直测

**结果：** 全部失败，音频只能从扬声器播放

### 2. Frida动态Hook
**Hook的API：**
- AudioManager.setPreferredDevice()
- AudioManager.setBluetoothScoOn()
- AudioManager.startBluetoothSco()
- ExoPlayer.setAudioAttributes()
- AudioTrack构造函数

**结果：** Hook成功拦截调用，但系统底层仍拒绝连接
```
FridaAudio: [*] setBluetoothScoOn called: true
FridaAudio: [+] Forcing SCO connection success
但 SCO状态仍为 false
```

### 3. 修改系统属性
**设置的属性：**
```
audio.fingerprint_a2dp.enabled = 1
persist.audio.fingerprint_a2dp.enabled = 1
persist.audio.a2dp.enabled = 1
persist.audio.bluetooth.enabled = 1
persist.audio.route.force_bluetooth = 1
persist.allow.a2dp.forbidden_apps = []
brlinkd.a2dp.muted = 0
```

**结果：** 重启后仍然失败

### 4. 应用内Root权限
**尝试：** 通过Runtime.exec("su")获取root权限
**结果：** 车机su命令格式特殊，应用内无法直接使用

## 失败的根本原因

从日志中发现关键证据：

```
W AS.AudioService: Audio Settings Permission Denial: setBluetoothScoOn() from pid=11817, uid=10081
```

AudioTrack日志显示：
```
isBrlink[False]
```

**结论：**
车机系统在`AudioService`层有严格的**白名单机制**，只有被系统认可的应用（如`com.huawei.dmsdpdevice`）才能使用蓝牙A2DP音频输出。第三方应用无法通过任何应用层手段绕过此限制。

## 测试工具

在研究过程中创建了三个测试Activity：
1. **BluetoothAudioTestActivity** - 蓝牙音频基础测试
2. **RootAudioTestActivity** - 使用root权限的高级测试
3. **DirectAudioTestActivity** - 不依赖root的直接测试

## 最终结论

车机系统的蓝牙音频限制是一个**系统级的安全限制**，无法通过以下方式绕过：
- 应用层API
- Frida动态Hook
- 系统属性修改
- Root权限（应用内无法直接获取）

可能的解决方案（未尝试，风险较高）：
1. 将应用安装到系统分区（/system/priv-app）
2. 修改系统白名单配置
3. 刷入修改过的系统镜像

**建议：** 接受现实，继续优化投屏应用的其他功能。
