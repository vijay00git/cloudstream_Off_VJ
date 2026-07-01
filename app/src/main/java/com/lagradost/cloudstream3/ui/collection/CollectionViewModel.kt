package com.lagradost.cloudstream3.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CollectionViewModel : ViewModel() {

    private val _collections = MutableStateFlow<List<CustomCollection>>(emptyList())
    val collections: StateFlow<List<CustomCollection>> = _collections.asStateFlow()

    private val _collectionData = MutableStateFlow<Map<String, List<Pair<CollectionSection, HomePageList>>>>(emptyMap())
    val collectionData: StateFlow<Map<String, List<Pair<CollectionSection, HomePageList>>>> = _collectionData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val mapMutex = Mutex()

    fun loadCollections(context: android.content.Context?) {
        val all = DataStoreHelper.getAllCustomCollections()
        _collections.value = all
        
        if (all.isEmpty()) {
            _collectionData.value = emptyMap()
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _collectionData.value = emptyMap()
            
            coroutineScope {
                all.forEach { collection ->
                    launch {
                        val sectionData = mutableListOf<Pair<CollectionSection, HomePageList>>()
                        val collectionItems = mutableListOf<com.lagradost.cloudstream3.SearchResponse>()
                        
                        val sectionJobs = collection.sections.map { section ->
                            async {
                                val api = APIHolder.getApiFromNameNull(section.apiName) ?: return@async null
                                val mainPage = api.mainPage.find { it.name == section.listName } ?: return@async null
                                
                                try {
                                    val request = MainPageRequest(mainPage.name, mainPage.data, mainPage.horizontalImages)
                                    val response = api.getMainPage(1, request)
                                    
                                    if (response != null) {
                                        val list = response.items.find { it.name == section.listName }
                                        if (list != null) {
                                            return@async Pair(section, list)
                                        } else {
                                            val firstList = response.items.firstOrNull()
                                            if (firstList != null) {
                                                return@async Pair(section, HomePageList(section.listName, firstList.list))
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                null
                            }
                        }
                        
                        val results = sectionJobs.awaitAll().filterNotNull()
                        for (res in results) {
                            sectionData.add(res)
                            collectionItems.addAll(res.second.list)
                        }
                        
                        // Update TV Channel for this collection
                        if (context != null) {
                            try {
                                val channelId = com.lagradost.cloudstream3.utils.TvChannelUtils.getChannelId(context, collection.name)
                                    ?: com.lagradost.cloudstream3.utils.TvChannelUtils.createTvChannel(context, collection.name)
                                    
                                if (channelId != null) {
                                    com.lagradost.cloudstream3.utils.TvChannelUtils.clearProgramsForChannel(context, channelId)
                                    com.lagradost.cloudstream3.utils.TvChannelUtils.addPrograms(context, channelId, collectionItems)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        
                        // Update _collectionData progressively in a thread-safe manner
                        mapMutex.withLock {
                            val currentMap = _collectionData.value.toMutableMap()
                            currentMap[collection.id] = sectionData
                            _collectionData.value = currentMap
                        }
                    }
                }
            }
            _isLoading.value = false
        }
    }

    fun removeSection(collectionId: String, section: CollectionSection, context: android.content.Context?) {
        val collection = DataStoreHelper.getAllCustomCollections().find { it.id == collectionId } ?: return
        val newSections = collection.sections.filterNot { it.apiName == section.apiName && it.listName == section.listName }
        val updatedCollection = collection.copy(sections = newSections)
        DataStoreHelper.setCustomCollection(updatedCollection)
        loadCollections(context)
    }

    fun createCollection(name: String, sections: List<CollectionSection>, context: android.content.Context?) {
        if (name.isBlank()) return
        val newCollection = CustomCollection(name = name, sections = sections)
        DataStoreHelper.setCustomCollection(newCollection)
        loadCollections(context)
    }

    fun removeCollection(collectionId: String, context: android.content.Context?) {
        val collection = DataStoreHelper.getAllCustomCollections().find { it.id == collectionId }
        if (collection != null) {
            DataStoreHelper.removeCustomCollection(collection.id)
            if (context != null) {
                com.lagradost.cloudstream3.utils.TvChannelUtils.getChannelId(context, collection.name)?.let { channelId ->
                    com.lagradost.cloudstream3.utils.TvChannelUtils.deleteTvChannel(context, channelId)
                }
            }
        }
        loadCollections(context)
    }
}
