package com.arihant.streaks.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.arihant.streaks.R
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.data.Streak
import com.arihant.streaks.databinding.DialogAddStreakBinding

/**
 * Create/edit dialog. All inputs travel through [arguments] and the result is delivered with the
 * Fragment Result API, so the dialog survives configuration changes and process death (the old
 * constructor-callback version crashed when the system recreated it).
 */
class AddStreakDialog : DialogFragment() {

    private var _binding: DialogAddStreakBinding? = null
    private val binding
        get() = _binding!!

    private var selectedEmoji: String = DEFAULT_EMOJI
    private var selectedColor: String = Streak.DEFAULT_COLOR

    private val isEditMode: Boolean
        get() = requireArguments().getBoolean(ARG_EDIT_MODE)

    private val resultKey: String
        get() = if (isEditMode) RESULT_KEY_EDIT else RESULT_KEY_ADD

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddStreakBinding.inflate(layoutInflater)
        val args = requireArguments()

        selectedEmoji = savedInstanceState?.getString(STATE_EMOJI)
            ?: args.getString(ARG_EMOJI) ?: DEFAULT_EMOJI
        selectedColor = savedInstanceState?.getString(STATE_COLOR)
            ?: args.getString(ARG_COLOR) ?: Streak.DEFAULT_COLOR

        binding.dialogTitle.text =
            if (isEditMode) getString(R.string.edit_streak) else getString(R.string.create_streak)
        binding.btnCreate.text =
            if (isEditMode) getString(R.string.save) else getString(R.string.create_streak)

        setupEmojiPicker()
        setupFrequencySpinner()
        setupColorGrid()
        setupClickListeners()

        if (savedInstanceState == null) {
            args.getString(ARG_NAME)?.let { binding.editStreakName.setText(it) }
            val frequency = args.getString(ARG_FREQUENCY)?.let { FrequencyType.valueOf(it) }
            if (frequency != null) {
                binding.spinnerFrequency.setSelection(frequency.ordinal)
            }
            val count = args.getInt(ARG_COUNT, 1)
            binding.editFrequencyCount.setText(count.toString())
            if (frequency != null && frequency != FrequencyType.DAILY) {
                binding.inputLayoutCount.visibility = View.VISIBLE
            }
        }

