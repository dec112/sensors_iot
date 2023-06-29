package run.jkdev.dec4iot.jetpack

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.text.method.LinkMovementMethod
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.gson.Gson
import run.jkdev.dec4iot.jetpack.gsonmodels.BangleJsConfig

class BangleJsNotLikeThisFragment : Fragment() {
    val args: BangleJsNotLikeThisFragmentArgs by navArgs()

    private var fromIntent: Boolean? = null
    private var sensorIdValue: String? = null
    private var apiEndpointValue: String? = null

    private val gson = Gson()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_bangle_js_not_here, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fromIntent = args.fromIntent
        sensorIdValue = args.sensorId
        apiEndpointValue = args.endpoint

        val writingInfo: TextView = view.findViewById(R.id.writingInfo_banglejs)
        val sensorId: TextView = view.findViewById(R.id.sensorIdToBeWritten_banglejs)
        val apiEndpoint: TextView = view.findViewById(R.id.apiEndpointToBeWritten_banglejs)
        val pleaseConfirm: TextView = view.findViewById(R.id.pleaseConfirm_banglejs)
        val confirmed: CheckBox = view.findViewById(R.id.valuesChecked_banglejs)
        val restart: Button = view.findViewById(R.id.restart_banglejs)
        val confirm: Button = view.findViewById(R.id.write_banglejs)

        val noHeader: TextView = view.findViewById(R.id.no_banglejs)
        val tutorial: TextView = view.findViewById(R.id.tutorial_banglejs)
        val restartBtn: Button = view.findViewById(R.id.restart_btn_banglejs)

        if(fromIntent == true) {
            writingInfo.visibility = VISIBLE
            writingInfo.text =
                getString(R.string.the_following_will_be_written, "your Bangle.JS")

            sensorId.visibility = VISIBLE
            sensorId.text =
                getString(R.string.sensor_id_to_be_written, sensorIdValue)

            apiEndpoint.visibility = VISIBLE
            apiEndpoint.text =
                getString(R.string.api_endpoint_to_be_written, apiEndpointValue)

            pleaseConfirm.visibility = VISIBLE

            confirmed.visibility = VISIBLE

            restart.visibility = VISIBLE
            restart.setOnClickListener(restartBtnWithIntentListener)

            confirm.visibility = VISIBLE
            confirm.setOnClickListener(continueBtnListener)
        } else if(fromIntent == false) {
            noHeader.visibility = VISIBLE

            tutorial.visibility = VISIBLE
            tutorial.movementMethod = LinkMovementMethod.getInstance()

            restartBtn.visibility = VISIBLE
            restartBtn.setOnClickListener(restartBtnListener)
        }

    }

    private val continueBtnListener = OnClickListener {
        val config = BangleJsConfig(true, sensorIdValue!!.toInt(), apiEndpointValue!!, 60)
        val configJson = gson.toJson(config)

        val confirmed: CheckBox = requireView().findViewById(R.id.valuesChecked_banglejs)
        if(!confirmed.isChecked) {
            Toast.makeText(publicApplicationContext, R.string.please_confirm_all_data, Toast.LENGTH_LONG).show()
            publicVibrator.vibrate(VibrationEffect.createOneShot(1000, 100))
        } else {
            val writeIntent = Intent("com.banglejs.uart.tx")
            writeIntent.putExtra("line", "require('Storage').writeJSON('dec4iot.settings.json', $configJson)")

            val feedbackIntent = Intent("com.banglejs.uart.tx")
            feedbackIntent.putExtra("line", "Bangle.buzz(1000, 1)")

            val restartBangleApp = Intent("com.banglejs.uart.tx")
            restartBangleApp.putExtra("line", "startLogic()")

            publicApplicationContext.sendBroadcast(writeIntent)
            publicApplicationContext.sendBroadcast(feedbackIntent)
            publicApplicationContext.sendBroadcast(restartBangleApp)

            val act = BangleJsNotLikeThisFragmentDirections.actionBangleJsNotLikeThisToOnboardingDone()
            findNavController().navigate(act)
        }
    }

    private val restartBtnWithIntentListener = OnClickListener {
        val restartIntent = Intent("me.byjkdev.dec4iot.intents.banglejs.SETUP")
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        restartIntent.addCategory("android.intent.category.DEFAULT")
        startActivity(restartIntent)
    }

    private val restartBtnListener = OnClickListener {
        val restartIntent: Intent? = publicApplicationContext.packageManager
            .getLaunchIntentForPackage(publicApplicationContext.packageName)
        restartIntent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(restartIntent)
    }
}