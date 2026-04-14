@echo off
setlocal enabledelayedexpansion

REM ==========================================
REM  Car IVI Telnet + Frida One-Click Deploy Script (Windows)
REM ==========================================
REM Purpose: Reuse device BusyBox for telnetd + deploy frida-inject2
REM          Inject fix.sh for persistent telnetd / Frida (USB-free)
REM          Enable CarFloatBall vehicle control and Frida Hook injection
REM
REM Full workflow:
REM   1. Check device BusyBox (with telnetd)
REM   2. Download/install frida-inject2
REM   3. Deploy Frida Hook scripts
REM   4. Check if fix.sh is already injected
REM   5. adb root + disable-verity -> Remove /system write protection
REM   6. Reboot then adb remount -> Mount /system as writable
REM   7. Safe inject fix.sh -> Insert setenforce 0 + telnetd + Frida loop
REM   8. Start and verify
REM
REM Usage: Double-click or run setup_telnet.bat in cmd
REM Prerequisite: ADB connected, device rooted

set "FIX_SCRIPT=/system/bin/fix.sh"
set "INJECT_MARKER=CARFLOAT_INJECT"
set "FIX_PAYLOAD_MARKER=CARFLOAT_MOUSE_GUARD_V1"
set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."
set "INIT_LOGCAT_URL=http://47.107.88.63/tools/init.logcat.sh"
set "INIT_LOGCAT_LOCAL=%SCRIPT_DIR%init.logcat.sh"
set "INIT_LOGCAT_DEVICE=/system/etc/init.logcat.sh"

REM Frida configuration
set "FRIDA_VERSION=16.2.1"
set "FRIDA_URL=http://47.107.88.63/tools/frida-inject-%FRIDA_VERSION%.zip"
set "FRIDA_LOCAL=%SCRIPT_DIR%frida-inject-%FRIDA_VERSION%.zip"
set "FRIDA_DEVICE_DIR=/data/local/bin"
set "FRIDA_DEVICE_BIN=%FRIDA_DEVICE_DIR%/frida-inject2"
set "FRIDA_SCRIPTS_LOCAL=%SCRIPT_DIR%frida"
set "FRIDA_DEVICE_SCRIPTS=/data/local/tmp/frida"

set "CARFLOAT_A11Y=com.carfloat.ball/com.carfloat.ball.service.FloatAccessibilityService"

set "TOTAL_STEPS=7"
set "busybox_path="

echo.
echo ==========================================
echo   Car IVI Telnet + Frida One-Click Deploy
echo ==========================================
echo.

REM ==================================================
REM Pre-check
REM ==================================================

REM Prefer platform-tools in the same directory as script
set "ADB_DIR=%SCRIPT_DIR%platform-tools"
set "ADB_EXE=%ADB_DIR%\platform-tools\adb.exe"

where adb >nul 2>&1
if errorlevel 1 (
    if exist "!ADB_EXE!" (
        echo   [OK] Using local ADB: !ADB_EXE!
        set "PATH=!ADB_DIR!\platform-tools;!PATH!"
    ) else (
        echo   [INFO] ADB not found, downloading...
        mkdir "!ADB_DIR!" >nul 2>&1
        powershell -NoProfile -Command ^
          "Invoke-WebRequest https://dl.google.com/android/repository/platform-tools-latest-windows.zip -OutFile '!ADB_DIR!\platform-tools.zip'"
        if not exist "!ADB_DIR!\platform-tools.zip" (
            echo   [ERROR] ADB download failed
            goto :fail
        )
        powershell -NoProfile -Command ^
          "Expand-Archive '!ADB_DIR!\platform-tools.zip' '!ADB_DIR!' -Force"
        if not exist "!ADB_EXE!" (
            echo   [ERROR] ADB extraction failed
            goto :fail
        )
        del /f "!ADB_DIR!\platform-tools.zip" >nul 2>&1
        echo   [OK] ADB installed
        set "PATH=!ADB_DIR!\platform-tools;!PATH!"
    )
)

REM Check device connection
set "device_count=0"
for /f "skip=1 tokens=1" %%a in ('adb devices 2^>nul') do (
    if not "%%a"=="" set /a device_count+=1
)
if !device_count! equ 0 (
    echo   [ERROR] No device connected, please connect ADB first
    goto :fail
)
echo   [OK] Device connected

REM Quick check: Is everything already set up?
set "telnet_running="
for /f "delims=" %%a in ('adb shell "netstat -tlnp 2>/dev/null" 2^>nul ^| findstr ":23 "') do set "telnet_running=%%a"
set "already_patched="
for /f "delims=" %%a in ('adb shell "grep \"%INJECT_MARKER%\" %FIX_SCRIPT% 2>/dev/null" 2^>nul') do set "already_patched=%%a"
set "current_fix_payload="
for /f "delims=" %%a in ('adb shell "grep \"%FIX_PAYLOAD_MARKER%\" %FIX_SCRIPT% 2>/dev/null" 2^>nul') do set "current_fix_payload=%%a"
set "frida_exists="
for /f "delims=" %%a in ('adb shell "[ -f %FRIDA_DEVICE_BIN% ] && echo yes" 2^>nul') do set "frida_exists=%%a"
set "hook_scripts_exist="
for /f "delims=" %%a in ('adb shell "[ -f %FRIDA_DEVICE_SCRIPTS%/load.bin ] && echo yes" 2^>nul') do set "hook_scripts_exist=%%a"

