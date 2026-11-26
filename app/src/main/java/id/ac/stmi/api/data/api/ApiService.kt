package id.ac.stmi.api.data.api

import com.google.gson.JsonObject
import id.ac.stmi.api.data.model.Hazard
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("location")
    suspend fun getHazards(): List<Hazard>

    @GET("location/{id}")
    suspend fun getHazardById(@Path("id") id: String): Hazard

    @POST("location")
    suspend fun createHazard(@Body request: JsonObject): Response<JsonObject>

    @PUT("location/{id}")
    suspend fun updateHazard(
        @Path("id") id: String,
        @Body request: JsonObject
    ): Response<JsonObject>

    @PATCH("location/{id}")
    suspend fun patchHazard(
        @Path("id") id: String,
        @Body request: JsonObject
    ): Response<JsonObject>

    @DELETE("location/{id}")
    suspend fun deleteHazard(@Path("id") id: String): Response<ResponseBody>
}
