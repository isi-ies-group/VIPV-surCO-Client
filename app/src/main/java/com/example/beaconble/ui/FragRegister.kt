package com.example.beaconble.ui

import android.content.Context
import androidx.fragment.app.viewModels
import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.beaconble.ApiUserSessionState
import com.example.beaconble.R

class FragRegister : Fragment() {
    lateinit var editTextUsername : EditText
    lateinit var editTextEmail : EditText
    lateinit var editTextPassword : EditText
    lateinit var editTextPassword2 : EditText
    lateinit var buttonRegister: Button
    lateinit var progressBar: ProgressBar

    private val viewModel: FragRegisterViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_register, container, false)

        editTextUsername = view.findViewById<EditText>(R.id.etUsername)
        editTextEmail = view.findViewById<EditText>(R.id.etEmail)
        editTextPassword = view.findViewById<EditText>(R.id.etPassword)
        editTextPassword2 = view.findViewById<EditText>(R.id.etPassword2)
        buttonRegister = view.findViewById<Button>(R.id.btnRegister)
        progressBar = view.findViewById<ProgressBar>(R.id.pbLogin)

        // observe the register status to show the user any errors or return to the main activity
        viewModel.registerStatus.observe(viewLifecycleOwner) { status ->
            if (status == ApiUserSessionState.LOGGED_IN) {
                // navigate to the main activity
                findNavController().navigate(R.id.action_fragRegister_to_homeFragment)
            } else {
                // show the user the error message
                if (status == ApiUserSessionState.ERROR_BAD_IDENTITY) {
                    editTextEmail.error = getString(R.string.bad_email_already_registered)
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
            progressBar.visibility = View.INVISIBLE
        }

        // observe the login button enabled status
        viewModel.registerButtonEnabled.observe(viewLifecycleOwner) { enabled ->
            buttonRegister.isEnabled = enabled
        }

        // observe the username, email, password, and password2 invalid flags
        // and set the error messages accordingly
        viewModel.usernameInvalid.observe(viewLifecycleOwner) { invalid ->
            if (invalid) {
                editTextUsername.error = getString(R.string.invalid_username)
            } else {
                editTextUsername.error = null
            }
        }

        viewModel.emailInvalid.observe(viewLifecycleOwner) { invalid ->
            if (invalid) {
                editTextEmail.error = getString(R.string.invalid_email)
            } else {
                editTextEmail.error = null
            }
        }

        viewModel.passwordInvalid.observe(viewLifecycleOwner) { invalid ->
            if (invalid) {
                editTextPassword.error = getString(R.string.invalid_password)
            } else {
                editTextPassword.error = null
            }
        }

        viewModel.password2Invalid.observe(viewLifecycleOwner) { invalid ->
            if (invalid) {
                editTextPassword2.error = getString(R.string.invalid_password_equals)
            } else {
                editTextPassword2.error = null
            }
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // set the text fields to the values in the view model
        editTextUsername.setText(viewModel.username.value, TextView.BufferType.EDITABLE)
        editTextEmail.setText(viewModel.email.value, TextView.BufferType.EDITABLE)
        editTextPassword.setText(viewModel.password.value, TextView.BufferType.EDITABLE)
        editTextPassword2.setText(viewModel.password2.value, TextView.BufferType.EDITABLE)

        // assign viewmodels text fields to the actual text fields on text changes
        editTextUsername.addTextChangedListener(object : TextWatcher{
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.username.value = s.toString()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        editTextEmail.addTextChangedListener(object : TextWatcher{
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.email.value = s.toString()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        editTextPassword.addTextChangedListener(object : TextWatcher{
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.password.value = s.toString()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        editTextPassword2.addTextChangedListener(object : TextWatcher{
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.password2.value = s.toString()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        buttonRegister.setOnClickListener {
            // close the keyboard
            // Only runs if there is a view that is currently focused
            activity?.currentFocus?.let { view ->
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }

            // show the user the login is in progress
            buttonRegister.isEnabled = false
            progressBar.visibility = View.VISIBLE

            viewModel.email.value = editTextEmail.text.toString()
            viewModel.password.value = editTextPassword.text.toString()
            viewModel.doRegister()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.username.value = editTextUsername.text.toString()
        viewModel.email.value = editTextEmail.text.toString()
        viewModel.password.value = editTextPassword.text.toString()
    }
}
