package run.jkdev.dec4iot.jetpack

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment


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
        val activity = requireActivity().applicationContext.packageManager
            .getLaunchIntentForPackage(requireActivity().applicationContext.packageName)

        requireActivity().finish()
        startActivity(activity!!)
    }
}