if defined telnet_running if defined already_patched if "!frida_exists!"=="yes" (
    REM [Fix] Require current fix.sh payload, otherwise force reinjection
    if not defined current_fix_payload (
        echo   [WARN] fix.sh missing current payload marker %FIX_PAYLOAD_MARKER%, need to refresh...
        goto :skip_quick_check
    )
    REM [Fix] Check init.logcat.sh has setenforce injection
    set "initlogcat_patched="
    for /f "tokens=* usebackq" %%a in (`adb shell "grep setenforce %INIT_LOGCAT_DEVICE% 2>/dev/null"`) do set "initlogcat_patched=%%a"
    if not defined initlogcat_patched (
        echo   [WARN] init.logcat.sh missing setenforce injection, need to fix...
        goto :skip_quick_check
    )
    echo   [OK] Telnetd is running ^(port 23^)
    echo   [OK] fix.sh is injected ^(USB-free^)
    echo   [OK] frida-inject2 is installed

    REM Deploy frida scripts if local scripts exist but not on device
    if not "!hook_scripts_exist!"=="yes" (
        call :push_frida_scripts
    ) else (
        echo   [OK] Frida Hook scripts deployed
    )

    REM Ensure loop is running
    call :ensure_frida_loop

    REM Ensure accessibility is enabled
    call :enable_carfloat_a11y

    REM Grant usage stats permission
    adb shell "appops set com.carfloat.ball android:get_usage_stats allow" >nul 2>&1
    echo   [OK] Usage stats permission granted

    REM Refresh init.logcat.sh even on already-rooted devices so local edits take effect
    call :refresh_init_logcat

    echo.
    echo ==========================================
    echo   All ready!
    echo ==========================================
    goto :done
)
:skip_quick_check

REM ==================================================
REM Step 1: Check BusyBox
REM ==================================================
echo.
echo [1/%TOTAL_STEPS%] Checking BusyBox

call :resolve_busybox_path

if defined busybox_path (
    set "telnetd_check="
    for /f "delims=" %%a in ('adb shell "!busybox_path! --list 2>/dev/null" ^| findstr "telnetd"') do set "telnetd_check=%%a"
    if defined telnetd_check (
        echo   [OK] BusyBox available: !busybox_path! ^(with telnetd^)
    ) else (
        echo   [ERROR] BusyBox found but telnetd is unavailable: !busybox_path!
        echo   [INFO] fix.sh and init.logcat.sh can probe multiple paths, but the device still needs a BusyBox build with telnetd.
        goto :fail
    )
) else (
    echo   [ERROR] No compatible BusyBox found on device
    echo   [INFO] fix.sh and init.logcat.sh already probe multiple paths, but this script no longer copies or runs an uploaded busybox.
    goto :fail
)

REM ==================================================
REM Step 2: Check/Install Frida
REM ==================================================
echo.
echo [2/%TOTAL_STEPS%] Checking Frida

if "!frida_exists!"=="yes" (
    echo   [OK] frida-inject2 installed: %FRIDA_DEVICE_BIN%
) else (
    echo   [INFO] Need to install frida-inject2...

    set "frida_local_bin=%SCRIPT_DIR%frida-inject2"

    if exist "!frida_local_bin!" (
        echo   [OK] Local frida-inject2 found
    ) else (
        if exist "!FRIDA_LOCAL!" (
            echo   [OK] Local frida zip found
        ) else (
            echo   [INFO] Downloading frida-inject v%FRIDA_VERSION% from personal server...
            curl -s -L -o "!FRIDA_LOCAL!" "!FRIDA_URL!"
            if errorlevel 1 (
                echo   [ERROR] Download failed: !FRIDA_URL!
                goto :fail
            )
            echo   [OK] Download complete
        )

        echo   [INFO] Extracting frida-inject...
        set "FRIDA_TMP_DIR=%TEMP%\frida_extract"
        if exist "!FRIDA_TMP_DIR!" rmdir /s /q "!FRIDA_TMP_DIR!" >nul 2>&1
        mkdir "!FRIDA_TMP_DIR!" >nul 2>&1
        powershell -NoProfile -Command "Expand-Archive '!FRIDA_LOCAL!' '!FRIDA_TMP_DIR!' -Force"

        set "frida_extracted="
        for /f "delims=" %%f in ('dir /b /s "!FRIDA_TMP_DIR!\frida-inject*" 2^>nul') do set "frida_extracted=%%f"
        if not defined frida_extracted (
            echo   [ERROR] frida-inject binary not found after extraction
            rmdir /s /q "!FRIDA_TMP_DIR!" >nul 2>&1
            goto :fail
        )

        copy /y "!frida_extracted!" "!frida_local_bin!" >nul
        rmdir /s /q "!FRIDA_TMP_DIR!" >nul 2>&1
        echo   [OK] Extraction complete
    )

    echo   [INFO] Pushing frida-inject2 to device...
    adb root >nul 2>&1
    timeout /t 2 >nul
    adb shell "mkdir -p %FRIDA_DEVICE_DIR%"
    adb push "!frida_local_bin!" "%FRIDA_DEVICE_BIN%" >nul
    adb shell "chmod 755 %FRIDA_DEVICE_BIN%"

    set "frida_check="
    for /f "delims=" %%a in ('adb shell "[ -f %FRIDA_DEVICE_BIN% ] && echo yes" 2^>nul') do set "frida_check=%%a"
    if "!frida_check!"=="yes" (
        echo   [OK] frida-inject2 installed successfully
    ) else (
        echo   [ERROR] frida-inject2 push failed
        goto :fail
    )
)

