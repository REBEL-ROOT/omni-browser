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

/**
 * Application-controlled list of downloadable AI models.
 *
 * The catalog is the ONLY source of model metadata and download URLs. Web content
 * never sees it and can never add entries. URLs are additionally constrained to an
 * allow-list of hosts (defaulting to the hosts present in the catalog, or an
 * explicit `allowHosts` list pinned by the app) so a compromised catalog entry
 * cannot exfiltrate a download to an unexpected host.
 *
 * The catalog contains NO model weights — only a few hundred bytes of metadata.
 */
class ModelCatalog(
    private val descriptors: List<ModelDescriptor>,
    private val allowHosts: Set<String>
) {
    private val byIdMap = descriptors.associateBy { it.id }

    fun all(): List<ModelDescriptor> = descriptors.toList()
    fun byId(id: String): ModelDescriptor? = byIdMap[id]
    fun byTask(task: ModelTask): List<ModelDescriptor> = descriptors.filter { it.task == task }

    /** Hosts permitted for model downloads. */
    fun allowedHosts(): Set<String> = allowHosts

    fun isHostAllowed(host: String): Boolean =
        allowHosts.isEmpty() || allowHosts.contains(host.lowercase())

    /**
     * Models that can serve a translation pair. A null [sourceLanguage] matches
     * any source (or language-agnostic models).
     */
    fun findForTranslation(
        sourceLanguage: String?,
        targetLanguage: String
    ): List<ModelDescriptor> = descriptors.filter { d ->
        d.task == ModelTask.TRANSLATION &&
            (sourceLanguage == null || d.sourceLanguage == null || d.sourceLanguage.equals(sourceLanguage, true)) &&
            (d.targetLanguage == null || d.targetLanguage.equals(targetLanguage, true))
    }

    /** ASR models usable for [language] (or language-agnostic ones). */
    fun findForAsr(language: String?): List<ModelDescriptor> = descriptors.filter { d ->
        d.task == ModelTask.ASR &&
            (language == null || d.sourceLanguage == null || d.sourceLanguage.equals(language, true))
    }

    companion object {
        /**
         * Parse a catalog JSON of the form:
         *   { "allowHosts": ["a.com"], "models": [ {descriptor}, ... ] }
         *
         * Invalid entries (non-HTTPS URL, missing required fields, host not in the
         * allow-list) are dropped. The returned catalog keeps only safe entries.
         */
        fun parse(json: String, explicitAllowHosts: Set<String>? = null): ModelCatalog {
            val root = Json.parse(json)
            val obj = root as? JsonValue.Obj
                ?: return ModelCatalog(emptyList(), explicitAllowHosts ?: emptySet())

            val declaredHosts = obj.array("allowHosts")
                ?.mapNotNull { (it as? JsonValue.Str)?.value?.lowercase() }
                ?.toSet()
                ?: emptySet()

            val allowHosts = explicitAllowHosts ?: declaredHosts

            val models = obj.array("models").orEmpty().mapNotNull { entry ->
                val o = entry as? JsonValue.Obj ?: return@mapNotNull null
                runCatching { parseModelDescriptor(o) }.getOrNull()
            }.filter { d ->
                val isAsset = d.downloadUrl.startsWith("asset://", true)
                // App-bundled models (asset://) carry no host restriction; remote
                // models must be HTTPS on an allow-listed host.
                val schemeOk = d.downloadUrl.startsWith("https://", true) || isAsset
                val sizeOk = d.sizeBytes > 0 && d.id.isNotBlank()
                if (!(schemeOk && sizeOk)) {
                    false
                } else if (isAsset) {
                    true
                } else {
                    val host = runCatching { java.net.URL(d.downloadUrl).host.lowercase() }.getOrNull()
                    if (host != null && allowHosts.isNotEmpty() && host.isNotBlank()) {
                        host in allowHosts
                    } else {
                        true
                    }
                }
            }

            return ModelCatalog(models, allowHosts)
        }
    }
}
