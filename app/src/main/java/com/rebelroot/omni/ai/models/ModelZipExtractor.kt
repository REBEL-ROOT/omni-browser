/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.rebelroot.omni.ai.models

import java.io.File
import java.util.zip.ZipInputStream

/**
 * Extracts a downloaded (verified) model archive into application-private storage.
 *
 * Vosk models ship as zip archives containing `am/`, `conf/`, etc.; the runtime
 * loads them from an extracted directory. Extraction runs only after the archive
 * passed [ModelVerifier] (download → verify → extract → activate).
 *
 * Defensive: archive entry names are sanitized so a malicious entry can never
 * escape the extraction directory via `..` path segments.
 */
object ModelZipExtractor {

    /** Returns true when the archive was extracted (or was already extracted). */
    fun extract(zipFile: File, destDir: File): Boolean {
        if (destDir.isDirectory && destDir.listFiles()?.isNotEmpty() == true) return true
        destDir.mkdirs()
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val safePath = entry.name
                    .split('/')
                    .filter { it.isNotEmpty() && it != "." && it != ".." }
                    .joinToString("/")
                if (safePath.isNotBlank()) {
                    val out = File(destDir, safePath)
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { os -> zip.copyTo(os) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return destDir.listFiles()?.isNotEmpty() == true
    }

    /**
     * Find the directory the runtime should load: the extraction root when it
     * directly contains `am` + `conf`, otherwise its single child model folder
     * (Vosk zips commonly nest under a `vosk-model-…` folder).
     */
    fun findModelRoot(extractDir: File): File {
        if (File(extractDir, "am").isDirectory && File(extractDir, "conf").isDirectory) return extractDir
        extractDir.listFiles()?.firstOrNull { it.isDirectory && File(it, "am").isDirectory }?.let {
            return it
        }
        return extractDir
    }
}
