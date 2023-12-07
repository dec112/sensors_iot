package run.jkdev.dec4iot.jetpack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import run.jkdev.dec4iot.jetpack.MainActivity.Companion.AppLinkData
import run.jkdev.dec4iot.jetpack.gsonmodels.OnboardingQr
import run.jkdev.dec4iot.jetpack.interfaces.QrResult
import run.jkdev.dec4iot.jetpack.qrcode.QrScanningActivity

class QrFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_qr, container, false)
    }

    private val startQrForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            QrResult.RESULT_KO -> {
                val bundle = result.data!!.extras!!
                Log.e(TAG, bundle.getString("error")!!)

                Toast.makeText(requireContext(), R.string.qr_access_fail, Toast.LENGTH_LONG).show()

                noThisWontWork(getString(R.string.qr_generic_fail))
            }

            QrResult.RESULT_NO_DEC4IOT -> noThisWontWork()

            QrResult.RESULT_OK -> {
                val bundle = result.data!!.extras!!

                qrCodeDone(bundle.getInt("sensor_id"), bundle.getString("endpoint")!!)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if(AppLinkData.value != null) {
            val data: OnboardingQr = AppLinkData.value!!
            qrCodeDone(data.sensorId!!.toShort(), data.endpoint!!)
        } else startQrForResult.launch(Intent(requireContext(), QrScanningActivity::class.java))
    }

    private fun noThisWontWork(textOverride: CharSequence? = null) {
        if (textOverride == null)
            requireView().findViewById<TextView>(R.id.qrErrorDisplay)
                .text = getString(R.string.qr_no_support)
        else requireView().findViewById<TextView>(R.id.qrErrorDisplay)
            .text = textOverride

        val btn = requireView().findViewById<Button>(R.id.qr_tryAgain)
        btn.visibility = View.VISIBLE
        btn.setOnClickListener {
            startQrForResult.launch(Intent(requireContext(), QrScanningActivity::class.java))
            it.visibility = View.INVISIBLE

            requireView().findViewById<TextView>(R.id.qrErrorDisplay)
                .text = ""
        }
    }

    private fun qrCodeDone(sensorId: Number, endpoint: String) {
        requireView().findViewById<TextView>(R.id.qrSuccessSensorId)
            .text = getString(R.string.qr_id, sensorId)

        requireView().findViewById<TextView>(R.id.qrSuccessEndpoint)
            .text = getString(R.string.qr_endpoint, endpoint)

        requireView().findViewById<TextView>(R.id.selectModelDisplay)
            .visibility = View.VISIBLE

        val bangleJsButton: Button = requireView().findViewById(R.id.bangleJsButton)
        val puckJsButton: Button = requireView().findViewById(R.id.puckJsButton)

        bangleJsButton.visibility = View.VISIBLE
        puckJsButton.visibility = View.VISIBLE

        bangleJsButton.setOnClickListener {
            try {
                val act = QrFragmentDirections.actionQrSuccessFragmentToBangleJsFragment(sensorId.toInt(), endpoint)
                findNavController().navigate(act)
            } catch (e: Throwable) {
                Log.e(TAG, "An error happened", e)
                Toast.makeText(context, R.string.qr_button_press_caught, Toast.LENGTH_SHORT).show()
            }
        }

        puckJsButton.setOnClickListener {
            try {
                val act = QrFragmentDirections.actionQrSuccessFragmentToPuckJsFragment(sensorId.toInt(), endpoint)
                findNavController().navigate(act)
            } catch (e: Throwable) {
                Log.e(TAG, "An error happened", e)
                Toast.makeText(context, R.string.qr_button_press_caught, Toast.LENGTH_SHORT).show()
            }
        }
    }
}