package es.upm.ies

import android.content.SharedPreferences
import es.upm.ies.surco.BuildConfig
import es.upm.ies.surco.api.APIService
import es.upm.ies.surco.api.ApiActions
import es.upm.ies.surco.api.ApiModels
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.BeforeClass
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * WARNING
 * THIS TESTS HAVE BEEN BROKEN AFTER SEVERAL CHANGES IN THE API.
 * SHOULD BE EASY TO FIX, BUT IT IS NOT A PRIORITY RIGHT NOW (sry, June 2025)
 */

class ApiInterfaceUT {
    companion object {
        const val ENDPOINT = "http://127.0.0.1:5000/"
        lateinit var apiService: APIService
        lateinit var user: ApiActions.User
        lateinit var actions: ApiActions
        lateinit var sharedPreferences: SharedPreferences

        @BeforeClass
        @JvmStatic
        fun setup() {
            val retrofit = Retrofit.Builder().baseUrl(ENDPOINT)
                .addConverterFactory(GsonConverterFactory.create()).build()
            apiService = retrofit.create(APIService::class.java)
            sharedPreferences.edit().clear().apply()  // Clear shared preferences before each test
            // TODO: Initialize sharedPreferences with a mock or real SharedPreferences instance
            ApiActions.initialize(
                sharedPreferences,
                apiService,
            )
            actions = ApiActions
            user = ApiActions.User
        }
    }

    @Test
    fun createUser() {
        val request = ApiModels.RegisterRequest(
            username = user.username!!,
            email = user.email!!,
            passHash = user.passHash!!,
            passSalt = user.passSalt!!
        )

        try {
            runBlocking {
                apiService.registerUser(request)
            }
        } catch (e: HttpException) {
            if (e.code() == 409) {
                println("User already exists")  // This is a valid response
            } else {
                throw e
            }
        }
    }

    @Test
    fun loginUser() {
        // login user normally
        val request = ApiModels.LoginRequest(
            email = user.email!!,
            passHash = user.passHash!!
        )

        runBlocking {
            val response = apiService.loginUser(request)
            assert(response.access_token != null)
        }

        // login user with bad password
        val bad_password_user = user
        bad_password_user.passHash = "badHash"

        val bad_password_request = ApiModels.LoginRequest(
            email = bad_password_user.email!!,
            passHash = bad_password_user.passHash!!
        )

        runBlocking {
            try {
                apiService.loginUser(bad_password_request)
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    println("Bad password")  // This is a valid response
                } else {
                    throw e
                }
            }
        }

        // login user with bad username
        val bad_username_user = user
        bad_username_user.username = "badUsername"

        val bad_username_request = ApiModels.LoginRequest(
            email = bad_username_user.email!!,
            passHash = bad_username_user.passHash!!,
            app_build_number = BuildConfig.VERSION_CODE
        )

        runBlocking {
            try {
                apiService.loginUser(bad_username_request)
            } catch (e: HttpException) {
                assert(e.code() == 404)
            }
        }
    }

    @Test
    fun getSalt() {
        val request = user.email!!

        runBlocking {
            val response = apiService.getUserSalt(request)
            assert(response.passSalt != null)
        }
    }
}
