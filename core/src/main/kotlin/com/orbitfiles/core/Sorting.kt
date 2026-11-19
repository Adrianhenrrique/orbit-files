package com.orbitfiles.core

import java.io.File

enum class SortBy(val label: String) { NAME("Nome"), DATE("Data"), SIZE("Tamanho"), TYPE("Tipo") }

/** Human friendly ordering: "foto 2" < "foto 10", case and accent insensitive. */
object NaturalComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val x = TextMatch.normalize(a)
        val y = TextMatch.normalize(b)
        var i = 0; var j = 0
        while (i < x.length && j < y.length) {
            val cx = x[i]; val cy = y[j]
            if (cx.isDigit() && cy.isDigit()) {
                val si = i; while (i < x.length && x[i].isDigit()) i++
                val sj = j; while (j < y.length && y[j].isDigit()) j++
                val nx = x.substring(si, i).trimStart('0')
                val ny = y.substring(sj, j).trimStart('0')
                if (nx.length != ny.length) return nx.length - ny.length
                val c = nx.compareTo(ny)
                if (c != 0) return c
            } else {
                if (cx != cy) return cx - cy
                i++; j++
            }
        }
        return (x.length - i) - (y.length - j)
    }
}

object FileSorter {
    /** Generic sorter usable for any item type through accessors. */
    fun <T> comparator(
        by: SortBy,
        ascending: Boolean,
        foldersFirst: Boolean,
        isDir: (T) -> Boolean,
        name: (T) -> String,
        size: (T) -> Long,
        modified: (T) -> Long,
    ): Comparator<T> {
        val byName = Comparator<T> { a, b -> NaturalComparator.compare(name(a), name(b)) }
        val primary: Comparator<T> = when (by) {
            SortBy.NAME -> byName
            SortBy.DATE -> compareBy<T> { modified(it) }.then(byName)
            SortBy.SIZE -> compareBy<T> { size(it) }.then(byName)
            SortBy.TYPE -> compareBy<T> { FileCategory.extensionOf(name(it)) }.then(byName)
        }
        val directed = if (ascending) primary else primary.reversed()
        return if (foldersFirst) compareBy<T> { !isDir(it) }.then(directed) else directed
    }

    fun files(list: List<File>, by: SortBy, ascending: Boolean, foldersFirst: Boolean): List<File> =
        list.sortedWith(comparator(by, ascending, foldersFirst, { it.isDirectory }, { it.name }, { it.length() }, { it.lastModified() }))
}
