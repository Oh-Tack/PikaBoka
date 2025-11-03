package com.cookandroide.pikaboka.ui.speech

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cookandroide.pikaboka.data.speech.SpeechSentence
import com.cookandroide.pikaboka.databinding.ItemSpeechSentenceBinding

class SpeechSentenceAdapter(
    private val items: List<SpeechSentence>,
    private val onClick: (SpeechSentence) -> Unit
) : RecyclerView.Adapter<SpeechSentenceAdapter.VH>() {

    inner class VH(val b: ItemSpeechSentenceBinding): RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inf = LayoutInflater.from(parent.context)
        return VH(ItemSpeechSentenceBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        with(holder.b) {
            tvJa.text = item.textJa
            tvPron.text = item.pronunciation
            tvTr.text = item.translation
            root.setOnClickListener { onClick(item) }
        }
    }

    override fun getItemCount() = items.size
}
