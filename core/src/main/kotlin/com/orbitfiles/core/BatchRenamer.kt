package com.orbitfiles.core

import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CaseMode { KEEP, LOWER, UPPER, TITLE }

/**
 * Pattern tokens:
 *  {name} original name without extension, {ext} extension, {n} counter, {n:3} zero padded counter,
 *  {date} / {date:yyyy-MM-dd} last modified date, {parent} parent folder name.
 */
data class RenameRule(
    val pattern: String = "{name}",
    val find: String = "",
    val replace: String = "",
    val useRegex: Boolean = false,
    val case: CaseMode = CaseMode.KEEP,
    val startAt: Int = 1,
    val keepExtension: Boolean = true,
)

data class RenamePreview(val file: File, val newName: String, val error: String?)

object BatchRenamer {
    private val token = Regex("""\{(name|ext|n|date|parent)(?::([^}]+))?\}""")

    fun preview(files: List<File>, rule: RenameRule): List<RenamePreview> {
        val names = files.mapIndexed { i, f -> f to apply(f, rule, rule.startAt + i) }
        val counts = names.groupingBy { (f, n) -> (f.parentFile?.path ?: "") + "/" + n.lowercase(Locale.ROOT) }.eachCount()
        val sources = files.map { it.path.lowercase(Locale.ROOT) }.toSet()
        return names.map { (f, n) ->
            val key = (f.parentFile?.path ?: "") + "/" + n.lowercase(Locale.ROOT)
            val target = File(f.parentFile, n)
            val err = FileOps.validateName(n)
                ?: if ((counts[key] ?: 0) > 1) "Nome duplicado no lote" else null
                ?: if (target.exists() && target.path.lowercase(Locale.ROOT) !in sources) "Já existe" else null
            RenamePreview(f, n, err)
        }
    }

    fun apply(file: File, rule: RenameRule, index: Int): String {
        val ext = if (file.isDirectory) "" else FileCategory.extensionOf(file.name)
        val base = if (ext.isEmpty()) file.name else file.name.dropLast(ext.length + 1)
        var out = token.replace(rule.pattern) { m ->
            val arg = m.groupValues[2]
            when (m.groupValues[1]) {
                "name" -> base
                "ext" -> ext
                "n" -> if (arg.isNotEmpty()) index.toString().padStart(arg.toIntOrNull() ?: 1, '0') else index.toString()
                "date" -> SimpleDateFormat(arg.ifEmpty { "yyyy-MM-dd" }, Locale.ROOT).format(Date(file.lastModified()))
                "parent" -> file.parentFile?.name ?: ""
                else -> m.value
            }
        }
        if (rule.find.isNotEmpty()) {
            out = if (rule.useRegex) {
                runCatching { out.replace(Regex(rule.find), rule.replace) }.getOrDefault(out)
            } else out.replace(rule.find, rule.replace, ignoreCase = true)
        }
        out = when (rule.case) {
            CaseMode.KEEP -> out
            CaseMode.LOWER -> out.lowercase(Locale.ROOT)
            CaseMode.UPPER -> out.uppercase(Locale.ROOT)
            CaseMode.TITLE -> out.split(' ').joinToString(" ") { w -> w.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) } }
        }
        val usesExt = rule.pattern.contains("{ext")
        return if (rule.keepExtension && ext.isNotEmpty() && !usesExt) "$out.$ext" else out
    }

    /**
     * Two phase rename (all to temporary names first) so swaps like a->b, b->a work.
     * Returns from->to pairs that succeeded.
     */
    fun execute(previews: List<RenamePreview>): List<Pair<File, File>> {
        val valid = previews.filter { it.error == null && it.newName != it.file.name }
        val temps = valid.map { p ->
            val tmp = File(p.file.parentFile, ".orbit_rn_${System.nanoTime()}_${p.file.name.hashCode()}")
            if (!p.file.renameTo(tmp)) throw IOException("Falha ao renomear ${p.file.name}")
            Triple(p.file, tmp, File(p.file.parentFile, p.newName))
        }
        val done = mutableListOf<Pair<File, File>>()
        for ((orig, tmp, target) in temps) {
            if (tmp.renameTo(target)) done += orig to target else tmp.renameTo(orig)
        }
        return done
    }
}
