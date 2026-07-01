package com.lagradost.cloudstream3.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR

@Composable
fun CollectionScreen(
    viewModel: CollectionViewModel,
    onResultClicked: (SearchResponse) -> Unit
) {
    val collections by viewModel.collections.collectAsState()
    val collectionData by viewModel.collectionData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showManageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadCollections(context)
    }

    val isTv = isLayout(TV or EMULATOR)
    val leftPadding = if (isTv) 80.dp else 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0E))
            .systemBarsPadding()
            .padding(start = leftPadding)
    ) {
        if (collections.isEmpty()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No collections yet.", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp, top = 70.dp)
            ) {
                for (collection in collections) {
                    item {
                        CollectionHeader(
                            name = collection.name,
                            onRemove = { viewModel.removeCollection(collection.id, context) }
                        )
                    }

                    val lists = collectionData[collection.id] ?: emptyList()
                    if (lists.isEmpty()) {
                        item {
                            Text(
                                text = "Loading sections...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                            )
                        }
                    } else {
                        for ((section, list) in lists) {
                            item {
                                SectionHeader(
                                    name = list.name,
                                    onRemove = { viewModel.removeSection(collection.id, section, context) }
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    items(list.list) { result ->
                                        SearchResultItem(result = result, onClick = { onResultClicked(result) })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).height(3.dp),
                    color = Color.White,
                    trackColor = Color.Transparent
                )
            }
        }
        
        var isManageBtnFocused by remember { mutableStateOf(false) }
        Button(
            onClick = { showManageDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .onFocusChanged { isManageBtnFocused = it.isFocused }
                .focusable()
                .border(
                    width = if (isManageBtnFocused) 2.dp else 0.dp,
                    color = if (isManageBtnFocused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(50)
                )
        ) {
            Icon(Icons.Default.Add, contentDescription = "Manage Collection")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Manage Collection")
        }
    }

    if (showManageDialog) {
        ManageCollectionDialog(
            onDismiss = { showManageDialog = false },
            onCreate = { name, sections ->
                viewModel.createCollection(name, sections, context)
                showManageDialog = false
            }
        )
    }
}

@Composable
fun CollectionHeader(name: String, onRemove: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .background(
                    color = if (isFocused) Color.Red.copy(alpha = 0.3f) else Color.Transparent,
                    shape = RoundedCornerShape(50)
                )
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) Color.Red else Color.Transparent,
                    shape = RoundedCornerShape(50)
                )
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete Collection", tint = Color.Red)
        }
    }
}

@Composable
fun SectionHeader(name: String, onRemove: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.LightGray
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .background(
                    color = if (isFocused) Color.Gray.copy(alpha = 0.3f) else Color.Transparent,
                    shape = RoundedCornerShape(50)
                )
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(50)
                )
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete Section", tint = if (isFocused) Color.White else Color.Gray)
        }
    }
}

@Composable
fun ManageCollectionDialog(
    onDismiss: () -> Unit,
    onCreate: (String, List<CollectionSection>) -> Unit
) {
    var collectionName by remember { mutableStateOf("") }
    val allSections = remember {
        APIHolder.apis.flatMap { api ->
            api.mainPage.map { mainPageData ->
                CollectionSection(apiName = api.name, listName = mainPageData.name)
            }
        }
    }
    val selectedSections = remember { mutableStateListOf<CollectionSection>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Collection") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = collectionName,
                    onValueChange = { collectionName = it },
                    label = { Text("Collection Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Sections:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                ) {
                    items(allSections) { section ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedSections.contains(section)) {
                                        selectedSections.remove(section)
                                    } else {
                                        selectedSections.add(section)
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedSections.contains(section),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedSections.add(section)
                                    } else {
                                        selectedSections.remove(section)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${section.apiName} - ${section.listName}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(collectionName, selectedSections.toList()) },
                enabled = collectionName.isNotBlank()
            ) {
                Text("Create")
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
fun SearchResultItem(result: SearchResponse, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .width(110.dp)
            .height(160.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = result.posterUrl,
                contentDescription = result.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color(0x99000000))
                    .padding(4.dp)
            ) {
                Text(
                    text = result.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
