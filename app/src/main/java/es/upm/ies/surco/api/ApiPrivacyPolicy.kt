package es.upm.ies.surco.api

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.concurrent.thread

enum class ApiPrivacyPolicyState {
    NEVER_PROMPTED,  // user has never been asked to accept the privacy policy
    ACCEPTED,  // user has accepted the privacy policy
    REJECTED,  // user has rejected the privacy policy, we must not access the API unless requested
    OUTDATED,  // user has accepted the privacy policy, but it has been updated since then
    CONNECTION_ERROR,  // an error occurred while checking the privacy policy
}

class ApiPrivacyPolicy {
    var apiService: APIService
    var sharedPrefs: SharedPreferences

    private val _privacyPolicyState =
        MutableLiveData<ApiPrivacyPolicyState>(ApiPrivacyPolicyState.NEVER_PROMPTED)
    val privacyPolicyState: LiveData<ApiPrivacyPolicyState> get() = _privacyPolicyState

    var privacyPolicyLastUpdated: String = ""
    var privacyPolicyConsentedRevision: String = ""

    constructor(sharedPrefs: SharedPreferences, apiService: APIService) {
        this.sharedPrefs = sharedPrefs
        this.apiService = apiService

        // Load the privacy policy state from shared preferences
        val state = sharedPrefs.getString(
            "privacy_policy_state", ApiPrivacyPolicyState.NEVER_PROMPTED.toString()
        )?.let { ApiPrivacyPolicyState.valueOf(it) } ?: ApiPrivacyPolicyState.NEVER_PROMPTED
        _privacyPolicyState.postValue(state)

        // Load the last updated date from shared preferences
        privacyPolicyLastUpdated = sharedPrefs.getString("privacy_policy_last_updated", "") ?: ""
        // Load the consented revision from shared preferences
        privacyPolicyConsentedRevision =
            sharedPrefs.getString("privacy_policy_consented_revision", "") ?: ""

        // If the state is ACCEPTED, it may have got outdated
        // this ensures newer versions of the app will require the user to accept the privacy policy again
        if (state == ApiPrivacyPolicyState.ACCEPTED) {
            if (privacyPolicyLastUpdated != privacyPolicyConsentedRevision) {
                _privacyPolicyState.postValue(ApiPrivacyPolicyState.OUTDATED)
            }
        }
    }

    /**
     * Accept the privacy policy and save the state to shared preferences.
     */
    fun accept() {
        _privacyPolicyState.postValue(ApiPrivacyPolicyState.ACCEPTED)
        privacyPolicyConsentedRevision = privacyPolicyLastUpdated
        this.sharedPrefs.edit {
            putString("privacy_policy_state", ApiPrivacyPolicyState.ACCEPTED.toString())
            putString("privacy_policy_consented_revision", privacyPolicyConsentedRevision)
            apply()
        }
    }

    /**
     * Reject the privacy policy and save the state to shared preferences.
     */
    fun reject() {
        _privacyPolicyState.postValue(ApiPrivacyPolicyState.REJECTED)
        this.sharedPrefs.edit {
            putString("privacy_policy_state", ApiPrivacyPolicyState.REJECTED.toString())
            remove("privacy_policy_consented_revision")
            apply()
        }
    }

    /**
     * Sets connection error state.
     */
    fun setConnectionError() {
        _privacyPolicyState.postValue(ApiPrivacyPolicyState.CONNECTION_ERROR)
        this.sharedPrefs.edit {
            putString("privacy_policy_state", ApiPrivacyPolicyState.CONNECTION_ERROR.toString())
            apply()
        }
    }

    /**
     * Gets the privacy policy content (for UI display).
     *
     * If not up to date or not cached, it will fetch the content from the API.
     */
    suspend fun getContent(): String? {
        if ((sharedPrefs.getString(
                "privacy_policy_content", null
            ) == null) || (privacyPolicyLastUpdated.isEmpty())
        ) {
            // If we don't have cached content, fetch it from the API
            try {
                val response = apiService.getPrivacyPolicy(Locale.getDefault().language)
                privacyPolicyLastUpdated = response.last_updated ?: ""
                sharedPrefs.edit {
                    putString("privacy_policy_last_updated", privacyPolicyLastUpdated)
                    putString("privacy_policy_content", response.content)
                    apply()
                }
            } catch (_: Exception) {
                return null  // Handle error appropriately
            }
        }
        return sharedPrefs.getString("privacy_policy_content", null)!!
    }

    fun refreshPrivacyPolicyForOutdated() {
        thread {
            runBlocking {
                var upResponse: ApiModels.UpResponse? = null
                try {
                    upResponse = apiService.up()
                } catch (_: Exception) {
                    return@runBlocking
                }
                // Keep the latest status except for when it was updated
                if (privacyPolicyConsentedRevision != upResponse.privacy_policy_last_updated) {
                    _privacyPolicyState.postValue(ApiPrivacyPolicyState.OUTDATED)
                } else {
                    _privacyPolicyState.postValue(ApiPrivacyPolicyState.ACCEPTED)
                }
                if (privacyPolicyLastUpdated != upResponse.privacy_policy_last_updated) {
                    var ppResponse: ApiModels.PrivacyPolicyResponse? = null
                    try {
                        ppResponse = apiService.getPrivacyPolicy(Locale.getDefault().language)
                    } catch (_: Exception) {
                        return@runBlocking
                    }
                    privacyPolicyLastUpdated = ppResponse.last_updated ?: privacyPolicyLastUpdated
                    sharedPrefs.edit {
                        putString("privacy_policy_last_updated", ppResponse.last_updated)
                        putString("privacy_policy_content", ppResponse.content)
                        apply()
                    }
                }
            }
        }
    }

    fun isAccepted(): Boolean {
        return this._privacyPolicyState.value == ApiPrivacyPolicyState.ACCEPTED
    }
}