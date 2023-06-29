package run.jkdev.dec4iot.jetpack

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button

class OnboardingDone : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_onboarding_done, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.restartBtn_done).setOnClickListener(doneBtnListener)
    }

    private val doneBtnListener = View.OnClickListener {
        val restartIntent: Intent? = publicApplicationContext.packageManager
            .getLaunchIntentForPackage(publicApplicationContext.packageName)
        restartIntent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(restartIntent)
    }
}