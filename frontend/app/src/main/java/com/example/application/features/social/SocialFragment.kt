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

        // Le clic fonctionne à nouveau !
        binding.btnCreateGroup.setOnClickListener {
            findNavController().navigate(R.id.action_social_to_createGroup)
        }
    }

    private fun setupRecyclerViews() {
        // 1. Adaptateur pour les Groupes Populaires (Horizontal)
        popularAdapter = GroupAdapter(
            onJoinClick = { group -> viewModel.onJoinGroupClicked(group) },
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}