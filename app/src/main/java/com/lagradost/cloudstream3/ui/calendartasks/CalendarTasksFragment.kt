package com.lagradost.cloudstream3.ui.calendartasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class CalendarTasksFragment : Fragment() {

    private val calendarViewModel: CalendarViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CalendarTasksScreen(
                    viewModel = calendarViewModel
                )
            }
        }
    }
}
