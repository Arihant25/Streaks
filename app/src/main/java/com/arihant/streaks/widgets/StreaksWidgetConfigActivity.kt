package com.arihant.streaks.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.arihant.streaks.data.Streak
import com.arihant.streaks.data.StreakRepository
import com.arihant.streaks.databinding.ActivityWidgetConfigBinding
import com.google.android.material.checkbox.MaterialCheckBox

/**
 * Shown when a widget is placed and when it is reconfigured from the launcher
 * (long-press -> reconfigure). Lets the user pick a layout mode and which
 * streaks the widget shows.
 */
class StreaksWidgetConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigBinding
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val checkboxes = mutableMapOf<String, MaterialCheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId =
                intent?.extras?.getInt(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID
                )
                        ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Cancelling (back press) must not add the widget
        setResult(
                RESULT_CANCELED,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repository = StreakRepository.getInstance()
        repository.loadStreaksFromFile(this)
        val streaks = (repository.streaks.value ?: emptyList()).sortedBy { it.position }
        val config = WidgetPrefs.load(this, appWidgetId)

        when (config.mode) {
            WidgetPrefs.Mode.AUTO -> binding.configModeAuto.isChecked = true
            WidgetPrefs.Mode.GRID -> binding.configModeGrid.isChecked = true
            WidgetPrefs.Mode.LIST -> binding.configModeList.isChecked = true
        }

        populateStreakList(streaks, config)

        binding.configSave.setOnClickListener { saveAndFinish(streaks) }
    }

    private fun populateStreakList(streaks: List<Streak>, config: WidgetPrefs.Config) {
        binding.configStreakList.removeAllViews()
        checkboxes.clear()
        for (streak in streaks) {
            val checkbox =
                    MaterialCheckBox(this).apply {
                        text = "${streak.emoji}  ${streak.name}"
                        isChecked =
                                config.selectedIds == null || streak.id in config.selectedIds
                        layoutParams =
                                LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                    }
            checkboxes[streak.id] = checkbox
            binding.configStreakList.addView(checkbox)
        }
    }

    private fun saveAndFinish(streaks: List<Streak>) {
        val mode =
                when (binding.configModeGroup.checkedRadioButtonId) {
                    binding.configModeGrid.id -> WidgetPrefs.Mode.GRID
                    binding.configModeList.id -> WidgetPrefs.Mode.LIST
                    else -> WidgetPrefs.Mode.AUTO
                }
        val checkedIds = checkboxes.filterValues { it.isChecked }.keys
        // Everything checked (or nothing) means "all streaks", so streaks added
        // later show up without reconfiguring the widget
        val selectedIds =
                if (checkedIds.isEmpty() || checkedIds.size == streaks.size) null
                else checkedIds.toSet()

        WidgetPrefs.save(this, appWidgetId, WidgetPrefs.Config(mode, selectedIds))
        StreaksWidgetProvider.updateAppWidget(
                this,
                AppWidgetManager.getInstance(this),
                appWidgetId
        )

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}
