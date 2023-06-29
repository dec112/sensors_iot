package run.jkdev.dec4iot.jetpack

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.*
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.mlkit.vision.barcode.BarcodeScanning
import run.jkdev.dec4iot.jetpack.databinding.ActivityMainBinding
import run.jkdev.dec4iot.jetpack.gms.GMSModuleRequestClient

lateinit var publicApplicationContext: Context
lateinit var publicMainActivityThis: MainActivity
lateinit var publicVibrator: Vibrator
const val TAG = "DEC4IOTJETPACK"

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private var nfcAdapter: NfcAdapter? = null
    private var nfcDataViewModel: NfcDataViewModel? = null

    private var vibrator: Vibrator? = null

    private var powerManager: PowerManager? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window,false)
        super.onCreate(savedInstanceState)

        publicApplicationContext = applicationContext
        publicMainActivityThis = this

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        supportActionBar!!.setDisplayHomeAsUpEnabled(false)

        this.nfcAdapter = getDefaultAdapter(this)
        this.nfcDataViewModel = ViewModelProvider(this)[NfcDataViewModel::class.java]

        this.vibrator = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        this.powerManager = getSystemService(POWER_SERVICE) as PowerManager

        publicVibrator = this.vibrator!!

        try {
            nfcDataViewModel?.shouldBeListening!!.observeForever { if(it == true) { this@MainActivity.startNfcAdapter() } }
        } catch (ex: Throwable) {
            Log.e(TAG, "Error Activating NfcAdapter", ex)
        }
    }

    private fun startNfcAdapter() {
        if (nfcAdapter != null) {
            val options = Bundle()
            // Work around for some broken Nfc firmware implementations that poll the card too fast
            options.putInt(EXTRA_READER_PRESENCE_CHECK_DELAY, 100)

            // Enable ReaderMode for all types of card and disable platform sounds
            nfcAdapter?.enableReaderMode(this,
                {
                    try {
                        this.nfcDataViewModel?.nfcData?.postValue(it)
                        this.vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                    } catch (ex: Throwable) {
                        Log.e(TAG, "Something went wrong here.", ex)
                    }
                },
                FLAG_READER_NFC_A or
                        FLAG_READER_NFC_B or
                        FLAG_READER_NFC_F or
                        FLAG_READER_NFC_V or
                        FLAG_READER_NFC_BARCODE or
                        FLAG_READER_NO_PLATFORM_SOUNDS,
                options
            )

            try {
                nfcDataViewModel?.shouldBeListening!!.observeForever { if (!it) { stopNfcAdapter() } }
            } catch (ex: Throwable) {
                Log.e(TAG, "Error deactivating NfcReader", ex)
            }
        }
    }

    private fun stopNfcAdapter() {
        try {
            nfcAdapter!!.disableReaderMode(this@MainActivity)

        } catch (ex: Throwable) { Log.e(TAG, "Error deactivating NfcReader", ex) }
    }

    override fun onResume() {
        super.onResume()

        try {
            if(nfcDataViewModel?.shouldBeListening!!.value!!) { this.startNfcAdapter() }
        } catch (ex: Throwable) {
            Log.e(TAG, "Error activating NfcReader", ex)
        }

    }

    override fun onPause() {
        super.onPause()

        stopNfcAdapter()
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
            R.id.action_check_permissions -> checkPermissions()
            R.id.action_check_backgroundloc -> checkBackgroundLocationPermission()
            R.id.action_check_batopt -> checkBatteryOptimizations()
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return false
    }

    private fun checkPermissions(): Boolean {
        if(allPermGranted()) {
            Toast.makeText(applicationContext, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUIRED_PERMISSION_CODE)
        }

        return true
    }

    @SuppressLint("InlinedApi")  // API level gets checked with `backgroundLocPermGranted()`
    private fun checkBackgroundLocationPermission(): Boolean {
        if(backgroundLocPermGranted()) {
            Toast.makeText(applicationContext, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
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
        val disabled = powerManager!!.isIgnoringBatteryOptimizations(packageName)
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

fun checkBarcodeScanningAvailable(): Boolean {
    val optMod = BarcodeScanning.getClient()
    GMSModuleRequestClient().checkAvailability(optMod) {
        if(it.areModulesAvailable()) {
            Toast.makeText(publicApplicationContext, R.string.modules_available_toast, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(publicApplicationContext, R.string.modules_unavailable_toast, Toast.LENGTH_SHORT).show()
        }
    }

    return true
}

private val REQUIRED_PERMISSIONS = if(Build.VERSION.SDK_INT >= 31) {
    mutableListOf (
        Manifest.permission.CAMERA,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NFC,
        Manifest.permission.INTERNET,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    ).toTypedArray()
} else {
    mutableListOf (
        Manifest.permission.CAMERA,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NFC,
        Manifest.permission.INTERNET,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    ).toTypedArray()
}

private const val REQUIRED_PERMISSION_CODE = 10

fun allPermGranted() = REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(publicApplicationContext, it) == PackageManager.PERMISSION_GRANTED }
fun backgroundLocPermGranted() = if (Build.VERSION.SDK_INT >= 29) ContextCompat.checkSelfPermission(publicApplicationContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED else true
