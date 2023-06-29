package run.jkdev.dec4iot.jetpack

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.gson.Gson
import run.jkdev.dec4iot.jetpack.gsonmodels.OnboardingQr

/**
 * A simple [Fragment] subclass.
 * Use the [QrSuccessFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class QrSuccessFragment : Fragment() {
    private val args: QrSuccessFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_qr_success, container, false)
    }

    private lateinit var sensorId: String
    private lateinit var endpoint: String


    private fun noThisWontWork(view: View) {
        view.findViewById<TextView>(R.id.qrErrorDisplay)
            .text = getString(R.string.qr_no_support)

        val btn = view.findViewById<Button>(R.id.qr_returnBtn)
        btn.visibility = View.VISIBLE
        btn.setOnClickListener {
            val act = QrSuccessFragmentDirections.actionQrSuccessFragmentToQrScanFragment2(false)
            findNavController().navigate(act)
        }

    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lateinit var qrData: OnboardingQr

        val gson = Gson()
        try {
            qrData = gson.fromJson(args.qrRes, OnboardingQr::class.java)
        } catch (error: Throwable) {
            Log.e(TAG, error.toString())
            return noThisWontWork(view)
        }

        qrData.sensorId?.also { this@QrSuccessFragment.sensorId = it } ?: return noThisWontWork(view)
        qrData.endpoint?.also { this@QrSuccessFragment.endpoint = it } ?: return noThisWontWork(view)

        view.findViewById<TextView>(R.id.qrSuccessSensorId)
            .text = "Sensor Id: ${qrData.sensorId}"

        view.findViewById<TextView>(R.id.qrSuccessEndpoint)
            .text = "Endpoint: ${qrData.endpoint}"

        view.findViewById<TextView>(R.id.selectModelDisplay)
            .visibility = View.VISIBLE

        val bangleJsButton: Button = view.findViewById(R.id.bangleJsButton)
        val puckJsButton: Button = view.findViewById(R.id.puckJsButton)

        bangleJsButton.visibility = View.VISIBLE
        puckJsButton.visibility = View.VISIBLE

        bangleJsButton.setOnClickListener {
            val act = QrSuccessFragmentDirections.actionQrSuccessFragmentToBangleJsFragment(this@QrSuccessFragment.sensorId, this@QrSuccessFragment.endpoint, args.fromIntent)
            findNavController().navigate(act)
        }

        puckJsButton.setOnClickListener {
            val act = QrSuccessFragmentDirections.actionQrSuccessFragmentToPuckJsFragment(this@QrSuccessFragment.sensorId, this@QrSuccessFragment.endpoint)
            findNavController().navigate(act)
        }
    }

}