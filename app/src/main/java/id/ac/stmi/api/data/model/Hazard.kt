package id.ac.stmi.api.data.model

data class Hazard(
    val id: String = "",
    val location_name: String = "",
    val noise_level: String = "",
    val category: String = "",
    val latitude: String = "",
    val longitude: String = ""
)

data class HazardRequest(
    val location_name: String? = null,
    val noise_level: String? = null,
    val category: String? = null,
    val latitude: String? = null,
    val longitude: String? = null
)
