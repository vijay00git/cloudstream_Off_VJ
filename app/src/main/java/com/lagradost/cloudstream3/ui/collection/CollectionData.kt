package com.lagradost.cloudstream3.ui.collection

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class CustomCollection(
    @JsonProperty("id") val id: String = UUID.randomUUID().toString(),
    @JsonProperty("name") val name: String,
    @JsonProperty("sections") val sections: List<CollectionSection> = emptyList()
)

data class CollectionSection(
    @JsonProperty("apiName") val apiName: String,
    @JsonProperty("listName") val listName: String
)
