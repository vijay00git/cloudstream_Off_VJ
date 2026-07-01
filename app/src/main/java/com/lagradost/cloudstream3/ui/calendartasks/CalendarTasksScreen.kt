package com.lagradost.cloudstream3.ui.calendartasks

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR

// Expands recurring events for the next 7 days in upcoming schedule
fun getUpcomingOccurrences(events: List<CalendarEvent>, days: Int = 7): List<CalendarEvent> {
    val result = mutableListOf<CalendarEvent>()
    val now = System.currentTimeMillis()
    val endLimit = now + days * 86400000L

    for (event in events) {
        if (event.recurrence == "None" || event.recurrence.isBlank()) {
            if (event.startTime >= now - 7200000) { // include events started in the last 2 hours
                result.add(event)
            }
        } else {
            val interval = when (event.recurrence.lowercase()) {
                "daily" -> 86400000L
                "weekly" -> 7 * 86400000L
                "monthly" -> 30 * 86400000L
                else -> 0L
            }
            if (interval > 0L) {
                var currentStart = event.startTime
                var currentEnd = event.endTime

                // Fast forward to present window if the event start is in the past
                while (currentStart < now - 7200000 && currentStart < endLimit) {
                    currentStart += interval
                    currentEnd += interval
                }

                var limit = 0
                while (currentStart < endLimit && limit < 15) {
                    result.add(
                        event.copy(
                            id = "${event.id}_occ_${currentStart}",
                            startTime = currentStart,
                            endTime = currentEnd
                        )
                    )
                    currentStart += interval
                    currentEnd += interval
                    limit++
                }
            }
        }
    }
    return result.sortedBy { it.startTime }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarTasksScreen(
    viewModel: CalendarViewModel
) {
    val context = LocalContext.current
    val events by viewModel.events.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Dialog flags
    var showAddEventDialog by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var selectedEventForOptions by remember { mutableStateOf<CalendarEvent?>(null) }
    var selectedTaskForOptions by remember { mutableStateOf<GoogleTask?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    // Filter and Sort states
    var selectedPriorityFilter by remember { mutableStateOf("All") }
    var sortByDueDate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    val isTv = isLayout(TV or EMULATOR)
    val leftPadding = if (isTv) 80.dp else 0.dp

    // Calculate progress stats
    val totalTasks = tasks.size
    val completedTasks = tasks.count { it.isCompleted }
    val progress = if (totalTasks > 0) completedTasks.toFloat() / totalTasks else 0f

    // Filtered & Sorted tasks
    val filteredTasks = remember(tasks, selectedPriorityFilter, sortByDueDate) {
        var list = if (selectedPriorityFilter == "All") {
            tasks
        } else {
            tasks.filter { it.priority.lowercase() == selectedPriorityFilter.lowercase() }
        }

        list = if (sortByDueDate) {
            list.sortedWith(compareBy({ it.isCompleted }, { it.dueDate ?: Long.MAX_VALUE }))
        } else {
            list.sortedWith(compareBy({ it.isCompleted }, { it.id }))
        }
        list
    }

    // Dynamic expanded list of upcoming occurrences
    val upcomingSchedule = remember(events) { getUpcomingOccurrences(events) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0C))
            .systemBarsPadding()
            .padding(start = leftPadding)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Header stats bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1A122B), Color(0xFF0F0C1B))
                        )
                    )
                    .border(width = 1.dp, color = Color(0xFF2E1C4E), shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Personal Planner",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Stored safely on this device",
                            fontSize = 13.sp,
                            color = Color(0xFFB388FF)
                        )
                    }

                    // Progress section
                    Column(
                        modifier = Modifier
                            .width(220.dp)
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "$completedTasks/$totalTasks Tasks Completed",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            color = Color(0xFF00E5FF),
                            trackColor = Color.DarkGray,
                            modifier = Modifier
                                .height(8.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }

                    // Clear button
                    var isClearFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = { showClearAllConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                        modifier = Modifier
                            .onFocusChanged { isClearFocused = it.isFocused }
                            .border(
                                width = if (isClearFocused) 2.dp else 0.dp,
                                color = if (isClearFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(50)
                            )
                    ) {
                        Text("Reset All", fontSize = 13.sp)
                    }
                }
            }

            // Main Split Panel
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                
                // Left Column: Upcoming Events (Schedule)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.55f)
                        .background(Color(0xFF141414), shape = RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF242424), shape = RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Upcoming Schedule (7 Days)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        
                        var isAddEventFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showAddEventDialog = true },
                            modifier = Modifier
                                .background(Color(0xFFB388FF), shape = RoundedCornerShape(50))
                                .onFocusChanged { isAddEventFocused = it.isFocused }
                                .border(
                                    width = if (isAddEventFocused) 2.dp else 0.dp,
                                    color = if (isAddEventFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(50)
                                )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Event", tint = Color.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (upcomingSchedule.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No events scheduled.", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(upcomingSchedule) { event ->
                                EventItemCard(
                                    event = event,
                                    onClick = {
                                        // Open options for the original base event, strip virtual suffix
                                        val baseId = event.id.substringBefore("_occ_")
                                        val baseEvent = events.firstOrNull { it.id == baseId } ?: event
                                        selectedEventForOptions = baseEvent
                                    }
                                )
                            }
                        }
                    }
                }

                // Right Column: Tasks Checklist
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.45f)
                        .background(Color(0xFF141414), shape = RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF242424), shape = RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Checklist",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        var isAddTaskFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showAddTaskDialog = true },
                            modifier = Modifier
                                .background(Color(0xFF00E5FF), shape = RoundedCornerShape(50))
                                .onFocusChanged { isAddTaskFocused = it.isFocused }
                                .border(
                                    width = if (isAddTaskFocused) 2.dp else 0.dp,
                                    color = if (isAddTaskFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(50)
                                )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Task", tint = Color.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Priority Filter chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("All", "High", "Normal", "Low").forEach { pFilter ->
                            var isChipFocused by remember { mutableStateOf(false) }
                            val isSelected = selectedPriorityFilter == pFilter
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedPriorityFilter = pFilter }
                                    .onFocusChanged { isChipFocused = it.isFocused }
                                    .focusable()
                                    .background(
                                        color = if (isSelected) Color(0xFF323232) else Color(0xFF1E1E1E),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = if (isChipFocused) 2.dp else 1.dp,
                                        color = if (isChipFocused) Color.White else if (isSelected) Color(0xFF00E5FF) else Color(0xFF2E2E2E),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = pFilter,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Sort controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sort by Due Date", fontSize = 12.sp, color = Color.Gray)
                        Switch(
                            checked = sortByDueDate,
                            onCheckedChange = { sortByDueDate = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00E5FF),
                                checkedTrackColor = Color(0xFF005B66)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (filteredTasks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No tasks found.", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredTasks) { task ->
                                TaskItemCard(
                                    task = task,
                                    onCheckChange = { viewModel.toggleTask(context, task) },
                                    onClick = { selectedTaskForOptions = task }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dialogs
        if (showAddEventDialog) {
            AddEventDialog(
                onDismiss = { showAddEventDialog = false },
                onSave = { title, desc, category, recurrence, startTime, endTime ->
                    viewModel.addEvent(context, title, desc, category, recurrence, startTime, endTime)
                    showAddEventDialog = false
                }
            )
        }

        if (showAddTaskDialog) {
            AddTaskDialog(
                onDismiss = { showAddTaskDialog = false },
                onSave = { title, notes, priority, recurrence, dueMs ->
                    viewModel.addTask(context, title, notes, priority, dueMs, recurrence)
                    showAddTaskDialog = false
                }
            )
        }

        selectedEventForOptions?.let { event ->
            EventOptionsDialog(
                event = event,
                onDismiss = { selectedEventForOptions = null },
                onDelete = {
                    viewModel.deleteEvent(context, event)
                    selectedEventForOptions = null
                }
            )
        }

        selectedTaskForOptions?.let { task ->
            TaskOptionsDialog(
                task = task,
                onDismiss = { selectedTaskForOptions = null },
                onToggle = {
                    viewModel.toggleTask(context, task)
                    selectedTaskForOptions = null
                },
                onDelete = {
                    viewModel.deleteTask(context, task)
                    selectedTaskForOptions = null
                }
            )
        }

        if (showClearAllConfirm) {
            AlertDialog(
                onDismissRequest = { showClearAllConfirm = false },
                title = { Text("Reset Planner") },
                text = { Text("Are you sure you want to delete all local events and tasks? This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAll(context)
                            showClearAllConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun FocusableSelectionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .background(
                color = if (isFocused) Color.White.copy(alpha = 0.15f) else Color(0xFF1E1E1E),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF2E2E2E),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// Custom highly TV-friendly Date-Time Picker Dialog
@Composable
fun CustomDateTimePickerDialog(
    onDismiss: () -> Unit,
    onDateTimeSelected: (Long) -> Unit
) {
    var selectedDayOffset by remember { mutableStateOf(0) }
    var hour by remember { mutableStateOf(12) }
    var minute by remember { mutableStateOf(0) }

    val daysList = remember {
        (0..29).map { offset ->
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, offset)
            cal.time
        }
    }
    val dayFormatter = remember { java.text.SimpleDateFormat("EEEE, MMM dd", java.util.Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick Date & Time", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select Date:", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 12.sp)
                
                // TV-friendly Date selection list (takes 90dp space, scrollable)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp)
                        .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(30) { index ->
                            val isSelected = selectedDayOffset == index
                            var isFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDayOffset = index }
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .focusable()
                                    .background(
                                        color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        width = if (isFocused) 1.5.dp else 0.dp,
                                        color = if (isFocused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = dayFormatter.format(daysList[index]),
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Time selector increment/decrement adjusters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Hour adjuster
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Hour", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            var isDecFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = { hour = if (hour == 0) 23 else hour - 1 },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF2C2C2C), RoundedCornerShape(50))
                                    .onFocusChanged { isDecFocused = it.isFocused }
                                    .border(width = if (isDecFocused) 1.5.dp else 0.dp, color = Color.White, shape = RoundedCornerShape(50))
                            ) { Text("-", color = Color.White, fontWeight = FontWeight.Bold) }

                            Text(String.format("%02d", hour), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)

                            var isIncFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = { hour = (hour + 1) % 24 },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF2C2C2C), RoundedCornerShape(50))
                                    .onFocusChanged { isIncFocused = it.isFocused }
                                    .border(width = if (isIncFocused) 1.5.dp else 0.dp, color = Color.White, shape = RoundedCornerShape(50))
                            ) { Text("+", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }

                    // Minute adjuster
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Minute", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            var isDecFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = { minute = if (minute < 5) 55 else minute - 5 },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF2C2C2C), RoundedCornerShape(50))
                                    .onFocusChanged { isDecFocused = it.isFocused }
                                    .border(width = if (isDecFocused) 1.5.dp else 0.dp, color = Color.White, shape = RoundedCornerShape(50))
                            ) { Text("-", color = Color.White, fontWeight = FontWeight.Bold) }

                            Text(String.format("%02d", minute), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)

                            var isIncFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = { minute = (minute + 5) % 60 },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF2C2C2C), RoundedCornerShape(50))
                                    .onFocusChanged { isIncFocused = it.isFocused }
                                    .border(width = if (isIncFocused) 1.5.dp else 0.dp, color = Color.White, shape = RoundedCornerShape(50))
                            ) { Text("+", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cal = java.util.Calendar.getInstance()
                    cal.time = daysList[selectedDayOffset]
                    cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
                    cal.set(java.util.Calendar.MINUTE, minute)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    onDateTimeSelected(cal.timeInMillis)
                }
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EventItemCard(
    event: CalendarEvent,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val categoryColor = when (event.category.lowercase()) {
        "watch" -> Color(0xFFB388FF)
        "personal" -> Color(0xFF80F066)
        "urgent" -> Color(0xFFFF5252)
        else -> Color(0xFF00E5FF)
    }

    val sdf = remember { java.text.SimpleDateFormat("EEE, MMM dd • HH:mm", java.util.Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .background(
                color = if (isFocused) Color(0xFF242424) else Color(0xFF1C1C1C),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF2C2C2C),
                shape = RoundedCornerShape(12.dp)
            )
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .background(categoryColor)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (event.recurrence != "None" && event.recurrence.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF262626), shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🔄 ${event.recurrence.uppercase()}",
                                color = Color.LightGray,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(categoryColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = event.category.uppercase(),
                            color = categoryColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            event.description?.let {
                if (it.isNotBlank()) {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Text(
                text = sdf.format(java.util.Date(event.startTime)),
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun TaskItemCard(
    task: GoogleTask,
    onCheckChange: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val priorityColor = when (task.priority.lowercase()) {
        "high" -> Color(0xFFFF5252)
        "normal" -> Color(0xFFFFA726)
        else -> Color(0xFF00E5FF)
    }

    val sdf = remember { java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .background(
                color = if (isFocused) Color(0xFF242424) else Color(0xFF1C1C1C),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF2C2C2C),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = if (task.isCompleted) Color(0xFF00E5FF) else Color.Transparent,
                    shape = RoundedCornerShape(6.dp)
                )
                .border(
                    width = 2.dp,
                    color = if (task.isCompleted) Color(0xFF00E5FF) else Color.Gray,
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable { onCheckChange() }
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (task.isCompleted) {
                Icon(Icons.Default.Check, contentDescription = "Checked", tint = Color.Black, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (task.isCompleted) Color.Gray else Color.White,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            task.notes?.let {
                if (it.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Tags
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (task.recurrence != "None" && task.recurrence.isNotBlank()) {
                Text(
                    text = "🔄 ${task.recurrence.uppercase()}",
                    fontSize = 9.sp,
                    color = Color.LightGray,
                    modifier = Modifier
                        .background(Color(0xFF262626), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            task.dueDate?.let {
                Text(
                    text = sdf.format(java.util.Date(it)),
                    fontSize = 10.sp,
                    color = Color.LightGray,
                    modifier = Modifier
                        .background(Color(0xFF2C2C2C), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .background(priorityColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
                    .border(width = 0.5.dp, color = priorityColor, shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = task.priority.uppercase(),
                    color = priorityColor,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AddEventDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, category: String, recurrence: String, startMs: Long, endMs: Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Watch") }
    var recurrence by remember { mutableStateOf("None") } // None, Daily, Weekly, Monthly
    
    // Custom picker selections
    var startTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var durationMinutes by remember { mutableStateOf(60) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val sdf = remember { java.text.SimpleDateFormat("EEE, MMM dd @ HH:mm", java.util.Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Event to Schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Notes / Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Watch", "Personal", "Urgent", "General").forEach { cat ->
                        val isSelected = category == cat
                        val catColor = when (cat.lowercase()) {
                            "watch" -> Color(0xFFB388FF)
                            "personal" -> Color(0xFF80F066)
                            "urgent" -> Color(0xFFFF5252)
                            else -> Color(0xFF00E5FF)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { category = cat }
                                .background(
                                    color = if (isSelected) catColor.copy(alpha = 0.2f) else Color(0xFF1E1E1E),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) catColor else Color(0xFF2E2E2E),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(cat, color = if (isSelected) Color.White else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text("Repeat schedule:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("None", "Daily", "Weekly", "Monthly").forEach { rep ->
                        val isSelected = recurrence == rep
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { recurrence = rep }
                                .background(
                                    color = if (isSelected) Color(0xFF3C3C3C) else Color(0xFF1E1E1E),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color(0xFF2E2E2E),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(rep, color = if (isSelected) Color.White else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Custom Date Picker Trigger Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Start Time:", fontSize = 11.sp, color = Color.Gray)
                        Text(sdf.format(java.util.Date(startTimeMs)), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    FocusableSelectionButton(
                        text = "Pick Date & Time",
                        onClick = { showTimePickerDialog = true }
                    )
                }

                Text("Duration:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("30 Mins" to 30, "1 Hour" to 60, "2 Hours" to 120).forEach { (label, mins) ->
                        val isSelected = durationMinutes == mins
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { durationMinutes = mins }
                                .background(
                                    color = if (isSelected) Color(0xFF3C3C3C) else Color(0xFF1E1E1E),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color(0xFF2E2E2E),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (isSelected) Color.White else Color.Gray, fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val durationMs = durationMinutes * 60000L
                        onSave(title, desc, category, recurrence, startTimeMs, startTimeMs + durationMs)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showTimePickerDialog) {
        CustomDateTimePickerDialog(
            onDismiss = { showTimePickerDialog = false },
            onDateTimeSelected = { timeMs ->
                startTimeMs = timeMs
                showTimePickerDialog = false
            }
        )
    }
}

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, notes: String, priority: String, recurrence: String, dueMs: Long?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Normal") }
    var recurrence by remember { mutableStateOf("None") } // None, Daily, Weekly, Monthly
    
    // Custom picker selections
    var dueDateMs by remember { mutableStateOf<Long?>(null) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val sdf = remember { java.text.SimpleDateFormat("EEE, MMM dd @ HH:mm", java.util.Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Task to Checklist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Priority:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("High", "Normal", "Low").forEach { pLevel ->
                        val isSelected = priority == pLevel
                        val pColor = when (pLevel.lowercase()) {
                            "high" -> Color(0xFFFF5252)
                            "normal" -> Color(0xFFFFA726)
                            else -> Color(0xFF00E5FF)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { priority = pLevel }
                                .background(
                                    color = if (isSelected) pColor.copy(alpha = 0.2f) else Color(0xFF1E1E1E),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) pColor else Color(0xFF2E2E2E),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(pLevel, color = if (isSelected) Color.White else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text("Repeat checklist item:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("None", "Daily", "Weekly", "Monthly").forEach { rep ->
                        val isSelected = recurrence == rep
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { recurrence = rep }
                                .background(
                                    color = if (isSelected) Color(0xFF3C3C3C) else Color(0xFF1E1E1E),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color(0xFF2E2E2E),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(rep, color = if (isSelected) Color.White else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Custom Date Picker Trigger Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Due Date:", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = dueDateMs?.let { sdf.format(java.util.Date(it)) } ?: "No date set",
                            fontSize = 13.sp,
                            color = if (dueDateMs != null) Color.White else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dueDateMs != null) {
                            FocusableSelectionButton(
                                text = "Clear",
                                onClick = { dueDateMs = null },
                                color = Color.LightGray
                            )
                        }
                        FocusableSelectionButton(
                            text = "Set Due Date",
                            onClick = { showTimePickerDialog = true }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title, notes, priority, recurrence, dueDateMs)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showTimePickerDialog) {
        CustomDateTimePickerDialog(
            onDismiss = { showTimePickerDialog = false },
            onDateTimeSelected = { timeMs ->
                dueDateMs = timeMs
                showTimePickerDialog = false
            }
        )
    }
}

@Composable
fun EventOptionsDialog(
    event: CalendarEvent,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(event.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category: ${event.category}", color = Color.Gray, fontSize = 13.sp)
                if (event.recurrence != "None" && event.recurrence.isNotBlank()) {
                    Text("Recurrence: Repeats ${event.recurrence}", color = Color(0xFFB388FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                event.description?.let {
                    if (it.isNotBlank()) {
                        Text(it, color = Color.LightGray, fontSize = 14.sp)
                    }
                }
                val sdf = remember { java.text.SimpleDateFormat("EEE, MMM dd @ HH:mm", java.util.Locale.getDefault()) }
                Text("Starts: ${sdf.format(java.util.Date(event.startTime))}", fontSize = 13.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Event")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TaskOptionsDialog(
    task: GoogleTask,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Priority: ${task.priority.uppercase()}", color = Color.Gray, fontSize = 13.sp)
                if (task.recurrence != "None" && task.recurrence.isNotBlank()) {
                    Text("Recurrence: Repeats ${task.recurrence}", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                task.notes?.let {
                    if (it.isNotBlank()) {
                        Text(it, color = Color.LightGray, fontSize = 14.sp)
                    }
                }
                task.dueDate?.let {
                    val sdf = remember { java.text.SimpleDateFormat("EEE, MMM dd", java.util.Locale.getDefault()) }
                    Text("Due: ${sdf.format(java.util.Date(it))}", fontSize = 13.sp, color = Color.Gray)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                ) {
                    Text(if (task.isCompleted) "Mark Pending" else "Mark Completed")
                }
                
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
