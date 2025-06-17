package com.example.streaks.ui.dialogs

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.streaks.R
import com.example.streaks.data.FrequencyType
import com.example.streaks.databinding.DialogAddStreakBinding
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.text.InputFilter

class AddStreakDialog(
    private val onStreakAdded: (String, String, FrequencyType, Int) -> Unit,
    private val isEditMode: Boolean = false,
    private val initialFrequency: FrequencyType? = null,
    private val initialFrequencyCount: Int? = null
) : DialogFragment() {

    private var _binding: DialogAddStreakBinding? = null
    private val binding get() = _binding!!
    
    private var selectedEmoji: String = "🔥"
    private val EMOJI_PICKER_REQUEST = 1001

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddStreakBinding.inflate(layoutInflater)
        
        setupEmojiPicker()
        setupFrequencySpinner()
        setupClickListeners()
        
        if (isEditMode) {
            // Prefill frequency and count, and disable editing
            initialFrequency?.let {
                val freqIndex = when (it) {
                    FrequencyType.DAILY -> 0
                    FrequencyType.WEEKLY -> 1
                    FrequencyType.MONTHLY -> 2
                    FrequencyType.YEARLY -> 3
                }
                binding.spinnerFrequency.setSelection(freqIndex)
                binding.spinnerFrequency.isEnabled = false
            }
            initialFrequencyCount?.let {
                binding.editFrequencyCount.setText(it.toString())
                binding.editFrequencyCount.isEnabled = false
            }
        }
        
        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
    }

    private fun setupEmojiPicker() {
        binding.selectedEmoji.text = selectedEmoji
        binding.cardEmojiPicker.setOnClickListener {
            // Show a simple emoji input dialog
            val input = EditText(requireContext())
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            input.hint = if (selectedEmoji == "🔥") "🔥" else ""
            input.setText("") // Do not prefill
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
        val frequencyOptions = arrayOf(
            getString(R.string.daily),
            getString(R.string.weekly),
            getString(R.string.monthly),
            getString(R.string.yearly)
        )
        
        // Create and set adapter
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            frequencyOptions
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFrequency.adapter = adapter
        
        // Set up spinner listener to show/hide frequency count input
        binding.spinnerFrequency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
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

    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        
        binding.btnCreate.setOnClickListener {
            createStreak()
        }
        // Prevent multi-line input for streak name
        binding.editStreakName.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                v.clearFocus()
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
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
        
        if ((frequencyPosition == 1 || frequencyPosition == 2 || frequencyPosition == 3) && frequencyCount <= 0) {
            binding.inputLayoutCount.error = "Please enter a valid number"
            return
        }
        
        val frequency = when (frequencyPosition) {
            0 -> FrequencyType.DAILY
            1 -> FrequencyType.WEEKLY
            2 -> FrequencyType.MONTHLY
            3 -> FrequencyType.YEARLY
            else -> FrequencyType.DAILY
        }
        
        onStreakAdded(name, emoji, frequency, frequencyCount)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
