package run.jkdev.dec4iot.jetpack.espsettings

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.MacAddress
import android.net.nsd.NsdServiceInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import run.jkdev.dec4iot.jetpack.TAG
import run.jkdev.dec4iot.jetpack.gsonmodels.EspDevicesResponse
import java.io.IOException


class EspDataViewModel : ViewModel() {
    val espDiscovered = MutableLiveData<MutableMap<String, NsdServiceInfo>>(mutableMapOf())
    val espClients = MutableLiveData<MutableMap<String, EspApi>>(mutableMapOf())

    val requestedScan = MutableLiveData<Boolean>()
    val foundAPs = MutableLiveData<WifiScanResults>()

    val wifiScanReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if(success) {
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

                val resultSuccess = WifiScanResults(true, wifiManager.scanResults)
                foundAPs.postValue(resultSuccess)
                requestedScan.postValue(false)

            } else {
                val resultFailed = WifiScanResults(false)
                foundAPs.postValue(resultFailed)
                requestedScan.postValue(false)
            }
        }

    }
}

data class WifiScanResults(val success: Boolean, val results: List<ScanResult> = listOf())

sealed class Result<out R> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()
    data class IOError(val exception: IOException) : Result<Nothing>()
}

class EspApi(
    ip: String,
    port: Int? = null
) {
    private val http = OkHttpClient()
    private val gson = Gson()
    private val baseUrl =
        if(port != null) { "http://$ip:$port/api" } else { "http://$ip/api" }

    private fun requestWithTag(): Request.Builder{
        return Request.Builder()
            .tag(TAG)
    }

    suspend fun GetDevices(): Result<EspDevicesResponse> {
        val req = requestWithTag()
            .url("$baseUrl/devices")
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            val res: Response

            try {
                res = http.newCall(req).execute()
            } catch (e: IOException) {
                return@withContext Result.IOError(e)
            }

            if (res.code != 200)
                return@withContext Result.Error(
                    Exception(
                        "Server returned other error",
                        Exception(res.body.toString())
                    ))

            val parsedBody: EspDevicesResponse
            try {
                parsedBody = gson.fromJson(res.body.toString(), EspDevicesResponse::class.java)
            } catch (e: Throwable) {
                return@withContext Result.Error(Exception(e))
            }

            return@withContext Result.Success(parsedBody)
        }
    }

    suspend fun AddDevice(device: MacAddress): Result<Boolean> {
        val body = device.toString()
            .toRequestBody(
                "text/plain".toMediaType())

        val req = requestWithTag()
            .url("$baseUrl/devices")
            .post(body)
            .build()

        return withContext(Dispatchers.IO) {
            val res: Response

            try {
                res = http.newCall(req).execute()
            } catch (e: IOException) {
                return@withContext Result.IOError(e)
            }

            when (res.code) {
                201 -> return@withContext Result.Success(true)

                400 -> return@withContext Result.Error(
                    Exception("Not a MAC address"))

                409 -> return@withContext Result.Error(
                    Exception("Device already registered"))

                else -> return@withContext Result.Error(
                    Exception(
                        "Server returned other error",
                        Exception(res.body.toString())  // more information in RequestBody
                    ))
            }
        }
    }

    suspend fun DeleteDevice(device: MacAddress): Result<Boolean> {
        val body = device.toString()
            .toRequestBody(
                "text/plain".toMediaType())

        val req = requestWithTag()
            .url("$baseUrl/devices")
            .delete(body)
            .build()

        return withContext(Dispatchers.IO) {
            val res: Response

            try {
                res = http.newCall(req).execute()
            } catch (e: IOException) {
                return@withContext Result.IOError(e)
            }

            when (res.code) {
                200 -> return@withContext Result.Success(true)

                400 -> return@withContext Result.Error(
                    Exception("Not a MAC address"))

                404 -> return@withContext Result.Error(
                    Exception("Device not found"))

                else -> return@withContext Result.Error(
                    Exception(
                        "Server returned other error",
                        Exception(res.body.toString())  // more information in RequestBody
                    ))
            }
        }
    }

    suspend fun DeleteAllDevices(ip: String): Result<Boolean> {
        val req = requestWithTag()
            .url("$baseUrl/devices")
            .delete()
            .build()

        return withContext(Dispatchers.IO) {
            val res: Response

            try {
                res = http.newCall(req).execute()
            } catch (e: IOException) {
                return@withContext Result.IOError(e)
            }

            when (res.code) {
                200 -> return@withContext Result.Success(true)

                else -> return@withContext Result.Error(
                    Exception(
                        "Server returned other error",
                        Exception(res.body.toString())  // more information in RequestBody
                    ))
            }
        }
    }

    suspend fun ChangeName(name: String): Result<Boolean> {
        val body = name.toRequestBody(
            "text/plain".toMediaType())

        val req = requestWithTag()
            .url("$baseUrl/name")
            .put(body)
            .build()

        return withContext(Dispatchers.IO) {
            val res: Response

            try {
                res = http.newCall(req).execute()
            } catch (e: IOException) {
                return@withContext Result.IOError(e)
            }

            when (res.code) {
                200 -> return@withContext Result.Success(true)

                else -> return@withContext Result.Error(
                    Exception(
                        "Server returned other error",
                        Exception(res.body.toString())  // more information in RequestBody
                    ))
            }
        }
    }
}