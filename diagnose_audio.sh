#!/bin/bash
# 音频诊断脚本 - 对比喜马拉雅和我们应用的音频配置

echo "=========================================="
echo "音频诊断脚本"
echo "=========================================="
echo ""

echo "1. 检查蓝牙A2DP连接状态："
adb shell dumpsys audio | grep -A 5 "bt_a2dp"
echo ""

echo "2. 检查STREAM_MUSIC的音量配置："
adb shell dumpsys audio | grep -A 3 "STREAM_MUSIC"
echo ""

echo "3. 检查音频焦点状态："
adb shell dumpsys audio | grep -A 15 "Audio Focus stack"
echo ""

echo "4. 检查运行的音频相关进程："
adb shell ps -A | grep -E "(com.example.floatingscreencasting|com.ximalaya)"
echo ""

echo "5. 检查AudioTracks和Patches："
adb shell dumpsys media.audio_flinger 2>&1 | grep -E "(AudioTrack|Active tracks)" | head -50
echo ""

echo "6. 检查音频输出设备："
adb shell dumpsys audio | grep -E "DeviceInfo.*bt_a2dp" -A 2
echo ""

echo "=========================================="
echo "诊断完成"
echo "=========================================="
