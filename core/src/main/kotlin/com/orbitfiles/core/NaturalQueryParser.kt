package com.orbitfiles.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Turns everyday language (Portuguese or English) into a structured [SearchQuery]:
 *  "fotos de ontem", "pdf maior que 10mb", "vídeos grandes do mês passado", "ext:kt main",
 *  "documentos dos últimos 30 dias", "musicas em 2025".
 * Runs fully on-device, instantly, with no network or model download.
 */
class NaturalQueryParser(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    data class Parsed(val query: SearchQuery, val chips: List<String>)

    private val categoryWords: Map<String, FileCategory> = buildMap {
        fun put(cat: FileCategory, vararg w: String) = w.forEach { put(it, cat) }
        put(FileCategory.IMAGE, "foto", "fotos", "imagem", "imagens", "image", "images", "photo", "photos", "pics", "prints", "screenshots", "capturas")
        put(FileCategory.VIDEO, "video", "videos", "filme", "filmes", "movie", "movies", "clipes", "gravacoes")
        put(FileCategory.AUDIO, "musica", "musicas", "audio", "audios", "song", "songs", "music", "podcast", "podcasts", "som", "sons")
        put(FileCategory.DOCUMENT, "documento", "documentos", "doc", "docs", "document", "documents")
        put(FileCategory.PDF, "pdf", "pdfs")
        put(FileCategory.SPREADSHEET, "planilha", "planilhas", "excel", "spreadsheet", "spreadsheets", "csv")
        put(FileCategory.PRESENTATION, "apresentacao", "apresentacoes", "slides", "powerpoint", "presentation", "presentations")
        put(FileCategory.ARCHIVE, "zip", "zips", "rar", "compactado", "compactados", "archive", "archives")
        put(FileCategory.APK, "apk", "apks", "app", "apps", "aplicativo", "aplicativos", "instalador", "instaladores")
        put(FileCategory.CODE, "codigo", "codigos", "code", "script", "scripts")
        put(FileCategory.EBOOK, "ebook", "ebooks", "livro", "livros", "epub")
        put(FileCategory.FONT, "fonte", "fontes", "font", "fonts")
        put(FileCategory.TEXT, "texto", "textos", "txt", "notas", "notes")
    }

    private val stopWords = setOf(
        "de", "da", "do", "das", "dos", "que", "com", "e", "o", "a", "os", "as", "um", "uma", "no", "na", "nos", "nas",
        "arquivo", "arquivos", "meu", "meus", "minha", "minhas", "todos", "todas", "tipo", "file", "files", "my", "all",
        "the", "of", "from", "with", "and", "in", "on", "mostrar", "mostre", "achar", "encontrar", "buscar", "procurar",
        "find", "show", "search", "por", "pra", "para", "modificados", "modificadas", "editados", "criados", "baixados",
    )

    private val unit = """(bytes?|kilobytes?|megabytes?|gigabytes?|terabytes?|kb|mb|gb|tb|k|m|g|b)(?=\s|$)"""
    private val number = """(\d+(?:[.,]\d+)?)"""
    private val biggerRe = Regex("""(?:maior(?:es)? (?:que|do que)|acima de|mais de|bigger than|larger than|greater than|over|more than|>=?)\s*$number\s*$unit""")
    private val smallerRe = Regex("""(?:menor(?:es)? (?:que|do que)|abaixo de|menos de|smaller than|less than|under|<=?)\s*$number\s*$unit""")
    private val lastDaysRe = Regex("""(?:ultimos|ultimas|last|past)\s+(\d+)\s+(dias|days|semanas|weeks|meses|months)""")
    private val yearRe = Regex("""(?:em|de|in|from)\s+(19\d{2}|20\d{2})""")
    private val extRe = Regex("""(?:ext:|^|\s)\.([a-z0-9]{1,8})(?=\s|$)|ext:([a-z0-9]{1,8})""")

    fun parse(input: String): Parsed {
        var s = " " + TextMatch.normalize(input).replace(Regex("\\s+"), " ").trim() + " "
        val chips = mutableListOf<String>()
        var q = SearchQuery()

        biggerRe.find(s)?.let { m ->
            val bytes = toBytes(m.groupValues[1], m.groupValues[2])
            q = q.copy(minSize = bytes); chips += "> ${Formatters.size(bytes)}"
            s = s.removeRange(m.range).let { " $it " }
        }
        smallerRe.find(s)?.let { m ->
            val bytes = toBytes(m.groupValues[1], m.groupValues[2])
            q = q.copy(maxSize = bytes); chips += "< ${Formatters.size(bytes)}"
            s = s.removeRange(m.range).let { " $it " }
        }
        lastDaysRe.find(s)?.let { m ->
            val n = m.groupValues[1].toLong()
            val days = when (m.groupValues[2]) { "semanas", "weeks" -> n * 7; "meses", "months" -> n * 30; else -> n }
            q = q.copy(modifiedAfter = startOfDay(today().minusDays(days - 1)))
            chips += "Últimos $n ${m.groupValues[2]}"
            s = s.removeRange(m.range).let { " $it " }
        }
        yearRe.find(s)?.let { m ->
            val y = m.groupValues[1].toInt()
            q = q.copy(modifiedAfter = startOfDay(LocalDate.of(y, 1, 1)), modifiedBefore = startOfDay(LocalDate.of(y + 1, 1, 1)))
            chips += "Ano $y"
            s = s.removeRange(m.range).let { " $it " }
        }

        val datePhrases = listOf(
            "semana passada", "last week", "mes passado", "last month", "ano passado", "last year",
            "esta semana", "essa semana", "this week", "este mes", "esse mes", "this month", "este ano", "esse ano", "this year",
            "hoje", "today", "ontem", "yesterday", "recentes", "recente", "recent", "antigos", "antigas", "old",
        )
        for (p in datePhrases) {
            val idx = s.indexOf(" $p ")
            if (idx >= 0 && q.modifiedAfter == null && q.modifiedBefore == null) {
                val (after, before, label) = dateRange(p)
                q = q.copy(modifiedAfter = after, modifiedBefore = before)
                chips += label
                s = s.replaceFirst(" $p ", " ")
            }
        }

        val exts = mutableSetOf<String>()
        extRe.findAll(s).forEach { m -> exts += (m.groupValues[1].ifEmpty { m.groupValues[2] }) }
        if (exts.isNotEmpty()) {
            s = extRe.replace(s, " ")
            q = q.copy(extensions = exts)
            chips += exts.joinToString { ".$it" }
        }

        val textTokens = mutableListOf<String>()
        val cats = mutableSetOf<FileCategory>()
        for (tok in s.split(' ').filter { it.isNotBlank() }) {
            when {
                tok in categoryWords -> cats += categoryWords.getValue(tok)
                tok == "pastas" || tok == "pasta" || tok == "folders" || tok == "folder" -> q = q.copy(foldersOnly = true)
                tok == "grandes" || tok == "grande" || tok == "large" || tok == "big" || tok == "pesados" -> {
                    if (q.minSize == null) { q = q.copy(minSize = 100L * 1024 * 1024); chips += "> 100 MB" }
                }
                tok == "pequenos" || tok == "small" || tok == "leves" -> {
                    if (q.maxSize == null) { q = q.copy(maxSize = 1024L * 1024); chips += "< 1 MB" }
                }
                tok == "ocultos" || tok == "hidden" -> q = q.copy(includeHidden = true)
                tok in stopWords -> Unit
                else -> textTokens += tok
            }
        }
        if (cats.isNotEmpty()) {
            q = q.copy(categories = cats)
            chips.addAll(0, cats.map { it.label })
        }
        if (q.foldersOnly) chips.add(0, "Pastas")
        if (q.includeHidden) chips += "Ocultos"
        val text = textTokens.joinToString(" ")
        q = q.copy(text = text, filesOnly = cats.isNotEmpty() && !q.foldersOnly)
        if (text.isNotBlank()) chips.add(0, "\"$text\"")
        return Parsed(q, chips)
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()
    private fun startOfDay(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun dateRange(p: String): Triple<Long?, Long?, String> {
        val t = today()
        val weekStart = t.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return when (p) {
            "hoje", "today" -> Triple(startOfDay(t), null, "Hoje")
            "ontem", "yesterday" -> Triple(startOfDay(t.minusDays(1)), startOfDay(t), "Ontem")
            "esta semana", "essa semana", "this week" -> Triple(startOfDay(weekStart), null, "Esta semana")
            "semana passada", "last week" -> Triple(startOfDay(weekStart.minusWeeks(1)), startOfDay(weekStart), "Semana passada")
            "este mes", "esse mes", "this month" -> Triple(startOfDay(t.withDayOfMonth(1)), null, "Este mês")
            "mes passado", "last month" -> Triple(startOfDay(t.withDayOfMonth(1).minusMonths(1)), startOfDay(t.withDayOfMonth(1)), "Mês passado")
            "este ano", "esse ano", "this year" -> Triple(startOfDay(t.withDayOfYear(1)), null, "Este ano")
            "ano passado", "last year" -> Triple(startOfDay(t.withDayOfYear(1).minusYears(1)), startOfDay(t.withDayOfYear(1)), "Ano passado")
            "recentes", "recente", "recent" -> Triple(startOfDay(t.minusDays(6)), null, "Últimos 7 dias")
            else -> Triple(null, startOfDay(t.minusYears(1)), "Mais de 1 ano") // antigos / old
        }
    }

    private fun toBytes(num: String, unit: String): Long {
        val v = num.replace(',', '.').toDouble()
        val mult = when {
            unit.startsWith("t") -> 1024.0 * 1024 * 1024 * 1024
            unit.startsWith("g") -> 1024.0 * 1024 * 1024
            unit.startsWith("m") -> 1024.0 * 1024
            unit.startsWith("k") -> 1024.0
            else -> 1.0
        }
        return (v * mult).toLong()
    }
}
