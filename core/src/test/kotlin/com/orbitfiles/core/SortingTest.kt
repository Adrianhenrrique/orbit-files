package com.orbitfiles.core

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class SortingTest {
    @Test fun naturalOrder() {
        val names = listOf("foto 10.jpg", "Foto 2.jpg", "foto 1.jpg", "Água.txt", "abc.txt", "foto 02b.jpg")
        assertEquals(listOf("abc.txt", "Água.txt", "foto 1.jpg", "Foto 2.jpg", "foto 02b.jpg", "foto 10.jpg"), names.sortedWith(NaturalComparator))
    }

    @Test fun sorterFoldersFirstAndDirection() {
        val d = tempDir()
        d.write("b.txt", "12345"); d.write("a.txt", "1"); File(d, "z-pasta").mkdirs()
        val files = d.listFiles()!!.toList()
        assertEquals(listOf("z-pasta", "a.txt", "b.txt"), FileSorter.files(files, SortBy.NAME, true, true).map { it.name })
        assertEquals(listOf("z-pasta", "b.txt", "a.txt"), FileSorter.files(files, SortBy.SIZE, false, true).map { it.name })
        assertEquals(listOf("a.txt", "b.txt", "z-pasta"), FileSorter.files(files, SortBy.NAME, true, false).map { it.name })
    }
}
