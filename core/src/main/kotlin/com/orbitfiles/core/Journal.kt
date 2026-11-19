package com.orbitfiles.core

import java.io.File
import java.io.IOException

/** A reversible record of something the user did. Powers the "Desfazer" snackbar and the history screen. */
sealed class JournalEntry {
    abstract val time: Long
    abstract val title: String
    open val undoable: Boolean = true

    data class Renamed(val from: File, val to: File, override val time: Long = now()) : JournalEntry() {
        override val title get() = "Renomeado: ${from.name} → ${to.name}"
    }

    data class Moved(val pairs: List<Pair<File, File>>, override val time: Long = now()) : JournalEntry() {
        override val title get() = "Movido: ${describe(pairs.map { it.first })}"
    }

    data class Copied(val created: List<File>, override val time: Long = now()) : JournalEntry() {
        override val title get() = "Copiado: ${describe(created)}"
    }

    data class Created(val file: File, override val time: Long = now()) : JournalEntry() {
        override val title get() = "Criado: ${file.name}"
    }

    data class Trashed(val storeRoot: File, val ids: List<String>, val names: List<String>, override val time: Long = now()) : JournalEntry() {
        override val title get() = "Enviado para a lixeira: ${names.take(2).joinToString()}${if (names.size > 2) " +${names.size - 2}" else ""}"
    }

    data class Info(override val title: String, override val time: Long = now()) : JournalEntry() {
        override val undoable get() = false
    }

    companion object {
        private fun now() = System.currentTimeMillis()
        fun describe(files: List<File>): String = when (files.size) {
            0 -> "nada"
            1 -> files[0].name
            else -> "${files[0].name} e mais ${files.size - 1}"
        }
    }
}

/** Bounded in-memory history with undo support. Thread-safe. */
class Journal(private val trashFor: (File) -> TrashStore?, private val capacity: Int = 100) {
    private val items = ArrayDeque<JournalEntry>()

    @Synchronized
    fun record(entry: JournalEntry) {
        items.addFirst(entry)
        while (items.size > capacity) items.removeLast()
    }

    @Synchronized
    fun entries(): List<JournalEntry> = items.toList()

    @Synchronized
    fun clear() = items.clear()

    /** Reverts [entry]. Returns a short description of what happened. */
    fun undo(entry: JournalEntry): String {
        val msg = when (entry) {
            is JournalEntry.Renamed -> {
                if (!entry.to.exists()) throw IOException("\"${entry.to.name}\" não existe mais")
                FileOps.rename(entry.to, entry.from.name)
                "Nome restaurado"
            }
            is JournalEntry.Moved -> {
                var n = 0
                entry.pairs.forEach { (from, to) ->
                    if (to.exists()) {
                        val parent = from.parentFile ?: return@forEach
                        val res = FileOps.move(listOf(to), parent, ConflictPolicy.KEEP_BOTH)
                        if (res.ok) n++
                    }
                }
                "$n ${if (n == 1) "item voltou" else "itens voltaram"} ao local original"
            }
            is JournalEntry.Copied -> {
                entry.created.filter { it.exists() }.forEach { f ->
                    val store = trashFor(f)
                    if (store != null) store.trash(f) else FileOps.deleteRecursively(f)
                }
                "Cópia desfeita"
            }
            is JournalEntry.Created -> {
                if (entry.file.exists()) {
                    val store = trashFor(entry.file)
                    if (store != null) store.trash(entry.file) else FileOps.deleteRecursively(entry.file)
                }
                "Criação desfeita"
            }
            is JournalEntry.Trashed -> {
                val store = TrashStore(entry.storeRoot)
                entry.ids.forEach { runCatching { store.restore(it) } }
                "${entry.ids.size} ${if (entry.ids.size == 1) "item restaurado" else "itens restaurados"}"
            }
            is JournalEntry.Info -> throw IOException("Esta ação não pode ser desfeita")
        }
        synchronized(this) { items.remove(entry) }
        return msg
    }
}
