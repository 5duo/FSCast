// FloatingScreenCasting Audio Fix Script
// Frida hook to enable Bluetooth A2DP audio routing

Java.perform(function() {
    console.log("[*] FloatingScreenCasting Audio Fix Started");

    // Hook AudioManager to force Bluetooth A2DP routing
    var AudioManager = Java.use("android.media.AudioManager");

    AudioManager.setPreferredDevice.overload('android.media.AudioDeviceInfo', function(deviceType, device) {
        console.log("[*] setPreferredDevice called with device type: " + deviceType);
        if (deviceType === 8) { // TYPE_BLUETOOTH_A2DP
            console.log("[+] Forcing Bluetooth A2DP routing");
        }
        return this.setPreferredDevice(deviceType, device);
    });

    // Hook setBluetoothScoOn to always return true
    AudioManager.setBluetoothScoOn.implementation = function(scoOn) {
        console.log("[*] setBluetoothScoOn called: " + scoOn);
        console.log("[+] Forcing SCO connection");
        return true;
    };

    // Hook startBluetoothSco to always succeed
    AudioManager.startBluetoothSco.implementation = function() {
        console.log("[*] startBluetoothSco called");
        console.log("[+] SCO connection forced");
        return true;
    };

    // Modify system properties
    var SystemProperties = Java.use("android.os.SystemProperties");
    SystemProperties.set.overload('java.lang.String', 'java.lang.String');

    SystemProperties.set.implementation = function(key, value) {
        if (key === "persist.allow.a2dpsnk.conn" || key === "persist.sys.qg.multibluetooth") {
            console.log("[*] Modifying property: " + key + " = enable");
            return this.set(key, "true");
        }
        return this.set(key, value);
    };

    console.log("[*] FloatingScreenCasting Audio Fix Loaded Successfully");
});

setTimeout(function() {
    console.log("[*] Executing post-load commands...");

    // Force enable A2DP
    Java.scheduleOnMainThread(function() {
        try {
            var SystemProperties = Java.use("android.os.SystemProperties");
            SystemProperties.set("persist.allow.a2dpsnk.conn", "enable");
            SystemProperties.set("persist.sys.qg.multibluetooth", "true");
            console.log("[+] System properties modified");
        } catch(e) {
            console.log("[-] Failed to modify properties: " + e);
        }
    });
}, 1000);
