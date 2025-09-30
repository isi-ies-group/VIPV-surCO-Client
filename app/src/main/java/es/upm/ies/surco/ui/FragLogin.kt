package es.upm.ies.surco.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import es.upm.ies.surco.R
import es.upm.ies.surco.api.ApiActions
import es.upm.ies.surco.api.ApiUserSessionState
import es.upm.ies.surco.databinding.FragmentLoginBinding

class FragLogin : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FragLoginViewModel by viewModels(
        factoryProducer = {
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        })

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment using view binding
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // if privacy policy is not accepted, navigate to the privacy policy fragment
        if (viewModel.requiresPrivacyPolicyAccept()) {
            findNavController().navigate(R.id.action_fragLogin_to_privacyPolicyFragment)
        }

        // observe the login status to show the user any errors or return to the main activity
        viewModel.loginStatus.observe(viewLifecycleOwner) { status ->
            if (status == ApiUserSessionState.LOGGED_IN) {
                // navigate to the main activity
                findNavController().navigate(R.id.action_fragLogin_to_homeFragment)
            } else {
                // show the user the error message
                when (status) {
                    ApiUserSessionState.ERROR_BAD_IDENTITY -> {
                        binding.etEmail.error = getString(R.string.bad_email)
                    }
                    ApiUserSessionState.ERROR_BAD_PASSWORD -> {
                        binding.etPassword.error = getString(R.string.bad_password)
                    }
                    ApiUserSessionState.CONNECTION_ERROR -> {
                        // Create an informative alert dialog
                        val builder = AlertDialog.Builder(requireContext())
                        builder.setTitle(R.string.connection_error)
                        builder.setMessage(R.string.connection_error_message)
                        builder.setPositiveButton(R.string.ok) { dialog, which ->
                            // do nothing
                        }
                        builder.show()
                    }
                    else -> {
                        // do nothing
                    }
                }
            }
            binding.pbLogin.visibility = View.INVISIBLE
        }

        binding.etEmail.setText(viewModel.email, TextView.BufferType.EDITABLE)
        binding.etPassword.setText(viewModel.password, TextView.BufferType.EDITABLE)

        binding.btnLogin.setOnClickListener {
            // close the keyboard
            activity?.currentFocus?.let { view ->
                val imm =
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }

            // clear previous errors
            binding.etEmail.error = null
            binding.etPassword.error = null

            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            var valid = true

            if (!ApiActions.User.CredentialsValidator.isEmailValid(email)) {
                binding.etEmail.error = getString(R.string.invalid_email)
                valid = false
            }
            if (!ApiActions.User.CredentialsValidator.isPasswordValid(password)) {
                binding.etPassword.error = getString(R.string.invalid_password)
                valid = false
            }

            if (!valid) return@setOnClickListener

            // show progress
            binding.pbLogin.visibility = View.VISIBLE

            viewModel.email = email
            viewModel.password = password
            viewModel.doLogin()
        }

        binding.btnGoToRegister.setOnClickListener {
            // navigate to the register fragment
            findNavController().navigate(R.id.action_fragLogin_to_fragRegister)
        }

        binding.btnUseOffline.setOnClickListener {
            // set the app to offline mode
            viewModel.setOffLineMode()
            // navigate back-pressing to the main activity
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        // Callback to use the app in offline mode if exited without never logging in
        if (viewModel.loginStatus.value == ApiUserSessionState.NEVER_LOGGED_IN) {
            // set the app to offline mode
            viewModel.setOffLineMode()
        }
    }
}
