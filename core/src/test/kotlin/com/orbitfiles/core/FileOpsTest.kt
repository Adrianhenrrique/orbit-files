package com.orbitfiles.core

import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileOpsTest {
    @Test fun validateNames() {
        assertNull(FileOps.validateName("Relatório final.pdf"))
        assertNotNull(FileOps.validateName(""))
        assertNotNull(FileOps.validateName("a/b"))
        assertNotNull(FileOps.validateName(".."))
        assertNotNull(FileOps.validateName("x".repeat(300)))
    }

    @Test fun uniqueNames() {
        val d = tempDir()
        d.write("foto.jpg", "1")
        assertEquals("foto (1).jpg", FileOps.uniqueName(d, "foto.jpg"))
        d.write("foto (1).jpg", "2")
        assertEquals("foto (2).jpg", FileOps.uniqueName(d, "foto.jpg"))
        assertEquals("foto (2).jpg", FileOps.uniqueName(d, "foto (1).jpg"))
        File(d, "Pasta").mkdirs()
        assertEquals("Pasta (1)", FileOps.uniqueName(d, "Pasta"))
        assertEquals("novo.txt", FileOps.uniqueName(d, "novo.txt"))
    }

    @Test fun copyTreeWithProgress() {
        val src = tempDir(); val dst = tempDir()
        src.write("album/a.txt", "a".repeat(1000))
        src.write("album/sub/b.txt", "b".repeat(3000))
        val updates = mutableListOf<Progress>()
        val res = FileOps.copy(listOf(File(src, "album")), dst) { updates += it }
        assertTrue(res.ok)
        assertEquals("b".repeat(3000), File(dst, "album/sub/b.txt").readText())
        assertTrue(File(src, "album/a.txt").exists())
        assertEquals(4000, updates.last().doneBytes)
        assertEquals(1f, updates.last().fraction)
        assertFalse(dst.walk().any { it.name.endsWith(".orbitpart") })
    }

    @Test fun conflictPolicies() {
        val src = tempDir(); val dst = tempDir()
        val f = src.write("n.txt", "novo")
        dst.write("n.txt", "velho")
        FileOps.copy(listOf(f), dst, ConflictPolicy.SKIP).let { assertEquals(1, it.skipped.size) }
        assertEquals("velho", File(dst, "n.txt").readText())
        FileOps.copy(listOf(f), dst, ConflictPolicy.KEEP_BOTH)
        assertEquals("novo", File(dst, "n (1).txt").readText())
        FileOps.copy(listOf(f), dst, ConflictPolicy.OVERWRITE)
        assertEquals("novo", File(dst, "n.txt").readText())
    }

    @Test fun duplicateInPlace() {
        val d = tempDir()
        val f = d.write("doc.txt", "x")
        val res = FileOps.copy(listOf(f), d, ConflictPolicy.OVERWRITE)
        assertTrue(res.ok)
        assertTrue(File(d, "doc (1).txt").exists())
        assertEquals("x", f.readText())
    }

    @Test fun moveAndGuardAgainstSelf() {
        val root = tempDir()
        val folder = File(root, "A").apply { mkdirs() }
        root.write("A/x.txt", "x")
        val inner = File(folder, "B").apply { mkdirs() }
        val bad = FileOps.move(listOf(folder), inner)
        assertEquals(1, bad.failed.size)
        val target = tempDir()
        val ok = FileOps.move(listOf(folder), target)
        assertTrue(ok.ok)
        assertFalse(folder.exists())
        assertEquals("x", File(target, "A/x.txt").readText())
    }

    @Test fun cancelStopsAndCleansPartial() {
        val src = tempDir(); val dst = tempDir()
        val big = src.writeBytesNamed("big.bin", ByteArray(3 * 1024 * 1024))
        val token = CancelToken()
        assertFailsWith<OperationCancelledException> {
            FileOps.copyFile(big, File(dst, "big.bin"), token) { token.cancel() }
        }
        assertTrue(dst.listFiles()!!.isEmpty())
    }

    @Test fun renameAndCreate() {
        val d = tempDir()
        val f = d.write("a.txt", "1")
        val r = FileOps.rename(f, "b.txt")
        assertTrue(r.exists() && !f.exists())
        val upper = FileOps.rename(r, "B.txt")
        assertEquals("B.txt", upper.name)
        d.write("c.txt", "2")
        assertFailsWith<IOException> { FileOps.rename(upper, "c.txt") }
        assertTrue(FileOps.createFolder(d, "Nova pasta").isDirectory)
        assertFailsWith<IOException> { FileOps.createFolder(d, "Nova pasta") }
        assertTrue(FileOps.createFile(d, "nota.md").isFile)
    }

    @Test fun deleteRecursive() {
        val d = tempDir()
        d.write("x/y/z.txt", "z")
        assertTrue(FileOps.deleteRecursively(File(d, "x")))
        assertFalse(File(d, "x").exists())
        val t = FileOps.totals(listOf(d))
        assertEquals(0, t.files)
    }
}