        return AlertDialog.Builder(requireContext()).setView(binding.root).create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_EMOJI, selectedEmoji)
        outState.putString(STATE_COLOR, selectedColor)
    }

    // ── Emoji ─────────────────────────────────────────────────────────────────

    private fun setupEmojiPicker() {
        binding.selectedEmoji.text = selectedEmoji
        binding.cardEmojiPicker.setOnClickListener { showEmojiPicker() }
    }

    /** Grid of common habit emojis plus a free-form field for anything else. */
    private fun showEmojiPicker() {
        val context = requireContext()

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(8), dpToPx(20), 0)
        }

        val customInput = EditText(context).apply {
            hint = getString(R.string.custom_emoji_hint)
            imeOptions = EditorInfo.IME_ACTION_DONE
            maxLines = 1
        }
        content.addView(customInput)

        val grid = GridLayout(context).apply {
            columnCount = EMOJI_GRID_COLUMNS
            setPadding(0, dpToPx(8), 0, 0)
        }
        content.addView(grid)

        val scroll = ScrollView(context).apply { addView(content) }

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.pick_emoji)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val custom = customInput.text.toString().trim()
                if (custom.isNotEmpty()) applyEmoji(custom)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        val cellSize = dpToPx(44)
        EMOJIS.forEach { emoji ->
            grid.addView(TextView(context).apply {
                text = emoji
                textSize = 24f
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cellSize
                    height = cellSize
                }
                background = rippleBackground(context)
                isClickable = true
                isFocusable = true
                contentDescription = emoji
                setOnClickListener {
                    applyEmoji(emoji)
                    dialog.dismiss()
                }
            })
        }

        dialog.show()
    }

    private fun applyEmoji(emoji: String) {
        selectedEmoji = emoji
        binding.selectedEmoji.text = emoji
    }

    private fun rippleBackground(context: Context): android.graphics.drawable.Drawable? {
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        return androidx.core.content.ContextCompat.getDrawable(context, outValue.resourceId)
    }

    // ── Frequency ─────────────────────────────────────────────────────────────

    private fun setupFrequencySpinner() {
        val frequencyOptions = arrayOf(
            getString(R.string.daily),
            getString(R.string.weekly),
            getString(R.string.monthly),
            getString(R.string.yearly)
        )
        val adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, frequencyOptions
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFrequency.adapter = adapter

        binding.spinnerFrequency.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    if (position == FrequencyType.DAILY.ordinal) {
                        binding.inputLayoutCount.visibility = View.GONE
                        binding.editFrequencyCount.setText("1")
                    } else {
                        binding.inputLayoutCount.visibility = View.VISIBLE
                        if (binding.editFrequencyCount.text.isNullOrEmpty()) {
                            binding.editFrequencyCount.setText("1")
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
    }

    // ── Colors ────────────────────────────────────────────────────────────────

    private fun setupColorGrid() {
        val grid = binding.colorGrid
        grid.removeAllViews()
        val context = requireContext()
        val size = dpToPx(48)
        val margin = dpToPx(6)
        val selectedRing = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
        val idleRing = resolveThemeColor(com.google.android.material.R.attr.colorOutline)

        COLOR_OPTIONS.forEach { colorHex ->
            val circle = View(context)
            circle.layoutParams = GridLayout.LayoutParams().apply {
                width = size
                height = size
                setMargins(margin, margin, margin, margin)
            }
            val isSelected = colorHex == selectedColor
            circle.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(colorHex))
                setStroke(if (isSelected) dpToPx(3) else dpToPx(1),
                        if (isSelected) selectedRing else idleRing)
            }
            circle.contentDescription = getString(R.string.cd_color_option)
            circle.isClickable = true
            circle.isFocusable = true
            circle.setOnClickListener {
                selectedColor = colorHex
                setupColorGrid() // refresh selection ring
            }
            grid.addView(circle)
        }
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnCreate.setOnClickListener { submit() }
        binding.editStreakName.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                v.clearFocus()
                val imm = requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else {
                false
            }
        }
    }

    private fun submit() {
        val name = binding.editStreakName.text.toString().trim()
        val frequency = FrequencyType.entries[binding.spinnerFrequency.selectedItemPosition]
        val frequencyCount =
            if (frequency == FrequencyType.DAILY) 1
            else binding.editFrequencyCount.text.toString().toIntOrNull() ?: 0

        if (name.isBlank()) {
            binding.inputLayoutName.error = getString(R.string.error_enter_name)
            return
        }
        if (frequencyCount <= 0) {
            binding.inputLayoutCount.error = getString(R.string.error_enter_valid_number)
            return
        }

        setFragmentResult(
            resultKey,
            bundleOf(
                KEY_NAME to name,
                KEY_EMOJI to selectedEmoji.ifEmpty { DEFAULT_EMOJI },
                KEY_FREQUENCY to frequency.name,
                KEY_COUNT to frequencyCount,
                KEY_COLOR to selectedColor
            )
        )
        dismiss()
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()

    private fun resolveThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class Result(
        val name: String,
        val emoji: String,
        val frequency: FrequencyType,
        val frequencyCount: Int,
        val color: String
    )

    companion object {
        const val RESULT_KEY_ADD = "add_streak_result"
        const val RESULT_KEY_EDIT = "edit_streak_result"

        private const val KEY_NAME = "name"
        private const val KEY_EMOJI = "emoji"
        private const val KEY_FREQUENCY = "frequency"
        private const val KEY_COUNT = "count"
        private const val KEY_COLOR = "color"

        private const val ARG_EDIT_MODE = "arg_edit_mode"
        private const val ARG_NAME = "arg_name"
        private const val ARG_EMOJI = "arg_emoji"
        private const val ARG_FREQUENCY = "arg_frequency"
        private const val ARG_COUNT = "arg_count"
        private const val ARG_COLOR = "arg_color"

        private const val STATE_EMOJI = "state_emoji"
        private const val STATE_COLOR = "state_color"

        private const val DEFAULT_EMOJI = "🔥"
        private const val EMOJI_GRID_COLUMNS = 6

        private val COLOR_OPTIONS = listOf(
            "#FF9900", // neon_orange
            "#F0F01B", // neon_yellow
            "#B1E80D", // neon_green
            "#0065F8", // neon_blue
            "#FF2DF1", // neon_purple
            "#F93827", // neon_red
            "#FF55BB", // neon_pink
            "#A3D8FF"  // neon_cyan
        )

        private val EMOJIS = listOf(
            "🔥", "💪", "🏃", "🚴", "🏋️", "🧘", "🚶", "⚽",
            "📖", "📚", "✍️", "💻", "🧠", "🎓", "🗣️", "🌍",
            "🎨", "🎸", "🎹", "🎯", "🎮", "📷", "🎬", "🧩",
            "🍎", "🥗", "🥦", "💧", "☕", "🍳", "🚭", "🍷",
            "🛌", "😴", "🌅", "☀️", "🌙", "⏰", "🚿", "🦷",
            "💊", "💰", "📈", "🧹", "🌱", "🐶", "❤️", "🙏"
        )

        fun newForAdd(): AddStreakDialog = AddStreakDialog().apply {
            arguments = bundleOf(ARG_EDIT_MODE to false)
        }

        fun newForEdit(streak: Streak): AddStreakDialog = AddStreakDialog().apply {
            arguments = bundleOf(
                ARG_EDIT_MODE to true,
                ARG_NAME to streak.name,
                ARG_EMOJI to streak.emoji,
                ARG_FREQUENCY to streak.frequency.name,
                ARG_COUNT to streak.frequencyCount,
                ARG_COLOR to streak.color
            )
        }

        fun parseResult(bundle: Bundle): Result = Result(
            name = bundle.getString(KEY_NAME).orEmpty(),
            emoji = bundle.getString(KEY_EMOJI) ?: DEFAULT_EMOJI,
            frequency = FrequencyType.valueOf(
                bundle.getString(KEY_FREQUENCY) ?: FrequencyType.DAILY.name
            ),
            frequencyCount = bundle.getInt(KEY_COUNT, 1),
            color = bundle.getString(KEY_COLOR) ?: Streak.DEFAULT_COLOR
        )
    }
}
