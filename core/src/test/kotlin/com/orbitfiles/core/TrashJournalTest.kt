package com.orbitfiles.core

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrashJournalTest {
    @Test fun trashRestoreRoundTrip() {
        val root = tempDir()
        val store = TrashStore(File(root, ".OrbitTrash"))
        val f = root.write("docs/tab\tname.txt", "conteúdo")
        val e = store.trash(f)
        assertFalse(f.exists())
        assertEquals(1, store.list().size)
        assertEquals(f.absolutePath, store.list()[0].originalPath)
        assertEquals(9, store.list()[0].size) // "conteúdo" is 9 bytes in UTF-8
        val restored = store.restore(e.id)
        assertEquals("conteúdo", restored.readText())
        assertEquals(f.absolutePath, restored.absolutePath)
        assertTrue(store.list().isEmpty())
    }

    @Test fun restoreDoesNotOverwrite() {
        val root = tempDir()
        val store = TrashStore(File(root, ".OrbitTrash"))
        val f = root.write("a.txt", "old")
        val e = store.trash(f)
        root.write("a.txt", "new")
        val r = store.restore(e.id)
        assertEquals("a (1).txt", r.name)
        assertEquals("new", File(root, "a.txt").readText())
    }

    @Test fun purgeAndEmpty() {
        val root = tempDir()
        val store = TrashStore(File(root, ".OrbitTrash"))
        val now = System.currentTimeMillis()
        store.trash(root.write("old.txt", "1"), now = now - 40 * TrashEntry.DAY)
        store.trash(root.write("new.txt", "2"), now = now)
        assertEquals(1, store.purge(30, now))
        assertEquals(listOf("new.txt"), store.list().map { it.name })
        assertEquals(30, store.list()[0].daysLeft(30, now))
        store.trash(File(root, "pasta").apply { mkdirs(); File(this, "x").writeText("x") })
        assertEquals(2, store.empty())
        assertTrue(store.list().isEmpty())
    }

    @Test fun journalUndo() {
        val root = tempDir()
        val store = TrashStore(File(root, ".OrbitTrash"))
        val journal = Journal({ store })
        val f = root.write("orig.txt", "x")
        val renamed = FileOps.rename(f, "novo.txt")
        val entry = JournalEntry.Renamed(f, renamed)
        journal.record(entry)
        journal.undo(entry)
        assertTrue(f.exists())
        assertTrue(journal.entries().isEmpty())

        val e = store.trash(f)
        val t = JournalEntry.Trashed(store.root, listOf(e.id), listOf(f.name))
        journal.record(t)
        assertTrue(t.title.contains("orig.txt"))
        journal.undo(t)
        assertTrue(f.exists())

        val dest = File(root, "dest").apply { mkdirs() }
        val res = FileOps.move(listOf(f), dest)
        val m = JournalEntry.Moved(res.created)
        journal.undo(m)
        assertTrue(f.exists())
        assertFalse(File(dest, "orig.txt").exists())

        val copy = FileOps.copy(listOf(f), dest).created.map { it.second }
        journal.undo(JournalEntry.Copied(copy))
        assertFalse(File(dest, "orig.txt").exists())
        assertEquals(1, store.list().size)
    }
}
