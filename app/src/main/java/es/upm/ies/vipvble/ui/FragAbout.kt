package es.upm.ies.vipvble.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import es.upm.ies.vipvble.R
import es.upm.ies.vipvble.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {
    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment using view binding
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Get the version name from the manifest
        val versionInfo =
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        // Set the version name and version code in the text view as: "Version: 1.0-1"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {  // versionInfo.versionCode was deprecated in API level 28. Use versionCodeLong instead.
            binding.versionTextview.text =
                getString(R.string.version, versionInfo.versionName, versionInfo.longVersionCode)
        } else {
            binding.versionTextview.text =
                getString(R.string.version, versionInfo.versionName, versionInfo.versionCode)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
