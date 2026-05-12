package com.example.application.features.social

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.application.R
import com.example.application.databinding.FragmentGroupDetailBinding
import com.example.application.features.path.ItineraryDetailsBottomSheet
import com.example.application.model.RetrofitInstance
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class GroupDetailFragment : Fragment(R.layout.fragment_group_detail) {

    private var _binding: FragmentGroupDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var feedAdapter: GroupFeedAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentGroupDetailBinding.bind(view)

        val groupId = arguments?.getString("groupId") ?: return
        val groupName = arguments?.getString("groupName") ?: "Groupe"
        val userId = Firebase.auth.currentUser?.uid ?: ""

        // Setup Toolbar
        binding.toolbarGroup.title = groupName
        binding.toolbarGroup.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.ivGroupInfo.setOnClickListener {
            GroupInfoBottomSheet.newInstance(groupId, groupName).show(childFragmentManager, "GroupInfo")
        }

        // Initialisation du flux hybride
        feedAdapter = GroupFeedAdapter(
            onItineraryClick = { itinerary ->
                val detailsSheet = ItineraryDetailsBottomSheet(itinerary)
                detailsSheet.show(parentFragmentManager, "ItineraryDetails")
            },
            onItineraryLikeClick = { itinerary ->
                lifecycleScope.launch {
                    try { RetrofitInstance.api.toggleLike(userId, itinerary.id!!) } catch (e: Exception) {}
                }
            }
        )

        binding.rvFeed.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = feedAdapter
        }

        loadMixedFeed(groupId, userId)
    }

    private fun loadMixedFeed(groupId: String, userId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1. On charge tout depuis Ktor
                val posts = RetrofitInstance.api.getGroupPosts(groupId)
                val itineraries = RetrofitInstance.api.getGroupItineraries(groupId, userId).body() ?: emptyList()

                // 2. On convertit en "GroupFeedItem"
                val feedItems = mutableListOf<GroupFeedItem>()
                feedItems.addAll(posts.map { GroupFeedItem.PostItem(it) })
                feedItems.addAll(itineraries.map { GroupFeedItem.ItineraryItem(it) })

                // 👇 3. LE TRI CHRONOLOGIQUE PARFAIT 👇
                feedItems.sortByDescending { item ->
                    when (item) {
                        is GroupFeedItem.PostItem -> item.post.timestamp      // Le shared_at du post
                        is GroupFeedItem.ItineraryItem -> item.itinerary.sharedAt // Le shared_at de l'itinéraire
                    }
                }

                // 4. On donne ça à l'UI
                feedAdapter.submitList(feedItems)

            } catch (e: Exception) {
                Log.e("GroupDetail", "Erreur chargement", e)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}