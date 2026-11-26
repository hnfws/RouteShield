package id.ac.stmi.api

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import id.ac.stmi.api.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val prefs = getSharedPreferences("shield_prefs", Context.MODE_PRIVATE)

        binding.btnSaveOnboarding.setOnClickListener {
            val thresholdBpm = when (binding.rgExercise.checkedRadioButtonId) {
                R.id.rbRarely -> 95
                R.id.rbAthlete -> 120
                else -> 108
            }

            val thresholdDb = when (binding.rgTrigger.checkedRadioButtonId) {
                R.id.rbCrowd -> 68
                R.id.rbConstruction -> 80
                else -> 75
            }

            prefs.edit()
                .putBoolean("is_calibrated", true)
                .putInt("threshold_db", thresholdDb)
                .putInt("threshold_bpm", thresholdBpm)
                .apply()

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
