package com.example.streaks.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.streaks.databinding.ItemEmojiBinding

class EmojiAdapter(
    private val emojis: List<String>,
    private val onEmojiSelected: (String) -> Unit
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    private var selectedPosition = 0

    class EmojiViewHolder(private val binding: ItemEmojiBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(emoji: String, isSelected: Boolean, onClick: () -> Unit) {
            binding.emojiText.text = emoji
            binding.root.alpha = if (isSelected) 1.0f else 0.5f
            binding.root.setOnClickListener { onClick() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val binding = ItemEmojiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EmojiViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        holder.bind(
            emojis[position],
            position == selectedPosition
        ) {
            val oldPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(oldPosition)
            notifyItemChanged(position)
            onEmojiSelected(emojis[position])
        }
    }

    override fun getItemCount() = emojis.size

    fun getSelectedEmoji(): String = emojis[selectedPosition]
}
