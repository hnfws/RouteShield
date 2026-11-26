package id.ac.stmi.api.data.api

import com.google.gson.JsonObject
import id.ac.stmi.api.data.model.DirectionsResponse
import id.ac.stmi.api.data.model.GeocodingResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MapApiService {

    // Ubah koordinat GPS (longitude, latitude) menjadi nama jalan otomatis
    @GET("geocoding/v5/mapbox.places/{lng},{lat}.json")
    suspend fun getAddressFromCoords(
        @Path("lng") lng: String,
        @Path("lat") lat: String,
        @Query("access_token") token: String = ApiConfig.MAPBOX_ACCESS_TOKEN,
        @Query("limit") limit: Int = 1
    ): GeocodingResponse

    // 1. Reverse Geocoding: Koordinat -> Nama Jalan Saat Ini
    @GET("geocoding/v5/mapbox.places/{lon},{lat}.json")
    suspend fun getPlaceName(
        @Path("lon") longitude: Double,
        @Path("lat") latitude: Double,
        @Query("access_token") accessToken: String = ApiConfig.MAPBOX_ACCESS_TOKEN,
        @Query("limit") limit: Int = 1
    ): JsonObject

    // 2. Forward Geocoding: Nama Tujuan -> Koordinat Tujuan
    @GET("geocoding/v5/mapbox.places/{query}.json")
    suspend fun searchDestination(
        @Path("query") destinationQuery: String,
        @Query("access_token") accessToken: String = ApiConfig.MAPBOX_ACCESS_TOKEN,
        @Query("proximity") proximity: String, // format: "lon,lat" agar cari yang terdekat
        @Query("country") country: String = "id",
        @Query("limit") limit: Int = 1
    ): JsonObject

    // 3. Autocomplete pencarian tempat (misal: "Depok")
    @GET("geocoding/v5/mapbox.places/{query}.json")
    suspend fun searchPlaces(
        @Path("query") query: String,
        @Query("access_token") token: String = ApiConfig.MAPBOX_ACCESS_TOKEN,
        @Query("autocomplete") autocomplete: Boolean = true,
        @Query("limit") limit: Int = 5
    ): GeocodingResponse

    // 4. Walking Directions: Hitung Rute Jalan Kaki & Instruksi Belokan
    @GET("directions/v5/mapbox/walking/{coordinates}.json")
    suspend fun getWalkingRoute(
        @Path("coordinates") coordinates: String, // format: "asalLon,asalLat;tujuanLon,tujuanLat"
        @Query("steps") steps: Boolean = true,
        @Query("geometries") geometries: String = "geojson",
        @Query("language") language: String = "id",
        @Query("access_token") accessToken: String = ApiConfig.MAPBOX_ACCESS_TOKEN
    ): JsonObject

    // Cari rute jalan kaki tenang dari titik asal ke tujuan
    @GET("directions/v5/mapbox/walking/{originLng},{originLat};{destLng},{destLat}.json")
    suspend fun getCalmWalkingRoute(
        @Path("originLng") originLng: String,
        @Path("originLat") originLat: String,
        @Path("destLng") destLng: String,
        @Path("destLat") destLat: String,
        @Query("access_token") token: String = ApiConfig.MAPBOX_ACCESS_TOKEN
    ): DirectionsResponse
}
