package com.orbitfiles.core

import java.io.File
import java.nio.charset.Charset
import java.text.Normalizer
import java.util.ArrayDeque
import java.util.Locale

data class SearchQuery(
    val text: String = "",
    val categories: Set<FileCategory> = emptySet(),
    val extensions: Set<String> = emptySet(),
    val minSize: Long? = null,
    val maxSize: Long? = null,
    val modifiedAfter: Long? = null,
    val modifiedBefore: Long? = null,
    val includeHidden: Boolean = false,
    val foldersOnly: Boolean = false,
    val filesOnly: Boolean = false,
    val regex: Boolean = false,
    val searchContent: Boolean = false,
    val limit: Int = 2000,
) {
    val isEmpty: Boolean get() = text.isBlank() && categories.isEmpty() && extensions.isEmpty() &&
        minSize == null && maxSize == null && modifiedAfter == null && modifiedBefore == null && !foldersOnly
}

data class SearchHit(val file: File, val score: Int, val contentLine: String? = null)

object TextMatch {
    /** Lowercase and strip accents so "musica" matches "Música". */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    /**
     * Fuzzy score: exact > prefix > word-prefix > contains > subsequence. 0 = no match.
     * Every whitespace separated token must match.
     */
    fun score(name: String, query: String): Int {
        val n = normalize(name)
        val tokens = normalize(query).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return 1
        var total = 0
        for (t in tokens) {
            val s = when {
                n == t -> 1000
                n.startsWith(t) -> 700
                Regex("[\\s._\\-(\\[]" + Regex.escape(t)).containsMatchIn(n) -> 500
                n.contains(t) -> 350
                isSubsequence(t, n) && t.length >= 3 -> 100 - (n.length - t.length).coerceAtMost(90)
                else -> return 0
            }
            total += s
        }
        return total
    }

    private fun isSubsequence(q: String, s: String): Boolean {
        var i = 0
        for (c in s) { if (i < q.length && q[i] == c) i++ }
        return i == q.length
    }
}

/** Walks directory trees iteratively (no recursion, symlink safe) and streams hits in batches. */
class FileSearcher(private val skipDirs: Set<String> = setOf(".orbit_trash", ".OrbitTrash", ".OrbitVault")) {

    fun matches(file: File, q: SearchQuery, regex: Regex?): Int {
        val isDir = file.isDirectory
        if (q.foldersOnly && !isDir) return 0
        if (q.filesOnly && isDir) return 0
        if (!q.includeHidden && file.name.startsWith(".")) return 0
        if (q.categories.isNotEmpty()) {
            val cat = FileCategory.of(file)
            if (cat !in q.categories && !(FileCategory.DOCUMENT in q.categories && cat in documentLike)) return 0
        }
        if (q.extensions.isNotEmpty() && FileCategory.extensionOf(file.name) !in q.extensions) return 0
        if (!isDir) {
            val len = file.length()
            if (q.minSize != null && len < q.minSize) return 0
            if (q.maxSize != null && len > q.maxSize) return 0
        } else if (q.minSize != null) return 0
        val lm = file.lastModified()
        if (q.modifiedAfter != null && lm < q.modifiedAfter) return 0
        if (q.modifiedBefore != null && lm >= q.modifiedBefore) return 0
        if (q.text.isBlank()) return 1
        return if (regex != null) (if (regex.containsMatchIn(file.name)) 500 else 0) else TextMatch.score(file.name, q.text)
    }

    fun search(
        roots: List<File>,
        q: SearchQuery,
        token: CancelToken = CancelToken(),
        onBatch: (List<SearchHit>) -> Unit = {},
    ): List<SearchHit> {
        val regex = if (q.regex && q.text.isNotBlank()) runCatching { Regex(q.text, RegexOption.IGNORE_CASE) }.getOrNull() else null
        val results = ArrayList<SearchHit>()
        val pending = ArrayList<SearchHit>()
        val stack = ArrayDeque<File>()
        roots.forEach { stack.push(it) }
        var lastFlush = System.nanoTime()
        while (stack.isNotEmpty() && results.size < q.limit) {
            token.throwIfCancelled()
            val dir = stack.pop()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory && !FileOps.isSymlink(child)) {
                    if (child.name in skipDirs) continue
                    if (q.includeHidden || !child.name.startsWith(".")) stack.push(child)
                }
                var score = matches(child, q, regex)
                var line: String? = null
                if (q.searchContent && q.text.isNotBlank() && !child.isDirectory) {
                    val contentQ = q.copy(text = "")
                    if (matches(child, contentQ, null) > 0 && FileCategory.isTextLike(child.name) && child.length() < 4_000_000) {
                        line = grep(child, q.text)
                        if (line != null) score = maxOf(score, 300)
                    }
                }
                if (score > 0) {
                    val hit = SearchHit(child, score, line)
                    results += hit; pending += hit
                    if (results.size >= q.limit) break
                }
            }
            if (pending.isNotEmpty() && System.nanoTime() - lastFlush > 120_000_000L) {
                onBatch(pending.toList()); pending.clear(); lastFlush = System.nanoTime()
            }
        }
        if (pending.isNotEmpty()) onBatch(pending.toList())
        return results.sortedWith(compareByDescending<SearchHit> { it.score }.thenByDescending { it.file.lastModified() })
    }

    private fun grep(file: File, text: String): String? {
        val needle = TextMatch.normalize(text)
        return runCatching {
            file.bufferedReader(Charset.forName("UTF-8")).useLines { lines ->
                lines.firstOrNull { TextMatch.normalize(it).contains(needle) }?.trim()?.take(160)
            }
        }.getOrNull()
    }

    companion object {
        val documentLike = setOf(FileCategory.DOCUMENT, FileCategory.PDF, FileCategory.SPREADSHEET, FileCategory.PRESENTATION, FileCategory.TEXT, FileCategory.EBOOK)
    }
}
