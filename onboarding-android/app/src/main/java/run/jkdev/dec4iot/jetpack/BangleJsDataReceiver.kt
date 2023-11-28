package run.jkdev.dec4iot.jetpack

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.work.*
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.OnSuccessListener
import com.google.gson.Gson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import run.jkdev.dec4iot.jetpack.enums.LocationType
import run.jkdev.dec4iot.jetpack.gms.CellGeoloc
import run.jkdev.dec4iot.jetpack.gms.Result
import run.jkdev.dec4iot.jetpack.gsonmodels.BangleJsSendData
import run.jkdev.dec4iot.jetpack.gsonmodels.BangleJsSendDataData
import run.jkdev.dec4iot.jetpack.gsonmodels.BangleJsSendDataInfo
import run.jkdev.dec4iot.jetpack.gsonmodels.CellInfo
import run.jkdev.dec4iot.jetpack.gsonmodels.MLSResponse
import run.jkdev.dec4iot.jetpack.gsonmodels.NetworkInfo
import run.jkdev.dec4iot.jetpack.gsonmodels.SenmlField
import java.io.IOException
import java.time.Instant

class BangleJsDataReceiver : BroadcastReceiver() {
    private lateinit var idField: SenmlField

    private val gson = Gson()

    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var cellGeoloc: CellGeoloc

    private lateinit var locationTask: Task<Location>

    private lateinit var info: BangleJsSendDataInfo
    private lateinit var sensors: BangleJsSendDataData

    private lateinit var context: Context

