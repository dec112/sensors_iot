package run.jkdev.dec4iot.jetpack

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import run.jkdev.dec4iot.jetpack.ble.BleAdapter
import run.jkdev.dec4iot.jetpack.ble.Espruino
import run.jkdev.dec4iot.jetpack.interfaces.NordicUUIDs
import java.util.*

class PuckJsDetectionFragment : Fragment() {
    private val args: PuckJsDetectionFragmentArgs by navArgs()

    private var sensorId: String = ""
    private var endpoint: String = ""

    private val le = BleAdapter()
    private val espruino = Espruino(le)

    private val foundDevicesList = mutableSetOf<BluetoothDevice>()
    private val foundDevicesLiveData = MutableLiveData<MutableSet<BluetoothDevice>>()

    private val deviceButtonMutableMap = mutableMapOf<Button, BluetoothDevice>()

    private val connectedDevices = mutableSetOf<BluetoothGatt>()
    private val deviceService = mutableMapOf<BluetoothGatt, BluetoothGattService?>()

    private var selection: BluetoothGatt? = null
    private val lastSelection = MutableLiveData<BluetoothGatt?>(null)

    private var buttonEnabled = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_puck_js_detection, container, false)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        this.sensorId = args.sensorId.toString()
        this.endpoint = args.endpoint

        view.findViewById<Button>(R.id.continueButton).setOnClickListener(continueButtonListener)

        espruino.addCallback(this.scanCallback)
        espruino.startScanning()

        foundDevicesLiveData.observeForever { set ->
            val layout = view.findViewById<LinearLayout>(R.id.foundDevicesButtons)

            set.forEach {
                if(layout.findViewById<Button>(it.hashCode()) == null) {
                    val btn = createNewDeviceButton(it.name, deviceButtonOnClickListener, it.hashCode())

                    deviceButtonMutableMap[btn] = it
                    layout.addView(btn)
                }
            }
        }

        lastSelection.observeForever {
            if(it != null && le.isDeviceConnected(it.device.address) && deviceService[it] != null) {
                val service = deviceService[it]
                val characteristic = service!!.getCharacteristic(NordicUUIDs.TX_CHARACTERISTIC)

                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    characteristic.value = espruino.discoveredCmdPuckJs
                    it.writeCharacteristic(characteristic)
                } else {
                    it.writeCharacteristic(characteristic, espruino.discoveredCmdPuckJs, WRITE_TYPE_NO_RESPONSE)
                }

                it.disconnect()
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private val continueButtonListener = OnClickListener {
        if(selection == null) {
            Toast.makeText(requireActivity().applicationContext, R.string.no_device_selected_stop, Toast.LENGTH_LONG).show()
            MainActivity.vibrator.vibrate(VibrationEffect.createOneShot(1000, 100))
            return@OnClickListener
        }
        if(!buttonEnabled) { return@OnClickListener }

        espruino.stopScanning()
        connectedDevices.forEach {
            if(deviceService[it] != null) {
                val service = deviceService[it]
                val characteristic = service!!.getCharacteristic(NordicUUIDs.TX_CHARACTERISTIC)

                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    characteristic.value = espruino.discoveredCmdPuckJs
                    it.writeCharacteristic(characteristic)
                } else {
                    it.writeCharacteristic(characteristic, espruino.discoveredCmdPuckJs, WRITE_TYPE_NO_RESPONSE)
                }
            }
            it.disconnect()
        }
        connectedDevices.clear()

        val act = PuckJsDetectionFragmentDirections.actionPuckJsFragmentToPuckJsWritingFragment(sensorId, endpoint, selection!!.device.address, selection!!.device.name)
        findNavController().navigate(act)
    }

    private fun createNewDeviceButton(text: String, onClickListener: OnClickListener, id: Int): Button {
        val btn = Button(requireActivity().applicationContext)

        btn.setOnClickListener(onClickListener)
        btn.text = text
        btn.id = id

        return btn
    }

    @SuppressLint("MissingPermission")
    private val deviceButtonOnClickListener = OnClickListener {
        if(!buttonEnabled) { return@OnClickListener }
        buttonEnabled = false

        val btn = it.findViewById<Button>(it.id)

        val device = deviceButtonMutableMap[btn]
        if(!le.isDeviceConnected(device!!.address)) {
            device.connectGatt(requireActivity().applicationContext, false, gattConnectionCallback)

            val infoText = requireView().findViewById<TextView>(R.id.infoText_puckJs)
            infoText.textSize = 26F
            infoText.text =
                getString(R.string.selected_device, device.name)
        }
    }


    @SuppressLint("MissingPermission")
    private val gattConnectionCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if(newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevices.add(gatt!!)

                if(selection != null) {
                    lastSelection.postValue(selection)
                }
                selection = gatt

                gatt.discoverServices()
            }
            if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevices.remove(gatt)
                deviceService.remove(gatt)
            }
        }

        @Suppress("DEPRECATION")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)

            if(status == BluetoothGatt.GATT_SUCCESS) {
                buttonEnabled = true

                val service = gatt!!.getService(NordicUUIDs.SERVICE)
                deviceService[gatt] = service

                val characteristic = service!!.getCharacteristic(NordicUUIDs.TX_CHARACTERISTIC)

                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    characteristic.value = espruino.discoveryCmdPuckJs
                    gatt.writeCharacteristic(characteristic)
                } else {
                    gatt.writeCharacteristic(characteristic, espruino.discoveryCmdPuckJs, WRITE_TYPE_NO_RESPONSE)
                }
            } else if(status == BluetoothGatt.GATT_FAILURE) {
                Toast.makeText(requireActivity().applicationContext, R.string.something_went_wrong, Toast.LENGTH_LONG).show()
                MainActivity.vibrator.vibrate(VibrationEffect.createOneShot(1000, 100))
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            try {
                if(result!!.device !in foundDevicesList) {
                    foundDevicesList.add(result.device)
                    foundDevicesLiveData.postValue(foundDevicesList)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during LeScanCallback", e)
            }

        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.w(TAG, "No device found.")
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override fun onPause() {
        super.onPause()

        espruino.stopScanning()
        connectedDevices.forEach {
            if(deviceService[it] != null) {
                val service = deviceService[it]
                val characteristic = service!!.getCharacteristic(NordicUUIDs.TX_CHARACTERISTIC)

                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    characteristic.value = espruino.discoveredCmdPuckJs
                    it.writeCharacteristic(characteristic)
                } else {
                    it.writeCharacteristic(characteristic, espruino.discoveredCmdPuckJs, WRITE_TYPE_NO_RESPONSE)
                }

                it.disconnect()
            }
        }
        connectedDevices.clear()

        val infoText = requireView().findViewById<TextView>(R.id.infoText_puckJs)
        infoText.textSize = 18F
        infoText.text =
            getString(R.string.choose_puckjs)
    }

    override fun onResume() {
        super.onResume()

        espruino.startScanning()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()

        espruino.stopScanning()
        connectedDevices.forEach {
            if(deviceService[it] != null) {
                val service = deviceService[it]
                val characteristic = service!!.getCharacteristic(NordicUUIDs.TX_CHARACTERISTIC)

                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    characteristic.value = espruino.discoveredCmdPuckJs
                    it.writeCharacteristic(characteristic)
                } else {
                    it.writeCharacteristic(characteristic, espruino.discoveredCmdPuckJs, WRITE_TYPE_NO_RESPONSE)
                }

                it.disconnect()
            }
        }
        connectedDevices.clear()
    }
}