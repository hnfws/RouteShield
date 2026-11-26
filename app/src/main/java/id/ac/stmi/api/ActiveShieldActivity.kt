package id.ac.stmi.api

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import id.ac.stmi.api.data.api.ApiConfig
import id.ac.stmi.api.data.api.RetrofitClient
import id.ac.stmi.api.data.api.SpotifyClient
import id.ac.stmi.api.data.model.Hazard
import id.ac.stmi.api.data.model.SpotifyPlayRequest
import id.ac.stmi.api.databinding.ActivityActiveShieldBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ActiveShieldActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActiveShieldBinding
    private var isPlaying = true
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    private var currentLat = -6.3918
    private var currentLng = 106.8214
    private var destLat = -6.3850
    private var destLng = 106.8250
    private var nearbyHazards: List<Hazard> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActiveShieldBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentDb = intent.getIntExtra("CURRENT_DB", 78)
        val currentBpm = intent.getIntExtra("CURRENT_BPM", 92)
        val destination = intent.getStringExtra("DESTINATION") ?: "Jl. Media 4"
        val turnInstruction = intent.getStringExtra("TURN_INSTRUCTION") ?: "Belok Kiri ke $destination"
        val routeGeoJson = intent.getStringExtra("ROUTE_GEOJSON") ?: "[]"
        val spotifyToken = intent.getStringExtra("SPOTIFY_TOKEN")

        val dbCategory = when {
            currentDb < 60 -> "Tenang"
            currentDb < 75 -> "Sedang"
            else -> "Bising"
        }
        val hrCategory = when {
            currentBpm < 80 -> "Rileks"
            currentBpm < 100 -> "Normal"
            else -> "Stres"
        }
        binding.tvOrbReadout.text = "$currentDb dB ($dbCategory) • $currentBpm BPM ($hrCategory)"
        binding.tvTurnDistance.text = "150 m"
        binding.tvTurnInstruction.text = "$turnInstruction (Mode Tenang)"

        // Format estimasi jam sampai (ETA)
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, 12)
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val etaTime = timeFormat.format(cal.time)

        binding.tvDurationEta.text = "12 min  🚶"
        binding.tvRemainingDistance.text = "950 m • Sampai $etaTime (ke $destination)"

        setupActiveMapWebView(routeGeoJson)
        loadNearbyCrowdHotspots()
        startLiveLocationTracking(destination, turnInstruction)

        // Otomatis picu pemutaran Spotify jika token tersedia
        if (!spotifyToken.isNullOrEmpty()) {
            triggerSpotifyPlayback(spotifyToken)
        } else {
            openSpotifyFallback()
        }

        binding.btnPlayPauseAudio.setOnClickListener {
            isPlaying = !isPlaying
            if (isPlaying) {
                binding.btnPlayPauseAudio.text = "⏸"
                binding.tvTrackTitle.text = "🎵  Shield Audio: Brown Noise (Melindungi Sensori)"
                if (!spotifyToken.isNullOrEmpty()) {
                    triggerSpotifyPlayback(spotifyToken)
                } else {
                    openSpotifyFallback()
                }
            } else {
                binding.btnPlayPauseAudio.text = "▶"
                binding.tvTrackTitle.text = "🎵  Audio Dijeda • Tap Play untuk Melanjutkan"
            }
        }

        binding.btnStopShield.setOnClickListener {
            Toast.makeText(this, "Sesi navigasi selesai & dicatat ke Riwayat Analitik", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupActiveMapWebView(routeGeoJson: String) {
        binding.webViewActiveMap.settings.javaScriptEnabled = true
        binding.webViewActiveMap.settings.domStorageEnabled = true
        val mapboxToken = ApiConfig.MAPBOX_ACCESS_TOKEN

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    body, html { margin: 0; padding: 0; height: 100%; background: #121619; }
                    #map { width: 100%; height: 100%; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map', {zoomControl: false, attributionControl: false}).setView([-6.3918, 106.8214], 18.5);
                    
                    var mapboxToken = '$mapboxToken';
                    L.tileLayer('https://api.mapbox.com/styles/v1/mapbox/dark-v11/tiles/{z}/{x}/{y}@2x?access_token=' + mapboxToken, {
                        tileSize: 512,
                        zoomOffset: -1,
                        maxZoom: 19
                    }).addTo(map);

                    var userNavIcon = L.divIcon({
                        className: 'user-nav-icon',
                        html: '<div style="background-color: #2196F3; width: 22px; height: 22px; border-radius: 50%; border: 3px solid #FFFFFF; box-shadow: 0 0 10px rgba(33,150,243,0.8);"></div>',
                        iconSize: [22, 22]
                    });

                    var userMarker = L.marker([-6.3918, 106.8214], {icon: userNavIcon}).addTo(map);
                    var routePolyline = null;

                    var rawCoords = $routeGeoJson;
                    if (rawCoords && rawCoords.length > 0) {
                        var leafletCoords = rawCoords.map(function(pt) {
                            return [pt[1], pt[0]];
                        });
                        var endPoint = leafletCoords[leafletCoords.length - 1];
                        L.marker(endPoint).addTo(map).bindPopup("Tujuan").openPopup();

                        routePolyline = L.polyline(leafletCoords, {
                            color: '#6BA392',
                            weight: 8,
                            opacity: 0.95
                        }).addTo(map);
                    }

                    function updateUserLocation(lat, lng) {
                        var newLatLng = new L.LatLng(lat, lng);
                        userMarker.setLatLng(newLatLng);
                        map.setView(newLatLng, 18.5);
                    }

                    function addCrowdHotspot(lat, lng, title, level) {
                        var crowdIcon = L.divIcon({
                            className: 'crowd-marker',
                            html: '<div style="background-color: #D4A373; width: 20px; height: 20px; border-radius: 50%; border: 2px solid #FFFFFF; box-shadow: 0 0 10px rgba(212,163,115,0.9); display: flex; align-items: center; justify-content: center; font-size: 11px;">🔊</div>',
                            iconSize: [20, 20]
                        });
                        L.marker([lat, lng], {icon: crowdIcon}).addTo(map).bindPopup("<b>" + title + "</b><br>Kebisingan: " + level);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        binding.webViewActiveMap.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    private fun loadNearbyCrowdHotspots() {
        lifecycleScope.launch {
            try {
                nearbyHazards = RetrofitClient.apiService.getHazards()
                for (item in nearbyHazards) {
                    val lat = item.latitude.toDoubleOrNull() ?: continue
                    val lng = item.longitude.toDoubleOrNull() ?: continue
                    val title = item.location_name
                    val level = "${item.noise_level} (${item.category})"
                    binding.webViewActiveMap.evaluateJavascript("addCrowdHotspot($lat, $lng, '$title', '$level')", null)
                }
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLiveLocationTracking(destination: String, baseInstruction: String) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 2500
        ).setMinUpdateIntervalMillis(1200).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                currentLat = location.latitude
                currentLng = location.longitude

                // Update posisi marker user di peta besar & re-center zoom-in
                binding.webViewActiveMap.evaluateJavascript("updateUserLocation($currentLat, $currentLng)", null)

                // Hitung estimasi sisa jarak ke tujuan dalam meter
                val results = FloatArray(1)
                android.location.Location.distanceBetween(currentLat, currentLng, destLat, destLng, results)
                val distanceMeters = results[0].toInt().coerceAtLeast(10)
                val turnDist = (distanceMeters / 4).coerceAtLeast(15)
                val durationMins = (distanceMeters / 80).coerceAtLeast(1)

                binding.tvTurnDistance.text = "$turnDist m"

                // Cek apakah mendekati titik bising/keramaian terdekat (< 150m)
                var alertMsg: String? = null
                for (hazard in nearbyHazards) {
                    val hLat = hazard.latitude.toDoubleOrNull() ?: continue
                    val hLng = hazard.longitude.toDoubleOrNull() ?: continue
                    val hDist = FloatArray(1)
                    android.location.Location.distanceBetween(currentLat, currentLng, hLat, hLng, hDist)
                    if (hDist[0] <= 150f) {
                        alertMsg = "⚠️ Waspada: Mendekati ${hazard.location_name} (${hazard.noise_level}) dalam ${hDist[0].toInt()}m"
                        break
                    }
                }

                if (alertMsg != null) {
                    binding.tvTurnInstruction.text = alertMsg
                } else {
                    binding.tvTurnInstruction.text = baseInstruction
                }

                val cal = Calendar.getInstance()
                cal.add(Calendar.MINUTE, durationMins)
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val etaTime = timeFormat.format(cal.time)

                binding.tvDurationEta.text = "$durationMins min  🚶"
                binding.tvRemainingDistance.text = "$distanceMeters m • Sampai $etaTime (ke $destination)"
            }
        }

        try {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient?.removeLocationUpdates(locationCallback!!)
        }
    }

    private fun triggerSpotifyPlayback(token: String) {
        lifecycleScope.launch {
            try {
                SpotifyClient.apiService.playCalmingPlaylist(
                    bearerToken = "Bearer $token",
                    request = SpotifyPlayRequest("spotify:playlist:37i9dQZF1DWZd79rJ6a7lp")
                )
            } catch (_: Exception) {
                openSpotifyFallback()
            }
        }
    }

    private fun openSpotifyFallback() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:playlist:37i9dQZF1DWZd79rJ6a7lp"))
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
    }
}
