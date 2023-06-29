package run.jkdev.dec4iot.jetpack

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.gms.tasks.OnSuccessListener
import com.google.mlkit.vision.barcode.common.Barcode
import run.jkdev.dec4iot.jetpack.databinding.FragmentQrscannerBinding
import run.jkdev.dec4iot.jetpack.qrcode.AnalysisUseCase
import run.jkdev.dec4iot.jetpack.qrcode.PreviewUseCase
import run.jkdev.dec4iot.jetpack.qrcode.Scanner

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class QrScanFragment : Fragment() {
    private val args: QrScanFragmentArgs by navArgs()

    private var alreadyScanned = false

    private var _binding: FragmentQrscannerBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentQrscannerBinding.inflate(inflater, container, false)
        return binding.root

    }

    private val qrOnSuccess = OnSuccessListener<MutableList<Barcode>> {
        if(it.isEmpty()) { return@OnSuccessListener }
        if(this.alreadyScanned) { return@OnSuccessListener }

        val code = it.getOrNull(0)

        val act = QrScanFragmentDirections.actionQrScanFragmentToQrSuccessFragment(code!!.rawValue.toString(), args.fromIntent)
        findNavController().navigate(act)

        this.alreadyScanned = true
    }

    private fun startScanning() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(publicApplicationContext)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val surfaceProvider = requireView().findViewById<PreviewView>(R.id.qr_previewSurface).surfaceProvider

            // Create the UseCase Preview
            val casePreview = PreviewUseCase(surfaceProvider).case

            // Create the Analysis UseCase
            val caseAnalysis = AnalysisUseCase(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).case {
                    imageProxy -> Scanner().processImageProxy(imageProxy, this@QrScanFragment.qrOnSuccess)
            }

            val selectedCamera = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this, selectedCamera,
                    casePreview, caseAnalysis
                )

            } catch (e: Exception) {
                Log.e(TAG, "Camera Binding Failed", e)
            }

        }, ContextCompat.getMainExecutor(publicApplicationContext))
    }

    override fun onViewCreated(viewI: View, savedInstanceState: Bundle?) {
        super.onViewCreated(viewI, savedInstanceState)

        startScanning()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}