REM ==================================================
REM Step 3: Deploy Frida Hook Scripts
REM ==================================================
echo.
echo [3/%TOTAL_STEPS%] Deploying Frida Hook scripts

adb root >nul 2>&1
timeout /t 1 >nul

call :push_frida_scripts

REM ==================================================
REM Step 4: Check if fix.sh needs modification
REM ==================================================
echo.
echo [4/%TOTAL_STEPS%] Checking fix.sh

set "already_patched="
for /f "delims=" %%a in ('adb shell "grep \"%INJECT_MARKER%\" %FIX_SCRIPT% 2>/dev/null" 2^>nul') do set "already_patched=%%a"

if defined already_patched (
    set "current_fix_payload="
    for /f "delims=" %%a in ('adb shell "grep \"%FIX_PAYLOAD_MARKER%\" %FIX_SCRIPT% 2>/dev/null" 2^>nul') do set "current_fix_payload=%%a"
    if not defined current_fix_payload (
        echo   [WARN] fix.sh contains old payload, will refresh latest version...
        goto :fix_needs_refresh
    )

    echo   [OK] fix.sh already contains current persistent payload

    REM Start telnetd directly
    call :start_telnetd

    call :push_frida_scripts
    call :ensure_frida_loop
    call :enable_carfloat_a11y

    echo   [INFO] Setting system properties...
    adb shell "settings put global hidden_api_policy_pre_p_apps 1; settings put global hidden_api_policy_p_apps 1; settings put global hidden_api_policy 1" >nul 2>&1
    adb shell "settings put global enable_freeform_support 1; settings put global force_resizable_activities 1" >nul 2>&1
    echo   [OK] System properties configured

    adb shell "appops set com.carfloat.ball android:get_usage_stats allow" >nul 2>&1
    echo   [OK] Usage stats permission granted

    REM Refresh init.logcat.sh so local changes are not skipped
    call :refresh_init_logcat

    echo.
    echo ==========================================
    echo   Deployment complete!
    echo ==========================================
    echo.
    goto :done
)
:fix_needs_refresh

if defined already_patched (
    echo   [INFO] Proceeding to reinject fix.sh with the latest payload...
) else (
    echo   [WARN] fix.sh not yet injected, first-time deployment required
)
echo.

REM ==================================================
REM Step 5: adb root + disable-verity
REM ==================================================
echo.
echo [5/%TOTAL_STEPS%] Disabling dm-verity

echo   [INFO] Obtaining root access...
adb root >nul 2>&1
timeout /t 2 >nul
echo   [OK] adb root

REM Disable SELinux first
adb shell "setenforce 0" >nul 2>&1

set "VERITY_TMP=%TEMP%\verity_output.txt"
adb disable-verity >"!VERITY_TMP!" 2>&1

findstr /i /c:"Successfully disabled" "!VERITY_TMP!" >nul 2>&1
if not errorlevel 1 (
    echo   [OK] dm-verity disabled
    del /f "!VERITY_TMP!" >nul 2>&1
    echo.
    echo   [WARN] Device reboot required for changes to take effect
    echo.
    echo   Press any key to reboot now...
    pause >nul

    echo   [INFO] Rebooting device...
    adb reboot
    echo   Waiting for device to restart...
    adb wait-for-device
    timeout /t 5 >nul
    echo   [OK] Device reconnected

    adb root >nul 2>&1
    timeout /t 2 >nul
    goto :step6
)

findstr /i /c:"already disabled" "!VERITY_TMP!" >nul 2>&1
if not errorlevel 1 (
    echo   [OK] dm-verity already disabled, skipping reboot
    del /f "!VERITY_TMP!" >nul 2>&1
    goto :step6
)

echo   [WARN] Failed to disable dm-verity, see output:
type "!VERITY_TMP!"
del /f "!VERITY_TMP!" >nul 2>&1
echo   [INFO] Trying direct su remount /system ...

:step6
REM ==================================================
REM Step 6: remount + Safe inject fix.sh
REM ==================================================
echo.
echo [6/%TOTAL_STEPS%] Safe inject fix.sh

echo   [INFO] Mounting /system as writable...
set "REMOUNT_TMP=%TEMP%\remount_output.txt"
adb remount >"!REMOUNT_TMP!" 2>&1

findstr /i /c:"succeeded" "!REMOUNT_TMP!" >nul 2>&1
if not errorlevel 1 (
    echo   [OK] remount succeeded
) else (
    echo   [WARN] adb remount failed, trying su mount...
    adb shell "su -c 'mount -o rw,remount /'" >nul 2>&1
    adb shell "su -c 'mount -o rw,remount /system'" >nul 2>&1
    adb shell "su -c 'touch /system/.test_rw'" >nul 2>&1
    if errorlevel 1 (
        echo   [ERROR] Cannot mount /system as writable
        goto :fail
    )
    adb shell "su -c 'rm /system/.test_rw'" >nul 2>&1
    echo   [OK] su mount remount succeeded
)
del /f "!REMOUNT_TMP!" >nul 2>&1

