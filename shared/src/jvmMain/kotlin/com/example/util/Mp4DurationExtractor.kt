package com.example.util

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

object Mp4DurationExtractor {
    fun getDurationMs(file: File): Long {
        if (!file.exists()) return 0L
        try {
            RandomAccessFile(file, "r").use { raf ->
                return findDurationInAtoms(raf, raf.length(), 0)
            }
        } catch (e: Exception) {
            // println("Error extracting MP4 duration: ${e.message}")
        }
        return 0L
    }

    private fun findDurationInAtoms(raf: RandomAccessFile, limit: Long, depth: Int): Long {
        if (depth > 12) return 0L
        val buffer = ByteArray(4)
        while (raf.filePointer < limit - 8) {
            val pos = raf.filePointer
            val sizeInt = try { raf.readInt() } catch (e: Exception) { break }
            var size = sizeInt.toLong() and 0xffffffffL
            
            if (raf.read(buffer, 0, 4) < 4) break
            val type = String(buffer, 0, 4)

            // Handling extended sizes (64-bit)
            if (size == 1L) {
                size = try { raf.readLong() } catch (e: Exception) { break }
            } else if (size == 0L) {
                // Size 0 means atom extends to the end of file
                size = raf.length() - pos
            }

            if (size < 8 && type != "moov" && type != "trak") break 

            when (type) {
                "moov", "trak", "mdia" -> {
                    // Dive into these containers
                    val result = findDurationInAtoms(raf, pos + size, depth + 1)
                    if (result > 0) return result
                    // Resume search after the container if duration wasn't found inside
                    raf.seek(pos + size)
                }
                "mvhd" -> {
                    val version = raf.readByte().toInt()
                    raf.skipBytes(3) // flags
                    
                    val timescale: Long
                    val duration: Long
                    
                    if (version == 1) {
                        raf.skipBytes(16) // creation/mod times
                        timescale = raf.readInt().toLong() and 0xffffffffL
                        duration = raf.readLong()
                    } else {
                        raf.skipBytes(8) // creation/mod times
                        timescale = raf.readInt().toLong() and 0xffffffffL
                        duration = raf.readInt().toLong() and 0xffffffffL
                    }
                    
                    if (timescale > 0) {
                        val durationMs = (duration * 1000) / timescale
                        return if (durationMs > 0) durationMs else 5000L
                    }
                    return 0L
                }
                "tkhd" -> {
                    val version = raf.readByte().toInt()
                    raf.skipBytes(3) // flags
                    if (version == 1) {
                        raf.readInt() // track id
                        raf.readInt() // reserved
                        raf.readLong() // duration
                    }
                    raf.seek(pos + size)
                }
                else -> {
                    try {
                        raf.seek(pos + size)
                    } catch (e: Exception) {
                        break
                    }
                }
            }
        }
        return 0L
    }
}
