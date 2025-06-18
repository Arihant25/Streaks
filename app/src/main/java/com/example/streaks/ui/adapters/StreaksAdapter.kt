package com.example.streaks.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.streaks.R
import com.example.streaks.data.Streak
import com.example.streaks.databinding.ItemStreakCardBinding

class StreaksAdapter(
        private val onStreakToggled: (String, Boolean) -> Unit,
        private val onStreakClicked: (Streak, View) -> Unit
) : ListAdapter<Streak, StreaksAdapter.StreakViewHolder>(DiffCallback()) {

        class StreakViewHolder(private val binding: ItemStreakCardBinding) :
                RecyclerView.ViewHolder(binding.root) {

                fun bind(
                        streak: Streak,
                        onToggled: (String, Boolean) -> Unit,
                        onClicked: (Streak, View) -> Unit
                ) {
                        binding.emojiIcon.text = streak.emoji
                        binding.streakName.text = streak.name
                        binding.streakCount.text =
                                if (streak.currentStreak == 0) {
                                        binding.root.context.getString(R.string.streak_not_started)
                                } else {
                                        val unit =
                                                when (streak.frequency) {
                                                        com.example.streaks.data.FrequencyType
                                                                .DAILY ->
                                                                binding.root.context.resources
                                                                        .getQuantityString(
                                                                                R.plurals
                                                                                        .streak_days,
                                                                                streak.currentStreak,
                                                                                streak.currentStreak
                                                                        )
                                                        com.example.streaks.data.FrequencyType
                                                                .WEEKLY ->
                                                                binding.root.context.resources
                                                                        .getQuantityString(
                                                                                R.plurals
                                                                                        .streak_weeks,
                                                                                streak.currentStreak,
                                                                                streak.currentStreak
                                                                        )
                                                        com.example.streaks.data.FrequencyType
                                                                .MONTHLY ->
                                                                binding.root.context.resources
                                                                        .getQuantityString(
                                                                                R.plurals
                                                                                        .streak_months,
                                                                                streak.currentStreak,
                                                                                streak.currentStreak
                                                                        )
                                                        com.example.streaks.data.FrequencyType
                                                                .YEARLY ->
                                                                binding.root.context.resources
                                                                        .getQuantityString(
                                                                                R.plurals
                                                                                        .streak_years,
                                                                                streak.currentStreak,
                                                                                streak.currentStreak
                                                                        )
                                                }
                                        unit
                                }

                        // Update completion circle
                        binding.completionCircle.isSelected = streak.isCompletedToday
                        binding.checkIcon.isVisible = streak.isCompletedToday
                        // Tint the completion circle and check icon with streak color
                        val color =
                                try {
                                        android.graphics.Color.parseColor(streak.color)
                                } catch (e: Exception) {
                                        android.graphics.Color.parseColor("#FF9900")
                                }
                        if (streak.isCompletedToday) {
                                val drawable = android.graphics.drawable.GradientDrawable()
                                drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
                                drawable.setColor(color)
                                binding.completionCircle.background = drawable
                                binding.checkIcon.setColorFilter(android.graphics.Color.WHITE)
                        } else {
                                binding.completionCircle.background =
                                        binding.root.context.getDrawable(
                                                R.drawable.circle_background
                                        )
                                binding.checkIcon.setColorFilter(color)
                        }

                        binding.completionCircle.setOnClickListener {
                                // Haptic feedback
                                binding.completionCircle.performHapticFeedback(
                                        android.view.HapticFeedbackConstants.VIRTUAL_KEY
                                )
                                onToggled(streak.id, !streak.isCompletedToday)
                        }
                        // Add click listener for the whole card
                        binding.root.setOnClickListener { onClicked(streak, binding.root) }

                        // Set transitionName on the card container
                        binding.root.transitionName = "streak_card_${streak.id}"
                }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreakViewHolder {
                val binding =
                        ItemStreakCardBinding.inflate(
                                LayoutInflater.from(parent.context),
                                parent,
                                false
                        )
                return StreakViewHolder(binding)
        }

        override fun onBindViewHolder(holder: StreakViewHolder, position: Int) {
                holder.bind(getItem(position), onStreakToggled, onStreakClicked)
        }

        private class DiffCallback : DiffUtil.ItemCallback<Streak>() {
                override fun areItemsTheSame(oldItem: Streak, newItem: Streak): Boolean {
                        return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(oldItem: Streak, newItem: Streak): Boolean {
                        return oldItem == newItem
                }
        }

        fun moveItem(fromPosition: Int, toPosition: Int) {
                val currentList = currentList.toMutableList()
                val item = currentList.removeAt(fromPosition)
                currentList.add(toPosition, item)
                submitList(currentList)
        }
}
