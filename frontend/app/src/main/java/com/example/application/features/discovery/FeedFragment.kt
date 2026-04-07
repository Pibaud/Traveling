package com.example.application.features.discovery

import DiscoveryAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.application.databinding.FragmentDiscoveryBinding
import androidx.navigation.fragment.findNavController
import com.example.application.R


class FeedFragment : Fragment() {

    // Utilisation du ViewBinding pour lier le XML (fragment_discovery.xml)
    private var _binding: FragmentDiscoveryBinding? = null
    private val binding get() = _binding!!

    // Initialisation du ViewModel (qui contient la logique de tes fausses données)
    private val viewModel: DiscoveryViewModel by viewModels()
    private lateinit var adapter: DiscoveryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiscoveryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()

        binding.ivSearch.setOnClickListener {
            findNavController().navigate(R.id.action_feed_to_search)
        }
    }

    private fun setupRecyclerView() {
        adapter = DiscoveryAdapter()
        val layoutManager = LinearLayoutManager(requireContext())

        binding.rvDiscovery.layoutManager = layoutManager
        binding.rvDiscovery.adapter = adapter

        // LE SECRET POUR L'EFFET TIKTOK / REELS (Plein écran strict)
        val snapHelper = PagerSnapHelper()
        // On s'assure de ne l'attacher qu'une seule fois
        binding.rvDiscovery.onFlingListener = null
        snapHelper.attachToRecyclerView(binding.rvDiscovery)

        // Ajout de l'écouteur pour le Scroll Infini
        binding.rvDiscovery.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                // Si on s'approche de la fin de la liste (ex: il reste 3 items)
                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 3) {
                    // On demande au ViewModel de charger la page suivante
                    viewModel.loadMorePosts()
                }
            }
        })
    }

    private fun observeViewModel() {
        // Observe les changements de la liste envoyée par le ViewModel
        viewModel.posts.observe(viewLifecycleOwner) { posts ->
            // Met à jour l'adaptateur avec les nouvelles données
            adapter.submitList(posts)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Très important en Android : on nettoie le binding pour éviter les fuites de mémoire
        _binding = null
    }
}