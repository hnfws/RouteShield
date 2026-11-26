package id.ac.stmi.api

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import id.ac.stmi.api.data.api.RetrofitClient
import id.ac.stmi.api.databinding.ActivityInsightBinding
import kotlinx.coroutines.launch

class InsightActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInsightBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsightBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        loadRealtimeAnalytics()
    }

    private fun loadRealtimeAnalytics() {
        lifecycleScope.launch {
            try {
                val hazards = RetrofitClient.apiService.getHazards()
                
                binding.tvSafeTrips.text = "Total Perjalanan Aman: ${hazards.size}"

                val dbValues = hazards.mapNotNull { 
                    it.noise_level.replace("dB", "").trim().toIntOrNull() 
                }
                
                if (dbValues.isNotEmpty()) {
                    val avgDb = dbValues.average().toInt()
                    val status = if (avgDb < 75) "Normal" else "Waspada"
                    binding.tvAvgBpm.text = "Rate-rata BPM: $avgDb bpm ($status)"
                } else {
                    binding.tvAvgBpm.text = "Rate-rata BPM: 64 bpm (Normal)"
                }

                // Buat Kartu Insiden Individu Sesuai Gambar #6
                binding.layoutIncidentCards.removeAllViews()

                if (hazards.isEmpty()) {
                    val emptyTv = TextView(this@InsightActivity).apply {
                        text = "Belum ada insiden kebisingan tercatat di server."
                        setTextColor(ContextCompat.getColor(this@InsightActivity, R.color.calm_text_secondary))
                        textSize = 14f
                        setPadding(16, 16, 16, 16)
                    }
                    binding.layoutIncidentCards.addView(emptyTv)
                } else {
                    hazards.forEachIndexed { index, item ->
                        val card = createIncidentCard(
                            incidentNumber = index + 1,
                            locationName = item.location_name,
                            peakNoise = item.noise_level,
                            category = item.category
                        )
                        binding.layoutIncidentCards.addView(card)
                    }
                }

            } catch (_: Exception) {
                binding.tvSafeTrips.text = "Total Perjalanan Aman: --"
                binding.tvAvgBpm.text = "Rate-rata BPM: Gagal Memuat"
            }
        }
    }

    private fun createIncidentCard(
        incidentNumber: Int,
        locationName: String,
        peakNoise: String,
        category: String
    ): View {
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@InsightActivity, R.drawable.bg_turn_banner)
            setPadding(32, 32, 32, 32)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 24)
            layoutParams = params
        }

        val tvTitle = TextView(this).apply {
            text = "Insiden $incidentNumber"
            setTextColor(ContextCompat.getColor(this@InsightActivity, R.color.calm_text_primary))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val tvSubtitle = TextView(this).apply {
            text = "14:30 — $locationName"
            setTextColor(ContextCompat.getColor(this@InsightActivity, R.color.calm_text_primary))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 12)
        }

        val tvPeak = TextView(this).apply {
            text = "Puncak: $peakNoise ($category)"
            setTextColor(ContextCompat.getColor(this@InsightActivity, R.color.calm_text_secondary))
            textSize = 13f
        }

        val tvAudio = TextView(this).apply {
            text = "Audio: Brown Noise"
            setTextColor(ContextCompat.getColor(this@InsightActivity, R.color.calm_text_secondary))
            textSize = 13f
            setPadding(0, 4, 0, 0)
        }

        val tvRecovery = TextView(this).apply {
            text = "Pulih: 3 menit"
            setTextColor(ContextCompat.getColor(this@InsightActivity, R.color.calm_text_secondary))
            textSize = 13f
            setPadding(0, 4, 0, 0)
        }

        cardLayout.addView(tvTitle)
        cardLayout.addView(tvSubtitle)
        cardLayout.addView(tvPeak)
        cardLayout.addView(tvAudio)
        cardLayout.addView(tvRecovery)

        return cardLayout
    }
}
