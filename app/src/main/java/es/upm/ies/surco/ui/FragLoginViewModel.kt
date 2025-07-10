package es.upm.ies.surco.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import es.upm.ies.surco.api.ApiActions
import es.upm.ies.surco.api.ApiUserSessionState
import kotlinx.coroutines.launch

class FragLoginViewModel(application: Application) : AndroidViewModel(application) {
    var email: String = ""
    var password: String = ""
    val loginStatus = MutableLiveData<ApiUserSessionState>()

    fun doLogin() = viewModelScope.launch {
        val result = ApiActions.User.login(email, password)
        loginStatus.postValue(result)
    }

    fun setOffLineMode() {
        ApiActions.User.setOfflineMode()
    }

    fun requiresPrivacyPolicyAccept(): Boolean {
        return !ApiActions.PrivacyPolicy.isAccepted()
    }
}