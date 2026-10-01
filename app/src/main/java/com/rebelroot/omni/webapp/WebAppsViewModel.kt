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

package com.rebelroot.omni.webapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WebAppsSortOrder(val displayName: String) {
    RECENTLY_USED("Recently Used"),
    MOST_LAUNCHED("Most Launched"),
    NAME_ASC("Name (A-Z)"),
    DATE_ADDED("Date Added")
}

class WebAppsViewModel(application: Application) : AndroidViewModel(application) {

    val repository = WebAppRepository.getInstance(application)

    val installedApps: StateFlow<List<WebAppEntity>> = repository.installedApps.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow(PwaCategory.ALL)
    val selectedProfileFilter = MutableStateFlow("All")
    val sortOrder = MutableStateFlow(WebAppsSortOrder.RECENTLY_USED)

    // Filtered installed apps
    val filteredInstalledApps: StateFlow<List<WebAppEntity>> = combine(
        installedApps,
        searchQuery,
        selectedProfileFilter,
        sortOrder
    ) { apps, query, profile, sort ->
        var list = apps

        if (profile != "All") {
            list = list.filter { it.profileId.equals(profile, ignoreCase = true) }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                    it.url.lowercase().contains(q) ||
                    it.category.lowercase().contains(q) ||
                    it.profileId.lowercase().contains(q)
            }
        }

        when (sort) {
            WebAppsSortOrder.RECENTLY_USED -> list.sortedWith(
                compareByDescending<WebAppEntity> { it.isPinned }
                    .thenByDescending { it.lastUsedDate }
            )
            WebAppsSortOrder.MOST_LAUNCHED -> list.sortedWith(
                compareByDescending<WebAppEntity> { it.isPinned }
                    .thenByDescending { it.launchCount }
            )
            WebAppsSortOrder.NAME_ASC -> list.sortedWith(
                compareByDescending<WebAppEntity> { it.isPinned }
                    .thenBy { it.name.lowercase() }
            )
            WebAppsSortOrder.DATE_ADDED -> list.sortedWith(
                compareByDescending<WebAppEntity> { it.isPinned }
                    .thenByDescending { it.installDate }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered discover catalog
    val filteredDiscoverCatalog: StateFlow<List<PwaCatalogItem>> = combine(
        searchQuery,
        selectedCategory
    ) { query, category ->
        var list = PwaCatalog.curatedApps

        if (category != PwaCategory.ALL) {
            list = list.filter { it.category == category }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter { item ->
                item.name.lowercase().contains(q) ||
                    item.description.lowercase().contains(q) ||
                    item.displayDomain.lowercase().contains(q) ||
                    item.category.displayName.lowercase().contains(q) ||
                    item.keywords.any { it.lowercase().contains(q) }
            }
        }

        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PwaCatalog.curatedApps
    )

    fun installWebApp(
        url: String,
        name: String,
        category: String = "Productivity",
        description: String? = null,
        profileId: String = "default",
        pinToHomeScreen: Boolean = false,
        onComplete: ((WebAppEntity) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val app = repository.installWebApp(
                url = url,
                name = name,
                category = category,
                description = description,
                profileId = profileId,
                pinToHomeScreen = pinToHomeScreen
            )
            onComplete?.invoke(app)
        }
    }

    fun cloneWebApp(
        sourceApp: WebAppEntity,
        newName: String,
        newProfileId: String,
        pinToHomeScreen: Boolean = false,
        onComplete: ((WebAppEntity) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val cloned = repository.cloneWebApp(
                sourceApp = sourceApp,
                newName = newName,
                newProfileId = newProfileId,
                pinToHomeScreen = pinToHomeScreen
            )
            onComplete?.invoke(cloned)
        }
    }

    fun updateWebApp(app: WebAppEntity) {
        viewModelScope.launch {
            repository.updateWebApp(app)
        }
    }

    fun togglePin(app: WebAppEntity) {
        viewModelScope.launch {
            repository.togglePin(app)
        }
    }

    fun uninstallWebApp(app: WebAppEntity) {
        viewModelScope.launch {
            repository.uninstallWebApp(app)
        }
    }

    fun recordLaunch(appId: String) {
        viewModelScope.launch {
            repository.recordLaunch(appId)
        }
    }

    fun pinToHomeScreen(app: WebAppEntity) {
        repository.pinShortcutToLauncher(app)
    }
}
