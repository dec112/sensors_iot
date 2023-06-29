package run.jkdev.dec4iot.jetpack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.work.*
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import run.jkdev.dec4iot.jetpack.gsonmodels.BangleJsSendData
import run.jkdev.dec4iot.jetpack.gsonmodels.BangleJsSendDataData
import run.jkdev.dec4iot.jetpack.gsonmodels.BangleJsSendDataInfo
import run.jkdev.dec4iot.jetpack.gsonmodels.SenmlField
import java.io.IOException
import java.time.Instant

class BangleJsDataReceiver : BroadcastReceiver() {
    private lateinit var idField: SenmlField

    private val gson = Gson()

    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationTask: Task<Location>

    private lateinit var info: BangleJsSendDataInfo
    private lateinit var sensors: BangleJsSendDataData

    private lateinit var context: Context

    override fun onReceive(context: Context?, intent: Intent?) {
        if(context == null || intent == null) { return }

        if(intent.action === "me.byjkdev.dec4iot.intents.banglejs.SEND_DATA") {
            val data = intent.getStringExtra("json_data")
            val jsonData: BangleJsSendData = gson.fromJson(data, BangleJsSendData::class.java)

            info = jsonData.info
            sensors = jsonData.data

            locationClient = LocationServices.getFusedLocationProviderClient(context)

            this.context = context

            if (checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return
            }

            idField = SenmlField(
                base_name = "urn:dev:mac:${formatMacAddress(info.mac_address)}",
                base_time = getCurrentEpochSecond(),

                name = "identifier",
                value = info.sensor_id
            )

            if(info.bpm_only) {
                val fields = listOf(
                    idField,
                    SenmlField(
                        name = "bpm",
                        value = sensors.hrm!!.bpm,
                        unit = "beat/min"
                    ),

                    SenmlField(
                        name = "bpmConfidence",
                        value = sensors.hrm!!.bpmConfidence
                    ),
                )

                val jsonObject = gson.toJson(fields)

                val inputData = Data.Builder()
                    .putString("endpoint", "https://${info.sensor_endpoint}")
                    .putString("json", jsonObject.toString())
                    .build()

                val workRequest: WorkRequest =
                    OneTimeWorkRequestBuilder<UploadWorker>()
                        .setInputData(inputData)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()

                WorkManager
                    .getInstance(context)
                    .enqueue(workRequest)

                return
            }

            val fields = listOf(
                idField,

                SenmlField(
                    name = "batt",
                    unit = "%EL",
                    value = sensors.bat
                ),

                SenmlField(
                    name = "heading",
                    value = sensors.com!!.heading
                ),

                SenmlField(
                    name = "temperature",
                    value = sensors.bar!!.temperature
                ),

                SenmlField(
                    name = "pressure",
                    value = sensors.bar!!.pressure,
                    unit = "hPa"
                ),

                SenmlField(
                    name = "altitude",
                    value = sensors.bar!!.altitude,
                    unit = "m"
                ),

                SenmlField(
                    name = "steps",
                    value = sensors.hrm!!.steps,
                    unit = "counter"
                ),

                SenmlField(
                    name = "manually_triggered",
                    boolean_value = info.manually_triggered,
                )
            )

            val jsonObject = gson.toJson(fields)

            val inputData = Data.Builder()
                .putString("endpoint", "https://www.${info.sensor_endpoint}")
                .putString("json", jsonObject.toString())
                .build()

            val workRequest: WorkRequest =
                OneTimeWorkRequestBuilder<UploadWorker>()
                    .setInputData(inputData)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

            WorkManager
                .getInstance(this@BangleJsDataReceiver.context)
                .enqueue(workRequest)

            val locationRequest = CurrentLocationRequest.Builder()
                .setGranularity(Granularity.GRANULARITY_FINE)
                .setMaxUpdateAgeMillis(0)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(Long.MAX_VALUE)
                .build()


            locationTask = locationClient.getCurrentLocation(locationRequest, null)
            locationTask.addOnFailureListener {
                locationTask = locationClient.lastLocation
                locationTask.addOnSuccessListener(lastLocationOnSuccessListener)
            }
            locationTask.addOnSuccessListener(locationOnSuccessListener)

        }
    }

    @SuppressLint("MissingPermission")
    private val locationOnSuccessListener = OnSuccessListener<Location> {
        if (it == null) {
            locationTask = locationClient.lastLocation
            locationTask.addOnSuccessListener(lastLocationOnSuccessListener)
            return@OnSuccessListener
        }

        latLongFieldsArrived(it.latitude, it.longitude)

    }

    private val lastLocationOnSuccessListener = OnSuccessListener<Location> {
        if(it == null) {
            latLongFieldsArrived(0.0, 0.0)
            return@OnSuccessListener
        }

        latLongFieldsArrived(it.latitude, it.longitude)
    }

    private fun latLongFieldsArrived(lat: Number, long: Number) {
        val idFieldNew = idField
        idFieldNew.base_time = getCurrentEpochSecond()

        val fields = listOf(
            idFieldNew,
            SenmlField(
                name = "latitude",
                value = lat,
                unit = "lat"
            ),

            SenmlField(
                name = "longitude",
                value = long,
                unit = "long"
            )
        )

        val jsonObject = gson.toJson(fields)

        val inputData = Data.Builder()
            .putString("endpoint", "https://www.${info.sensor_endpoint}")
            .putString("json", jsonObject.toString())
            .build()

        val workRequest: WorkRequest =
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(inputData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

        WorkManager
            .getInstance(this@BangleJsDataReceiver.context)
            .enqueue(workRequest)
    }

    class UploadWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
        private val okHttpClient = OkHttpClient()

        override fun getForegroundInfo(): ForegroundInfo {
            Log.i(TAG, "Foreground Job Started for UploadWorker")
            return super.getForegroundInfo()
        }

        override fun doWork(): Result {
            val request = Request.Builder()
                .tag(TAG)
                .url(inputData.getString("endpoint")!!)
                .post(inputData.getString("json")!!.toRequestBody("application/json".toMediaType()))
                .build()

            val http = sendHttpRequest(request)
            return if(http is Result) {
                http
            } else {
                Result.success()
            }
        }

        private fun sendHttpRequest(request: Request): Result? {
            try {
                okHttpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "HTTP Request threw:\n${e.stackTrace}")
                        throw Exception("HTTP Request threw", e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        Log.i(TAG, "HTTP Request returned response:\n${response}")
                        Log.i(TAG, "type: ${response.request.method}")
                        Log.i(TAG, "code: ${response.code}")
                        Log.i(TAG, "headers: ${response.headers}")
                        Log.i(TAG, "data ${response.body.toString()}")
                        response.close()
                    }

                })
            } catch (e: Exception) {
                return Result.failure()
            }

            return null
        }

    }
}

fun getCurrentEpochSecond(): String {
    val epochSecond = Instant.now().epochSecond.toDouble()

    return "%1.9e".format(epochSecond)
}

fun formatMacAddress(macAddress: String): String {
    val split = macAddress.split(":")

    val firstSegment = arrayOf(split[0], split[1], split[2]).joinToString("")
    val lastSegment = arrayOf(split[3], split[4], split[5]).joinToString("")

    return "${firstSegment}ffff${lastSegment}"
}