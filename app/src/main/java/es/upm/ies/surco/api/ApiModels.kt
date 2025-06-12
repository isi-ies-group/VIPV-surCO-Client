package es.upm.ies.surco.api

import androidx.annotation.Keep
import es.upm.ies.surco.BuildConfig


class ApiModels {
    @Suppress("PropertyName")  // linter hates snake_case
    @Keep
    data class UpResponse(
        var message: String? = null,
        var privacy_policy_last_updated: String? = null,
        var client_build_number_minimal: String? = null,
        var client_build_number_deprecated: String? = null,
    )

    @Keep
    data class SaltResponse(
        var passSalt: String? = null
    )

    @Suppress("PropertyName")  // linter hates snake_case
    @Keep
    data class LoginRequest(
        val email: String,
        val passHash: String,
        val app_build_number: Int = BuildConfig.VERSION_CODE
    )


    @Suppress("PropertyName")  // linter hates snake_case
    @Keep
    data class LoginResponse(
        var username: String? = null, var access_token: String? = null, var validity: Int? = null
    )

    @Keep
    data class RegisterRequest(
        val username: String, val email: String, val passHash: String, val passSalt: String
    )

    @Suppress("PropertyName")  // linter hates snake_case
    @Keep
    data class PrivacyPolicyResponse(
        var content: String? = null,
        var last_updated: String? = null,
        var language: String? = null,
    )

    companion object {
        const val API_BASE = "api/v1/"
    }
}