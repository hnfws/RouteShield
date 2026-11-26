package id.ac.stmi.api.data.api

import id.ac.stmi.api.BuildConfig

object ApiConfig {
    // 1. MockAPI.io (CRUD Titik Bising dengan resource /location)
    const val MOCK_API_BASE_URL = "https://6b06f20a57fa1297bb56779c.mockapi.io/"

    // 2. Spotify Web API (Intervensi Audio Penenang)
    const val SPOTIFY_CLIENT_ID = "55d5f838d3224c3889202f61a7597c03"
    const val SPOTIFY_REDIRECT_URI = "routeshield://callback"
    const val SPOTIFY_BASE_URL = "https://api.spotify.com/v1/"

    // 3. Mapbox API (Reverse Geocoding Nama Jalan & Rute Tenang)
    val MAPBOX_ACCESS_TOKEN = BuildConfig.MAPBOX_ACCESS_TOKEN
    const val MAPBOX_BASE_URL = "https://api.mapbox.com/"
}
