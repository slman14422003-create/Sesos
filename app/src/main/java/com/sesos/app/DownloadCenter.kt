package com.sesos.app

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

object DownloadCenter {
    val items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val canceled: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun add(item: DownloadItem) {
        items.value = listOf(item) + items.value
    }

    fun update(id: String, f: (DownloadItem) -> DownloadItem) {
        items.value = items.value.map { if (it.id == id) f(it) else it }
    }

    fun remove(id: String) {
        items.value = items.value.filterNot { it.id == id }
    }
}
