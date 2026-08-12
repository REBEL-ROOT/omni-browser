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

/** JSON (de)serialization for [ModelDescriptor] using the dependency-free [Json] helper. */
fun ModelDescriptor.toJsonObject(): JsonValue.Obj = Json.obj(
    "id" to Json.str(id),
    "version" to Json.str(version),
    "task" to Json.str(task.key),
    "name" to Json.str(name),
    "sourceLanguage" to sourceLanguage?.let { Json.str(it) },
    "targetLanguage" to targetLanguage?.let { Json.str(it) },
    "sizeBytes" to Json.num(sizeBytes),
    "downloadUrl" to Json.str(downloadUrl),
    "sha256" to sha256?.let { Json.str(it) },
    "license" to Json.str(license),
    "sourceProject" to Json.str(sourceProject),
    "minimumRuntimeVersion" to minimumRuntimeVersion?.let { Json.str(it) }
)

fun ModelDescriptor.toJson(): String = Json.write(toJsonObject())

fun parseModelDescriptor(o: JsonValue.Obj): ModelDescriptor {
    val id = o.str("id") ?: throw IllegalArgumentException("ModelDescriptor missing 'id'")
    val version = o.str("version") ?: throw IllegalArgumentException("ModelDescriptor '$id' missing 'version'")
    val task = ModelTask.fromKey(o.str("task"))
    val name = o.str("name") ?: id
    val sizeBytes = o.long("sizeBytes") ?: throw IllegalArgumentException("ModelDescriptor '$id' missing 'sizeBytes'")
    val downloadUrl = o.str("downloadUrl") ?: throw IllegalArgumentException("ModelDescriptor '$id' missing 'downloadUrl'")
    val license = o.str("license") ?: "Unknown"
    val sourceProject = o.str("sourceProject") ?: "Unknown"
    return ModelDescriptor(
        id = id,
        version = version,
        task = task,
        name = name,
        sourceLanguage = o.str("sourceLanguage"),
        targetLanguage = o.str("targetLanguage"),
        sizeBytes = sizeBytes,
        downloadUrl = downloadUrl,
        sha256 = o.str("sha256"),
        license = license,
        sourceProject = sourceProject,
        minimumRuntimeVersion = o.str("minimumRuntimeVersion")
    )
}

fun parseModelDescriptor(json: String): ModelDescriptor =
    parseModelDescriptor(Json.parse(json) as JsonValue.Obj)

fun parseModelDescriptorList(json: String): List<ModelDescriptor> {
    val root = Json.parse(json)
    val items = when (root) {
        is JsonValue.Arr -> root.items
        is JsonValue.Obj -> root.array("models") ?: emptyList()
        else -> emptyList()
    }
    return items.mapNotNull { (it as? JsonValue.Obj)?.let { obj -> runCatching { parseModelDescriptor(obj) }.getOrNull() } }
}
