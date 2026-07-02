package com.arihant.streaks.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.data.Streak
import com.arihant.streaks.databinding.ItemStreakCardBinding

class StreaksAdapter(
        private val onStreakToggled: (String, Boolean) -> Unit,
        private val onStreakClicked: (Streak, View) -> Unit
) : ListAdapter<Streak, StreaksAdapter.StreakViewHolder>(DiffCallback()) {

        // Whether to prefix active streak counts with 🔥 (user setting)
        var showFlame: Boolean = true
                set(value) {
                        if (field != value) {
                                field = value
                                notifyDataSetChanged()
                        }
                }

        class StreakViewHolder(private val binding: ItemStreakCardBinding) :
                RecyclerView.ViewHolder(binding.root) {

                fun bind(
                        streak: Streak,
                        onToggled: (String, Boolean) -> Unit,
                        onClicked: (Streak, View) -> Unit,
                        showFlame: Boolean
                ) {
                        binding.emojiIcon.text = streak.emoji
                        binding.streakName.text = streak.name
                        binding.streakCount.text =
                                if (streak.currentStreak == 0) {
                                        binding.root.context.getString(R.string.streak_not_started)
                                } else {
                                        val unit =
                                                when (streak.frequency) {
                                                        FrequencyType.DAILY ->
                                                                binding.root.context.resources
                                                                        .getQuantityString(
                                                                                R.plurals
                                                                                        .streak_days,
                                                                                streak.currentStreak,
                                                                                streak.currentStreak
                                                                        )
                                                        FrequencyType.WEEKLY ->
                                                                binding.root.context.resources
                                                                        .getQuantityString(
                                                                                R.plurals
                                                                                        .streak_weeks,
                                                                                streak.currentStreak,
                                                                                streak.currentStreak
                                                                        )
                                                        FrequencyType.MONTHLY ->
                                                                binding.root.context.resources
                                                                        .getQuantityString(
                                                                                R.plurals
                                                                                        .streak_months,
                                                                                streak.currentStreak,
                                                                                streak.currentStreak
                                                                        )
                                                        FrequencyType.YEARLY ->
                                                                binding.root.context.resources
                                                                        .getQuantityString(
                                                                                R.plurals
                                                                                        .streak_years,
                                                                                streak.currentStreak,
                                                                                streak.currentStreak
                                                                        )
                                                }
                                        if (showFlame) "🔥 $unit" else unit
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
                                if (!streak.isCompletedToday) {
                                        // Completing: satisfying pop + confirm haptic
                                        binding.completionCircle.performHapticFeedback(
                                                android.view.HapticFeedbackConstants.CONFIRM
                                        )
                                        // Color pulse bursting outward from the circle
                                        val pulseDrawable =
                                                android.graphics.drawable.GradientDrawable()
                                        pulseDrawable.shape =
                                                android.graphics.drawable.GradientDrawable.OVAL
                                        pulseDrawable.setColor(color)
                                        binding.pulseRing.background = pulseDrawable
                                        binding.pulseRing.visibility = View.VISIBLE
                                        binding.pulseRing.alpha = 0.4f
                                        binding.pulseRing.scaleX = 1f
                                        binding.pulseRing.scaleY = 1f
                                        binding.pulseRing
                                                .animate()
                                                .scaleX(2.1f)
                                                .scaleY(2.1f)
                                                .alpha(0f)
                                                .setDuration(500)
                                                .withEndAction {
                                                        binding.pulseRing.visibility =
                                                                View.INVISIBLE
                                                }
                                                .start()
                                        binding.completionCircle
                                                .animate()
                                                .scaleX(1.2f)
                                                .scaleY(1.2f)
                                                .setDuration(120)
                                                .withEndAction {
                                                        binding.completionCircle
                                                                .animate()
                                                                .scaleX(1f)
                                                                .scaleY(1f)
                                                                .setDuration(200)
                                                                .setInterpolator(
                                                                        android.view.animation
                                                                                .OvershootInterpolator()
                                                                )
                                                                .start()
                                                }
                                                .start()
                                } else {
                                        binding.completionCircle.performHapticFeedback(
                                                android.view.HapticFeedbackConstants.VIRTUAL_KEY
                                        )
                                }
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
                holder.bind(getItem(position), onStreakToggled, onStreakClicked, showFlame)
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
