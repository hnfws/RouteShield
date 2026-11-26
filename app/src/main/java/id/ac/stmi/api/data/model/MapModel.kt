package id.ac.stmi.api.data.model

// Respons Reverse Geocoding Mapbox (Koordinat -> Nama Jalan)
data class GeocodingResponse(
    val features: List<GeoFeature>
)

data class GeoFeature(
    val place_name: String,
    val center: List<Double>? = null
)

// Respons Walking Directions Mapbox (Rute Jalan Kaki)
data class DirectionsResponse(
    val routes: List<RouteItem>
)

data class RouteItem(
    val distance: Double, // dalam meter
    val duration: Double  // dalam detik
)

data class SpotifyPlayRequest(
    val context_uri: String
)
