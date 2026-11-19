package com.orbitfiles.core

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalysisTest {
    @Test fun storageReport() {
        val d = tempDir()
        d.write("DCIM/a.jpg", "x".repeat(5000))
        d.write("DCIM/Camera/b.mp4", "x".repeat(20000))
        d.write("Docs/c.pdf", "x".repeat(300))
        d.write("Docs/d.txt", "x".repeat(200))
        val r = StorageAnalyzer.scan(d, topFiles = 2)
        assertEquals(25500, r.root.size)
        assertEquals(4, r.totalFiles)
        assertEquals("DCIM", r.root.children.first().name)
        assertEquals(25000, r.root.children.first().size)
        assertEquals(500, r.byCategory[FileCategory.DOCUMENT])
        assertEquals(20000, r.byCategory[FileCategory.VIDEO])
        assertEquals(listOf("b.mp4", "a.jpg"), r.largestFiles.map { it.name })
    }

    @Test fun duplicates() {
        val d = tempDir()
        val big = ByteArray(200_000) { (it % 251).toByte() }
        d.writeBytesNamed("a/foto.jpg", big)
        d.writeBytesNamed("b/foto copia.jpg", big)
        d.writeBytesNamed("c/foto3.jpg", big)
        val almost = big.copyOf().also { it[150_000] = 7 }
        d.writeBytesNamed("d/diferente.jpg", almost) // same size, same head, different tail
        d.write("x.txt", "igual igual")
        d.write("y.txt", "igual igual")
        val groups = DuplicateFinder.find(listOf(d), minSize = 1)
        assertEquals(2, groups.size)
        assertEquals(3, groups[0].files.size)
        assertEquals(400_000L, groups[0].wasted)
        assertTrue(groups.none { g -> g.files.any { it.name == "diferente.jpg" } })
    }

    @Test fun cleaner() {
        val d = tempDir()
        d.write("Download/velho.zip", "z").setLastModified(System.currentTimeMillis() - 200 * TrashEntry.DAY)
        d.write("Download/novo.zip", "z")
        d.write("lixo/temp.tmp", "t")
        d.write("DCIM/.thumbnails/1.jpg", "thumb")
        d.write("apps/jogo.apk", "apk")
        File(d, "vazia/sub/subsub").mkdirs()
        File(d, "Android/data/vazia").mkdirs()
        val r = CleanerScanner.scan(d, largeThreshold = 4)
        assertEquals(listOf("velho.zip"), r.getValue(JunkKind.OLD_DOWNLOADS).map { it.file.name })
        assertEquals(listOf("temp.tmp"), r.getValue(JunkKind.TEMP).map { it.file.name })
        assertEquals(listOf(".thumbnails"), r.getValue(JunkKind.THUMBNAILS).map { it.file.name })
        assertEquals(listOf("jogo.apk"), r.getValue(JunkKind.APKS).map { it.file.name })
        assertEquals(listOf("vazia"), r.getValue(JunkKind.EMPTY_FOLDERS).map { it.file.name })
        assertTrue(r.getValue(JunkKind.LARGE).any { it.file.name == "1.jpg" }.not())
    }

    @Test fun checksum() {
        val d = tempDir()
        val f = d.write("abc.txt", "abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Checksums.hash(f))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Checksums.hash(f, "MD5"))
    }
}
