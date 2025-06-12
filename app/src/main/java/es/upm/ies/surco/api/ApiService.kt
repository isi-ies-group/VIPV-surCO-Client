package es.upm.ies.surco.api

import es.upm.ies.surco.api.ApiModels.Companion.API_BASE
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface APIService {
    @GET(API_BASE + "up")
    suspend fun up(): ApiModels.UpResponse

    @GET(API_BASE + "salt")
    @Headers("Content-type: application/json")
    suspend fun getUserSalt(
        @Header("email") email: String,
    ): ApiModels.SaltResponse

    @POST(API_BASE + "register")
    @Headers("Content-type: application/json")
    suspend fun registerUser(
        @Body request: ApiModels.RegisterRequest,
    ): ResponseBody

    @POST(API_BASE + "login")
    @Headers("Content-type: application/json")
    suspend fun loginUser(
        @Body request: ApiModels.LoginRequest,
    ): ApiModels.LoginResponse

    @Multipart
    @POST(API_BASE + "session/upload")
    suspend fun uploadBeacons(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
    ): ResponseBody

    @GET(API_BASE + "privacy-policy")
    suspend fun getPrivacyPolicy(
        @Query("lang") lang: String = "en",
    ): ApiModels.PrivacyPolicyResponse
}
