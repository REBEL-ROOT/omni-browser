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
 * Immutable metadata describing a downloadable AI model.
 *
 * Models are NEVER bundled in the APK. This descriptor only carries tiny
 * metadata (a few hundred bytes). The actual weights live at [downloadUrl] on a
 * legitimate upstream host chosen by Omni; the application downloads, verifies
 * (size + SHA-256), and installs them into application-private storage.
 *
 * Web content can NEVER supply or alter these values — the catalog is
 * application-controlled. See [ModelCatalog].
 *
 * @param id Stable model id, unique per (task, languages), e.g. "bergamot-en-es".
 * @param version Model version string, e.g. "1.2.0".
 * @param task The AI task this model serves.
 * @param name Human-readable display name.
 * @param sourceLanguage Source language code, or null for language-agnostic
 *   models (e.g. some ASR models).
 * @param targetLanguage Target language code, or null.
 * @param sizeBytes Expected exact byte size of the model file.
 * @param downloadUrl Upstream URL of the model file (HTTPS). Must be on an
 *   allow-listed host; never a webpage-supplied URL.
 * @param sha256 Expected lowercase hex SHA-256 of the model file. When null the
 *   model is integrity-checked by size only and flagged as UNVERIFIED (the UI
 *   must warn the user before install).
 * @param license Model license (e.g. "Apache-2.0", "Mozilla Public License 2.0").
 * @param sourceProject Upstream project name (e.g. "Bergamot", "Vosk", "OPUS-MT").
 * @param minimumRuntimeVersion Minimum native runtime / engine version required.
 */
data class ModelDescriptor(
    val id: String,
    val version: String,
    val task: ModelTask,
    val name: String,
    val sourceLanguage: String?,
    val targetLanguage: String?,
    val sizeBytes: Long,
    val downloadUrl: String,
    val sha256: String?,
    val license: String,
    val sourceProject: String,
    val minimumRuntimeVersion: String? = null
) {
    /** True when a SHA-256 is pinned and can be cryptographically verified. */
    val isChecksumPinned: Boolean get() = !sha256.isNullOrBlank()

    /** Unique installed-file key combining id + version. */
    val fileKey: String get() = "$id-$version"
}
