package run.jkdev.dec4iot.jetpack.qrcode

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.OnSuccessListener
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executors

class AnalysisUseCase (strategy: Int) {

    private var caseI: ImageAnalysis
    private var strategy: Int

    init {
        this.strategy = strategy

        this.caseI = ImageAnalysis.Builder()
            .setBackpressureStrategy(strategy)
            .build()

    }

    fun case(cb: (ImageProxy) -> Unit): ImageAnalysis {
        this.caseI.setAnalyzer(
            Executors.newSingleThreadExecutor()
        ) { cb(it) }

        return this.caseI
    }

    fun customAnalysis(): ImageAnalysis.Builder {
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(this.strategy)
    }


}