REM ---- Update init.logcat.sh (executed by init with root SELinux domain) ----
echo   [INFO] Updating init.logcat.sh ...
if exist "!INIT_LOGCAT_LOCAL!" (
    echo   [OK] Using local init.logcat.sh
) else (
    echo   [INFO] Downloading init.logcat.sh from server...
    curl -s -L -o "!INIT_LOGCAT_LOCAL!" "!INIT_LOGCAT_URL!"
    if errorlevel 1 (
        echo   [WARN] Download init.logcat.sh failed, skipping
        set "INIT_LOGCAT_LOCAL="
    ) else (
        echo   [OK] Downloaded
    )
)
if defined INIT_LOGCAT_LOCAL if exist "!INIT_LOGCAT_LOCAL!" (
    adb shell "cp %INIT_LOGCAT_DEVICE% /data/local/tmp/init.logcat.sh.bak" >nul 2>&1
    adb push "!INIT_LOGCAT_LOCAL!" "%INIT_LOGCAT_DEVICE%" >nul 2>&1
    adb shell "chmod 755 %INIT_LOGCAT_DEVICE%" >nul 2>&1
    echo   [OK] init.logcat.sh updated ^(with setenforce 0 + telnetd + Frida guard^)
)

echo   [INFO] Backing up original fix.sh...
adb shell "cp %FIX_SCRIPT% /data/local/tmp/fix.sh.bak" >nul 2>&1
echo   [OK] Original fix.sh backed up to /data/local/tmp/fix.sh.bak

echo   [INFO] Generating injection payload...

