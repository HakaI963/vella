package com.vella.app

import java.io.File
import java.io.RandomAccessFile

/**
 * Minimal ELF header/program-header reader. It only needs to answer three
 * questions for the raw loader: which processor, where does file offset 0 map,
 * and what is the entry address.
 */
object Elf {
    data class Info(
        val language: String?,
        val base: Long,
        val entry: Long,
        val bits: Int
    )

    fun parse(file: File): Info? {
        if (!file.isFile || file.length() < 52) return null
        return try {
            RandomAccessFile(file, "r").use { f ->
                val ident = ByteArray(16)
                f.readFully(ident)
                if (ident[0] != 0x7f.toByte() ||
                    ident[1] != 'E'.code.toByte() ||
                    ident[2] != 'L'.code.toByte() ||
                    ident[3] != 'F'.code.toByte()
                ) return null

                val bits = when (ident[4].toInt() and 0xff) {
                    1 -> 32
                    2 -> 64
                    else -> return null
                }
                if ((ident[5].toInt() and 0xff) != 1) return null // little-endian only

                val machine = f.u16(18)
                val entry = if (bits == 64) f.u64(24) else f.u32(24)
                val phoff = if (bits == 64) f.u64(32) else f.u32(28)
                val phentsize = f.u16(if (bits == 64) 54 else 42)
                val phnum = f.u16(if (bits == 64) 56 else 44)

                var base = 0L
                var bestOffset = Long.MAX_VALUE
                if (phoff > 0 && phnum > 0 && phentsize >= (if (bits == 64) 56 else 32)) {
                    for (i in 0 until phnum) {
                        val ph = phoff + i.toLong() * phentsize
                        if (ph < 0 || ph + phentsize > file.length()) break
                        if (f.u32(ph) != 1L) continue // PT_LOAD
                        val offset = if (bits == 64) f.u64(ph + 8) else f.u32(ph + 4)
                        val vaddr = if (bits == 64) f.u64(ph + 16) else f.u32(ph + 8)
                        if (offset < bestOffset) {
                            bestOffset = offset
                            base = vaddr - offset
                        }
                    }
                }
                if (base < 0) base = 0

                val language = when (machine) {
                    0x3E -> if (bits == 64) "x86:LE:64:default" else "x86:LE:32:default"
                    0x03 -> "x86:LE:32:default"
                    0x28 -> "ARM:LE:32:v7"
                    0xB7 -> "AARCH64:LE:64:v8A"
                    else -> null
                }
                Info(language, base, entry, bits)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun RandomAccessFile.u16(offset: Long): Int {
        seek(offset)
        val b0 = readUnsignedByte()
        val b1 = readUnsignedByte()
        return b0 or (b1 shl 8)
    }

    private fun RandomAccessFile.u32(offset: Long): Long {
        seek(offset)
        return readInt().toLong() and 0xffffffffL
    }

    private fun RandomAccessFile.u64(offset: Long): Long {
        seek(offset)
        return readLong()
    }
}
