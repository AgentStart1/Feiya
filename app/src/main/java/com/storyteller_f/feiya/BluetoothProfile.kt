package com.storyteller_f.feiya

import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.os.Build

fun BluetoothManager.closeBluetoothProfile(
    bluetoothHidDevice: BluetoothHidDevice?
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, bluetoothHidDevice)
    }
}
