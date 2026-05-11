package com.example.application.features.social

import GroupMemberResponse
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.application.R
import com.example.application.databinding.ItemGroupMemberBinding

class GroupMemberAdapter : RecyclerView.Adapter<GroupMemberAdapter.MemberViewHolder>() {

    private var members: List<GroupMemberResponse> = emptyList()

    fun submitList(newList: List<GroupMemberResponse>) {
        members = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemGroupMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(members[position])
    }

    override fun getItemCount() = members.size

    inner class MemberViewHolder(private val binding: ItemGroupMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(member: GroupMemberResponse) {
            binding.tvMemberName.text = member.username
            binding.tvMemberRole.text = if (member.role == "ADMIN") "Admin" else "Membre"

            binding.ivMemberAvatar.load(member.avatarUrl) {
                placeholder(R.drawable.round_account_circle_24)
                error(R.drawable.round_account_circle_24)
                transformations(CircleCropTransformation())
            }
        }
    }
}