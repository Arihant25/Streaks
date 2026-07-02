package com.arihant.streaks.ui.dialogs

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.databinding.DialogAddStreakBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.BreakIterator

class AddStreakDialog(
        private val onStreakAdded: (String, String, FrequencyType, Int, String) -> Unit,
        private val isEditMode: Boolean = false,
        private val initialFrequency: FrequencyType? = null,
        private val initialFrequencyCount: Int? = null,
        private val initialName: String? = null,
        private val initialEmoji: String? = null,
        private val initialColor: String? = null
) : BottomSheetDialogFragment() {

    private var _binding: DialogAddStreakBinding? = null
    private val binding
        get() = _binding!!

    private var selectedEmoji: String = initialEmoji ?: "🔥"
    private var selectedColor: String = initialColor ?: "#FF9900"
    private var selectedCount: Int = initialFrequencyCount ?: 1

    companion object {
        private val DEFAULT_EMOJIS =
                listOf(
                        // Fitness & sports
                        "💪", "🏃", "🏋️", "🚴", "🧘", "🏊", "🤸", "🧗", "🚶", "⚽",
                        "🏀", "🎾", "🥊", "🏸", "⛰️",
                        // Health & self-care
                        "💧", "🥗", "🍎", "🥦", "☕", "💊", "😴", "🛏️", "🪥", "🚿",
                        // Learning & work
                        "📚", "✍️", "🧠", "💻", "🗣️", "♟️", "📝", "🎓",
                        // Creative
                        "🎸", "🎹", "🥁", "🎤", "🎻", "🎨", "📷", "🧶",
                        // Home & life
                        "🌱", "🐕", "🐈", "🧹", "🍳", "🍵", "💰", "📈", "🙏", "🧎",
                        // Mood & world
                        "❤️", "📞", "🎮", "🌊", "🌍", "♻️", "☀️", "🌙", "⭐", "🎯",
                        "⚡", "🔥"
                )

        /** The handful shown in the quick-pick row; anything else via "Type any emoji". */
        private val QUICK_PICK_EMOJIS = listOf("💪", "📚", "🏃", "💧", "🧘")

        /** The first streak keeps the classic fire; later ones get a random suggestion. */
        fun defaultEmojiFor(existingStreakCount: Int): String =
                if (existingStreakCount == 0) "🔥" else DEFAULT_EMOJIS.random()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        // Keep the name field visible above the keyboard
        dialog.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        return dialog
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddStreakBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.sheetTitle.text =
                if (isEditMode) getString(R.string.edit_streak)
                else getString(R.string.create_streak)
        binding.btnCreate.text =
                if (isEditMode) getString(R.string.save_changes)
                else getString(R.string.create_streak)

        setupEmojiPicker()
        setupFrequencyToggle()
        setupCountStepper()
        setupColorWheel()
        setupClickListeners()

        if (isEditMode) {
            initialName?.let { binding.editStreakName.setText(it) }
            initialEmoji?.let {
                selectedEmoji = it
                binding.selectedEmoji.text = it
            }
            // Prefill frequency but keep it editable; saving recalculates the
            // streak counts against the new frequency
            initialFrequency?.let { checkFrequency(it) }
        } else {
            if (initialFrequency != null) {
                checkFrequency(initialFrequency)
                lockFrequencyControls()
            }
        }
        updateCountRow()
        applyColorEverywhere(parseSelectedColor())

        // Playful entrance: the identity hero pops in
        binding.identityHero.scaleX = 0.6f
        binding.identityHero.scaleY = 0.6f
        binding.identityHero.alpha = 0f
        binding.identityHero
                .animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(350)
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
    }

    // --- Identity hero: emoji inside the hue ring ---

    private fun setupEmojiPicker() {
        binding.selectedEmoji.text = selectedEmoji
        populateEmojiGrid()
        binding.cardEmojiPicker.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            toggleEmojiGrid()
        }
    }

    private fun toggleEmojiGrid(forceHide: Boolean = false) {
        val showing = binding.emojiQuickPick.visibility == View.VISIBLE
        TransitionManager.beginDelayedTransition(
                binding.root as ViewGroup,
                AutoTransition().setDuration(220)
        )
        binding.emojiQuickPick.visibility = if (showing || forceHide) View.GONE else View.VISIBLE
    }

    private fun populateEmojiGrid() {
        val grid = binding.emojiGrid
        grid.removeAllViews()
        binding.btnTypeEmoji.setOnClickListener { showCustomEmojiInput() }
        QUICK_PICK_EMOJIS.forEach { emoji ->
            val cell =
                    TextView(requireContext()).apply {
                        text = emoji
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
                        gravity = android.view.Gravity.CENTER
                        setPadding(0, dpToPx(8), 0, dpToPx(8))
                        val outValue = TypedValue()
                        requireContext()
                                .theme
                                .resolveAttribute(
                                        android.R.attr.selectableItemBackgroundBorderless,
                                        outValue,
                                        true
                                )
                        setBackgroundResource(outValue.resourceId)
                        layoutParams =
                                GridLayout.LayoutParams(
                                                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                                                GridLayout.spec(GridLayout.UNDEFINED, 1f)
                                        )
                                        .apply { width = 0 }
                        setOnClickListener { selectEmoji(emoji) }
                    }
            grid.addView(cell)
        }
    }

    private fun selectEmoji(emoji: String) {
        selectedEmoji = emoji
        binding.selectedEmoji.text = emoji
        binding.selectedEmoji.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        // Springy pop so the pick feels physical
        binding.selectedEmoji.scaleX = 0.4f
        binding.selectedEmoji.scaleY = 0.4f
        binding.selectedEmoji
                .animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator(2.5f))
                .start()
        toggleEmojiGrid(forceHide = true)
    }

    private fun showCustomEmojiInput() {
        val input = EditText(requireContext())
        input.inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.hint = selectedEmoji
        input.imeOptions = EditorInfo.IME_ACTION_DONE
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.type_any_emoji))
                .setView(input)
                .setPositiveButton("OK") { _, _ ->
                    val emoji = input.text.toString().trim()
                    when {
                        emoji.isEmpty() -> {
                            // Keep the current emoji
                        }
                        isSingleEmoji(emoji) -> selectEmoji(emoji)
                        else ->
                                Toast.makeText(
                                                requireContext(),
                                                "Please pick a single emoji",
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
    }

    // --- Frequency ---

    private fun checkFrequency(frequency: FrequencyType) {
        val buttonId =
                when (frequency) {
                    FrequencyType.DAILY -> R.id.btn_freq_daily
                    FrequencyType.WEEKLY -> R.id.btn_freq_weekly
                    FrequencyType.MONTHLY -> R.id.btn_freq_monthly
                    FrequencyType.YEARLY -> R.id.btn_freq_yearly
                }
        binding.toggleFrequency.check(buttonId)
    }

    private fun selectedFrequency(): FrequencyType =
            when (binding.toggleFrequency.checkedButtonId) {
                R.id.btn_freq_weekly -> FrequencyType.WEEKLY
                R.id.btn_freq_monthly -> FrequencyType.MONTHLY
                R.id.btn_freq_yearly -> FrequencyType.YEARLY
                else -> FrequencyType.DAILY
            }

    private fun setupFrequencyToggle() {
        binding.toggleFrequency.check(R.id.btn_freq_daily)
        binding.toggleFrequency.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) updateCountRow()
        }
    }

    private fun lockFrequencyControls() {
        for (i in 0 until binding.toggleFrequency.childCount) {
            binding.toggleFrequency.getChildAt(i).isEnabled = false
        }
        binding.btnCountMinus.isEnabled = false
        binding.btnCountPlus.isEnabled = false
    }

    // --- Count stepper ---

    private fun setupCountStepper() {
        binding.btnCountMinus.setOnClickListener {
            if (selectedCount > 1) {
                selectedCount--
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                updateCountRow(popCount = true)
            }
        }
        binding.btnCountPlus.setOnClickListener {
            if (selectedCount < 99) {
                selectedCount++
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                updateCountRow(popCount = true)
            }
        }
    }

    /** Shows/hides the stepper and keeps the "times per week" label in plain language. */
    private fun updateCountRow(popCount: Boolean = false) {
        val frequency = selectedFrequency()
        TransitionManager.beginDelayedTransition(
                binding.root as ViewGroup,
                AutoTransition().setDuration(200)
        )
        binding.countStepperRow.visibility =
                if (frequency == FrequencyType.DAILY) View.GONE else View.VISIBLE

        binding.textCount.text = selectedCount.toString()
        binding.textCountLabel.text =
                when (frequency) {
                    FrequencyType.WEEKLY ->
                            getString(
                                    if (selectedCount == 1) R.string.once_per_week
                                    else R.string.times_per_week
                            )
                    FrequencyType.MONTHLY ->
                            getString(
                                    if (selectedCount == 1) R.string.once_per_month
                                    else R.string.times_per_month
                            )
                    FrequencyType.YEARLY ->
                            getString(
                                    if (selectedCount == 1) R.string.once_per_year
                                    else R.string.times_per_year
                            )
                    FrequencyType.DAILY -> ""
                }
        if (popCount) {
            binding.textCount.scaleX = 0.6f
            binding.textCount.scaleY = 0.6f
            binding.textCount
                    .animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
        }
    }

    // --- Color ---

    private fun parseSelectedColor(): Int =
            try {
                Color.parseColor(selectedColor)
            } catch (e: Exception) {
                Color.parseColor("#FF9900")
            }

    private fun setupColorWheel() {
        binding.hueWheel.setColor(parseSelectedColor())
        binding.hueWheel.onColorChanged = { color ->
            selectedColor = String.format("#%06X", 0xFFFFFF and color)
            applyColorEverywhere(color)
        }
    }

    /** Live-tints the emoji circle and the create button so the pick is felt everywhere. */
    private fun applyColorEverywhere(color: Int) {
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(ColorUtils.setAlphaComponent(color, 46))
        circle.setStroke(dpToPx(2), color)
        binding.cardEmojiPicker.background = circle

        binding.btnCreate.backgroundTintList = ColorStateList.valueOf(color)
        binding.btnCreate.setTextColor(
                if (ColorUtils.calculateLuminance(color) > 0.5) Color.BLACK else Color.WHITE
        )

        val colorList = ColorStateList.valueOf(color)
        val ripple = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 60))
        listOf(binding.btnCountMinus, binding.btnCountPlus).forEach { button ->
            button.strokeColor = colorList
            button.setTextColor(color)
            button.rippleColor = ripple
        }
    }

    /** Exactly one grapheme cluster. Must be an emoji, not a letter or digit. */
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
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnCreate.setOnClickListener { createStreak() }
        // Prevent multi-line input for streak name
        binding.editStreakName.setOnEditorActionListener { v, actionId, _ ->
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
        binding.editStreakName.addTextChangedListener(
                object : android.text.TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}
                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {
                        binding.inputLayoutName.error = null
                    }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                }
        )
    }

    private fun createStreak() {
        val name = binding.editStreakName.text.toString().trim()

        if (name.isBlank()) {
            binding.inputLayoutName.error = "Please enter a name"
            binding.editStreakName.requestFocus()
            return
        }

        onStreakAdded(name, selectedEmoji, selectedFrequency(), selectedCount, selectedColor)
        dismiss()
    }

    private fun dpToPx(dp: Int): Int =
            TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            dp.toFloat(),
                            resources.displayMetrics
                    )
                    .toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
