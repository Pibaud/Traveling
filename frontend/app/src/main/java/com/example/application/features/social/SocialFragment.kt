package com.example.application.features.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.application.R
import com.example.application.databinding.FragmentSocialBinding
import com.example.application.model.Group
import com.example.application.utils.GuestUpsellBottomSheet
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class SocialFragment : Fragment() {

    private var _binding: FragmentSocialBinding? = null
    private val binding get() = _binding!!

    // On instancie le ViewModel qui va déclencher les appels réseau
    private val viewModel: SocialViewModel by viewModels()
    private lateinit var popularAdapter: GroupAdapter
    private lateinit var myGroupsAdapter: GroupAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSocialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        observeViewModel()

        binding.btnCreateGroup.setOnClickListener {
            val isGuest = Firebase.auth.currentUser?.isAnonymous == true
            if (isGuest) {
                GuestUpsellBottomSheet().show(childFragmentManager, "GuestUpsell")
            } else {
                findNavController().navigate(R.id.action_social_to_createGroup)
            }
        }
    }

    private fun setupRecyclerViews() {
        // 1. Adaptateur pour les Groupes Populaires (Horizontal)
        val onJoinAttempt = { group: Group ->
            val isGuest = Firebase.auth.currentUser?.isAnonymous == true
            if (isGuest) {
                GuestUpsellBottomSheet().show(childFragmentManager, "GuestUpsell")
            } else {
                viewModel.onJoinGroupClicked(group)
            }
        }

        popularAdapter = GroupAdapter(
            onJoinClick = onJoinAttempt,
            onNotificationClick = { group, enabled -> viewModel.onNotificationToggleClicked(group, enabled) }
        )
        binding.rvPopularGroups.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvPopularGroups.adapter = popularAdapter

        // 2. Adaptateur pour Mes Groupes (Vertical)
        myGroupsAdapter = GroupAdapter(
            onJoinClick = { group -> viewModel.onJoinGroupClicked(group) },
            onNotificationClick = { group, enabled -> viewModel.onNotificationToggleClicked(group, enabled) }
        )
        binding.rvMyGroups.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.rvMyGroups.adapter = myGroupsAdapter
    }

    private fun observeViewModel() {
        viewModel.popularGroups.observe(viewLifecycleOwner) { groups ->
            popularAdapter.submitList(groups)
            binding.tvPopularGroupsTitle.visibility = if (groups.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.myGroups.observe(viewLifecycleOwner) { groups ->
            myGroupsAdapter.submitList(groups)
            if (groups.isEmpty()) {
                binding.tvMyGroupsTitle.visibility = View.GONE
                binding.rvMyGroups.visibility = View.GONE
            } else {
                binding.tvMyGroupsTitle.visibility = View.VISIBLE
                binding.rvMyGroups.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Force le rechargement avec le véritable utilisateur actuel de Firebase
        viewModel.loadGroups()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}