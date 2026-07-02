package com.arihant.streaks.ui.dialogs

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.databinding.DialogAddStreakBinding
import java.text.BreakIterator

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
        setupColorWheel()
        setupClickListeners()

        if (isEditMode) {
            // Prefill name and emoji
            initialName?.let { binding.editStreakName.setText(it) }
            initialEmoji?.let {
                selectedEmoji = it
                binding.selectedEmoji.text = it
            }
            // Prefill frequency but keep it editable; saving recalculates the
            // streak counts against the new frequency
            initialFrequency?.let { frequency ->
                val freqIndex =
                        when (frequency) {
                            FrequencyType.DAILY -> 0
                            FrequencyType.WEEKLY -> 1
                            FrequencyType.MONTHLY -> 2
                            FrequencyType.YEARLY -> 3
                        }
                binding.spinnerFrequency.setSelection(freqIndex)
            }
            initialFrequencyCount?.let {
                binding.editFrequencyCount.setText(it.toString())
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

        return MaterialAlertDialogBuilder(requireContext()).setView(binding.root).create()
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
            MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Pick Emoji")
                    .setView(input)
                    .setPositiveButton("OK") { _, _ ->
                        val emoji = input.text.toString().trim()
                        when {
                            emoji.isEmpty() -> {
                                // Keep the current emoji
                            }
                            isSingleEmoji(emoji) -> {
                                selectedEmoji = emoji
                                binding.selectedEmoji.text = selectedEmoji
                            }
                            else ->
                                    Toast.makeText(
                                                    requireContext(),
                                                    "Please pick a single emoji",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                        }
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

    private fun setupColorWheel() {
        val initial =
                try {
                    Color.parseColor(selectedColor)
                } catch (e: Exception) {
                    Color.parseColor("#FF9900")
                }
        binding.hueWheel.setColor(initial)
        binding.hueWheel.onColorChanged = { color ->
            selectedColor = String.format("#%06X", 0xFFFFFF and color)
        }
    }

    /** Exactly one grapheme cluster, and it has to be an emoji — not letters or digits. */
    private fun isSingleEmoji(text: String): Boolean {
        if (text.isEmpty()) return false
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        iterator.first()
        if (iterator.next() != text.length) return false

        var hasEmojiCodePoint = false
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            // Digits only appear in keycap sequences like 1️⃣ (which contain U+20E3)
            if (Character.isLetterOrDigit(codePoint) || Character.isWhitespace(codePoint)) {
                if (!text.contains('⃣')) return false
            }
            if (codePoint >= 0x2000) hasEmojiCodePoint = true
            i += Character.charCount(codePoint)
        }
        return hasEmojiCodePoint
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
