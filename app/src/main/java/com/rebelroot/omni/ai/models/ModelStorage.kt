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
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Application-private storage for downloaded, verified models.
 *
 * Models live under [root]/<modelId>/<fileKey>.bin with a sidecar
 * <fileKey>.json holding the (tiny) [ModelDescriptor]. Downloads are staged as
 * <fileKey>.bin.partial and only atomically moved into place after verification.
 *
 * This class performs NO network access and NO verification — it is purely file
 * management. [root] is supplied by the platform (e.g. a directory inside
 * `getFilesDir()`); in tests a temporary directory is used.
 */
class ModelStorage(private val root: File) {

    init {
        if (!root.exists()) root.mkdirs()
    }

    private fun safeName(id: String): String =
        id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    fun modelDir(id: String): File = File(root, safeName(id)).also { if (!it.exists()) it.mkdirs() }

    fun finalFile(d: ModelDescriptor): File = File(modelDir(d.id), "${d.fileKey}.bin")
    fun partialFile(d: ModelDescriptor): File = File(modelDir(d.id), "${d.fileKey}.bin.partial")
    private fun metaFile(d: ModelDescriptor): File = File(modelDir(d.id), "${d.fileKey}.json")

    fun isInstalled(d: ModelDescriptor): Boolean = finalFile(d).isFile
    fun installedSize(d: ModelDescriptor): Long = if (isInstalled(d)) finalFile(d).length() else 0L

    /** Total bytes occupied by all installed models. */
    fun totalInstalledBytes(): Long {
        var total = 0L
        root.listFiles()?.forEach { dir ->
            dir.listFiles()?.forEach { f ->
                if (f.name.endsWith(".bin")) total += f.length()
            }
        }
        return total
    }

    fun partialBytes(d: ModelDescriptor): Long = if (partialFile(d).isFile) partialFile(d).length() else 0L

    /** True if a partial download exists and can be resumed. */
    fun hasPartial(d: ModelDescriptor): Boolean = partialFile(d).isFile

    /**
     * Atomically promote a verified partial file to the installed location and
     * write its descriptor sidecar. The partial is removed on success.
     * Returns true on success.
     */
    fun commit(d: ModelDescriptor): Boolean {
        val partial = partialFile(d)
        val final = finalFile(d)
        if (!partial.isFile) return false
        try {
            if (final.exists()) final.delete()
            Files.move(partial.toPath(), final.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            // Fall back to a plain copy+delete when ATOMIC_MOVE is unsupported
            // (e.g. some filesystems / emulated storage).
            try {
                partial.inputStream().use { input ->
                    final.outputStream().use { out -> input.copyTo(out) }
                }
                partial.delete()
            } catch (_: Exception) {
                return false
            }
        }
        runCatching { metaFile(d).writeText(d.toJson()) }
        return final.isFile
    }

    /** Remove an installed model (and its partial + meta). Best-effort. */
    fun delete(d: ModelDescriptor): Boolean {
        var ok = true
        finalFile(d).takeIf { it.exists() }?.let { ok = ok && it.delete() }
        partialFile(d).takeIf { it.exists() }?.let { ok = ok && it.delete() }
        metaFile(d).takeIf { it.exists() }?.let { ok = ok && it.delete() }
        return ok
    }

    /** Discard an in-progress partial download (e.g. on cancel). */
    fun discardPartial(d: ModelDescriptor): Boolean =
        partialFile(d).takeIf { it.exists() }?.delete() ?: true

    /** List all installed models by reading their descriptor sidecars. */
    fun listInstalled(): List<ModelDescriptor> {
        val result = mutableListOf<ModelDescriptor>()
        root.listFiles()?.forEach { dir ->
            dir.listFiles()?.forEach { f ->
                if (f.name.endsWith(".json")) {
                    runCatching { parseModelDescriptor(f.readText()) }.getOrNull()?.let {
                        if (finalFile(it).isFile) result.add(it)
                    }
                }
            }
        }
        return result
    }

    /** Remove every model under [root] (used by private-mode transient wipe). */
    fun clearAll() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }
}