REM Write payload directly to device via adb shell
REM This avoids all CMD special character parsing and Chinese path issues
set "PF=/data/local/tmp/fix_payload.sh"
adb shell "echo '# ==================== CARFLOAT_INJECT ====================' > %PF%"
adb shell "echo 'setenforce 0' >> %PF%"
adb shell "echo '# %FIX_PAYLOAD_MARKER%' >> %PF%"
adb shell "echo '# 360 reverse mouse guard: early cleanup + background watchdog' >> %PF%"
adb shell "echo 'is_pointer_mouse_device() {' >> %PF%"
adb shell "echo '  sysdev=\"$1\"' >> %PF%"
adb shell "echo '  [ -d \"$sysdev\" ] || return 1' >> %PF%"
adb shell "echo '' >> %PF%"
adb shell "echo '  devname=$(cat \"$sysdev/name\" 2>/dev/null)' >> %PF%"
adb shell "echo '  case \"$devname\" in' >> %PF%"
adb shell "echo '    *\" Mouse\"|*\" mouse\") return 0 ;;' >> %PF%"
adb shell "echo '  esac' >> %PF%"
adb shell "echo '' >> %PF%"
adb shell "echo '  rel=$(cat \"$sysdev/capabilities/rel\" 2>/dev/null)' >> %PF%"
adb shell "echo '  abs=$(cat \"$sysdev/capabilities/abs\" 2>/dev/null)' >> %PF%"
adb shell "echo '  if [ -n \"$rel\" ] && [ \"$rel\" != \"0\" ]; then' >> %PF%"
adb shell "echo '    if [ -z \"$abs\" ] || [ \"$abs\" = \"0\" ]; then' >> %PF%"
adb shell "echo '      return 0' >> %PF%"
adb shell "echo '    fi' >> %PF%"
adb shell "echo '  fi' >> %PF%"
adb shell "echo '  return 1' >> %PF%"
adb shell "echo '}' >> %PF%"
adb shell "echo '' >> %PF%"
adb shell "echo 'disable_mouse_devices_once() {' >> %PF%"
adb shell "echo '  removed_any=1' >> %PF%"
adb shell "echo '  for s in /sys/class/input/event*/device; do' >> %PF%"
adb shell "echo '    [ -d \"$s\" ] || continue' >> %PF%"
adb shell "echo '    eventnode=$(basename \"$(dirname \"$s\")\")' >> %PF%"
adb shell "echo '    devpath=\"/dev/input/$eventnode\"' >> %PF%"
adb shell "echo '    [ -e \"$devpath\" ] || continue' >> %PF%"
adb shell "echo '' >> %PF%"
adb shell "echo '    if is_pointer_mouse_device \"$s\"; then' >> %PF%"
adb shell "echo '      devname=$(cat \"$s/name\" 2>/dev/null)' >> %PF%"
adb shell "echo '      rm -f \"$devpath\" 2>/dev/null' >> %PF%"
adb shell "echo '      log -p i -t FIX \"CARFLOAT: removed pointer $devpath ($devname)\"' >> %PF%"
adb shell "echo '      removed_any=0' >> %PF%"
adb shell "echo '    fi' >> %PF%"
adb shell "echo '  done' >> %PF%"
adb shell "echo '  return $removed_any' >> %PF%"
adb shell "echo '}' >> %PF%"
adb shell "echo '' >> %PF%"
adb shell "echo 'restart_dls_reverse_safely() {' >> %PF%"
adb shell "echo '  if pidof dls_reverse_svr >/dev/null 2>&1; then' >> %PF%"
adb shell "echo '    killall -9 dls_reverse_svr 2>/dev/null' >> %PF%"
adb shell "echo '    log -p i -t FIX \"CARFLOAT: killed dls_reverse_svr after pointer cleanup\"' >> %PF%"
adb shell "echo '    sleep 2' >> %PF%"
adb shell "echo '  fi' >> %PF%"
adb shell "echo '  if ! pidof dls_reverse_svr >/dev/null 2>&1; then' >> %PF%"
adb shell "echo '    start dls_reverse_svr 2>/dev/null' >> %PF%"
adb shell "echo '    start vendor.dls_reverse_svr 2>/dev/null' >> %PF%"
adb shell "echo '    log -p i -t FIX \"CARFLOAT: started dls_reverse_svr\"' >> %PF%"
adb shell "echo '    sleep 2' >> %PF%"
adb shell "echo '  fi' >> %PF%"
adb shell "echo '}' >> %PF%"
adb shell "echo '' >> %PF%"
adb shell "echo 'if disable_mouse_devices_once; then' >> %PF%"
adb shell "echo '  restart_dls_reverse_safely' >> %PF%"
adb shell "echo 'elif ! pidof dls_reverse_svr >/dev/null 2>&1; then' >> %PF%"
adb shell "echo '  restart_dls_reverse_safely' >> %PF%"
adb shell "echo 'fi' >> %PF%"
adb shell "echo '' >> %PF%"
adb shell "echo '(while true; do' >> %PF%"
adb shell "echo '  if disable_mouse_devices_once; then' >> %PF%"
adb shell "echo '    restart_dls_reverse_safely' >> %PF%"
adb shell "echo '  fi' >> %PF%"
adb shell "echo '  sleep 2' >> %PF%"
adb shell "echo 'done) >> /data/local/tmp/mouse_guard.log 2>&1 &' >> %PF%"
adb shell "echo '' >> %PF%"
adb shell "echo '(while true; do setenforce 0 2>/dev/null; sleep 30; done) &' >> %PF%"
adb shell "echo 'BB=\"\"' >> %PF%"
adb shell "echo 'for p in /sbin/busybox /system/xbin/busybox /system/bin/busybox /vendor/bin/busybox /data/local/bin/busybox /data/local/tmp/busybox /data/adb/magisk/busybox; do' >> %PF%"
adb shell "echo '  [ -x \"$p\" ] && BB=\"$p\" && break' >> %PF%"
adb shell "echo 'done' >> %PF%"
adb shell "echo '[ -z \"$BB\" ] && BB=$(which busybox 2>/dev/null)' >> %PF%"
adb shell "echo 'if [ -n \"$BB\" ]; then' >> %PF%"
adb shell "echo '    $BB telnetd -l sh' >> %PF%"
adb shell "echo '    log -p i -t FIX \"telnetd started via $BB\"' >> %PF%"
adb shell "echo 'else' >> %PF%"
adb shell "echo '    log -p e -t FIX \"busybox not found, telnetd not started\"' >> %PF%"
adb shell "echo 'fi' >> %PF%"
adb shell "echo 'mkdir -p /data/local/bin/' >> %PF%"
adb shell "echo '' >> %PF%"
adb shell "echo '# Grant CarFloatBall usage stats permission' >> %PF%"
adb shell "echo 'appops set com.carfloat.ball android:get_usage_stats allow 2>/dev/null || true' >> %PF%"
REM 添加FloatingScreenCasting的音频权限
adb shell "echo '' >> %PF%"
adb shell "echo '# FloatingScreenCasting Audio Permissions' >> %PF%"
adb shell "echo '# 启用A2DP Sink连接' >> %PF%"
adb shell "echo 'setprop persist.allow.a2dpsnk.conn enable' >> %PF%"
adb shell "echo '# 启用多蓝牙设备支持' >> %PF%"
adb shell "echo 'setprop persist.sys.qg.multibluetooth true' >> %PF%"
adb shell "echo '# 修改A2DP静音状态' >> %PF%"
adb shell "echo 'setprop brlinkd.a2dp.muted 0' >> %PF%"
adb shell "echo 'setprop persist.brlinkd.a2dp.muted 0' >> %PF%"
adb shell "echo '# 强制启用A2DP音频输出' >> %PF%"
adb shell "echo 'setprop audio.fingerprint_a2dp.enabled 1' >> %PF%"
adb shell "echo 'setprop persist.audio.fingerprint_a2dp.enabled 1' >> %PF%"
adb shell "echo '# 禁用音频白名单限制' >> %PF%"
adb shell "echo 'setprop ro.audio.monitor_output_period_us 0' >> %PF%"
adb shell "echo '# 强制所有音频流支持蓝牙A2DP' >> %PF%"
adb shell "echo 'setprop persist.audio.a2dp.enabled 1' >> %PF%"
adb shell "echo 'setprop persist.audio.bluetooth.enabled 1' >> %PF%"
adb shell "echo '# 修改音频路由策略' >> %PF%"
adb shell "echo 'setprop persist.audio.route.force_bluetooth 1' >> %PF%"
adb shell "echo '# 允许第三方应用使用蓝牙A2DP' >> %PF%"
adb shell "echo 'setprop persist.allow.a2dp.forbidden_apps \"\"' >> %PF%"
adb shell "echo '' >> %PF%"
adb shell "echo '# Frida: auto-discover frida-inject2' >> %PF%"
adb shell "echo '[ ! -f /data/local/bin/frida-inject2 ] \' >> %PF%"
adb shell "echo '    && [ -f /sdcard/Download/frida-inject2 ] \' >> %PF%"
adb shell "echo '    && cp /sdcard/Download/frida-inject2 /data/local/bin/frida-inject2 \' >> %PF%"
adb shell "echo '    && chmod +x /data/local/bin/frida-inject2' >> %PF%"
adb shell "echo '' >> %PF%"
adb shell "echo '# Frida: supervisor loop' >> %PF%"
adb shell "echo '(while true; do' >> %PF%"
adb shell "echo '    [ -f /data/local/tmp/frida/load.bin ] && /system/bin/sh /data/local/tmp/frida/load.bin' >> %PF%"
adb shell "echo '    sleep 5' >> %PF%"
adb shell "echo 'done) >> /data/local/tmp/frida/inject.log 2>&1 &' >> %PF%"
adb shell "echo '' >> %PF%"
adb shell "echo '# USB watchdog loop' >> %PF%"
adb shell "echo '(while true; do' >> %PF%"
adb shell "echo '    [ -f /data/local/tmp/usb_watchdog.sh ] && /system/bin/sh /data/local/tmp/usb_watchdog.sh' >> %PF%"
adb shell "echo '    sleep 5' >> %PF%"
adb shell "echo 'done) >> /data/local/tmp/usb_watchdog.log 2>&1 &' >> %PF%"
adb shell "echo '' >> %PF%"
adb shell "echo 'log -p e -t FIX CARFLOAT_telnetd_frida_watchdog_started' >> %PF%"
adb shell "echo '# =========================================================' >> %PF%"

