package com.orbitfiles.core

import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchTest {
    @Test fun fuzzyScoring() {
        assertTrue(TextMatch.score("Música favorita.mp3", "musica") > 0)
        assertTrue(TextMatch.score("relatorio.pdf", "relatorio.pdf") > TextMatch.score("relatorio-final.pdf", "relatorio"))
        assertTrue(TextMatch.score("relatorio-final.pdf", "final") > TextMatch.score("xfinalx", "final"))
        assertTrue(TextMatch.score("IMG_2024_praia.jpg", "img praia") > 0)
        assertEquals(0, TextMatch.score("foto.jpg", "video"))
        assertTrue(TextMatch.score("screenshot.png", "scrn") > 0)
    }

    @Test fun searcherFilters() {
        val d = tempDir()
        d.write("viagem/praia.jpg", "x".repeat(2000))
        d.write("viagem/roteiro.pdf", "p")
        d.write(".oculto/praia.jpg", "h")
        d.write("notas/ideias.txt", "comprar leite\nprojeto orbit files")
        val searcher = FileSearcher()
        assertEquals(1, searcher.search(listOf(d), SearchQuery("praia")).size)
        assertEquals(2, searcher.search(listOf(d), SearchQuery("praia", includeHidden = true)).size)
        val docs = searcher.search(listOf(d), SearchQuery(categories = setOf(FileCategory.DOCUMENT)))
        assertEquals(setOf("roteiro.pdf", "ideias.txt"), docs.map { it.file.name }.toSet())
        assertEquals(listOf("praia.jpg"), searcher.search(listOf(d), SearchQuery(minSize = 1000)).map { it.file.name })
        val content = searcher.search(listOf(d), SearchQuery("orbit", searchContent = true))
        assertEquals("ideias.txt", content.single().file.name)
        assertEquals("projeto orbit files", content.single().contentLine)
        assertEquals(listOf("viagem"), searcher.search(listOf(d), SearchQuery("viag", foldersOnly = true)).map { it.file.name })
        assertEquals(listOf("roteiro.pdf"), searcher.search(listOf(d), SearchQuery("^rot.*\\.pdf$", regex = true)).map { it.file.name })
    }

    private val zone = ZoneId.of("America/Sao_Paulo")
    // Wednesday 2026-11-18 21:00
    private val now = LocalDateTime.of(2026, 11, 18, 21, 0).atZone(zone).toInstant().toEpochMilli()
    private val parser = NaturalQueryParser(zone) { now }
    private fun day(y: Int, m: Int, d: Int) = LocalDateTime.of(y, m, d, 0, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun naturalLanguagePt() {
        val p = parser.parse("fotos de ontem")
        assertEquals(setOf(FileCategory.IMAGE), p.query.categories)
        assertEquals(day(2026, 11, 17), p.query.modifiedAfter)
        assertEquals(day(2026, 11, 18), p.query.modifiedBefore)
        assertEquals("", p.query.text)

        val q = parser.parse("PDF maior que 10mb contrato").query
        assertEquals(setOf(FileCategory.PDF), q.categories)
        assertEquals(10L * 1024 * 1024, q.minSize)
        assertEquals("contrato", q.text)

        val v = parser.parse("vídeos grandes do mês passado").query
        assertEquals(setOf(FileCategory.VIDEO), v.categories)
        assertEquals(100L * 1024 * 1024, v.minSize)
        assertEquals(day(2026, 10, 1), v.modifiedAfter)
        assertEquals(day(2026, 11, 1), v.modifiedBefore)

        val s = parser.parse("músicas menores que 1,5 GB em 2025").query
        assertEquals((1.5 * 1024 * 1024 * 1024).toLong(), s.maxSize)
        assertEquals(day(2025, 1, 1), s.modifiedAfter)
        assertEquals(day(2026, 1, 1), s.modifiedBefore)

        val w = parser.parse("documentos da semana passada").query
        assertEquals(day(2026, 11, 9), w.modifiedAfter)
        assertEquals(day(2026, 11, 16), w.modifiedBefore)

        val l = parser.parse("arquivos dos últimos 30 dias").query
        assertEquals(day(2026, 10, 20), l.modifiedAfter)
        assertNull(l.modifiedBefore)
    }

    @Test fun naturalLanguageEnAndExt() {
        val e = parser.parse("images bigger than 2 mb today").query
        assertEquals(setOf(FileCategory.IMAGE), e.categories)
        assertEquals(2L * 1024 * 1024, e.minSize)
        assertEquals(day(2026, 11, 18), e.modifiedAfter)

        val x = parser.parse("ext:kt main").query
        assertEquals(setOf("kt"), x.extensions)
        assertEquals("main", x.text)

        val y = parser.parse(".apk").query
        assertEquals(setOf("apk"), y.extensions)

        val f = parser.parse("pastas viagem")
        assertTrue(f.query.foldersOnly)
        assertEquals("viagem", f.query.text)
        assertNotNull(f.chips.firstOrNull { it == "Pastas" })

        val plain = parser.parse("Relatório Final")
        assertEquals("relatorio final", plain.query.text)
        assertTrue(plain.query.categories.isEmpty())
    }
}