    override fun onReceive(context: Context?, intent: Intent?) {
        if(context == null || intent == null) { return }

        if(intent.action === "me.byjkdev.dec4iot.intents.banglejs.SEND_DATA") {
            Log.i(TAG, "Intent received!!!!")

            val data = intent.getStringExtra("json_data")
            val jsonData: BangleJsSendData = gson.fromJson(data, BangleJsSendData::class.java)

            info = jsonData.info
            sensors = jsonData.data

            locationClient = LocationServices.getFusedLocationProviderClient(context)
            telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            cellGeoloc = CellGeoloc(telephonyManager)

            this.context = context

            idField = SenmlField(
                base_name = "urn:dev:mac:${formatMacAddress(info.mac_address)}:",
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
                .putString("endpoint", "https://${info.sensor_endpoint}")
                .putString("json", jsonObject.toString())
                .putString("bangleMessage", "Your emergency call succeeded.")
                .putBoolean("shouldMessageBangle", true)
                .build()

            val workRequest: WorkRequest =
                OneTimeWorkRequestBuilder<UploadWorker>()
                    .setInputData(inputData)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

            val operation = WorkManager
                .getInstance(this@BangleJsDataReceiver.context)
                .enqueue(workRequest)

            val workInfo = WorkManager
                .getInstance(this@BangleJsDataReceiver.context)
                .getWorkInfoById(workRequest.id)

            val workInfoListener = Runnable {
                val info = workInfo.get()
                Log.i(TAG, info.state.name)

            }

            operation.result.addListener(workInfoListener, context.mainExecutor)

            // MLS Location is ALWAYS sent
            val network = cellGeoloc.getNetworkInfo()
            val cells = cellGeoloc.getCells()

            mlsLocationRequest(network, cells)

            // Live Location allowed?
            if (checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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
            } else {
                // If no Live Location Permission, then try LastLocation; This doesn't require Live Location Perm
                locationTask = locationClient.lastLocation
                locationTask.addOnSuccessListener(lastLocationOnSuccessListener)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private val locationOnSuccessListener = OnSuccessListener<Location?> {
        if (it == null) {
            locationTask = locationClient.lastLocation
            locationTask.addOnSuccessListener(lastLocationOnSuccessListener)
            return@OnSuccessListener
        }

        if(Build.VERSION.SDK_INT >= 33) {
            latLongFieldsArrived(it.latitude, it.longitude, it.accuracy, it.elapsedRealtimeAgeMillis, LocationType.GPS_CURRENT_LOCATION)
        } else {
            val nsElapsedSinceSysBoot = SystemClock.elapsedRealtimeNanos()
            val nsElapsedSinceLoc = it.elapsedRealtimeNanos
            val msLocationAge = (nsElapsedSinceSysBoot - nsElapsedSinceLoc) / 1000000

            latLongFieldsArrived(it.latitude, it.longitude, it.accuracy, msLocationAge, LocationType.GPS_CURRENT_LOCATION)
        }

    }

    private val lastLocationOnSuccessListener = OnSuccessListener<Location?> {
        if(it == null) return@OnSuccessListener
        if(Build.VERSION.SDK_INT >= 33) {
            latLongFieldsArrived(it.latitude, it.longitude, it.accuracy, it.elapsedRealtimeAgeMillis, LocationType.GPS_LAST_LOCATION)
        } else {
            val nsElapsedSinceSysBoot = SystemClock.elapsedRealtimeNanos()
            val nsElapsedSinceLoc = it.elapsedRealtimeNanos
            val msLocationAge = (nsElapsedSinceSysBoot - nsElapsedSinceLoc) / 1000000

            latLongFieldsArrived(it.latitude, it.longitude, it.accuracy, msLocationAge, LocationType.GPS_LAST_LOCATION)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun mlsLocationRequest(networkInfo: NetworkInfo, cells: List<CellInfo>) {
        if (
            checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return

        GlobalScope.launch {
            when(val result = cellGeoloc.requestGeolocation(networkInfo, cells)) {
                is Result.Success<MLSResponse> -> latLongFieldsArrived(result.data.location.latitude, result.data.location.longitude, result.data.accuracy, 1, LocationType.MLS_LOCATION_VIA_CELL_TOWERS)
                is Result.Error -> {
                    Log.e(TAG, "MLS request threw", result.exception)
                }
            }
        }
    }

    private fun latLongFieldsArrived(lat: Number, long: Number, accuracy: Number, msElapsedSince: Number, source: LocationType) {
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
                unit = "lon"
            ),

            SenmlField(
                name = "accuracy",
                value = accuracy,
                unit = "m"
            ),

            SenmlField(
                name = "timestamp",
                value = msElapsedSince.toFloat() / 1000.toFloat(),
                unit = "s"
            ),

            SenmlField(
                name = "source",
                value = source.toInt()
            )
        )

        val jsonObject = gson.toJson(fields)

        val inputData = Data.Builder()
            .putString("endpoint", "https://${info.sensor_endpoint}")
            .putString("json", jsonObject.toString())
            .build()

        val workRequest: OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(inputData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()


        WorkManager
            .getInstance(this@BangleJsDataReceiver.context)
            .enqueue(workRequest)
    }

    class UploadWorker(private val appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
        private val okHttpClient = OkHttpClient()
        private var shouldMessageBangle = false
        private var bangleMessage: String? = ""

        private fun sendMessageBox(title: String, message: String) {
            val sendMsgIntent = Intent("com.banglejs.uart.tx")
            sendMsgIntent.putExtra("line", "E.showMessage(\"$message\", \"$title\");Bangle.buzz(1000, 100)")

            appContext.sendBroadcast(sendMsgIntent)
        }

        override suspend fun getForegroundInfo(): ForegroundInfo {
            Log.i(TAG, "Foreground Job Started for UploadWorker")
            return super.getForegroundInfo()
        }

        override suspend fun doWork(): Result {
            this.shouldMessageBangle = inputData.getBoolean("shouldMessageBangle", false)
            this.bangleMessage = inputData.getString("bangleMessage")

            val request = Request.Builder()
                .tag(TAG)
                .url(inputData.getString("endpoint")!!)
                .post(inputData.getString("json")!!.toRequestBody("application/json".toMediaType()))
                .build()

            return sendHttpRequest(request)
        }

        private suspend fun sendHttpRequest(request: Request): Result {
            return withContext(Dispatchers.IO) {
                try {
                    val response = okHttpClient.newCall(request).execute()
                    val workResultData = workDataOf(
                        Pair("code", response.code),
                        Pair("response", response.body!!.string())
                    )
                    response.close()

                    if(response.code in 200..299) {
                        if (shouldMessageBangle && bangleMessage != null) sendMessageBox("", bangleMessage!!)
                        return@withContext Result.success(workResultData)
                    } else {
                        sendMessageBox("FUCK", "A request failed, please call emergency services from your phone!!")
                        return@withContext Result.failure(workResultData)
                    }

                } catch (e: IOException) {
                    Log.e(TAG, "HTTP Request threw", e)
                    return@withContext Result.failure(
                        workDataOf(Pair("Exception", e))
                    )
                }
            }
        }
    }
}

fun getCurrentEpochSecond(): Double {
    return Instant.now().epochSecond.toDouble()
}

fun formatMacAddress(macAddress: String): String {
    val split = macAddress.split(":")

    val firstSegment = arrayOf(split[0], split[1], split[2]).joinToString("")
    val lastSegment = arrayOf(split[3], split[4], split[5]).joinToString("")

    return "${firstSegment}ffff${lastSegment}"
}