echo   [OK] Payload generated

echo   [INFO] Performing safe non-destructive splice injection...
REM Non-destructive splice on device:
REM 1. Extract first line of original fix.sh (#!/system/bin/sh)
REM 2. Append payload
REM 3. Append all lines from line 2 onwards of original fix.sh
adb shell "head -n 1 %FIX_SCRIPT% > /data/local/tmp/fix.new" >nul 2>&1
adb shell "echo '' >> /data/local/tmp/fix.new" >nul 2>&1
adb shell "cat %PF% >> /data/local/tmp/fix.new" >nul 2>&1
adb shell "echo '' >> /data/local/tmp/fix.new" >nul 2>&1
adb shell "tail -n +2 %FIX_SCRIPT% >> /data/local/tmp/fix.new" >nul 2>&1

REM Validate merged script syntax before touching /system/bin/fix.sh
adb shell "/system/bin/sh -n /data/local/tmp/fix.new" >nul 2>&1
if errorlevel 1 (
    echo   [ERROR] Generated fix.new failed shell syntax check
    adb shell "rm -f /data/local/tmp/fix.new %PF%" >nul 2>&1
    goto :fail
)

REM Write back to system partition
adb shell "cp /data/local/tmp/fix.new %FIX_SCRIPT%" >nul 2>&1
adb shell "chmod 755 %FIX_SCRIPT%" >nul 2>&1

REM Clean up temp files
adb shell "rm -f /data/local/tmp/fix.new %PF%" >nul 2>&1

echo   [OK] fix.sh injection complete

REM ==================================================
REM Step 7: Start + Verify
REM ==================================================
echo.
echo [7/%TOTAL_STEPS%] Starting and verifying

call :start_telnetd

REM Verify frida
set "frida_check="
for /f "delims=" %%a in ('adb shell "[ -f %FRIDA_DEVICE_BIN% ] && echo yes" 2^>nul') do set "frida_check=%%a"
if "!frida_check!"=="yes" (
    echo   [OK] frida-inject2 ready
) else (
    echo   [WARN] frida-inject2 may not be correctly installed
)

REM Verify file persistence
set "patched="
for /f "delims=" %%a in ('adb shell "grep \"%INJECT_MARKER%\" %FIX_SCRIPT%" 2^>nul') do set "patched=%%a"
if defined patched (
    echo   [OK] fix.sh injection verified
) else (
    echo   [ERROR] fix.sh injection verification failed!
    echo   [WARN] Restoring from backup...
    adb shell "cp /data/local/tmp/fix.sh.bak %FIX_SCRIPT%" >nul 2>&1
    adb shell "chmod 755 %FIX_SCRIPT%" >nul 2>&1
    echo   [ERROR] Original fix.sh restored, please check and retry
    goto :fail
)

REM Deploy Frida Hook scripts
set "hook_check="
for /f "delims=" %%a in ('adb shell "[ -f %FRIDA_DEVICE_SCRIPTS%/load.bin ] && echo yes" 2^>nul') do set "hook_check=%%a"
if not "!hook_check!"=="yes" (
    call :push_frida_scripts
) else (
    echo   [OK] Frida Hook scripts ready
)

REM Enable accessibility service
call :enable_carfloat_a11y

REM Unlock Hidden API
echo   [INFO] Unlocking Hidden API...
adb shell "settings put global hidden_api_policy_pre_p_apps 1; settings put global hidden_api_policy_p_apps 1; settings put global hidden_api_policy 1" >nul 2>&1
echo   [OK] Hidden API policy configured

REM Enable multi-window support
adb shell "settings put global enable_freeform_support 1; settings put global force_resizable_activities 1" >nul 2>&1
echo   [OK] Multi-window/Freeform support enabled

REM Grant usage stats permission
adb shell "appops set com.carfloat.ball android:get_usage_stats allow" >nul 2>&1
echo   [OK] Usage stats permission granted

REM Grant FloatingScreenCasting root permissions
echo   [INFO] Granting FloatingScreenCasting root permissions...
adb shell "su 0 'pm grant com.example.floatingscreencasting android.permission.WRITE_SECURE_SETTINGS'" >nul 2>&1
adb shell "su 0 'appops set com.example.floatingscreencasting android:get_usage_stats allow'" >nul 2>&1
adb shell "su 0 'setenforce 0'" >nul 2>&1
echo   [OK] FloatingScreenCasting permissions granted

REM Start Frida injection loop immediately
call :ensure_frida_loop

REM Grant FloatingScreenCasting root permissions via Frida
echo   [INFO] Granting FloatingScreenCasting root privileges...
adb shell "su 0 sh -c 'mkdir -p /data/local/tmp/frida'" >nul 2>&1
if exist "!SCRIPT_DIR!frida\floatingscreencasting_root.sh" (
    echo   [INFO] Deploying FloatingScreenCasting root script...
    adb push "!SCRIPT_DIR!frida\floatingscreencasting_root.sh" "/data/local/tmp/frida/" >nul 2>&1
    adb shell "su 0 sh -c 'chmod 755 /data/local/tmp/frida/floatingscreencasting_root.sh && /data/local/tmp/frida/floatingscreencasting_root.sh'" >nul 2>&1
    echo   [OK] FloatingScreenCasting root permissions granted
)
echo   [OK] Frida root privileges configured

echo.
echo ==========================================
echo   Deployment complete! Effective immediately, no reboot needed
echo ==========================================
echo.
echo   Completed:
echo     + BusyBox ^(!busybox_path!^)
echo     + Frida ^(%FRIDA_DEVICE_BIN%^)
echo     + Frida Hook scripts ^(%FRIDA_DEVICE_SCRIPTS%^)
echo     + dm-verity disabled
echo     + fix.sh deep injection
echo     + Frida injection loop started
echo     + Telnetd ^(port 23, WiFi accessible^)
echo     + Hidden API restrictions removed
echo     + Multi-window/Freeform support enabled
echo.
echo   You can now permanently disable USB debugging,
echo   and connect to the car IVI via WiFi using telnet!
echo.
goto :done

REM ==================================================
REM Subroutines
REM ==================================================

:resolve_busybox_path
    set "busybox_path="
    for /f "tokens=* usebackq" %%a in (`adb shell "for p in /sbin/busybox /system/xbin/busybox /system/bin/busybox /vendor/bin/busybox /data/local/bin/busybox /data/local/tmp/busybox /data/adb/magisk/busybox; do [ -x \"$p\" ] && echo \"$p\" && break; done" 2^>nul`) do (
        if not defined busybox_path set "busybox_path=%%a"
    )
    if not defined busybox_path (
        for /f "tokens=* usebackq" %%a in (`adb shell "which busybox 2>/dev/null" 2^>nul`) do set "busybox_path=%%a"
    )
    if defined busybox_path (
        for /f "tokens=* delims=" %%a in ("!busybox_path!") do set "busybox_path=%%a"
    )
goto :eof

:start_telnetd
    set "telnet_running="
    if not defined busybox_path (
        echo   [WARN] No compatible BusyBox path resolved, skipping immediate telnetd start
        goto :eof
    )
    echo   [INFO] Starting telnetd via !busybox_path!...
    adb shell "setenforce 0; !busybox_path! telnetd -l sh; mkdir -p /data/local/bin/" >nul 2>&1
    timeout /t 1 >nul
    for /f "delims=" %%a in ('adb shell "netstat -tlnp 2>/dev/null" 2^>nul ^| findstr ":23 "') do set "telnet_running=%%a"
    if defined telnet_running (
        echo   [OK] Telnetd started, listening on port 23
    ) else (
        echo   [WARN] Telnetd may not have started, please check
    )
goto :eof

:refresh_init_logcat
    echo   [INFO] Refreshing init.logcat.sh ...
    adb root >nul 2>&1
    timeout /t 2 /nobreak >nul
    adb remount >nul 2>&1
    if exist "!INIT_LOGCAT_LOCAL!" (
        echo   [OK] Using local init.logcat.sh
    ) else (
        echo   [INFO] Downloading init.logcat.sh from server...
        curl -s -L -o "!INIT_LOGCAT_LOCAL!" "!INIT_LOGCAT_URL!"
    )
    if exist "!INIT_LOGCAT_LOCAL!" (
        adb shell "cp %INIT_LOGCAT_DEVICE% /data/local/tmp/init.logcat.sh.bak" >nul 2>&1
        adb push "!INIT_LOGCAT_LOCAL!" "/data/local/tmp/init.logcat.sh.new" >nul 2>&1
        adb shell "/system/bin/sh -n /data/local/tmp/init.logcat.sh.new" >nul 2>&1
        if errorlevel 1 (
            echo   [ERROR] init.logcat.sh syntax check failed, keeping existing file
            adb shell "rm -f /data/local/tmp/init.logcat.sh.new" >nul 2>&1
            goto :eof
        )
        adb shell "cp /data/local/tmp/init.logcat.sh.new %INIT_LOGCAT_DEVICE%" >nul 2>&1
        adb shell "chmod 755 %INIT_LOGCAT_DEVICE%" >nul 2>&1
        adb shell "rm -f /data/local/tmp/init.logcat.sh.new" >nul 2>&1
        echo   [OK] init.logcat.sh refreshed ^(syntax verified^)
    ) else (
        echo   [WARN] init.logcat.sh unavailable, skipped refresh
    )
goto :eof

:push_frida_scripts
    if not exist "!FRIDA_SCRIPTS_LOCAL!\" (
        echo   [WARN] Frida scripts directory not found: !FRIDA_SCRIPTS_LOCAL!
        echo   [INFO] Please place frida/ directory alongside this script
        goto :eof
    )

    echo   [INFO] Using !FRIDA_SCRIPTS_LOCAL! directory
    adb shell "mkdir -p %FRIDA_DEVICE_SCRIPTS%" >nul 2>&1

    set "pushed=0"
    for %%f in ("!FRIDA_SCRIPTS_LOCAL!\*.js" "!FRIDA_SCRIPTS_LOCAL!\*.sh" "!FRIDA_SCRIPTS_LOCAL!\*.bin") do (
        if exist "%%f" (
            adb push "%%f" "%FRIDA_DEVICE_SCRIPTS%/%%~nxf" >nul 2>&1
            adb shell "chmod 755 %FRIDA_DEVICE_SCRIPTS%/%%~nxf" >nul 2>&1
            set /a pushed+=1
        )
    )

    REM If no load.bin, use load_multidisplay_hook.sh as entry point
    set "has_loadbin="
    for /f "delims=" %%a in ('adb shell "[ -f %FRIDA_DEVICE_SCRIPTS%/load.bin ] && echo yes" 2^>nul') do set "has_loadbin=%%a"
    if not "!has_loadbin!"=="yes" (
        if exist "!FRIDA_SCRIPTS_LOCAL!\load_multidisplay_hook.sh" (
            adb push "!FRIDA_SCRIPTS_LOCAL!\load_multidisplay_hook.sh" "%FRIDA_DEVICE_SCRIPTS%/load.bin" >nul 2>&1
            adb shell "chmod 755 %FRIDA_DEVICE_SCRIPTS%/load.bin" >nul 2>&1
            set /a pushed+=1
            echo   [INFO] load_multidisplay_hook.sh -^> load.bin
        ) else (
            echo   [WARN] Missing load.bin entry script
        )
    )

    if !pushed! gtr 0 (
        echo   [OK] Deployed !pushed! Frida scripts
    ) else (
        echo   [WARN] No Frida scripts pushed
    )

    REM Deploy USB trigger script
    if exist "!SCRIPT_DIR!usb_watchdog.sh" (
        adb push "!SCRIPT_DIR!usb_watchdog.sh" "/data/local/tmp/usb_watchdog.sh" >nul 2>&1
        adb shell "chmod 755 /data/local/tmp/usb_watchdog.sh" >nul 2>&1
        echo   [INFO] USB trigger script deployed ^(usb_watchdog.sh^)
    )
