package com.lagradost.cloudstream3.ui.collection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.ui.result.ResultFragmentPhone
import com.lagradost.cloudstream3.ui.result.ResultFragmentTv
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.settings.Globals.TV

class CollectionFragment : Fragment() {

    private val collectionViewModel: CollectionViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CollectionScreen(
                    viewModel = collectionViewModel,
                    onResultClicked = { result ->
                        SearchHelper.handleSearchClickCallback(
                            com.lagradost.cloudstream3.ui.search.SearchClickCallback(
                                com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD,
                                requireView(),
                                -1,
                                result
                            )
                        )
                    }
                )
            }
        }
    }
}
