#!/system/bin/sh
# FloatingScreenCasting Root Permission Script
# 此脚本为FloatingScreenCasting应用授予必要的权限

echo "[*] FloatingScreenCasting Root Permission Script"

# 关闭SELinux
setenforce 0

# 启用音频路由相关属性
setprop persist.allow.a2dpsnk.conn enable
setprop persist.sys.qg.multibluetooth true
setprop brlinkd.a2dp.muted 0
setprop persist.brlinkd.a2dp.muted 0

# 为应用授予敏感权限
pm grant com.example.floatingscreencasting android.permission.WRITE_SECURE_SETTINGS
pm grant com.example.floatingscreencasting android.permission.READ_PRIVILEGED_PHONE_STATE

# 设置应用权限
appops set com.example.floatingscreencasting android:get_usage_stats allow

# 修改音频设备权限
chmod 666 /dev/snd/* 2>/dev/null || true
chmod 666 /dev/audio* 2>/dev/null || true

echo "[+] Permissions granted successfully"
echo "[+] A2DP Sink enabled"
echo "[+] Multi-bluetooth enabled"
