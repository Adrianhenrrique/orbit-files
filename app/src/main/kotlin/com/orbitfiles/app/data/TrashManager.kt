package com.orbitfiles.app.data

import com.orbitfiles.core.TrashEntry
import com.orbitfiles.core.TrashStore
import java.io.File

/** One hidden recycle bin per storage volume, so "delete" is an instant rename. */
class TrashManager(private val storage: StorageRepository) {
    private val stores = HashMap<String, TrashStore>()

    @Synchronized
    fun storeFor(file: File): TrashStore {
        val vol = storage.volumeFor(file)
        val root = vol?.root ?: storage.primaryRoot
        return stores.getOrPut(root.path) {
            val dir = File(root, ".OrbitTrash")
            if (dir.exists() || dir.mkdirs()) TrashStore(dir) else TrashStore(File(storage.primaryRoot, ".OrbitTrash"))
        }
    }

    fun allStores(): List<TrashStore> = storage.volumes().map { storeFor(it.root) }.distinctBy { it.root.path }

    data class Item(val store: TrashStore, val entry: TrashEntry)

    fun items(): List<Item> = allStores().flatMap { s -> s.list().map { Item(s, it) } }.sortedByDescending { it.entry.deletedAt }

    fun purge(retentionDays: Int): Int = allStores().sumOf { runCatching { it.purge(retentionDays) }.getOrDefault(0) }

    fun empty(): Int = allStores().sumOf { it.empty() }
}
