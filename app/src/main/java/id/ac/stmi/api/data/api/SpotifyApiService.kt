package id.ac.stmi.api.data.api

import android.net.Uri
import id.ac.stmi.api.data.model.SpotifyPlayRequest
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.PUT

interface SpotifyApiService {
    @PUT("me/player/play")
    suspend fun playCalmingPlaylist(
        @Header("Authorization") bearerToken: String,
        @Body request: SpotifyPlayRequest
    ): Response<Void>
}

object SpotifyClient {
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val apiService: SpotifyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.SPOTIFY_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpotifyApiService::class.java)
    }

    fun buildAuthUri(): Uri {
        return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", ApiConfig.SPOTIFY_CLIENT_ID)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("redirect_uri", ApiConfig.SPOTIFY_REDIRECT_URI)
            .appendQueryParameter("scope", "user-modify-playback-state user-read-playback-state")
            .build()
    }
}
