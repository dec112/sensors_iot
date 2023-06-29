package run.jkdev.dec4iot.jetpack

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import run.jkdev.dec4iot.jetpack.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val isFromSetupIntent: Boolean = requireActivity().intent.action == "me.byjkdev.dec4iot.intents.banglejs.SETUP"
        val isFromSettingsIntent: Boolean = requireActivity().intent.action == "me.byjkdev.dec4iot.intents.banglejs.SETTINGS"

        if(isFromSetupIntent) {
            binding.textviewFirst.text =
                getString(R.string.from_bangle)
        } else if(isFromSettingsIntent) {
            binding.buttonFirst.text =
                getString(R.string.settings_btn)

            binding.textviewFirst.text =
                getString(R.string.settings_desc)
        }

        binding.buttonFirst.setOnClickListener {
            if(isFromSetupIntent) {
                val act = FirstFragmentDirections.actionFirstFragmentToSecondFragment(true)
                findNavController().navigate(act)
            } else if(isFromSettingsIntent) {
                // FUTURE
            } else {
                val act = FirstFragmentDirections.actionFirstFragmentToSecondFragment(false)
                findNavController().navigate(act)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}