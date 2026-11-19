package com.orbitfiles.core

import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenameArchiveTest {
    @Test fun renamePatterns() {
        val d = tempDir()
        val a = d.write("IMG_001.JPG", "a")
        val b = d.write("IMG_002.JPG", "b")
        val prev = BatchRenamer.preview(listOf(a, b), RenameRule(pattern = "Viagem {n:3}", case = CaseMode.KEEP))
        assertEquals(listOf("Viagem 001.jpg", "Viagem 002.jpg").map { it.lowercase() }, prev.map { it.newName.lowercase() })
        assertTrue(prev.all { it.error == null })
        val done = BatchRenamer.execute(prev)
        assertEquals(2, done.size)
        assertTrue(File(d, prev[0].newName).exists())

        val f = d.write("meu arquivo velho.txt", "x")
        assertEquals("Meu Arquivo Novo.txt", BatchRenamer.apply(f, RenameRule(find = "velho", replace = "novo", case = CaseMode.TITLE), 1))
        assertEquals("arquivo-velho.txt", BatchRenamer.apply(f, RenameRule(find = "^meu ([a-z]+) ", replace = "$1-", useRegex = true), 1))
        assertEquals("${d.name}_meu arquivo velho.txt", BatchRenamer.apply(f, RenameRule(pattern = "{parent}_{name}"), 1))
    }

    @Test fun renameDetectsConflictsAndSwaps() {
        val d = tempDir()
        val a = d.write("a.txt", "A"); val b = d.write("b.txt", "B")
        val dup = BatchRenamer.preview(listOf(a, b), RenameRule(pattern = "mesmo"))
        assertTrue(dup.all { it.error != null })
        d.write("ocupado.txt", "x")
        assertNotNull(BatchRenamer.preview(listOf(a), RenameRule(pattern = "ocupado"))[0].error)
        // swap a <-> b
        val swap = listOf(RenamePreview(a, "b.txt", null), RenamePreview(b, "a.txt", null))
        BatchRenamer.execute(swap)
        assertEquals("B", File(d, "a.txt").readText())
        assertEquals("A", File(d, "b.txt").readText())
        assertNull(BatchRenamer.preview(listOf(File(d, "a.txt"), File(d, "b.txt")), RenameRule(pattern = "{name}_{n}")).firstOrNull { it.error != null })
    }

    @Test fun zipRoundTrip() {
        val src = tempDir(); val out = tempDir()
        src.write("projeto/src/main.kt", "fun main() {}")
        src.write("projeto/README.md", "# Olá")
        src.write("solto.txt", "s")
        val zip = ArchiveManager.zip(listOf(File(src, "projeto"), File(src, "solto.txt")), File(out, "pacote.zip"))
        val entries = ArchiveManager.list(zip)
        assertTrue(entries.any { it.path == "projeto/src/main.kt" && it.size == 13L })
        assertEquals("main.kt", entries.first { it.path.endsWith("main.kt") }.name)
        val dest = File(out, ArchiveManager.suggestedFolder(zip))
        ArchiveManager.extract(zip, dest)
        assertEquals("# Olá", File(dest, "projeto/README.md").readText())
        val partialDest = File(out, "parcial")
        ArchiveManager.extract(zip, partialDest, only = setOf("projeto/src/"))
        assertTrue(File(partialDest, "projeto/src/main.kt").exists())
        assertFalse(File(partialDest, "solto.txt").exists())
        val single = ArchiveManager.extractEntry(zip, "solto.txt", File(out, "x/solto.txt"))
        assertEquals("s", single.readText())
    }

    @Test fun zipSlipIsBlocked() {
        val d = tempDir()
        val evil = File(d, "evil.zip")
        ZipOutputStream(FileOutputStream(evil)).use { z ->
            z.putNextEntry(ZipEntry("../../escape.txt")); z.write("x".toByteArray()); z.closeEntry()
        }
        assertFailsWith<IOException> { ArchiveManager.extract(evil, File(d, "out")) }
        assertFalse(File(d.parentFile, "escape.txt").exists())
    }
}
