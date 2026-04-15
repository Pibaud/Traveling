package com.example.application.features.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.application.R
import com.example.application.databinding.ItemGroupBinding
import com.example.application.model.Group
import com.example.application.utils.GroupThemes
import com.google.android.material.chip.Chip

class GroupAdapter(
    private val onJoinClick: (Group) -> Unit,
    private val onNotificationClick: (Group, Boolean) -> Unit
) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {

    private var groups: List<Group> = emptyList()

    fun submitList(newList: List<Group>) {
        groups = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(groups[position])
    }

    override fun getItemCount() = groups.size

    inner class GroupViewHolder(private val binding: ItemGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(group: Group) {
            binding.tvGroupName.text = group.name
            binding.tvPostsCount.text = group.nbPosts.toString()

            // Chargement image avec Coil
            binding.ivGroupCover.load(group.photoUrl) {
                crossfade(true)
                placeholder(R.drawable.round_image_24)
            }

            // Calcul des membres factices
            val remainingMembers = group.nbMembers - 3
            if (remainingMembers > 0) {
                binding.tvExtraMembers.text = "+ $remainingMembers"
            } else {
                binding.tvExtraMembers.text = ""
            }

            // Gestion des tags
            binding.cgTags.removeAllViews()
            group.tags.take(2).forEach { tagName ->
                val chip = Chip(itemView.context).apply {
                    text = tagName
                    textSize = 10f
                    chipIcon = GroupThemes.getIconForTag(tagName)?.let { ContextCompat.getDrawable(context, it) }
                    isCheckable = false
                    isClickable = false
                }
                binding.cgTags.addView(chip)
            }

            // Gestion du Bouton Rejoindre
            if (group.isMember) {
                binding.btnJoin.visibility = View.GONE
            } else {
                binding.btnJoin.visibility = View.VISIBLE
                if (group.isPublic) {
                    binding.btnJoin.text = "Rejoindre"
                    binding.btnJoin.setIconResource(R.drawable.round_login_24) // ou round_login_24
                } else {
                    binding.btnJoin.text = "Demander"
                    binding.btnJoin.setIconResource(R.drawable.round_send_24)
                }

                binding.btnJoin.setOnClickListener {
                    onJoinClick(group)
                }
            }

            // Gestion de la Cloche (UI Optimiste)
            binding.btnNotification.setImageResource(
                if (group.isNotificationEnabled) R.drawable.round_notifications_active_24
                else R.drawable.round_notifications_none_24
            )

            binding.btnNotification.setOnClickListener {
                // UI OPTIMISTE : On inverse l'état visuellement tout de suite
                val newState = !group.isNotificationEnabled
                group.isNotificationEnabled = newState
                notifyItemChanged(bindingAdapterPosition)

                // On prévient le ViewModel d'envoyer la requête au serveur
                onNotificationClick(group, newState)
            }
        }
    }
}