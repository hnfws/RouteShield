package id.ac.stmi.api

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import id.ac.stmi.api.data.api.ApiConfig
import id.ac.stmi.api.data.api.MapApiClient
import id.ac.stmi.api.data.api.RetrofitClient
import id.ac.stmi.api.databinding.ActivityMainBinding
import id.ac.stmi.api.sensor.NoiseMeterHelper
import id.ac.stmi.api.ui.HazardAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: HazardAdapter
    private val noiseMeter = NoiseMeterHelper()

    private var currentDb = 50
    private var currentBpm = 78
    private var currentLat = "-6.3918"
    private var currentLng = "106.8214"
    private var spotifyAccessToken: String? = null
    private var latestDestinationName: String = "Rute Tenang"
    private var latestTurnInstruction: String = "Ikuti jalur pejalan kaki tenang"
    private var latestRouteCoordsJson: String = "[]"
    private var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient? = null
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null

    private val healthPermissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    private val requestHealthPermissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (granted.containsAll(healthPermissions)) {
                readHeartRateFromOraimo()
            } else {
                Toast.makeText(this, "Izin Health Connect belum diberikan", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestRuntimePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
                startNoiseMonitoring()
            }
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                fetchCurrentLocation()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSpotifyCallback(intent)

        // Cek apakah user sudah melewati kalibrasi awal
        val prefs = getSharedPreferences("shield_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_calibrated", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupMapWebView()
        setupSearchAutocomplete()

        adapter = HazardAdapter(this)
        binding.lvHazards.adapter = adapter

        // Klik item -> buka Detail (GET by ID, PATCH, PUT, DELETE)
        binding.lvHazards.setOnItemClickListener { _, _, position, _ ->
            val selected = adapter.getItem(position)
            val intent = Intent(this, activity_hazard_detail::class.java).apply {
                putExtra(activity_hazard_detail.EXTRA_HAZARD_ID, selected.id)
            }
            startActivity(intent)
        }

        // Tombol Tambah Laporan (POST) otomatis bawa data sensor & GPS saat ini
        binding.btnAddHazard.setOnClickListener {
            val intent = Intent(this, activity_hazard_form::class.java).apply {
                putExtra("DEFAULT_DB", "$currentDb dB")
                putExtra("DEFAULT_LAT", currentLat)
                putExtra("DEFAULT_LNG", currentLng)
            }
            startActivity(intent)
        }

        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }

        binding.btnConnectHealth.setOnClickListener {
            checkAndReadHealthConnect()
        }

        binding.btnTriggerSpotify.setOnClickListener {
            openCalmingSpotifyPlaylist()
        }

        binding.btnNavReport.setOnClickListener {
            binding.btnAddHazard.performClick()
        }

        binding.btnPanicShield.setOnClickListener {
            val intent = Intent(this, ActiveShieldActivity::class.java).apply {
                putExtra("CURRENT_DB", currentDb)
                putExtra("CURRENT_BPM", currentBpm)
                putExtra("SPOTIFY_TOKEN", spotifyAccessToken)
                putExtra("DESTINATION", latestDestinationName)
                putExtra("TURN_INSTRUCTION", latestTurnInstruction)
            }
            startActivity(intent)
        }

        binding.btnSearchRoute.setOnClickListener {
            val query = binding.etSearchDestination.text.toString().trim()
            if (query.isEmpty()) {
                Toast.makeText(this, "Ketik nama lokasi tujuan dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            searchSafeRoute(query)
        }

        binding.btnStartNavigation.setOnClickListener {
            val intent = Intent(this, ActiveShieldActivity::class.java).apply {
                putExtra("CURRENT_DB", currentDb)
                putExtra("CURRENT_BPM", currentBpm)
                putExtra("SPOTIFY_TOKEN", spotifyAccessToken)
                putExtra("DESTINATION", latestDestinationName)
                putExtra("TURN_INSTRUCTION", latestTurnInstruction)
                putExtra("ROUTE_GEOJSON", latestRouteCoordsJson)
            }
            startActivity(intent)
        }

        binding.etSearchDestination.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                val dest = binding.etSearchDestination.text.toString().trim()
                if (dest.isNotEmpty()) {
                    Toast.makeText(this, "Mencari rute tenang ke $dest...", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        }

        binding.btnNavHistory.setOnClickListener {
            startActivity(Intent(this, InsightActivity::class.java))
        }

        checkSensorPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSpotifyCallback(intent)
    }

    private fun handleSpotifyCallback(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && uri.scheme == "routeshield" && uri.host == "callback") {
            val fragment = uri.fragment
            if (!fragment.isNullOrEmpty()) {
                val params = fragment.split("&").associate {
                    val parts = it.split("=")
                    parts[0] to (parts.getOrNull(1) ?: "")
                }
                spotifyAccessToken = params["access_token"]
                if (!spotifyAccessToken.isNullOrEmpty()) {
                    Toast.makeText(this, "Spotify Terhubung Berhasil!", Toast.LENGTH_SHORT).show()
                }
            } else {
                val token = uri.getQueryParameter("access_token")
                if (!token.isNullOrEmpty()) {
                    spotifyAccessToken = token
                    Toast.makeText(this, "Spotify Terhubung Berhasil!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadHazards()
    }

    override fun onDestroy() {
        super.onDestroy()
        noiseMeter.stop()
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient?.removeLocationUpdates(locationCallback!!)
        }
    }

    private fun loadHazards() {
        lifecycleScope.launch {
            try {
                val data = RetrofitClient.apiService.getHazards()
                adapter.updateData(data)
                for (item in data) {
                    val lat = item.latitude.toDoubleOrNull() ?: -6.3918
                    val lng = item.longitude.toDoubleOrNull() ?: 106.8214
                    val title = "${item.location_name} - ${item.noise_level} (${item.category})"
                    binding.webViewMap.evaluateJavascript("addHazardMarker($lat, $lng, '$title')", null)
                }
            } catch (_: Exception) {}
        }
    }

    private fun checkSensorPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        } else {
            startNoiseMonitoring()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            fetchCurrentLocation()
        }

        if (needed.isNotEmpty()) {
            requestRuntimePermissions.launch(needed.toTypedArray())
        }
    }

    private fun getNoiseCategory(db: Int): String {
        return when {
            db < 60 -> "Tenang (Aman)"
            db < 75 -> "Sedang (Wajar)"
            db < 85 -> "Bising (Waspada)"
            else -> "Sangat Bising (Bahaya!)"
        }
    }

    private fun getHeartRateCategory(bpm: Int): String {
        return when {
            bpm < 80 -> "Rileks"
            bpm < 100 -> "Normal"
            else -> "Terindikasi Stres"
        }
    }

    private fun startNoiseMonitoring() {
        noiseMeter.start()
        val prefs = getSharedPreferences("shield_prefs", Context.MODE_PRIVATE)
        val thresholdDb = prefs.getInt("threshold_db", 75)
        val thresholdBpm = prefs.getInt("threshold_bpm", 105)

        lifecycleScope.launch(Dispatchers.Default) {
            while (isActive) {
                val db = noiseMeter.getDecibel()
                currentDb = db
                withContext(Dispatchers.Main) {
                    binding.tvLiveDb.text = "dB: $db"
                    evaluateSensoryShield(db, currentBpm, thresholdDb, thresholdBpm)
                }
                delay(1500)
            }
        }
    }

    private fun evaluateSensoryShield(db: Int, bpm: Int, maxDb: Int, maxBpm: Int) {
        if (db >= maxDb || bpm >= maxBpm) {
            binding.tvShieldStatus.text = "⚠️ PERINGATAN: Kebisingan ($db dB) melebihi batas nyaman ($maxDb dB)! Disarankan menyalakan audio penenang."
            binding.tvShieldStatus.setTextColor(ContextCompat.getColor(this, R.color.calm_warning))
        } else {
            binding.tvShieldStatus.text = "🛡️ Status Perisai: Lingkungan Tenang & Aman (Batas Nyaman: $maxDb dB)"
            binding.tvShieldStatus.setTextColor(ContextCompat.getColor(this, R.color.calm_accent))
        }
    }

    private fun searchSafeRoute(destinationQuery: String) {
        lifecycleScope.launch {
            try {
                val originLat = currentLat.toDoubleOrNull() ?: -6.3686
                val originLon = currentLng.toDoubleOrNull() ?: 106.8315

                val geoResponse = MapApiClient.apiService.searchDestination(
                    destinationQuery = destinationQuery,
                    accessToken = ApiConfig.MAPBOX_ACCESS_TOKEN,
                    proximity = "$originLon,$originLat"
                )

                val features = geoResponse.getAsJsonArray("features")
                if (features == null || features.size() == 0) {
                    Toast.makeText(this@MainActivity, "Lokasi '$destinationQuery' tidak ditemukan", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val firstPlace = features[0].asJsonObject
                val placeName = firstPlace.get("place_name").asString
                val center = firstPlace.getAsJsonArray("center")
                val destLon = center[0].asDouble
                val destLat = center[1].asDouble

                try {
                    val routeResponse = MapApiClient.apiService.getWalkingRoute(
                        coordinates = "$originLon,$originLat;$destLon,$destLat",
                        accessToken = ApiConfig.MAPBOX_ACCESS_TOKEN
                    )

                    val routes = routeResponse.getAsJsonArray("routes")
                    if (routes != null && routes.size() > 0) {
                        val bestRoute = routes[0].asJsonObject
                        val distanceMeters = bestRoute.get("distance").asDouble.toInt()
                        val durationMinutes = (bestRoute.get("duration").asDouble / 60).toInt().coerceAtLeast(1)

                        val geometry = bestRoute.getAsJsonObject("geometry")
                        val coords = geometry?.getAsJsonArray("coordinates")
                        val coordsJsonString = coords?.toString() ?: "[[$originLon,$originLat], [$destLon,$destLat]]"

                        val legs = bestRoute.getAsJsonArray("legs")
                        val steps = legs[0].asJsonObject.getAsJsonArray("steps")
                        val firstInstruction = if (steps != null && steps.size() > 0) {
                            steps[0].asJsonObject.getAsJsonObject("maneuver").get("instruction").asString
                        } else {
                            "Menuju $destinationQuery via rute tenang"
                        }

                        latestDestinationName = placeName.substringBefore(",")
                        latestTurnInstruction = firstInstruction
                        latestRouteCoordsJson = coordsJsonString

                        binding.layoutRouteResult.visibility = View.VISIBLE
                        binding.tvRouteSummary.text = "Sensory-Safe Route • $durationMinutes mnt ($distanceMeters m)\nTujuan: $latestDestinationName"
                        binding.tvRouteFirstStep.text = "Arah: $firstInstruction"

                        binding.webViewMap.evaluateJavascript("drawPolylineRoute($coordsJsonString)", null)
                        return@launch
                    }
                } catch (_: Exception) {}

                // Fallback rute jika jalan kaki terlalu jauh / Mapbox return NoRoute
                latestDestinationName = placeName.substringBefore(",")
                latestTurnInstruction = "Jalan terus menuju $latestDestinationName"
                val fallbackCoords = "[[$originLon,$originLat], [${(originLon + destLon) / 2 + 0.001}, ${(originLat + destLat) / 2 - 0.001}], [$destLon,$destLat]]"
                latestRouteCoordsJson = fallbackCoords

                binding.layoutRouteResult.visibility = View.VISIBLE
                binding.tvRouteSummary.text = "Sensory-Safe Route (Rute Tenang)\nTujuan: $latestDestinationName"
                binding.tvRouteFirstStep.text = "Arah: $latestTurnInstruction"

                binding.webViewMap.evaluateJavascript("drawPolylineRoute($fallbackCoords)", null)

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Gagal memuat lokasi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var currentStreetName = "Lokasi Terdeteksi GPS"

    private fun setupSearchAutocomplete() {
        val autoComplete = binding.etSearchDestination
        val placeNames = mutableListOf<String>()
        val placeCoords = mutableMapOf<String, List<Double>>()

        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, placeNames)
        autoComplete.setAdapter(adapter)

        autoComplete.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.length >= 2) {
                    lifecycleScope.launch {
                        try {
                            val response = MapApiClient.apiService.searchPlaces(query)
                            placeNames.clear()
                            placeCoords.clear()
                            for (feature in response.features) {
                                placeNames.add(feature.place_name)
                                if (feature.center != null && feature.center.size >= 2) {
                                    placeCoords[feature.place_name] = feature.center
                                }
                            }
                            adapter.notifyDataSetChanged()
                            autoComplete.showDropDown()
                        } catch (_: Exception) {}
                    }
                }
            }
        })

        autoComplete.setOnItemClickListener { _, _, position, _ ->
            val selectedPlace = adapter.getItem(position) ?: return@setOnItemClickListener
            autoComplete.setText(selectedPlace)
            val coords = placeCoords[selectedPlace]
            if (coords != null && coords.size >= 2) {
                val lng = coords[0]
                val lat = coords[1]
                searchAndDrawRoute(selectedPlace, lng, lat)
            }
        }
    }

    private fun searchAndDrawRoute(destName: String, destLng: Double, destLat: Double) {
        lifecycleScope.launch {
            try {
                val routeResp = MapApiClient.apiService.getCalmWalkingRoute(
                    originLng = currentLng,
                    originLat = currentLat,
                    destLng = destLng.toString(),
                    destLat = destLat.toString()
                )
                val route = routeResp.routes.firstOrNull()
                if (route != null) {
                    val distKm = String.format("%.1f km", route.distance / 1000)
                    val durationMins = (route.duration / 60).toInt()
                    Toast.makeText(this@MainActivity, "Rute Aman ke $destName: $distKm (~$durationMins min)", Toast.LENGTH_LONG).show()

                    binding.webViewMap.evaluateJavascript("drawRoute($currentLat, $currentLng, $destLat, $destLng)", null)
                } else {
                    Toast.makeText(this@MainActivity, "Rute tidak ditemukan", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Gagal memuat rute: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMapWebView() {
        binding.webViewMap.settings.javaScriptEnabled = true
        binding.webViewMap.settings.domStorageEnabled = true
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
                    var map = L.map('map', {zoomControl: false, attributionControl: false}).setView([-6.3918, 106.8214], 15);
                    
                    // Menggunakan Mapbox Dark Style Tiles dengan Access Token resmi
                    var mapboxToken = '$mapboxToken';
                    L.tileLayer('https://api.mapbox.com/styles/v1/mapbox/dark-v11/tiles/{z}/{x}/{y}@2x?access_token=' + mapboxToken, {
                        tileSize: 512,
                        zoomOffset: -1,
                        maxZoom: 19,
                        attribution: '© Mapbox'
                    }).addTo(map);

                    var marker = L.marker([-6.3918, 106.8214]).addTo(map);
                    
                    var latlngs = [
                        [-6.3918, 106.8214],
                        [-6.3890, 106.8230],
                        [-6.3850, 106.8250]
                    ];
                    var polyline = L.polyline(latlngs, {color: '#6BA392', weight: 5}).addTo(map);

                    var currentRoutePolyline = null;
                    var destMarker = null;

                    function updateLocation(lat, lng) {
                        var newLatLng = new L.LatLng(lat, lng);
                        marker.setLatLng(newLatLng);
                        map.panTo(newLatLng);
                    }

                    function addHazardMarker(lat, lng, title) {
                        var hazardIcon = L.divIcon({
                            className: 'hazard-marker',
                            html: '<div style="background-color: #C96A6A; width: 14px; height: 14px; border-radius: 50%; border: 2px solid #E6ECEF;"></div>',
                            iconSize: [14, 14]
                        });
                        L.marker([lat, lng], {icon: hazardIcon}).addTo(map).bindPopup(title);
                    }

                    function drawPolylineRoute(coords) {
                        if (currentRoutePolyline) {
                            map.removeLayer(currentRoutePolyline);
                        }
                        if (destMarker) {
                            map.removeLayer(destMarker);
                        }
                        if (!coords || coords.length === 0) return;

                        var leafletLatLngs = coords.map(function(pt) {
                            return [pt[1], pt[0]];
                        });

                        var endPoint = leafletLatLngs[leafletLatLngs.length - 1];
                        destMarker = L.marker(endPoint).addTo(map).bindPopup("Tujuan").openPopup();

                        currentRoutePolyline = L.polyline(leafletLatLngs, {
                            color: '#6BA392',
                            weight: 6,
                            opacity: 0.85
                        }).addTo(map);

                        map.fitBounds(currentRoutePolyline.getBounds(), {padding: [40, 40]});
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        binding.webViewMap.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 4000
        ).setMinUpdateIntervalMillis(2000).build()

        locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                val location = locationResult.lastLocation ?: return
                currentLat = String.format("%.4f", location.latitude)
                currentLng = String.format("%.4f", location.longitude)
                binding.tvLiveGps.text = "GPS: $currentLat, $currentLng (Memperbarui jalan...)"
                binding.webViewMap.evaluateJavascript("updateLocation($currentLat, $currentLng)", null)

                // Panggil Mapbox Geocoding API secara real-time via Coroutine
                lifecycleScope.launch {
                    try {
                        val geo = MapApiClient.apiService.getAddressFromCoords(
                            lng = currentLng,
                            lat = currentLat
                        )
                        val place = geo.features.firstOrNull()?.place_name
                        if (!place.isNullOrEmpty()) {
                            currentStreetName = place
                            binding.tvLiveGps.text = "Lokasi: $place"
                            binding.tvCurrentPositionInfo.text = "Posisi Anda:\n$place"
                        }
                    } catch (_: Exception) {
                        binding.tvLiveGps.text = "GPS Aktif: $currentLat, $currentLng"
                    }
                }
            }
        }

        try {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                android.os.Looper.getMainLooper()
            )
        } catch (_: Exception) {}
    }

    private fun checkAndReadHealthConnect() {
        val status = HealthConnectClient.getSdkStatus(this)
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            Toast.makeText(this, "Aplikasi Health Connect belum tersedia di perangkat ini", Toast.LENGTH_LONG).show()
            return
        }
        val client = HealthConnectClient.getOrCreate(this)
        lifecycleScope.launch {
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.containsAll(healthPermissions)) {
                readHeartRateFromOraimo()
            } else {
                requestHealthPermissionLauncher.launch(healthPermissions)
            }
        }
    }

    private fun readHeartRateFromOraimo() {
        val client = HealthConnectClient.getOrCreate(this)
        lifecycleScope.launch {
            try {
                val endTime = Instant.now()
                val startTime = endTime.minus(30, ChronoUnit.MINUTES)
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                    )
                )
                val latestBpm = response.records.lastOrNull()?.samples?.lastOrNull()?.beatsPerMinute
                if (latestBpm != null) {
                    currentBpm = latestBpm.toInt()
                    binding.tvLiveHr.text = "BPM: $currentBpm"
                } else {
                    binding.tvLiveHr.text = "BPM: $currentBpm"
                    Toast.makeText(this@MainActivity, "Belum ada rekaman baru dari Oraimo 30 menit terakhir", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Gagal baca Health Connect: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCalmingSpotifyPlaylist() {
        // Membuka playlist Ambient / Brown Noise penenang di Spotify
        val spotifyUri = Uri.parse("spotify:playlist:37i9dQZF1DWZd79rJ6a7lp")
        val intent = Intent(Intent.ACTION_VIEW, spotifyUri)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/playlist/37i9dQZF1DWZd79rJ6a7lp"))
            startActivity(webIntent)
        }
    }
}