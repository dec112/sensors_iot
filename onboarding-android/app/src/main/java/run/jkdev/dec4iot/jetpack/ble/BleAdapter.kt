package run.jkdev.dec4iot.jetpack.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import run.jkdev.dec4iot.jetpack.publicApplicationContext
import run.jkdev.dec4iot.jetpack.publicMainActivityThis

class BleAdapter {
    val bluetoothManager = getSystemService(publicApplicationContext, BluetoothManager::class.java)

    private fun startBleAdapterIntent() {
        val startIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        ActivityCompat.startActivityForResult(publicMainActivityThis, startIntent, 1, Bundle())
    }

    val bluetoothAdapter: BluetoothAdapter
        get() {
            val bluetoothAdapter = if(Build.VERSION.SDK_INT >= 31) {
                bluetoothManager!!.adapter
            } else {
                BluetoothAdapter.getDefaultAdapter()
            }

            if(bluetoothAdapter === null || !bluetoothAdapter.isEnabled) {
                startBleAdapterIntent()
            }

            return bluetoothAdapter
        }

    @SuppressLint("MissingPermission")
    fun isDeviceConnected(deviceAddress: String): Boolean {
        // Check if the device is currently connected
        val connectedDevices = bluetoothManager!!.getConnectedDevices(BluetoothProfile.GATT)
        for (device in connectedDevices) {
            if(device.address == deviceAddress) { return true }
        }

        // Device is not connected
        return false
    }

}