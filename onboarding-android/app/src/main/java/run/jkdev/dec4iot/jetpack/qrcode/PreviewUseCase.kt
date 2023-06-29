package run.jkdev.dec4iot.jetpack.qrcode

import androidx.camera.core.Preview

class PreviewUseCase(sf: Preview.SurfaceProvider) {

    private var sf: Preview.SurfaceProvider

    var case: Preview

    init {
        this.sf = sf

        this.case = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(this@PreviewUseCase.sf) }
    }



}