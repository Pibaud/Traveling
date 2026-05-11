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
import com.example.application.model.RetrofitInstance
import kotlinx.coroutines.launch

class GroupDetailFragment : Fragment(R.layout.fragment_group_detail) {

    private var _binding: FragmentGroupDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var postAdapter: GroupPostAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentGroupDetailBinding.bind(view)

        val groupId = arguments?.getString("groupId") ?: return
        val groupName = arguments?.getString("groupName") ?: "Groupe"

        // Setup Toolbar
        binding.toolbarGroup.title = groupName
        binding.toolbarGroup.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.ivGroupInfo.setOnClickListener {
            val bottomSheet = GroupInfoBottomSheet.newInstance(groupId, groupName)
            bottomSheet.show(childFragmentManager, "GroupInfo")
        }

        // Setup RecyclerView des Posts
        postAdapter = GroupPostAdapter()
        binding.rvGroupPosts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = postAdapter
        }

        // Appel API pour récupérer les posts du groupe
        loadPosts(groupId)
    }

    private fun loadPosts(groupId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Remplacer par l'appel Retrofit réel
                val posts = RetrofitInstance.api.getGroupPosts(groupId)
                postAdapter.submitList(posts)
            } catch (e: Exception) {
                Log.e("GroupDetail", "Erreur chargement posts", e)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}