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
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.data.Streak
import com.arihant.streaks.databinding.DialogAddStreakBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.BreakIterator

/**
 * Create/edit sheet. All inputs travel through [getArguments] and the result is delivered with
 * the Fragment Result API, so the sheet survives configuration changes and process death (the
 * old constructor-callback version crashed when the system recreated it).
 *
 * Besides regular "build" habits, a streak can be a "quit" habit (negative): the streak grows
 * by itself and check-ins record slip-ups instead of completions. The type is chosen at
 * creation and can't be changed later — the meaning of every recorded date would flip.
 */
class AddStreakDialog : BottomSheetDialogFragment() {

    private var _binding: DialogAddStreakBinding? = null
    private val binding
        get() = _binding!!

    private var selectedEmoji: String = "🔥"
    private var selectedColor: String = Streak.DEFAULT_COLOR
    private var selectedCount: Int = 1
    private var selectedIsNegative: Boolean = false

    private val isEditMode: Boolean
        get() = requireArguments().getBoolean(ARG_EDIT_MODE)

    companion object {
        const val RESULT_KEY_ADD = "add_streak_result"
        const val RESULT_KEY_EDIT = "edit_streak_result"

        private const val KEY_NAME = "name"
        private const val KEY_EMOJI = "emoji"
        private const val KEY_FREQUENCY = "frequency"
        private const val KEY_COUNT = "count"
        private const val KEY_COLOR = "color"
        private const val KEY_IS_NEGATIVE = "is_negative"

        private const val ARG_EDIT_MODE = "arg_edit_mode"
        private const val ARG_NAME = "arg_name"
        private const val ARG_EMOJI = "arg_emoji"
        private const val ARG_FREQUENCY = "arg_frequency"
        private const val ARG_COUNT = "arg_count"
        private const val ARG_COLOR = "arg_color"
        private const val ARG_IS_NEGATIVE = "arg_is_negative"

        private const val STATE_EMOJI = "state_emoji"
        private const val STATE_COLOR = "state_color"
        private const val STATE_COUNT = "state_count"
        private const val STATE_IS_NEGATIVE = "state_is_negative"

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

        fun newForAdd(defaultEmoji: String): AddStreakDialog =
                AddStreakDialog().apply {
                    arguments = bundleOf(ARG_EDIT_MODE to false, ARG_EMOJI to defaultEmoji)
                }

        fun newForEdit(streak: Streak): AddStreakDialog =
                AddStreakDialog().apply {
                    arguments =
                            bundleOf(
                                    ARG_EDIT_MODE to true,
                                    ARG_NAME to streak.name,
                                    ARG_EMOJI to streak.emoji,
                                    ARG_FREQUENCY to streak.frequency.name,
                                    ARG_COUNT to streak.frequencyCount,
                                    ARG_COLOR to streak.color,
                                    ARG_IS_NEGATIVE to streak.isNegative
                            )
                }

        fun parseResult(bundle: Bundle): Result =
                Result(
                        name = bundle.getString(KEY_NAME).orEmpty(),
                        emoji = bundle.getString(KEY_EMOJI) ?: "🔥",
                        frequency =
                                FrequencyType.valueOf(
                                        bundle.getString(KEY_FREQUENCY)
                                                ?: FrequencyType.DAILY.name
                                ),
                        frequencyCount = bundle.getInt(KEY_COUNT, 1),
                        color = bundle.getString(KEY_COLOR) ?: Streak.DEFAULT_COLOR,
                        isNegative = bundle.getBoolean(KEY_IS_NEGATIVE, false)
                )
    }

    data class Result(
            val name: String,
            val emoji: String,
            val frequency: FrequencyType,
            val frequencyCount: Int,
            val color: String,
            val isNegative: Boolean
    )

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
        val args = requireArguments()

        selectedEmoji = savedInstanceState?.getString(STATE_EMOJI)
                ?: args.getString(ARG_EMOJI) ?: "🔥"
        selectedColor = savedInstanceState?.getString(STATE_COLOR)
                ?: args.getString(ARG_COLOR) ?: Streak.DEFAULT_COLOR
        selectedCount = savedInstanceState?.getInt(STATE_COUNT)
                ?: args.getInt(ARG_COUNT, 1)
        selectedIsNegative = savedInstanceState?.getBoolean(STATE_IS_NEGATIVE)
                ?: args.getBoolean(ARG_IS_NEGATIVE, false)

