package es.upm.ies.surco.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import es.upm.ies.surco.AppMain
import es.upm.ies.surco.R
import es.upm.ies.surco.api.ApiActions
import es.upm.ies.surco.databinding.FragmentPrivacyPolicyBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class FragPrivacyPolicy : Fragment() {
    private var _binding: FragmentPrivacyPolicyBinding? = null
    // Use custom getter that checks isAdded
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null or fragment is detached")

    // Application instance
    lateinit var appMain: AppMain

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPrivacyPolicyBinding.inflate(inflater, container, false)
        appMain = requireActivity().application as AppMain

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.pbLoading.visibility = View.VISIBLE
        binding.btnAccept.isEnabled = false
        binding.btnReject.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    ApiActions.PrivacyPolicy.getContent()
                }

                if (!isAdded) return@launch // Check if fragment is still attached

                if (content != null) {
                    binding.tvPrivacyPolicy.text = content
                } else {
                    showErrorAndExit()
                }
            } catch (_: Exception) {
                if (isAdded) showErrorAndExit()
            } finally {
                if (isAdded) {
                    binding.pbLoading.visibility = View.GONE
                    binding.btnAccept.isEnabled = true
                    binding.btnReject.isEnabled = true
                }
            }
        }

        binding.btnAccept.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                ApiActions.PrivacyPolicy.accept()
                parentFragmentManager.popBackStack()
            }
        }

        binding.btnReject.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                ApiActions.PrivacyPolicy.reject()
                findNavController().navigate(R.id.action_privacyPolicyFragment_to_homeFragment)
            }
        }
    }

    private fun showErrorAndExit() {
        if (!isAdded) return
        Toast.makeText(
            requireContext(),
            getString(R.string.error_loading_privacy_policy),
            Toast.LENGTH_LONG
        ).show()
        ApiActions.PrivacyPolicy.setConnectionError()
        findNavController().navigate(R.id.action_privacyPolicyFragment_to_homeFragment)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}