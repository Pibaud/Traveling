package com.example.application.features.post

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.application.R
import com.example.application.model.Comment

class CommentAdapter : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private val comments = mutableListOf<Comment>()

    fun submitList(newComments: List<Comment>) {
        comments.clear()
        comments.addAll(newComments)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.tvName.text = comment.authorName
        holder.tvContent.text = comment.content

        if (!comment.authorAvatarUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(comment.authorAvatarUrl)
                .placeholder(R.drawable.round_account_circle_24)
                .error(R.drawable.round_account_circle_24)
                .centerCrop()
                .into(holder.ivAvatar)
        } else {
            holder.ivAvatar.setImageResource(R.drawable.round_account_circle_24)
        }
    }

    override fun getItemCount() = comments.size

    class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
        val tvName: TextView = view.findViewById(R.id.tvAuthorName)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
    }
}