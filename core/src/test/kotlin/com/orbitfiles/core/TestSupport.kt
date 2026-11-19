package com.orbitfiles.core

import java.io.File
import java.nio.file.Files

/** Creates an isolated temp directory per test. */
fun tempDir(prefix: String = "orbit"): File = Files.createTempDirectory(prefix).toFile().also { it.deleteOnExit() }

fun File.write(name: String, content: String): File = File(this, name).also { it.parentFile.mkdirs(); it.writeText(content) }

fun File.writeBytesNamed(name: String, bytes: ByteArray): File = File(this, name).also { it.parentFile.mkdirs(); it.writeBytes(bytes) }
