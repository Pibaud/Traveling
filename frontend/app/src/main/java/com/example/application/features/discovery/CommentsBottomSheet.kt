package com.example.application.features.post

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.application.R
import com.example.application.model.CreateCommentRequest
import com.example.application.model.RetrofitInstance
import com.example.application.utils.GuestUpsellBottomSheet
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class CommentsBottomSheet(private val postId: String,
                          private val onCommentAdded: ((Int) -> Unit)? = null) : BottomSheetDialogFragment() {

    private lateinit var adapter: CommentAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_comments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvComments = view.findViewById<RecyclerView>(R.id.rvComments)
        val etInput = view.findViewById<EditText>(R.id.etCommentInput)
        val btnSend = view.findViewById<ImageView>(R.id.btnSendComment)
        val pbLoading = view.findViewById<ProgressBar>(R.id.pbLoading)

        adapter = CommentAdapter()
        rvComments.layoutManager = LinearLayoutManager(requireContext())
        rvComments.adapter = adapter

        // 1. Fetching des commentaires
        pbLoading.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val commentsList = RetrofitInstance.api.getComments(postId)
                adapter.submitList(commentsList)
                // Scroll en bas si on a des commentaires
                if (commentsList.isNotEmpty()) {
                    rvComments.scrollToPosition(commentsList.size - 1)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur de chargement", Toast.LENGTH_SHORT).show()
            } finally {
                pbLoading.visibility = View.GONE
            }
        }

        // 2. Envoi d'un nouveau commentaire
        btnSend.setOnClickListener {
            val content = etInput.text.toString().trim()
            if (content.isEmpty()) return@setOnClickListener

            val user = Firebase.auth.currentUser
            if (user == null || user.isAnonymous) {
                GuestUpsellBottomSheet().show(childFragmentManager, "GuestUpsell")
                return@setOnClickListener
            }

            // UI Optimiste : on bloque le bouton
            btnSend.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val request = CreateCommentRequest(postId, user.uid, content)
                    val response = RetrofitInstance.api.postComment(request)

                    if (response.isSuccessful) {
                        etInput.text.clear()
                        // On refetch pour avoir le bon format avec nom/avatar
                        val updatedComments = RetrofitInstance.api.getComments(postId)
                        adapter.submitList(updatedComments)
                        rvComments.scrollToPosition(updatedComments.size - 1)

                        onCommentAdded?.invoke(updatedComments.size)
                    } else {
                        Toast.makeText(context, "Erreur d'envoi", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Erreur réseau", Toast.LENGTH_SHORT).show()
                } finally {
                    btnSend.isEnabled = true
                }
            }
        }
    }
}