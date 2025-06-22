package com.speedrawer.conkot.ui.adapters

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.speedrawer.conkot.R
import com.speedrawer.conkot.data.models.AppInfo

class AppGridAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit,
    private val getAppIcon: (AppInfo) -> Drawable?,
    private val animationsEnabled: () -> Boolean
) : ListAdapter<AppInfo, AppGridAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = getItem(position)
        holder.bind(app)
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconImageView: ImageView = itemView.findViewById(R.id.appIcon)
        private val nameTextView: TextView = itemView.findViewById(R.id.appName)
        private val favoriteIndicator: ImageView = itemView.findViewById(R.id.favoriteIndicator)

        fun bind(app: AppInfo) {
            // Set app name
            nameTextView.text = app.displayName

            // Set app icon
            val icon = getAppIcon(app)
            if (icon != null) {
                iconImageView.setImageDrawable(icon)
            } else {
                iconImageView.setImageResource(R.drawable.ic_launcher_foreground)
            }

            // Show favorite indicator
            favoriteIndicator.visibility = if (app.isFavorite) View.VISIBLE else View.GONE

            // Set click listeners
            itemView.setOnClickListener {
                if (animationsEnabled()) {
                    itemView.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .withEndAction {
                            itemView.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(100)
                                .start()
                        }
                        .start()
                }
                onAppClick(app)
            }

            itemView.setOnLongClickListener {
                if (animationsEnabled()) {
                    itemView.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(150)
                        .withEndAction {
                            itemView.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(150)
                                .start()
                        }
                        .start()
                }
                onAppLongClick(app)
                true
            }
        }
    }

    private class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem == newItem
        }
    }
} 