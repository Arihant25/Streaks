package com.arihant.streaks.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.data.Streak
import com.arihant.streaks.databinding.ItemStreakCardBinding
import com.arihant.streaks.utils.WeekConfig
import java.time.LocalDate

class StreaksAdapter(
        private val onStreakToggled: (String, Boolean, View) -> Unit,
        private val onStreakClicked: (Streak, View) -> Unit
) : ListAdapter<Streak, StreaksAdapter.StreakViewHolder>(DiffCallback()) {

        class StreakViewHolder(private val binding: ItemStreakCardBinding) :
                RecyclerView.ViewHolder(binding.root) {

                fun bind(
                        streak: Streak,
                        onToggled: (String, Boolean, View) -> Unit,
                        onClicked: (Streak, View) -> Unit
                ) {
                        val context = binding.root.context
                        val color =
                                try {
                                        android.graphics.Color.parseColor(streak.color)
                                } catch (e: Exception) {
                                        android.graphics.Color.parseColor("#FF9900")
                                }

                        binding.emojiIcon.text = streak.emoji
                        // Emoji chip tinted with the streak's own color
                        binding.emojiIcon.background =
                                android.graphics.drawable.GradientDrawable().apply {
                                        shape = android.graphics.drawable.GradientDrawable.OVAL
                                        setColor(
                                                androidx.core.graphics.ColorUtils
                                                        .setAlphaComponent(color, 38)
                                        )
                                }
                        binding.streakName.text = streak.name

                        // Completed cards get a soft wash + stroke of their accent color
                        val surface = ContextCompat.getColor(context, R.color.card_surface)
                        if (streak.isCompletedToday) {
                                binding.root.setCardBackgroundColor(
                                        androidx.core.graphics.ColorUtils.compositeColors(
                                                androidx.core.graphics.ColorUtils
                                                        .setAlphaComponent(color, 22),
                                                surface
                                        )
                                )
                                binding.root.strokeColor =
                                        androidx.core.graphics.ColorUtils.setAlphaComponent(
                                                color,
                                                110
                                        )
                        } else {
                                binding.root.setCardBackgroundColor(surface)
                                binding.root.strokeColor =
                                        ContextCompat.getColor(context, R.color.gray_medium)
                        }

                        if (streak.currentStreak == 0) {
                                binding.streakCount.text =
                                        context.getString(R.string.streak_not_started)
                                binding.streakCount.setTextColor(
                                        ContextCompat.getColor(context, R.color.gray_dark)
                                )
                        } else {
                                val plural =
                                        when (streak.frequency) {
                                                FrequencyType.DAILY -> R.plurals.streak_days
                                                FrequencyType.WEEKLY -> R.plurals.streak_weeks
                                                FrequencyType.MONTHLY -> R.plurals.streak_months
                                                FrequencyType.YEARLY -> R.plurals.streak_years
                                        }
                                val unit =
                                        context.resources.getQuantityString(
                                                plural,
                                                streak.currentStreak,
                                                streak.currentStreak
                                        )
                                when (riskFor(streak)) {
                                        Risk.LAST_DAY -> {
                                                binding.streakCount.text =
                                                        context.getString(
                                                                R.string.streak_ends_today,
                                                                unit
                                                        )
                                                binding.streakCount.setTextColor(
                                                        ContextCompat.getColor(
                                                                context,
                                                                R.color.orange_dark
                                                        )
                                                )
                                        }
                                        Risk.AT_RISK -> {
                                                binding.streakCount.text =
                                                        context.getString(
                                                                R.string.streak_at_risk,
                                                                unit
                                                        )
                                                binding.streakCount.setTextColor(
                                                        ContextCompat.getColor(
                                                                context,
                                                                R.color.orange_dark
                                                        )
                                                )
                                        }
                                        Risk.NONE -> {
                                                binding.streakCount.text = unit
                                                binding.streakCount.setTextColor(
                                                        ContextCompat.getColor(
                                                                context,
                                                                R.color.gray_dark
                                                        )
                                                )
                                        }
                                }
                        }

                        // Update completion circle
                        binding.completionCircle.isSelected = streak.isCompletedToday
                        binding.checkIcon.isVisible = streak.isCompletedToday
                        // Tint the completion circle and check icon with streak color
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
                                onToggled(
                                        streak.id,
                                        !streak.isCompletedToday,
                                        binding.completionCircle
                                )
                        }
                        // Expand the completion circle's touch target to the full right side of
                        // the card: 48 dp to the left, and the full height (top/bottom padding
                        // gaps included), so any tap on the right portion marks the streak.
                        val circleParent = binding.completionCircle.parent as android.view.View
                        circleParent.post {
                                val touchRect = android.graphics.Rect()
                                binding.completionCircle.getHitRect(touchRect)
                                val extraPx =
                                        (48 * binding.root.context.resources.displayMetrics.density)
                                                .toInt()
                                touchRect.left = maxOf(0, touchRect.left - extraPx)
                                touchRect.right = circleParent.width
                                touchRect.top = 0
                                touchRect.bottom = circleParent.height
                                circleParent.touchDelegate =
                                        android.view.TouchDelegate(
                                                touchRect,
                                                binding.completionCircle
                                        )
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

        private enum class Risk {
                NONE,
                AT_RISK,
                LAST_DAY
        }

        companion object {
                /**
                 * A streak is at risk when the remaining days in its period are only
                 * just enough (or not enough) to still meet the frequency requirement.
                 */
                private fun riskFor(streak: Streak): Risk {
                        val today = LocalDate.now()
                        val completionsThisPeriod =
                                streak.asLocalDateCompletions().count {
                                        samePeriod(it, today, streak.frequency)
                                }
                        val needed = streak.frequencyCount - completionsThisPeriod
                        if (needed <= 0) return Risk.NONE

                        val daysLeft =
                                when (streak.frequency) {
                                        FrequencyType.DAILY -> 1
                                        FrequencyType.WEEKLY -> {
                                                8 - today.get(WeekConfig.weekFields().dayOfWeek())
                                        }
                                        FrequencyType.MONTHLY ->
                                                today.lengthOfMonth() - today.dayOfMonth + 1
                                        FrequencyType.YEARLY ->
                                                today.lengthOfYear() - today.dayOfYear + 1
                                }
                        return when {
                                daysLeft > needed -> Risk.NONE
                                daysLeft == 1 -> Risk.LAST_DAY
                                else -> Risk.AT_RISK
                        }
                }

                private fun samePeriod(
                        date: LocalDate,
                        other: LocalDate,
                        frequency: FrequencyType
                ): Boolean {
                        return when (frequency) {
                                FrequencyType.DAILY -> date == other
                                FrequencyType.WEEKLY -> {
                                        val weekFields = WeekConfig.weekFields()
                                        date.with(weekFields.dayOfWeek(), 1) ==
                                                other.with(weekFields.dayOfWeek(), 1)
                                }
                                FrequencyType.MONTHLY ->
                                        date.month == other.month && date.year == other.year
                                FrequencyType.YEARLY -> date.year == other.year
                        }
                }
        }
}
