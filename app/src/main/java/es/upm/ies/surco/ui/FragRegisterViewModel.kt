package es.upm.ies.surco.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import es.upm.ies.surco.api.ApiActions
import es.upm.ies.surco.api.ApiUserSessionState
import kotlinx.coroutines.launch

class FragRegisterViewModel(application: Application) : AndroidViewModel(application) {
    // variables for the login form persistence between destroy and create
    var username: MutableLiveData<String> = MutableLiveData("")
    var email: MutableLiveData<String> = MutableLiveData("")
    var password: MutableLiveData<String> = MutableLiveData("")
    var password2: MutableLiveData<String> = MutableLiveData("")

    // mutable flags for the error messages
    val usernameInvalid = MutableLiveData<Boolean>()
    val emailInvalid = MutableLiveData<Boolean>()

    // mutable status for the login process, to report errors to the user
    val registerStatus = MutableLiveData<ApiUserSessionState>()

    init {
        // observe the email and password fields for changes
        username.observeForever { onCredentialsChanged() }
        email.observeForever { onCredentialsChanged() }
    }

    override fun onCleared() {
        super.onCleared()
        username.removeObserver { onCredentialsChanged() }
        email.removeObserver { onCredentialsChanged() }
    }

    fun onCredentialsChanged() {
        // enable the login button if both email and password are not empty
        val validUsername =
            username.value!!.isNotEmpty() and ApiActions.User.CredentialsValidator.isUsernameValid(username.value!!)
        val validEmail = email.value!!.isNotEmpty()
        usernameInvalid.postValue(!validUsername)
        emailInvalid.postValue(!validEmail)
    }

    fun doRegister() = viewModelScope.launch {
        // call login method from the application and return the result
        // if successful, the user will be redirected to the main activity
        // else, update the loginStatus variable with the error message
        val result = ApiActions.User.register(
            username.value!!, email.value!!, password.value!!
        )
        registerStatus.postValue(result)
    }

    fun requiresPrivacyPolicyAccept(): Boolean {
        // Check if the privacy policy has been accepted
        return !ApiActions.PrivacyPolicy.isAccepted()
    }
}