        binding.sheetTitle.text =
                if (isEditMode) getString(R.string.edit_streak)
                else getString(R.string.create_streak)
        binding.btnCreate.text =
                if (isEditMode) getString(R.string.save_changes)
                else getString(R.string.create_streak)

        setupEmojiPicker()
        setupHabitTypeToggle()
        setupFrequencyToggle()
        setupCountStepper()
        setupColorWheel()
        setupClickListeners()

        if (savedInstanceState == null) {
            args.getString(ARG_NAME)?.let { binding.editStreakName.setText(it) }
            args.getString(ARG_FREQUENCY)?.let { checkFrequency(FrequencyType.valueOf(it)) }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_EMOJI, selectedEmoji)
        outState.putString(STATE_COLOR, selectedColor)
        outState.putInt(STATE_COUNT, selectedCount)
        outState.putBoolean(STATE_IS_NEGATIVE, selectedIsNegative)
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

    // --- Habit type (build / quit) ---

    private fun setupHabitTypeToggle() {
        if (isEditMode) {
            // The type flips the meaning of every recorded date — locked after creation
            binding.textHabitTypeTitle.visibility = View.GONE
            binding.toggleHabitType.visibility = View.GONE
            binding.textHabitTypeHint.visibility = View.GONE
            return
        }
        binding.toggleHabitType.check(
                if (selectedIsNegative) R.id.btn_type_quit else R.id.btn_type_build
        )
        updateHabitTypeHint()
        binding.toggleHabitType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val negative = checkedId == R.id.btn_type_quit
            if (negative == selectedIsNegative) return@addOnButtonCheckedListener
            selectedIsNegative = negative
            // Sensible defaults: quitting is zero-tolerance, building starts at once-per-period
            selectedCount = if (negative) 0 else 1
            updateHabitTypeHint()
            updateCountRow()
        }
    }

    private fun updateHabitTypeHint() {
        binding.textHabitTypeHint.text =
                getString(
                        if (selectedIsNegative) R.string.habit_type_hint_quit
                        else R.string.habit_type_hint_build
                )
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

    // --- Count stepper ---

    private fun minCount(): Int = if (selectedIsNegative) 0 else 1

    private fun setupCountStepper() {
        binding.btnCountMinus.setOnClickListener {
            if (selectedCount > minCount()) {
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

    /**
     * Shows/hides the stepper and keeps the label in plain language. Build habits hide the
     * stepper for DAILY (it's always once a day); quit habits always show it because the
     * count is the slip-up allowance (0 = zero tolerance).
     */
    private fun updateCountRow(popCount: Boolean = false) {
        val frequency = selectedFrequency()
        TransitionManager.beginDelayedTransition(
                binding.root as ViewGroup,
                AutoTransition().setDuration(200)
        )
        binding.countStepperRow.visibility =
                if (!selectedIsNegative && frequency == FrequencyType.DAILY) View.GONE
                else View.VISIBLE

        selectedCount = selectedCount.coerceAtLeast(minCount())
        binding.textCount.text = selectedCount.toString()
        binding.textCountLabel.text =
                if (selectedIsNegative) {
                    when (frequency) {
                        FrequencyType.DAILY -> getString(R.string.slips_per_day)
                        FrequencyType.WEEKLY -> getString(R.string.slips_per_week)
                        FrequencyType.MONTHLY -> getString(R.string.slips_per_month)
                        FrequencyType.YEARLY -> getString(R.string.slips_per_year)
                    }
                } else {
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
                Color.parseColor(Streak.DEFAULT_COLOR)
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
        binding.btnCreate.setOnClickListener { submit() }
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

    private fun submit() {
        val name = binding.editStreakName.text.toString().trim()

        if (name.isBlank()) {
            binding.inputLayoutName.error = "Please enter a name"
            binding.editStreakName.requestFocus()
            return
        }

        setFragmentResult(
                if (isEditMode) RESULT_KEY_EDIT else RESULT_KEY_ADD,
                bundleOf(
                        KEY_NAME to name,
                        KEY_EMOJI to selectedEmoji,
                        KEY_FREQUENCY to selectedFrequency().name,
                        KEY_COUNT to selectedCount.coerceAtLeast(minCount()),
                        KEY_COLOR to selectedColor,
                        KEY_IS_NEGATIVE to selectedIsNegative
                )
        )
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
