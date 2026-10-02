package com.storyteller_f.feiya

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

sealed interface BluetoothAction {
    object NotSupport : BluetoothAction

    class Done(val result: Boolean, val message: String) : BluetoothAction

    object PermissionDenied : BluetoothAction
}

private fun Boolean.done(): BluetoothAction.Done {
    return BluetoothAction.Done(this, "")
}

fun Context.alreadyBondedDevices(bluetoothManager: BluetoothManager): Set<BluetoothDevice> {
    val adapter = bluetoothManager.adapter
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            )
                adapter?.bondedDevices.orEmpty().toSet()
            else setOf()
        }

        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_ADMIN
        ) == PackageManager.PERMISSION_GRANTED -> {
            adapter?.bondedDevices.orEmpty().toSet()
        }

        else -> setOf()
    }
}


fun Context.permissionOk(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_ADMIN
        ) == PackageManager.PERMISSION_GRANTED
    }
}

fun Context.readHidDescriptor(): ByteArray {
    return assets.open("hid").use { stream ->
        stream.bufferedReader().lineSequence().flatMap { line ->
            val lastCommas = line.lastIndexOf(", ")
            line.substring(0, lastCommas).split(", ").map {
                it.substring(2).toInt(16).toByte()
            }
        }.toList().toByteArray()
    }
}

fun Context.registerAsHid(
    bluetoothHidDevice: BluetoothHidDevice,
    registerCallback: BluetoothHidDevice.Callback
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val sdp = BluetoothHidDeviceAppSdpSettings(
            getString(R.string.hid_app_name),
            "auto input address",
            "storytellerF",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            readHidDescriptor()
        )
        bluetoothHidDevice.registerApp(
            sdp,
            null,
            null,
            ContextCompat.getMainExecutor(this),
            registerCallback
        )
    }
}

fun Context.unRegisterAsHid(hidDevice: BluetoothHidDevice?) {
    hidDevice ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        )
            hidDevice.unregisterApp()
        else {
            return
        }
    }
}

fun Context.connectDevice(
    hidDevice: BluetoothHidDevice?,
    bondDevices: Set<BluetoothDevice>,
    address: String
): BluetoothAction {
    hidDevice ?: return BluetoothAction.Done(false, "未连接HID")
    val device = bondDevices.firstOrNull {
        it.address == address
    } ?: return BluetoothAction.Done(false, "设备$address 不存在")
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) hidDevice.connect(device).done() else BluetoothAction.PermissionDenied
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_ADMIN
                ) == PackageManager.PERMISSION_GRANTED
            ) hidDevice.connect(device).done() else BluetoothAction.PermissionDenied
        }

        else -> BluetoothAction.NotSupport
    }
}

fun Context.disconnectDevice(
    hidDevice: BluetoothHidDevice?,
    bondDevices: Set<BluetoothDevice>,
    address: String
): Boolean {
    hidDevice ?: return false
    val device = bondDevices.firstOrNull {
        it.address == address
    } ?: return false
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) hidDevice.disconnect(device) else false
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_ADMIN
                ) == PackageManager.PERMISSION_GRANTED
            ) hidDevice.disconnect(device) else false
        }
        else -> false
    }
}

fun Context.isBonded(device: BluetoothDevice): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        )
            device.bondState == BluetoothDevice.BOND_BONDED
        else {
            return false
        }
    } else if (ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_ADMIN
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        device.bondState == BluetoothDevice.BOND_BONDED
    } else {
        throw Exception("impossible")
    }
}
