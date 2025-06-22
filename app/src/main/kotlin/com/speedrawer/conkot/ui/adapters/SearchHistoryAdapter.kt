package com.speedrawer.conkot.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.speedrawer.conkot.R

class SearchHistoryAdapter(
    private val onHistoryClick: (String) -> Unit
) : ListAdapter<String, SearchHistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val query = getItem(position)
        holder.bind(query)
    }

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val queryTextView: TextView = itemView.findViewById(R.id.queryText)
        private val historyIcon: ImageView = itemView.findViewById(R.id.historyIcon)

        fun bind(query: String) {
            queryTextView.text = query
            
            itemView.setOnClickListener {
                onHistoryClick(query)
            }
        }
    }

    private class HistoryDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
} 