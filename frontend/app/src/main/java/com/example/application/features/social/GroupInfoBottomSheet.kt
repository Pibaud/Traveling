package com.example.application.features.social

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.application.databinding.BottomSheetGroupInfoBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class GroupInfoBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetGroupInfoBinding? = null
    private val binding get() = _binding!!
    private lateinit var memberAdapter: GroupMemberAdapter

    companion object {
        fun newInstance(groupId: String, groupName: String): GroupInfoBottomSheet {
            return GroupInfoBottomSheet().apply {
                arguments = Bundle().apply {
                    putString("groupId", groupId)
                    putString("groupName", groupName)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetGroupInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val groupName = arguments?.getString("groupName")
        val groupId = arguments?.getString("groupId") ?: return

        binding.tvInfoTitle.text = "Membres de $groupName"

        // Setup Adapter
        memberAdapter = GroupMemberAdapter()
        binding.rvMembers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = memberAdapter
        }

        // Récupérer les membres
        loadMembers(groupId)
    }

    private fun loadMembers(groupId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Remplacer par l'appel Retrofit réel
                val members = RetrofitInstance.api.getGroupMembers(groupId)
                memberAdapter.submitList(members)
            } catch (e: Exception) {
                Log.e("GroupInfo", "Erreur chargement membres", e)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}