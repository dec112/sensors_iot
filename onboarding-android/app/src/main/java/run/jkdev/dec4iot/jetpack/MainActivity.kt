package run.jkdev.dec4iot.jetpack

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.*
import android.nfc.tech.Ndef
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScanning
import okio.ByteString.Companion.decodeBase64
import run.jkdev.dec4iot.jetpack.databinding.ActivityMainBinding
import run.jkdev.dec4iot.jetpack.espsettings.EspApi
import run.jkdev.dec4iot.jetpack.espsettings.EspDataViewModel
import run.jkdev.dec4iot.jetpack.gms.GMSModuleRequestClient
import run.jkdev.dec4iot.jetpack.gsonmodels.OnboardingQr

const val TAG = "DEC4IOTJETPACK"

var publicApplicationContext: Context? = null

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private lateinit var powerManager: PowerManager
    private lateinit var nsdManager: NsdManager

    private lateinit var nfcAdapter: NfcAdapter

    private lateinit var pjsData: PuckJsDataViewModel
    private lateinit var espData: EspDataViewModel

    companion object {
        val BangleJsInstalled = MutableLiveData(false)
        val AppLinkData = MutableLiveData<OnboardingQr?>(null)

        var nfcSupported = false


        lateinit var vibrator: Vibrator
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window,false)
        super.onCreate(savedInstanceState)

        publicApplicationContext = applicationContext

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        supportActionBar!!.setDisplayHomeAsUpEnabled(false)

        vibrator = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        nsdManager = getSystemService(NSD_SERVICE) as NsdManager

        try {
            nfcAdapter = getDefaultAdapter(applicationContext)
            nfcSupported = true
        } catch (e: Exception) {
            Log.e(TAG, "nfc not supported, cannot configure esp32", e)
        }

        pjsData = ViewModelProvider(this)[PuckJsDataViewModel::class.java]
        espData = ViewModelProvider(this)[EspDataViewModel::class.java]

        nsdManager.discoverServices("_http._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        if (intent.action == Intent.ACTION_VIEW && intent.data!!.scheme == "https") {
            if(intent.data!!.host == "dec4iot.data-container.net")
                parseAppLink(intent.dataString!!)
        }


    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(service: NsdServiceInfo) {
            when {
                service.serviceName.lowercase().contains("dec4iot") -> nsdManager.resolveService(service, resolveListener)
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            val currentlyFoundServices = espData.espDiscovered.value!! // why does this have to have "!!"? i have no clue.
            val apiClients = espData.espClients.value!! // same here
            currentlyFoundServices.remove(service.serviceName)
            apiClients.remove(service.serviceName)

            espData.espDiscovered.postValue(currentlyFoundServices)
            espData.espClients.postValue(apiClients)

            Log.e(TAG, "service lost: $service")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed! Error code $errorCode")
            nsdManager.stopServiceDiscovery(this)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed! Error code $errorCode")
            nsdManager.stopServiceDiscovery(this)
        }

        override fun onDiscoveryStarted(p0: String?) {
            Log.i(TAG, "NSD discovery started")
        }
        override fun onDiscoveryStopped(p0: String?) {
            Log.i(TAG, "NSD discovery stopped")
        }
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onServiceResolved(service: NsdServiceInfo) {
            Log.d(TAG, "service found and resolved $service")

            val currentlyFoundServices = espData.espDiscovered.value!! // why does this have to have "!!"? i have no clue.
            val apiClients = espData.espClients.value!!                // same here
            currentlyFoundServices[service.serviceName] = service
            apiClients[service.serviceName] = EspApi(service.host.toString().substring(1), service.port)

            espData.espDiscovered.postValue(currentlyFoundServices)
            espData.espClients.postValue(apiClients)

            val attr = service.attributes
            val attrAsString: MutableMap<String, String> = mutableMapOf()
            attr.forEach {
                val decoded = it.value.toString(Charsets.UTF_8)
                attrAsString[it.key] = decoded
            }
        }

        override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "Couldn't resolve ${service.serviceName}. Error code $errorCode")
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onResume() {
        super.onResume()

        checkPermissions(true)
        checkBackgroundLocationPermission(true)

        if(nfcSupported) {

            val intentSingleTop = Intent(this, javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            val ndefFilter = IntentFilter(ACTION_NDEF_DISCOVERED).apply {
                addDataScheme("jkdev-dec4iot")
            }

            val techList = arrayOf(arrayOf<String>(Ndef::class.java.name))

            val pendingIntent = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.getActivity(
                    this, 0,
                    intentSingleTop, PendingIntent.FLAG_MUTABLE
                )
            } else {
                PendingIntent.getActivity(
                    this, 0,
                    intentSingleTop, 0
                )
            }

            nfcAdapter.enableForegroundDispatch(this, pendingIntent, arrayOf(ndefFilter), techList)
        }
    }

    override fun onPause() {
        super.onPause()

        if(nfcSupported) nfcAdapter.disableForegroundDispatch(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        publicApplicationContext = null
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (intent == null) return

        if (
            intent.action == ACTION_NDEF_DISCOVERED &&
            intent.data != null &&
            intent.data!!.host == "puckjs" &&
            intent.data!!.query != null
        ) {
            pjsData.nfc.value =
                parseNfcQuery(intent.data!!.query.toString())
        }

        if (intent.action == "me.byjkdev.dec4iot.intents.banglejs.SETUP")
            BangleJsInstalled.value = true

        if (intent.action == Intent.ACTION_VIEW && intent.data!!.scheme == "https") {
            if(intent.data!!.host == "dec4iot.data-container.net" && intent.data!!.path == "/sensors/qr")
                parseAppLink(intent.dataString!!)
        }
    }

    private fun parseAppLink(original_data: String) {
        val b64String = original_data.split("?d=")[1]
        val b64Decoded = b64String.decodeBase64()
        val data: String = b64Decoded.toString()
            .removePrefix("[text=")
            .removeSuffix("]")

        try {
            AppLinkData.value = Gson().fromJson(data, OnboardingQr::class.java)
        } catch (e: Throwable) {
            Log.e(TAG, "Could not parse Onboarding QR", e)

            val msg = e.message.toString()
            if (msg.contains("Expected BEGIN_OBJECT"))
                Log.e(TAG, "Unsupported QR code", e)

            Log.e(TAG, "Error while parsing QR code", e)
            return
        }
    }

    private fun parseNfcQuery(query: String): Map<String, String> {
        if ("&" !in query) {
            return if ("=" !in query) {
                mapOf(
                    Pair(query, "true")
                )
            } else {
                val kv = query.split("=")
                mapOf(
                    Pair(kv[0], kv[1])
                )
            }
        }

        val returnKVs: MutableMap<String, String> = mutableMapOf()

        val parts = query.split("&")
        parts.forEach {
            if ("=" !in it) {
                returnKVs[it] = "true"
            } else {
                val kv = it.split("=")
                returnKVs[kv[0]] = kv[1]
            }
        }

        return returnKVs
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_check_modules -> checkBarcodeScanningAvailable()
            R.id.action_check_permissions -> checkPermissions(false)
            R.id.action_check_backgroundloc -> checkBackgroundLocationPermission(false)
            R.id.action_check_batopt -> checkBatteryOptimizations()
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkBarcodeScanningAvailable(): Boolean {
        val optMod = BarcodeScanning.getClient()
        GMSModuleRequestClient().checkAvailability(optMod) {
            if(it.areModulesAvailable()) {
                Toast.makeText(applicationContext, R.string.modules_available_toast, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, R.string.modules_unavailable_toast, Toast.LENGTH_SHORT).show()
            }
        }

        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        return false
    }

    private fun checkPermissions(silent: Boolean): Boolean {
        if(allPermGranted(applicationContext)) {
            if(!silent) Toast.makeText(applicationContext, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUIRED_PERMISSION_CODE)
        }

        return true
    }

    @SuppressLint("InlinedApi")  // API level gets checked with `backgroundLocPermGranted()`
    private fun checkBackgroundLocationPermission(silent: Boolean): Boolean {
        if(backgroundLocPermGranted(applicationContext)) {
            if(!silent) Toast.makeText(applicationContext, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
        } else {
            if(shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                Toast.makeText(applicationContext, R.string.why_we_need_perm_bgloc, Toast.LENGTH_LONG).show()
            }
            requestPermissions(mutableListOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION).toTypedArray(), 10)
        }

        return true
    }

    @SuppressLint("BatteryLife")
    private fun checkBatteryOptimizations(): Boolean {
        val disabled = powerManager.isIgnoringBatteryOptimizations(packageName)
        return if(disabled) {
            Toast.makeText(applicationContext, R.string.bat_optim_off, Toast.LENGTH_LONG).show()
            true
        } else {
            val disableIntent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${packageName}")
            )
            startActivity(disableIntent)

            false
        }
    }
}

private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= 31) {
    listOf (
        Manifest.permission.CAMERA,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NFC,
        Manifest.permission.INTERNET,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.READ_PHONE_STATE
    ).toTypedArray()
} else {
    listOf (
        Manifest.permission.CAMERA,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NFC,
        Manifest.permission.INTERNET,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.READ_PHONE_STATE
    ).toTypedArray()
}

private const val REQUIRED_PERMISSION_CODE = 10

fun allPermGranted(context: Context) = REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
fun backgroundLocPermGranted(context: Context) = if (Build.VERSION.SDK_INT >= 29) ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED else true
