package run.jkdev.dec4iot.jetpack.qrcode

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.OnSuccessListener
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.common.Barcode
import run.jkdev.dec4iot.jetpack.R
import run.jkdev.dec4iot.jetpack.TAG
import run.jkdev.dec4iot.jetpack.gsonmodels.OnboardingQr
import run.jkdev.dec4iot.jetpack.interfaces.QrResult

class QrScanningActivity : AppCompatActivity() {
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private val gson = Gson()

    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanning)

        cameraProviderFuture = ProcessCameraProvider.getInstance(applicationContext)
        cameraProviderFuture.addListener({ setupCameraProvider() }, ContextCompat.getMainExecutor(applicationContext))
    }

    private fun setupCameraProvider() {
        val provider = cameraProviderFuture.get()
        val surface: PreviewView = findViewById(R.id.qr_preview)

        val preview = PreviewUseCase(surface.surfaceProvider).case
        val analysis = AnalysisUseCase(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).case {
            imageProxy -> Scanner().processImageProxy(imageProxy, onQrSuccess)
        }

        try {
            provider.unbindAll()

            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA,
                preview, analysis
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to bind camera", e)

            finishWithError(e)
            return
        }
    }

    private val onQrSuccess = OnSuccessListener<MutableList<Barcode>> {
        if(it.isEmpty()) { return@OnSuccessListener }
        if(done) { return@OnSuccessListener }

        val qr = it.getOrNull(0) ?: return@OnSuccessListener

        val rawString = qr.rawValue.toString()
        lateinit var parsedData: OnboardingQr

        try {
            parsedData = gson.fromJson(rawString, OnboardingQr::class.java)
        } catch (e: Throwable) {
            Log.e(TAG, "Could not parse Onboarding QR", e)

            val msg = e.message.toString()
            if (msg.contains("Expected BEGIN_OBJECT"))
                finishWithUnsupported()

            finishWithError(e)
            return@OnSuccessListener
        }

        if (parsedData.sensorId == null) return@OnSuccessListener finishWithUnsupported()  // If no sensorId or endpoint exists,
        if (parsedData.endpoint == null) return@OnSuccessListener finishWithUnsupported()  // this was not created by dec4iot

        done = true

        val result = Intent()

        val bundle = Bundle()
        bundle.putInt("sensor_id", parsedData.sensorId!!.toInt())
        bundle.putString("endpoint", parsedData.endpoint)

        result.putExtras(bundle)

        setResult(QrResult.RESULT_OK, result)
        finish()
    }

    private fun finishWithError(e: Throwable) {
        val result = Intent()

        val bundle = Bundle()
        bundle.putString("error", e.toString())
        result.putExtras(bundle)

        setResult(QrResult.RESULT_KO, result)
        finish()
    }

    private fun finishWithUnsupported() {
        setResult(QrResult.RESULT_NO_DEC4IOT)
        finish()
    }
}