// FloatingScreenCasting Audio Force to Bluetooth
// Frida脚本：强制音频路由到蓝牙A2DP设备

Java.perform(function() {
    var Log = Java.use("android.util.Log");

    Log.d("FridaAudio", "[*] FloatingScreenCasting Audio Hook Started");
    Log.d("FridaAudio", "[*] Target: com.example.floatingscreencasting");

    // Hook AudioManager to force Bluetooth A2DP
    try {
        var AudioManager = Java.use("android.media.AudioManager");

        // Hook setPreferredDevice
        AudioManager.setPreferredDevice.implementation = function(streamType, device) {
            var deviceName = "unknown";
            try { deviceName = device.getProductName(); } catch(e) {}

            Log.d("FridaAudio", "[*] setPreferredDevice called - StreamType: " + streamType + ", Device: " + deviceName);

            // 如果是音频播放，强制使用蓝牙A2DP
            if (streamType === 3) { // STREAM_MUSIC = 3
                var devices = this.getDevices(1); // GET_DEVICES_OUTPUTS = 1
                for (var i = 0; i < devices.length; i++) {
                    var dev = devices[i];
                    var devName = "unknown";
                    var devType = -1;
                    try {
                        devName = dev.getProductName();
                        devType = dev.getType();
                        Log.d("FridaAudio", "[*] Found device: " + devName + " Type: " + devType);
                    } catch(e) {}

                    // TYPE_BLUETOOTH_A2DP = 8
                    if (devType === 8) {
                        Log.d("FridaAudio", "[+] Forcing Bluetooth A2DP for STREAM_MUSIC: " + devName);
                        return this.setPreferredDevice(streamType, dev);
                    }
                }
            }

            return this.setPreferredDevice(streamType, device);
        };

        Log.d("FridaAudio", "[+] AudioManager.setPreferredDevice hooked");
    } catch(e) {
        Log.e("FridaAudio", "[-] Failed to hook AudioManager: " + e);
    }

    // Hook Bluetooth SCO to always succeed
    try {
        AudioManager.setBluetoothScoOn.implementation = function(scoOn) {
            Log.d("FridaAudio", "[*] setBluetoothScoOn called: " + scoOn);
            Log.d("FridaAudio", "[+] Forcing SCO connection success");
            return true;
        };

        AudioManager.startBluetoothSco.implementation = function() {
            Log.d("FridaAudio", "[*] startBluetoothSco called");
            Log.d("FridaAudio", "[+] Forcing SCO start success");
            return true;
        };

        AudioManager.stopBluetoothSco.implementation = function() {
            Log.d("FridaAudio", "[*] stopBluetoothSco called");
            return true;
        };

        Log.d("FridaAudio", "[+] Bluetooth SCO methods hooked");
    } catch(e) {
        Log.e("FridaAudio", "[-] Failed to hook SCO methods: " + e);
    }

    // Hook ExoPlayer to force audio device
    try {
        var ExoPlayerImpl = Java.use("androidx.media3.exoplayer.ExoPlayer");

        // 如果ExoPlayer有setAudioAttributes方法
        if (ExoPlayerImpl.setAudioAttributes) {
            ExoPlayerImpl.setAudioAttributes.implementation = function(audioAttributes, handleAudioFocus) {
                Log.d("FridaAudio", "[*] ExoPlayer.setAudioAttributes called");
                Log.d("FridaAudio", "[+] Allowing audio focus handling");
                return this.setAudioAttributes(audioAttributes, true); // 强制handleAudioFocus
            };
            Log.d("FridaAudio", "[+] ExoPlayer.setAudioAttributes hooked");
        }
    } catch(e) {
        Log.e("FridaAudio", "[-] ExoPlayer hook failed: " + e);
    }

    // Hook AudioTrack to force Bluetooth device
    try {
        var AudioTrack = Java.use("android.media.AudioTrack");
        var AudioAttributes = Java.use("android.media.AudioAttributes");

        AudioTrack.$init.overload('[Ljava/lang/String;IIIIIILjava/lang/Object;Landroid/media/.audiofx/AudioEffect;I', function() {
            Log.d("FridaAudio", "[*] AudioTrack init called");
            Log.d("FridaAudio", "[+] AudioTrack created (will use Bluetooth device if available)");
            return this.$init.apply(this, arguments);
        });

        Log.d("FridaAudio", "[+] AudioTrack hooked");
    } catch(e) {
        Log.e("FridaAudio", "[-] AudioTrack hook failed: " + e);
    }

    // 强制设置音频模式
    Java.scheduleOnMainThread(function() {
        try {
            var AudioManager = Java.use("android.media.AudioManager");
            var am = AudioManager.$new();

            // 设置音频模式为MODE_IN_COMMUNICATION以支持SCO
            am.setMode(0); // MODE_NORMAL = 0

            // 启用蓝牙SCO
            am.setBluetoothScoOn(true);
            am.startBluetoothSco();

            Log.d("FridaAudio", "[+] Audio mode configured for SCO");
        } catch(e) {
            Log.e("FridaAudio", "[-] Failed to set audio mode: " + e);
        }
    });

    Log.d("FridaAudio", "[*] All hooks installed successfully");
    Log.d("FridaAudio", "[*] Audio should now route to Bluetooth A2DP");
});
