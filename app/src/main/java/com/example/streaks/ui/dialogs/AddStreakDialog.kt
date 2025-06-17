package com.example.streaks.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.streaks.R
import com.example.streaks.data.FrequencyType
import com.example.streaks.databinding.DialogAddStreakBinding
import com.example.streaks.ui.adapters.EmojiAdapter

class AddStreakDialog(
    private val onStreakAdded: (String, String, FrequencyType, Int) -> Unit
) : DialogFragment() {

    private var _binding: DialogAddStreakBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var emojiAdapter: EmojiAdapter
    
    private val emojis = listOf(
        "🏃", "💪", "📚", "🎯", "💧", "🥗", "🧘", "🎵", "✍️", "🌅",
        "🏋️", "🚶", "📱", "💻", "🎨", "🍎", "☕", "🛌", "🧠", "❤️"
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddStreakBinding.inflate(layoutInflater)
        
        setupEmojiRecycler()
        setupFrequencySpinner()
        setupClickListeners()
        
        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
    }

    private fun setupEmojiRecycler() {
        emojiAdapter = EmojiAdapter(emojis) { /* emoji selected */ }
        binding.emojiRecycler.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = emojiAdapter
        }
    }

    private fun setupFrequencySpinner() {
        val frequencies = arrayOf(
            getString(R.string.daily),
            getString(R.string.weekly),
            getString(R.string.monthly),
            getString(R.string.yearly)
        )
        
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            frequencies
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFrequency.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        
        binding.btnCreate.setOnClickListener {
            createStreak()
        }
    }

    private fun createStreak() {
        val name = binding.editStreakName.text.toString().trim()
        val emoji = emojiAdapter.getSelectedEmoji()
        val frequencyPosition = binding.spinnerFrequency.selectedItemPosition
        val frequencyCount = binding.editFrequencyCount.text.toString().toIntOrNull() ?: 1
        
        if (name.isBlank()) {
            binding.inputLayoutName.error = "Please enter a name"
            return
        }
        
        if (frequencyCount <= 0) {
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
