package com.orbitfiles.core

import org.junit.Test
import kotlin.test.assertEquals

class FormattersTest {
    @Test fun sizes() {
        assertEquals("0 B", Formatters.size(0))
        assertEquals("1023 B", Formatters.size(1023))
        assertEquals("1,0 KB", Formatters.size(1024))
        assertEquals("1,5 MB", Formatters.size(1024L * 1024 * 3 / 2))
        assertEquals("120 GB", Formatters.size(120L * 1024 * 1024 * 1024))
    }

    @Test fun relative() {
        val now = 1_000_000_000_000L
        assertEquals("agora", Formatters.relative(now - 10_000, now))
        assertEquals("há 5 min", Formatters.relative(now - 5 * 60_000, now))
        assertEquals("há 3 h", Formatters.relative(now - 3 * 3_600_000, now))
        assertEquals("ontem", Formatters.relative(now - 30 * 3_600_000L, now))
        assertEquals("há 4 dias", Formatters.relative(now - 4 * 86_400_000L, now))
    }

    @Test fun durationAndPercent() {
        assertEquals("1:05", Formatters.duration(65_000))
        assertEquals("1:01:01", Formatters.duration(3_661_000))
        assertEquals("25%", Formatters.percent(1, 4))
        assertEquals("0%", Formatters.percent(1, 0))
    }

    @Test fun categories() {
        assertEquals(FileCategory.IMAGE, FileCategory.ofName("Foto.JPG"))
        assertEquals(FileCategory.PDF, FileCategory.ofName("contrato.pdf"))
        assertEquals(FileCategory.OTHER, FileCategory.ofName("semextensao"))
        assertEquals(FileCategory.OTHER, FileCategory.ofName(".bashrc"))
        assertEquals("application/pdf", FileCategory.mimeOf("a.pdf"))
        assertEquals("image/*", FileCategory.mimeOf("a.dng"))
        assertEquals("", FileCategory.extensionOf("arquivo."))
    }
}
