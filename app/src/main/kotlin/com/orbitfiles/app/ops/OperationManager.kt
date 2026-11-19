package com.orbitfiles.app.ops

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import androidx.core.content.ContextCompat
import com.orbitfiles.app.data.AppSettings
import com.orbitfiles.app.data.TrashManager
import com.orbitfiles.core.ArchiveManager
import com.orbitfiles.core.CancelToken
import com.orbitfiles.core.ConflictPolicy
import com.orbitfiles.core.FileOps
import com.orbitfiles.core.Journal
import com.orbitfiles.core.JournalEntry
import com.orbitfiles.core.OperationCancelledException
import com.orbitfiles.core.Progress
import com.orbitfiles.core.TrashStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

enum class OpKind { COPY, MOVE, DELETE, TRASH, ZIP, EXTRACT, VAULT, OTHER }
enum class OpStatus { RUNNING, DONE, FAILED, CANCELLED }

data class OpTask(
    val id: Long,
    val kind: OpKind,
    val title: String,
    val progress: Progress? = null,
    val status: OpStatus = OpStatus.RUNNING,
    val message: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val token: CancelToken = CancelToken(),
) {
    /** Bytes per second since start. */
    val speed: Double get() {
        val p = progress ?: return 0.0
        val secs = (System.currentTimeMillis() - startedAt) / 1000.0
        return if (secs <= 0.2) 0.0 else p.doneBytes / secs
    }
    val etaMillis: Long? get() {
        val p = progress ?: return null
        val s = speed
        if (s <= 0 || p.totalBytes <= 0) return null
        return ((p.totalBytes - p.doneBytes) / s * 1000).toLong()
    }
}

data class UiEvent(val message: String, val undo: JournalEntry? = null, val isError: Boolean = false)

data class Clipboard(val files: List<File>, val cut: Boolean)

/**
 * Central, process-wide engine for every file mutation. Runs work on background threads,
 * exposes live progress, keeps the undo journal, and drives the foreground service so
 * long copies survive the app going to the background.
 */
class OperationManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val trash: TrashManager,
    val journal: Journal,
    private val settings: StateFlow<AppSettings>,
) {
    private val ids = AtomicLong(1)
    private val _tasks = MutableStateFlow<List<OpTask>>(emptyList())
    val tasks: StateFlow<List<OpTask>> = _tasks.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _clipboard = MutableStateFlow<Clipboard?>(null)
    val clipboard: StateFlow<Clipboard?> = _clipboard.asStateFlow()

    /** Incremented after every change on disk so open listings refresh themselves. */
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    private val _history = MutableStateFlow<List<JournalEntry>>(emptyList())
    val history: StateFlow<List<JournalEntry>> = _history.asStateFlow()

    fun notifyChanged() { _version.update { it + 1 }; _history.value = journal.entries() }

    fun emit(message: String, undo: JournalEntry? = null, error: Boolean = false) {
        _events.tryEmit(UiEvent(message, undo, error))
    }

    // ---------------- clipboard ----------------

    fun setClipboard(files: List<File>, cut: Boolean) {
        _clipboard.value = Clipboard(files, cut)
        emit(if (cut) "${files.size} ${plural(files.size, "item recortado", "itens recortados")}" else "${files.size} ${plural(files.size, "item copiado", "itens copiados")}")
    }

    fun clearClipboard() { _clipboard.value = null }

    /** Names in [dest] that would collide with the clipboard content. */
    fun conflictsFor(sources: List<File>, dest: File): List<String> =
        sources.filter { File(dest, it.name).exists() && File(dest, it.name).canonicalPath != it.canonicalPath }.map { it.name }

    fun paste(dest: File, policy: ConflictPolicy) {
        val clip = _clipboard.value ?: return
        if (clip.cut) move(clip.files, dest, policy) else copy(clip.files, dest, policy)
        if (clip.cut) _clipboard.value = null
    }

    // ---------------- task runner ----------------

    private fun startTask(kind: OpKind, title: String): OpTask {
        val task = OpTask(ids.getAndIncrement(), kind, title)
        _tasks.update { it + task }
        startService()
        return task
    }

    private fun updateTask(id: Long, f: (OpTask) -> OpTask) = _tasks.update { list -> list.map { if (it.id == id) f(it) else it } }

    fun cancel(id: Long) { _tasks.value.firstOrNull { it.id == id }?.token?.cancel() }

    fun dismiss(id: Long) = _tasks.update { list -> list.filterNot { it.id == id } }

    fun dismissFinished() = _tasks.update { list -> list.filter { it.status == OpStatus.RUNNING } }

    /**
     * Runs [block] in the background as a tracked task. The block returns a success message
     * and optionally a journal entry for undo.
     */
    fun run(
        kind: OpKind,
        title: String,
        block: suspend (task: OpTask, progress: (Progress) -> Unit) -> Pair<String, JournalEntry?>,
    ) {
        val task = startTask(kind, title)
        scope.launch(Dispatchers.IO) {
            try {
                val (msg, entry) = block(task) { p -> updateTask(task.id) { it.copy(progress = p) } }
                entry?.let { journal.record(it) }
                updateTask(task.id) { it.copy(status = OpStatus.DONE, message = msg) }
                emit(msg, entry?.takeIf { it.undoable })
                scope.launch { kotlinx.coroutines.delay(2500); dismiss(task.id) }
            } catch (e: OperationCancelledException) {
                updateTask(task.id) { it.copy(status = OpStatus.CANCELLED, message = "Cancelado") }
                emit("$title cancelado")
                scope.launch { kotlinx.coroutines.delay(1500); dismiss(task.id) }
            } catch (e: Exception) {
                updateTask(task.id) { it.copy(status = OpStatus.FAILED, message = e.message ?: "Erro desconhecido") }
                emit(e.message ?: "Falha: $title", error = true)
            } finally {
                notifyChanged()
            }
        }
    }

    // ---------------- operations ----------------

    fun copy(sources: List<File>, dest: File, policy: ConflictPolicy = ConflictPolicy.KEEP_BOTH) =
        run(OpKind.COPY, "Copiando ${describe(sources)}") { task, progress ->
            val res = FileOps.copy(sources, dest, policy, task.token, progress)
            scan(res.created.map { it.second })
            val created = res.created.map { it.second }
            resultMessage("copiado", "copiados", res.created.size, res.skipped.size, res.failed) to
                (if (created.isNotEmpty()) JournalEntry.Copied(created) else null)
        }

    fun move(sources: List<File>, dest: File, policy: ConflictPolicy = ConflictPolicy.KEEP_BOTH) =
        run(OpKind.MOVE, "Movendo ${describe(sources)}") { task, progress ->
            val res = FileOps.move(sources, dest, policy, task.token, progress)
            scan(res.created.flatMap { listOf(it.first, it.second) })
            resultMessage("movido", "movidos", res.created.size, res.skipped.size, res.failed) to
                (if (res.created.isNotEmpty()) JournalEntry.Moved(res.created) else null)
        }

    /** Sends to the recycle bin (default) or deletes permanently. */
    fun delete(files: List<File>, permanent: Boolean = !settings.value.useTrash) =
        if (permanent) {
            run(OpKind.DELETE, "Excluindo ${describe(files)}") { task, progress ->
                var n = 0
                files.forEachIndexed { i, f ->
                    if (FileOps.deleteRecursively(f, task.token)) n++
                    progress(Progress(0, 0, i + 1, files.size, f.name))
                }
                scan(files)
                "$n ${plural(n, "item excluído", "itens excluídos")} permanentemente" to JournalEntry.Info("Excluído permanentemente: ${JournalEntry.describe(files)}")
            }
        } else {
            run(OpKind.TRASH, "Movendo para a lixeira") { task, progress ->
                val byStore = LinkedHashMap<TrashStore, MutableList<String>>()
                val names = mutableListOf<String>()
                files.forEachIndexed { i, f ->
                    val store = trash.storeFor(f)
                    val e = store.trash(f, task.token)
                    byStore.getOrPut(store) { mutableListOf() } += e.id
                    names += f.name
                    progress(Progress(0, 0, i + 1, files.size, f.name))
                }
                scan(files)
                val entry = byStore.entries.firstOrNull()?.let { (store, ids) ->
                    if (byStore.size == 1) JournalEntry.Trashed(store.root, ids, names) else null
                }
                "${files.size} ${plural(files.size, "item enviado", "itens enviados")} para a lixeira" to entry
            }
        }

    fun zip(sources: List<File>, dest: File) =
        run(OpKind.ZIP, "Compactando ${describe(sources)}") { task, progress ->
            val out = ArchiveManager.zip(sources, dest, token = task.token, onProgress = progress)
            scan(listOf(out))
            "Criado ${out.name}" to JournalEntry.Created(out)
        }

    fun extract(zip: File, destDir: File, only: Set<String>? = null) =
        run(OpKind.EXTRACT, "Extraindo ${zip.name}") { task, progress ->
            val existed = destDir.exists()
            val files = ArchiveManager.extract(zip, destDir, only, task.token, progress)
            scan(files)
            "${files.size} ${plural(files.size, "item extraído", "itens extraídos")}" to
                (if (!existed) JournalEntry.Created(destDir) else null)
        }

    suspend fun rename(file: File, newName: String): File? = withContext(Dispatchers.IO) {
        try {
            val r = FileOps.rename(file, newName)
            val entry = JournalEntry.Renamed(file, r)
            journal.record(entry)
            scan(listOf(file, r))
            emit("Renomeado para ${r.name}", entry)
            r
        } catch (e: Exception) {
            emit(e.message ?: "Falha ao renomear", error = true); null
        } finally { notifyChanged() }
    }

    suspend fun createFolder(parent: File, name: String): File? = create { FileOps.createFolder(parent, name) }
    suspend fun createFile(parent: File, name: String): File? = create { FileOps.createFile(parent, name) }

    private suspend fun create(block: () -> File): File? = withContext(Dispatchers.IO) {
        try {
            val f = block()
            val entry = JournalEntry.Created(f)
            journal.record(entry)
            emit("Criado: ${f.name}", entry)
            f
        } catch (e: Exception) {
            emit(e.message ?: "Falha ao criar", error = true); null
        } finally { notifyChanged() }
    }

    fun recordBatchRename(pairs: List<Pair<File, File>>) {
        pairs.forEach { (a, b) -> journal.record(JournalEntry.Renamed(a, b)) }
        scan(pairs.flatMap { listOf(it.first, it.second) })
        emit("${pairs.size} ${plural(pairs.size, "item renomeado", "itens renomeados")}")
        notifyChanged()
    }

    fun undo(entry: JournalEntry) {
        scope.launch(Dispatchers.IO) {
            try {
                emit(journal.undo(entry))
            } catch (e: Exception) {
                emit(e.message ?: "Não foi possível desfazer", error = true)
            } finally { notifyChanged() }
        }
    }

    // ---------------- helpers ----------------

    /** Keeps the MediaStore index (gallery, music apps…) in sync with our changes. */
    fun scan(files: List<File>) {
        val paths = files.flatMap { f -> if (f.isDirectory) f.walkTopDown().take(2000).map { it.path }.toList() else listOf(f.path) }
        if (paths.isEmpty()) return
        runCatching { MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null) }
    }

    private fun startService() {
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, FileOperationService::class.java))
        }
    }

    companion object {
        fun plural(n: Int, one: String, many: String) = if (n == 1) one else many
        fun describe(files: List<File>) = JournalEntry.describe(files)

        private fun resultMessage(one: String, many: String, ok: Int, skipped: Int, failed: List<Pair<File, String>>): String {
            val sb = StringBuilder("$ok ${plural(ok, "item $one", "itens $many")}")
            if (skipped > 0) sb.append(", $skipped ${plural(skipped, "ignorado", "ignorados")}")
            if (failed.isNotEmpty()) sb.append(" · ${failed.size} com erro: ${failed.first().second}")
            return sb.toString()
        }
    }
}
