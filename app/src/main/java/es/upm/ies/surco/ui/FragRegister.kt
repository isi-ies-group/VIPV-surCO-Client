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
import androidx.navigation.fragment.findNavController
import es.upm.ies.surco.api.ApiUserSessionState
import es.upm.ies.surco.R
import es.upm.ies.surco.api.ApiActions
import es.upm.ies.surco.databinding.FragmentRegisterBinding

class FragRegister : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FragRegisterViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // if privacy policy is not accepted, navigate to the privacy policy fragment
        if (viewModel.requiresPrivacyPolicyAccept()) {
            findNavController().navigate(R.id.action_fragRegister_to_privacyPolicyFragment)
        }

        // observe the register status to show the user any errors or return to the main activity
        viewModel.registerStatus.observe(viewLifecycleOwner) { status ->
            if (status == ApiUserSessionState.LOGGED_IN) {
                // navigate to the main activity
                findNavController().navigate(R.id.action_fragRegister_to_homeFragment)
            } else {
                // show the user the error message
                if (status == ApiUserSessionState.ERROR_BAD_IDENTITY) {
                    binding.etEmail.error = getString(R.string.bad_email_already_registered)
                } else if (status == ApiUserSessionState.CONNECTION_ERROR) {
                    // Create an informative alert dialog
                    val builder = AlertDialog.Builder(requireContext())
                    builder.setTitle(R.string.connection_error)
                    builder.setMessage(R.string.connection_error_message)
                    builder.setPositiveButton(R.string.ok) { dialog, which ->
                        // do nothing
                    }
                    builder.show()
                }
            }
            binding.pbLogin.visibility = View.INVISIBLE
        }

        // observe the username, email, password, and password2 invalid flags
        // and set the error messages accordingly
        viewModel.usernameInvalid.observe(viewLifecycleOwner) { invalid ->
            if (invalid) {
                binding.etUsername.error = getString(R.string.invalid_username)
            } else {
                binding.etUsername.error = null
            }
        }

        viewModel.emailInvalid.observe(viewLifecycleOwner) { invalid ->
            if (invalid) {
                binding.etEmail.error = getString(R.string.invalid_email)
            } else {
                binding.etEmail.error = null
            }
        }

        // set the text fields to the values in the view model
        binding.etUsername.setText(viewModel.username.value, TextView.BufferType.EDITABLE)
        binding.etEmail.setText(viewModel.email.value, TextView.BufferType.EDITABLE)
        binding.etPassword.setText(viewModel.password.value, TextView.BufferType.EDITABLE)
        binding.etPassword2.setText(viewModel.password2.value, TextView.BufferType.EDITABLE)

        binding.btnRegister.setOnClickListener {
            // Hide keyboard
            activity?.currentFocus?.let { view ->
                val imm =
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }

            // Clear previous errors
            binding.etUsername.error = null
            binding.etEmail.error = null
            binding.etPassword.error = null
            binding.etPassword2.error = null

            val username = binding.etUsername.text.toString()
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            val password2 = binding.etPassword2.text.toString()
            var valid = true

            if (!ApiActions.User.CredentialsValidator.isUsernameValid(username)) {
                binding.etUsername.error = getString(R.string.invalid_username)
                valid = false
            } else if (!ApiActions.User.CredentialsValidator.isEmailValid(email)) {
                binding.etEmail.error = getString(R.string.invalid_email)
                valid = false
            } else if (!ApiActions.User.CredentialsValidator.isPasswordValid(password)) {
                binding.etPassword.error = getString(R.string.invalid_password)
                valid = false
            } else if (password2 != password) {
                binding.etPassword2.error = getString(R.string.invalid_password_equals)
                valid = false
            }

            if (!valid) return@setOnClickListener

            // Show progress and proceed with registration
            binding.pbLogin.visibility = View.VISIBLE
            viewModel.username.value = username
            viewModel.email.value = email
            viewModel.password.value = password
            viewModel.password2.value = password2
            viewModel.doRegister()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.username.value = binding.etUsername.text.toString()
        viewModel.email.value = binding.etEmail.text.toString()
        viewModel.password.value = binding.etPassword.text.toString()
        _binding = null
    }
}
