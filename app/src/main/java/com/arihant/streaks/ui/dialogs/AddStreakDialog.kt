package com.arihant.streaks.ui.dialogs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.databinding.DialogAddStreakBinding

class AddStreakDialog(
        private val onStreakAdded: (String, String, FrequencyType, Int, String) -> Unit,
        private val isEditMode: Boolean = false,
        private val initialFrequency: FrequencyType? = null,
        private val initialFrequencyCount: Int? = null,
        private val initialName: String? = null,
        private val initialEmoji: String? = null,
        private val initialColor: String? = null
) : DialogFragment() {

    private var _binding: DialogAddStreakBinding? = null
    private val binding
        get() = _binding!!

    private var selectedEmoji: String = "🔥"
    private val EMOJI_PICKER_REQUEST = 1001
    private var selectedColor: String = initialColor ?: "#FF9900"
    private val colorOptions =
            listOf(
                    "#FF9900", // neon_orange
                    "#F0F01B", // neon_yellow
                    "#B1E80D", // neon_green
                    "#0065F8", // neon_blue
                    "#FF2DF1", // neon_purple
                    "#F93827", // neon_red
                    "#FF55BB", // neon_pink
                    "#A3D8FF" // neon_cyan
            )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddStreakBinding.inflate(layoutInflater)

        // Set dialog title based on mode
        val titleTextView = (_binding?.root as ViewGroup).getChildAt(0)
        if (titleTextView is android.widget.TextView) {
            titleTextView.text =
                    if (isEditMode) getString(R.string.edit_streak)
                    else getString(R.string.create_streak)
        }
        // Set button text based on mode
        binding.btnCreate.text =
                if (isEditMode) getString(R.string.edit_streak)
                else getString(R.string.create_streak)

        setupEmojiPicker()
        setupFrequencySpinner()
        setupColorGrid()
        setupClickListeners()

        if (isEditMode) {
            // Prefill name and emoji
            initialName?.let { binding.editStreakName.setText(it) }
            initialEmoji?.let {
                selectedEmoji = it
                binding.selectedEmoji.text = it
            }
            // Hide frequency-related views in edit mode
            binding.spinnerFrequency.visibility = View.GONE
            binding.inputLayoutCount.visibility = View.GONE
            // Hide the frequency label (TextView just above spinner)
            val parent = binding.spinnerFrequency.parent as ViewGroup
            val spinnerIndex = parent.indexOfChild(binding.spinnerFrequency)
            if (spinnerIndex > 0) {
                val labelView = parent.getChildAt(spinnerIndex - 1)
                if (labelView is android.widget.TextView &&
                                labelView.text == getString(R.string.frequency)
                ) {
                    labelView.visibility = View.GONE
                }
            }
        } else {
            if (initialFrequency != null) {
                val freqIndex =
                        when (initialFrequency) {
                            FrequencyType.DAILY -> 0
                            FrequencyType.WEEKLY -> 1
                            FrequencyType.MONTHLY -> 2
                            FrequencyType.YEARLY -> 3
                        }
                binding.spinnerFrequency.setSelection(freqIndex)
                binding.spinnerFrequency.isEnabled = false
            }
            if (initialFrequencyCount != null) {
                binding.editFrequencyCount.setText(initialFrequencyCount.toString())
                binding.editFrequencyCount.isEnabled = false
            }
        }

        return AlertDialog.Builder(requireContext()).setView(binding.root).create()
    }

    private fun setupEmojiPicker() {
        binding.selectedEmoji.text = selectedEmoji
        binding.cardEmojiPicker.setOnClickListener {
            // Show a simple emoji input dialog
            val input = EditText(requireContext())
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            input.hint = if (selectedEmoji == "🔥") "🔥" else ""
            input.setText("") // Do not prefill
            // Enable emoji support
            input.imeOptions = EditorInfo.IME_ACTION_DONE
            AlertDialog.Builder(requireContext())
                    .setTitle("Pick Emoji")
                    .setView(input)
                    .setPositiveButton("OK") { _, _ ->
                        val emoji = input.text.toString().trim()
                        selectedEmoji = if (emoji.isEmpty()) "🔥" else emoji
                        binding.selectedEmoji.text = selectedEmoji
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
        }
    }

    private fun setupFrequencySpinner() {
        // Create frequency options array
        val frequencyOptions =
                arrayOf(
                        getString(R.string.daily),
                        getString(R.string.weekly),
                        getString(R.string.monthly),
                        getString(R.string.yearly)
                )

        // Create and set adapter
        val adapter =
                ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        frequencyOptions
                )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFrequency.adapter = adapter

        // Set up spinner listener to show/hide frequency count input
        binding.spinnerFrequency.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long
                    ) {
                        when (position) {
                            0 -> { // Daily - hide frequency count
                                binding.inputLayoutCount.visibility = View.GONE
                                binding.editFrequencyCount.setText("1")
                            }
                            1, 2, 3 -> { // Weekly, Monthly, Yearly - show frequency count
                                binding.inputLayoutCount.visibility = View.VISIBLE
                                if (binding.editFrequencyCount.text.toString().isEmpty()) {
                                    binding.editFrequencyCount.setText("1")
                                }
                            }
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        // Do nothing
                    }
                }

        // Set default selection to Daily
        binding.spinnerFrequency.setSelection(0)
    }

    private fun setupColorGrid() {
        val grid = binding.colorGrid
        grid.removeAllViews()
        val context = requireContext()
        val size = (48 * context.resources.displayMetrics.density).toInt() // 48dp
        val margin = (6 * context.resources.displayMetrics.density).toInt() // 6dp
        colorOptions.forEach { colorHex ->
            val circle = View(context)
            val params =
                    GridLayout.LayoutParams().apply {
                        width = size
                        height = size
                        setMargins(margin, margin, margin, margin)
                    }
            circle.layoutParams = params
            circle.background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor(colorHex))
                        setStroke(
                                if (colorHex == selectedColor) 6 else 2,
                                if (colorHex == selectedColor) Color.BLACK else Color.LTGRAY
                        )
                    }
            circle.isSelected = colorHex == selectedColor
            circle.setOnClickListener {
                selectedColor = colorHex
                setupColorGrid() // Refresh selection
            }
            grid.addView(circle)
        }
    }

    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnCreate.setOnClickListener { createStreak() }
        // Prevent multi-line input for streak name
        binding.editStreakName.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                v.clearFocus()
                val imm =
                        requireContext()
                                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as
                                android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else {
                false
            }
        }
    }

    private fun createStreak() {
        val name = binding.editStreakName.text.toString().trim()
        val emoji = binding.selectedEmoji.text.toString().trim().ifEmpty { "🔥" }
        val frequencyPosition = binding.spinnerFrequency.selectedItemPosition
        val frequencyCount = binding.editFrequencyCount.text.toString().toIntOrNull() ?: 1

        if (name.isBlank()) {
            binding.inputLayoutName.error = "Please enter a name"
            return
        }

        if ((frequencyPosition == 1 || frequencyPosition == 2 || frequencyPosition == 3) &&
                        frequencyCount <= 0
        ) {
            binding.inputLayoutCount.error = "Please enter a valid number"
            return
        }

        val frequency =
                when (frequencyPosition) {
                    0 -> FrequencyType.DAILY
                    1 -> FrequencyType.WEEKLY
                    2 -> FrequencyType.MONTHLY
                    3 -> FrequencyType.YEARLY
                    else -> FrequencyType.DAILY
                }

        onStreakAdded(name, emoji, frequency, frequencyCount, selectedColor)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
