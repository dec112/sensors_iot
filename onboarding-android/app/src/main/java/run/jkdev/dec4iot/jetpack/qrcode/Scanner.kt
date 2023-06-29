package run.jkdev.dec4iot.jetpack.qrcode

import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.OnSuccessListener
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import run.jkdev.dec4iot.jetpack.TAG

class Scanner {
    private val instance: BarcodeScanner

    init {
        val bcOpts = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE
            )
            .build()

        this.instance = BarcodeScanning.getClient(bcOpts)
    }

    fun process(image: InputImage, success: OnSuccessListener<MutableList<Barcode>>) {
        // Process Image and ship it off!

        this.instance.process(image)
            .addOnSuccessListener(success)
            .addOnFailureListener { Log.e(TAG, "Ml.Kit: Error Processing Image", it) }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    fun processImageProxy(imageProxy: ImageProxy, success: OnSuccessListener<MutableList<Barcode>>) {
        // Turn ImageProxy into InputImage, process it and ship it off!

        val img = imageProxy.image
        if(img !== null) {
            val image = InputImage.fromMediaImage(img, imageProxy.imageInfo.rotationDegrees)

            this.instance.process(image)
                .addOnSuccessListener {
                    success.onSuccess(it)
                    imageProxy.close()
                }
                .addOnFailureListener { Log.e(TAG, "Ml.Kit: Error Processing Image", it) }
        }
    }
}