package run.jkdev.dec4iot.jetpack.ble

import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.util.Log
import android.widget.Toast
import run.jkdev.dec4iot.jetpack.*
import run.jkdev.dec4iot.jetpack.interfaces.NordicUUIDs
import java.util.*

class Espruino(leAdapter: BleAdapter) {
    private var leScanner: BluetoothLeScanner

    val discoveryCmdPuckJs = "discovery()\n".toByteArray(Charsets.UTF_8)
    val discoveredCmdPuckJs = "discovered()\n".toByteArray(Charsets.UTF_8)
    fun writeConfigCmdPuckJs(fileName: String, id: String, endpoint: String, sensorUpdateInterval: String): ByteArray {
        return "writeConfig('$fileName','$id','$endpoint','$sensorUpdateInterval')\n".toByteArray(Charsets.UTF_8)
    }

    init {
        leScanner = leAdapter.bluetoothAdapter.bluetoothLeScanner
    }

    private var scanning = false

    private val scanCallbacks = mutableListOf<ScanCallback>()

    private val scanSettings = ScanSettings.Builder()
        .build()

    private val scanFilter = ScanFilter.Builder()
        .setServiceUuid(ParcelUuid(NordicUUIDs.SERVICE))

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            scanCallbacks.forEach { it.onScanResult(callbackType, result) }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            scanCallbacks.forEach { it.onScanFailed(errorCode) }
        }
    }

    fun addCallback(scanCallback: ScanCallback) {
        scanCallbacks.add(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun startScanning(macAddress: String? = null) {
        if (!allPermGranted(publicApplicationContext!!)) {
            Toast.makeText(publicApplicationContext, R.string.permission_not_granted_no_continue, Toast.LENGTH_LONG).show()
            MainActivity.vibrator.vibrate(VibrationEffect.createOneShot(1000, 100))
            return
        }
        if(scanning) { return }

        val localScanFilter = scanFilter
        if(macAddress != null) { localScanFilter.setDeviceAddress(macAddress) }

        leScanner.startScan(listOf<ScanFilter>(localScanFilter.build()), scanSettings, scanCallback)
        scanning = true
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!allPermGranted(publicApplicationContext!!)) {
            Log.e(TAG, "Something went terribly wrong!")
            return
        }
        if(!scanning) { return }

        leScanner.stopScan(scanCallback)
        scanning = false
    }


}