goto :eof

:ensure_frida_loop
    set "loop_running="
    for /f "delims=" %%a in ('adb shell "ps -eo args 2>/dev/null" 2^>nul ^| findstr "load.bin"') do set "loop_running=%%a"
    if defined loop_running (
        echo   [OK] Frida injection loop is running
    ) else (
        echo   [INFO] Starting Frida injection loop...
        adb forward tcp:2323 tcp:23 >nul 2>&1
        powershell -NoProfile -Command ^
          "try { $c = New-Object Net.Sockets.TcpClient('127.0.0.1',2323); $s = $c.GetStream(); $w = New-Object IO.StreamWriter($s); Start-Sleep -Milliseconds 500; $w.WriteLine('(while true; do [ -f /data/local/tmp/frida/load.bin ] && sh /data/local/tmp/frida/load.bin; sleep 5; done) >> /data/local/tmp/frida/inject.log 2>&1 &'); $w.Flush(); Start-Sleep -Seconds 1; $c.Close() } catch {}" >nul 2>&1
        echo   [OK] Frida injection loop started
    )
goto :eof

:enable_carfloat_a11y
    set "cf_installed="
    for /f "delims=" %%a in ('adb shell "pm list packages 2>/dev/null" 2^>nul ^| findstr "com.carfloat.ball"') do set "cf_installed=%%a"
    if not defined cf_installed (
        echo   [INFO] CarFloatBall not installed, please grant accessibility after installation
        goto :eof
    )
    set "a11y_existing="
    for /f "delims=" %%a in ('adb shell "settings get secure enabled_accessibility_services" 2^>nul') do set "a11y_existing=%%a"
    echo !a11y_existing! | findstr /i "com.carfloat.ball" >nul
    if not errorlevel 1 (
        echo   [OK] Accessibility service enabled
        goto :eof
    )
    if "!a11y_existing!"=="null" (
        set "a11y_new=%CARFLOAT_A11Y%"
    ) else if "!a11y_existing!"=="" (
        set "a11y_new=%CARFLOAT_A11Y%"
    ) else (
        set "a11y_new=!a11y_existing!:%CARFLOAT_A11Y%"
    )
    adb shell "settings put secure enabled_accessibility_services '!a11y_new!'" >nul 2>&1
    adb shell "settings put secure accessibility_enabled 1" >nul 2>&1
    echo   [OK] Accessibility service auto-enabled
goto :eof

:fail
echo.
echo   Deployment failed, please check the errors above
echo.
pause
exit /b 1

:done
pause
exit /b 0
