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

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rebelroot.omni.browser.BrowserViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen for managing installed Progressive Web Apps and discovering new ones like a pro.
 * Inspired by Nira Browser's PWA architecture with multi-profile support, cloning,
 * category discovery, search filtering, and launcher shortcuts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebAppsScreen(
    browserViewModel: BrowserViewModel,
    onNavigateBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    webAppsViewModel: WebAppsViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 = Installed, 1 = Discover
    var isSearchActive by remember { mutableStateOf(false) }

    val installedApps by webAppsViewModel.filteredInstalledApps.collectAsState()
    val allInstalledApps by webAppsViewModel.installedApps.collectAsState()
    val discoverItems by webAppsViewModel.filteredDiscoverCatalog.collectAsState()

    val searchQuery by webAppsViewModel.searchQuery.collectAsState()
    val selectedCategory by webAppsViewModel.selectedCategory.collectAsState()
    val selectedProfileFilter by webAppsViewModel.selectedProfileFilter.collectAsState()
    val sortOrder by webAppsViewModel.sortOrder.collectAsState()

    // Dialog state
    var showInstallCustomDialog by remember { mutableStateOf(false) }
    var appToClone by remember { mutableStateOf<WebAppEntity?>(null) }
    var appToEdit by remember { mutableStateOf<WebAppEntity?>(null) }
    var appToDelete by remember { mutableStateOf<WebAppEntity?>(null) }
    var catalogItemToInstall by remember { mutableStateOf<PwaCatalogItem?>(null) }

    // Active page detection from browser
    val activeTab = browserViewModel.activeTab
    val hasValidActivePage = remember(activeTab?.url) {
        val u = activeTab?.url ?: ""
        (u.startsWith("http://") || u.startsWith("https://")) &&
            !u.contains("file:///android_asset") &&
            u != "about:blank"
    }

    var dismissActivePageBanner by remember { mutableStateOf(false) }

    BackHandler {
        if (isSearchActive) {
            isSearchActive = false
            webAppsViewModel.searchQuery.value = ""
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { webAppsViewModel.searchQuery.value = it },
                            placeholder = { Text(if (selectedTabIndex == 0) "Search installed apps..." else "Search web app catalog...") },
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { webAppsViewModel.searchQuery.value = "" }) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column {
                            Text(
                                text = "Web Apps Hub",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = if (selectedTabIndex == 0) "${allInstalledApps.size} installed web apps" else "Curated Progressive Web Apps directory",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            webAppsViewModel.searchQuery.value = ""
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) webAppsViewModel.searchQuery.value = ""
                    }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Search"
                        )
                    }

                    IconButton(onClick = { showInstallCustomDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Install Any App"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Segmented Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Installed",
                                fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (selectedTabIndex == 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${allInstalledApps.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTabIndex == 0) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )

                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Explore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Discover",
                                fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${PwaCatalog.curatedApps.size}+",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                )
            }

            // Tab 0: Manage Installed Web Apps
            if (selectedTabIndex == 0) {
                // Active Website Quick-Install Banner
                AnimatedVisibility(
                    visible = hasValidActivePage && !dismissActivePageBanner,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val pageTitle = activeTab?.title?.ifBlank { activeTab.url } ?: activeTab?.url ?: ""
                    ActivePageQuickInstallBanner(
                        title = pageTitle,
                        url = activeTab?.url ?: "",
                        onInstall = {
                            webAppsViewModel.installWebApp(
                                url = activeTab?.url ?: "",
                                name = pageTitle,
                                category = "Productivity",
                                profileId = "default",
                                pinToHomeScreen = true
                            ) {
                                Toast.makeText(context, "Installed '$pageTitle' to Web Apps!", Toast.LENGTH_SHORT).show()
                                dismissActivePageBanner = true
                            }
                        },
                        onDismiss = { dismissActivePageBanner = true }
                    )
                }

                // Filter & Sort Bar
                InstalledFilterBar(
                    currentProfile = selectedProfileFilter,
                    allProfiles = listOf("All", "Default", "Work", "Personal") +
                        allInstalledApps.map { it.profileLabel }.distinct().filter { it !in listOf("Default", "Work", "Personal") },
                    onProfileSelected = { webAppsViewModel.selectedProfileFilter.value = it },
                    sortOrder = sortOrder,
                    onSortSelected = { webAppsViewModel.sortOrder.value = it }
                )

                if (installedApps.isEmpty()) {
                    EmptyInstalledAppsView(
                        onOpenDiscover = { selectedTabIndex = 1 },
                        onInstallCustom = { showInstallCustomDialog = true }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(installedApps, key = { it.id }) { app ->
                            InstalledAppCard(
                                app = app,
                                onLaunch = {
                                    webAppsViewModel.recordLaunch(app.id)
                                    onOpenUrl(app.url)
                                },
                                onPinShortcut = {
                                    webAppsViewModel.pinToHomeScreen(app)
                                },
                                onTogglePin = {
                                    webAppsViewModel.togglePin(app)
                                },
                                onClone = {
                                    appToClone = app
                                },
                                onEdit = {
                                    appToEdit = app
                                },
                                onCopyUrl = {
                                    clipboardManager.setText(AnnotatedString(app.url))
                                    Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = {
                                    appToDelete = app
                                }
                            )
                        }
                    }
                }
            }

            // Tab 1: Discover Catalog
            if (selectedTabIndex == 1) {
                // Category Filter Pills
                CategoryChipsRow(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { webAppsViewModel.selectedCategory.value = it }
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(discoverItems, key = { it.id }) { item ->
                        val isAlreadyInstalled = allInstalledApps.any {
                            it.url.removeSuffix("/") == item.url.removeSuffix("/")
                        }

                        DiscoverAppCard(
                            item = item,
                            isInstalled = isAlreadyInstalled,
                            onInstall = {
                                catalogItemToInstall = item
                            },
                            onOpen = {
                                onOpenUrl(item.url)
                            }
                        )
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    // 1. Install Custom Web App Dialog
    if (showInstallCustomDialog) {
        InstallCustomAppDialog(
            initialUrl = if (hasValidActivePage) activeTab?.url ?: "" else "",
            initialTitle = if (hasValidActivePage) activeTab?.title ?: "" else "",
            onDismiss = { showInstallCustomDialog = false },
            onConfirm = { url, name, category, profile, pinToHome ->
                webAppsViewModel.installWebApp(
                    url = url,
                    name = name,
                    category = category,
                    profileId = profile,
                    pinToHomeScreen = pinToHome
                ) {
                    Toast.makeText(context, "Installed '$name' successfully!", Toast.LENGTH_SHORT).show()
                    showInstallCustomDialog = false
                }
            }
        )
    }

    // 2. Discover Item Install Dialog (with profile & pin options)
    catalogItemToInstall?.let { item ->
        InstallCatalogAppDialog(
            item = item,
            onDismiss = { catalogItemToInstall = null },
            onConfirm = { profileId, pinToHome ->
                webAppsViewModel.installWebApp(
                    url = item.url,
                    name = item.name,
                    category = item.category.displayName,
                    description = item.description,
                    profileId = profileId,
                    pinToHomeScreen = pinToHome
                ) {
                    Toast.makeText(context, "Installed '${item.name}'!", Toast.LENGTH_SHORT).show()
                    catalogItemToInstall = null
                }
            }
        )
    }

    // 3. Clone App Dialog (Pro feature like Nira Browser)
    appToClone?.let { app ->
        CloneWebAppDialog(
            sourceApp = app,
            onDismiss = { appToClone = null },
            onConfirm = { newName, newProfile, pinToHome ->
                webAppsViewModel.cloneWebApp(
                    sourceApp = app,
                    newName = newName,
                    newProfileId = newProfile,
                    pinToHomeScreen = pinToHome
                ) {
                    Toast.makeText(context, "Cloned app: '$newName'", Toast.LENGTH_SHORT).show()
                    appToClone = null
                }
            }
        )
    }

    // 4. Edit App Dialog
    appToEdit?.let { app ->
        EditWebAppDialog(
            app = app,
            onDismiss = { appToEdit = null },
            onConfirm = { updatedApp ->
                webAppsViewModel.updateWebApp(updatedApp)
                Toast.makeText(context, "Web app updated", Toast.LENGTH_SHORT).show()
                appToEdit = null
            }
        )
    }

    // 5. Delete Confirm Dialog
    appToDelete?.let { app ->
        AlertDialog(
            onDismissRequest = { appToDelete = null },
            title = { Text("Uninstall Web App") },
            text = { Text("Are you sure you want to uninstall '${app.name}'? This will remove its shortcut and cached metadata.") },
            confirmButton = {
                Button(
                    onClick = {
                        webAppsViewModel.uninstallWebApp(app)
                        Toast.makeText(context, "'${app.name}' uninstalled", Toast.LENGTH_SHORT).show()
                        appToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Uninstall")
                }
            },
            dismissButton = {
                TextButton(onClick = { appToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Components ─────────────────────────────────────────────────────────────

@Composable
private fun ActivePageQuickInstallBanner(
    title: String,
    url: String,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Install Current Site",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onInstall,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Install", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun InstalledFilterBar(
    currentProfile: String,
    allProfiles: List<String>,
    onProfileSelected: (String) -> Unit,
    sortOrder: WebAppsSortOrder,
    onSortSelected: (WebAppsSortOrder) -> Unit
) {
    var showProfileMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile filter dropdown pill
        Box {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { showProfileMenu = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Profile: $currentProfile",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            DropdownMenu(
                expanded = showProfileMenu,
                onDismissRequest = { showProfileMenu = false }
            ) {
                allProfiles.forEach { profile ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = profile,
                                fontWeight = if (profile == currentProfile) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        trailingIcon = {
                            if (profile == currentProfile) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = {
                            onProfileSelected(profile)
                            showProfileMenu = false
                        }
                    )
                }
            }
        }

        // Sort dropdown pill
        Box {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { showSortMenu = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = sortOrder.displayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                WebAppsSortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = order.displayName,
                                fontWeight = if (order == sortOrder) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        trailingIcon = {
                            if (order == sortOrder) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = {
                            onSortSelected(order)
                            showSortMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledAppCard(
    app: WebAppEntity,
    onLaunch: () -> Unit,
    onPinShortcut: () -> Unit,
    onTogglePin: () -> Unit,
    onClone: () -> Unit,
    onEdit: () -> Unit,
    onCopyUrl: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val lastUsedStr = remember(app.lastUsedDate) {
        formatRelativeTime(app.lastUsedDate)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onLaunch() },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (app.isPinned) 3.dp else 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = if (app.isPinned) 1.5.dp else 1.dp,
            color = if (app.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            AppIconImage(
                name = app.name,
                url = app.url,
                iconPath = app.iconPath,
                modifier = Modifier.size(46.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            // App details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (app.isPinned) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Profile Chip
                    ProfileTag(app.profileLabel)

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = app.displayDomain,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${app.launchCount} launches • Used $lastUsedStr",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Fast Open Button
            FilledTonalButton(
                onClick = onLaunch,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Open", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            // 3-dot overflow menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "App Options")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to Home Screen") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onPinShortcut()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clone Web App") },
                        leadingIcon = { Icon(Icons.Rounded.Apps, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onClone()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (app.isPinned) "Unpin from Top" else "Pin to Top") },
                        leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Details") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy Link") },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onCopyUrl()
                        }
                    )
                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("Uninstall", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverAppCard(
    item: PwaCatalogItem,
    isInstalled: Boolean,
    onInstall: () -> Unit,
    onOpen: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { if (isInstalled) onOpen() else onInstall() },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                AppIconImage(
                    name = item.name,
                    url = item.url,
                    iconPath = null,
                    modifier = Modifier.size(44.dp)
                )

                // Category or tag badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.tag,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = item.displayDomain,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(34.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (isInstalled) {
                OutlinedButton(
                    onClick = onOpen,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Installed", fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onInstall,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Install", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CategoryChipsRow(
    selectedCategory: PwaCategory,
    onCategorySelected: (PwaCategory) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PwaCategory.entries.forEach { cat ->
            val isSelected = cat == selectedCategory
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { onCategorySelected(cat) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = cat.icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = cat.displayName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIconImage(
    name: String,
    url: String,
    iconPath: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val domain = remember(url) {
        try {
            android.net.Uri.parse(url).host ?: url
        } catch (e: Exception) {
            url
        }
    }

    val iconFile = remember(iconPath) {
        if (!iconPath.isNullOrBlank()) File(iconPath) else null
    }

    val imageModel = remember(iconFile, domain) {
        if (iconFile != null && iconFile.exists()) {
            iconFile
        } else {
            "https://www.google.com/s2/favicons?sz=128&domain=$domain"
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageModel)
                .crossfade(true)
                .build(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ProfileTag(profile: String) {
    val (bgColor, textColor) = when (profile.lowercase()) {
        "work" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "personal" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = profile,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
private fun EmptyInstalledAppsView(
    onOpenDiscover: () -> Unit,
    onInstallCustom: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Web Apps Installed",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Install websites as standalone progressive web apps, or explore curated apps in the Discover directory.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onOpenDiscover,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Explore Web Apps Directory")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onInstallCustom,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Install Any Website URL")
        }
    }
}

// ── Dialogs ────────────────────────────────────────────────────────────────

@Composable
private fun InstallCustomAppDialog(
    initialUrl: String,
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (url: String, name: String, category: String, profile: String, pinToHome: Boolean) -> Unit
) {
    var urlText by remember { mutableStateOf(initialUrl) }
    var nameText by remember { mutableStateOf(initialTitle) }
    var selectedProfile by remember { mutableStateOf("Default") }
    var pinToHome by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Install Web App", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        if (nameText.isBlank()) {
                            nameText = try {
                                android.net.Uri.parse(it).host?.removePrefix("www.") ?: ""
                            } catch (e: Exception) { "" }
                        }
                    },
                    label = { Text("Website URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("App Name") },
                    placeholder = { Text("My App") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Profile Selection
                Text("Select Profile", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Default", "Work", "Personal").forEach { p ->
                        val isSelected = selectedProfile == p
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedProfile = p }
                        ) {
                            Text(
                                text = p,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = pinToHome, onCheckedChange = { pinToHome = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pin shortcut to Android Home Screen", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlText.isNotBlank()) {
                        onConfirm(urlText, nameText.ifBlank { urlText }, "Productivity", selectedProfile.lowercase(), pinToHome)
                    }
                },
                enabled = urlText.isNotBlank()
            ) {
                Text("Install")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun InstallCatalogAppDialog(
    item: PwaCatalogItem,
    onDismiss: () -> Unit,
    onConfirm: (profileId: String, pinToHome: Boolean) -> Unit
) {
    var selectedProfile by remember { mutableStateOf("Default") }
    var pinToHome by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(name = item.name, url = item.url, iconPath = null, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(item.displayDomain, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(item.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text("Choose Profile", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Default", "Work", "Personal").forEach { p ->
                        val isSelected = selectedProfile == p
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedProfile = p }
                        ) {
                            Text(
                                text = p,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = pinToHome, onCheckedChange = { pinToHome = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pin to Home Screen", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedProfile.lowercase(), pinToHome) }) {
                Text("Install Web App")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CloneWebAppDialog(
    sourceApp: WebAppEntity,
    onDismiss: () -> Unit,
    onConfirm: (newName: String, newProfile: String, pinToHome: Boolean) -> Unit
) {
    var newName by remember { mutableStateOf("${sourceApp.name} (Work)") }
    var selectedProfile by remember { mutableStateOf("Work") }
    var pinToHome by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clone Web App", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Create an isolated duplicate instance of '${sourceApp.name}' for a different account or work profile.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Cloned App Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Profile for Cloned App", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Work", "Personal", "School").forEach { p ->
                        val isSelected = selectedProfile == p
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedProfile = p
                                    newName = "${sourceApp.name} ($p)"
                                }
                        ) {
                            Text(
                                text = p,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = pinToHome, onCheckedChange = { pinToHome = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pin clone to Home Screen", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newName, selectedProfile.lowercase(), pinToHome) },
                enabled = newName.isNotBlank()
            ) {
                Text("Clone App")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditWebAppDialog(
    app: WebAppEntity,
    onDismiss: () -> Unit,
    onConfirm: (WebAppEntity) -> Unit
) {
    var nameText by remember { mutableStateOf(app.name) }
    var urlText by remember { mutableStateOf(app.url) }
    var profileText by remember { mutableStateOf(app.profileId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Web App", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("App Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = profileText,
                    onValueChange = { profileText = it },
                    label = { Text("Profile Tag (e.g. work, personal)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        app.copy(
                            name = nameText.ifBlank { app.name },
                            url = urlText.ifBlank { app.url },
                            profileId = profileText.ifBlank { app.profileId }
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        diff < 60_000 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "yesterday"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
