package com.cookandroide.pikaboka.ui.vocab

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cookandroide.pikaboka.R
import com.cookandroide.pikaboka.databinding.ItemWordBinding
import com.cookandroide.pikaboka.ui.model.WordUi

class VocabAdapter(
    private val onSpeakClick: (WordUi) -> Unit,
    private val onFavoriteClick: (WordUi) -> Unit
) : ListAdapter<WordUi, VocabAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WordUi>() {
            override fun areItemsTheSame(oldItem: WordUi, newItem: WordUi) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: WordUi, newItem: WordUi) =
                oldItem == newItem
        }
    }

    inner class VH(val b: ItemWordBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemWordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val w = getItem(position)
        with(holder.b) {
            tvJp.text = w.jp
            tvKana.text = w.kana
            tvMeaning.text = w.mean

            btnFavorite.setImageResource(
                if (w.isFavorite) R.drawable.ic_star_filled
                else R.drawable.ic_star_blanked
            )

            btnSpeak.setOnClickListener { onSpeakClick(w) }
            btnFavorite.setOnClickListener { onFavoriteClick(w) }
        }
    }

    // 타입을 WordUi로
    fun submit(list: List<WordUi>) = submitList(list)
}
