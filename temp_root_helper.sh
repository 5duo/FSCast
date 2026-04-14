#!/system/bin/sh
# FloatingScreenCasting Root Helper
setenforce 0
# 检查并授予应用root权限
pm grant com.example.floatingscreencasting android.permission.WRITE_SECURE_SETTINGS
# 启用A2DP连接
setprop persist.allow.a2dpsnk.conn enable
# 启用多蓝牙
setprop persist.sys.qg.multibluetooth true
# 设置音频属性
settings put global floating_screen_casting_audio_bluetooth 1
