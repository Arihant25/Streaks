package com.arihant.streaks.ui.adapters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.data.Streak
import com.arihant.streaks.databinding.ItemStreakCardBinding
import java.util.Collections

/**
 * Plain adapter with manual DiffUtil dispatch. ListAdapter's async diffing fights with
 * ItemTouchHelper during drag & drop (items flicker and jump back), so drags mutate the
 * backing list directly via [moveItem] and observers update it via [submitList].
 */
class StreaksAdapter(
        private val onStreakToggled: (String, Boolean) -> Unit,
        private val onStreakClicked: (Streak, View) -> Unit
) : RecyclerView.Adapter<StreaksAdapter.StreakViewHolder>() {

        private val items = mutableListOf<Streak>()

        val currentIds: List<String>
                get() = items.map { it.id }

        fun submitList(newItems: List<Streak>) {
                val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                        override fun getOldListSize() = items.size
                        override fun getNewListSize() = newItems.size
                        override fun areItemsTheSame(old: Int, new: Int) =
                                items[old].id == newItems[new].id
                        override fun areContentsTheSame(old: Int, new: Int) =
                                items[old] == newItems[new]
                })
                items.clear()
                items.addAll(newItems)
                diff.dispatchUpdatesTo(this)
        }

        fun moveItem(fromPosition: Int, toPosition: Int) {
                if (fromPosition == toPosition) return
                if (fromPosition < toPosition) {
                        for (i in fromPosition until toPosition) Collections.swap(items, i, i + 1)
                } else {
                        for (i in fromPosition downTo toPosition + 1) Collections.swap(items, i, i - 1)
                }
                notifyItemMoved(fromPosition, toPosition)
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreakViewHolder {
                val binding = ItemStreakCardBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                )
                return StreakViewHolder(binding)
        }

        override fun onBindViewHolder(holder: StreakViewHolder, position: Int) {
                holder.bind(items[position], onStreakToggled, onStreakClicked)
        }

        class StreakViewHolder(private val binding: ItemStreakCardBinding) :
                RecyclerView.ViewHolder(binding.root) {

                fun bind(
                        streak: Streak,
                        onToggled: (String, Boolean) -> Unit,
                        onClicked: (Streak, View) -> Unit
                ) {
                        val context = binding.root.context
                        binding.emojiIcon.text = streak.emoji
                        binding.streakName.text = streak.name
                        binding.streakCount.text =
                                if (streak.currentStreak == 0) {
                                        context.getString(R.string.streak_not_started)
                                } else {
                                        val plural = when (streak.frequency) {
                                                FrequencyType.DAILY -> R.plurals.streak_days
                                                FrequencyType.WEEKLY -> R.plurals.streak_weeks
                                                FrequencyType.MONTHLY -> R.plurals.streak_months
                                                FrequencyType.YEARLY -> R.plurals.streak_years
                                        }
                                        context.resources.getQuantityString(
                                                plural, streak.currentStreak, streak.currentStreak
                                        )
                                }

                        val color = try {
                                Color.parseColor(streak.color)
                        } catch (e: IllegalArgumentException) {
                                Color.parseColor(Streak.DEFAULT_COLOR)
                        }

                        binding.completionCircle.isSelected = streak.isCompletedToday
                        binding.checkIcon.isVisible = streak.isCompletedToday
                        if (streak.isCompletedToday) {
                                binding.completionCircle.background = GradientDrawable().apply {
                                        shape = GradientDrawable.OVAL
                                        setColor(color)
                                }
                                binding.checkIcon.setColorFilter(Color.WHITE)
                        } else {
                                binding.completionCircle.background =
                                        ContextCompat.getDrawable(context, R.drawable.circle_background)
                                binding.checkIcon.setColorFilter(color)
                        }

                        binding.completionCircle.contentDescription =
                                if (streak.isCompletedToday) {
                                        context.getString(R.string.cd_completed_today, streak.name)
                                } else {
                                        context.getString(R.string.cd_mark_completed, streak.name)
                                }
                        binding.completionCircle.setOnClickListener {
                                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onToggled(streak.id, !streak.isCompletedToday)
                        }

                        binding.root.setOnClickListener { onClicked(streak, binding.root) }
                        binding.root.transitionName = "streak_card_${streak.id}"
                }
        }
}
