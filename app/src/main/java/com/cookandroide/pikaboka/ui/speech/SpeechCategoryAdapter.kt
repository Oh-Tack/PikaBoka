package com.cookandroide.pikaboka.ui.speech

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cookandroide.pikaboka.databinding.ItemSpeechCategoryBinding

class SpeechCategoryAdapter(
    private val items: List<Pair<String, Int>>,
    private val onClick: (Pair<String, Int>) -> Unit
) : RecyclerView.Adapter<SpeechCategoryAdapter.VH>() {

    inner class VH(val b: ItemSpeechCategoryBinding): RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inf = LayoutInflater.from(parent.context)
        return VH(ItemSpeechCategoryBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (name, count) = items[position]
        with(holder.b) {
            tvCategory.text = name
            tvCount.text = count.toString()
            root.setOnClickListener { onClick(items[position]) }
        }
    }

    override fun getItemCount() = items.size
}
