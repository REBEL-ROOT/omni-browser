package com.rebelroot.omni.browser.chrome

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.ui.graphics.vector.ImageVector
import com.rebelroot.omni.R
import com.rebelroot.omni.browser.BlackholeIcon

/**
 * The surfaces that can host a [ChromeAction]. A surface is any location in the
 * browser chrome that renders a user-configurable list of actions: the two bottom
 * navigation bars, the address bar, the tablet rail, the quick tools sheet and the
 * all-in-one menu.
 */
enum class ChromeSurface {
    HOME_BAR,
    PAGE_BAR,
    ADDRESS_BAR,
    TABLET_RAIL,
    QUICK_TOOLS,
    ALL_IN_ONE,
    /** Reorderable sections of the new-tab / home screen, top to bottom. */
    HOME_SECTIONS,
}

/**
 * A single unit of browser chrome behaviour: one thing a button can do.
 *
 * Actions are pure data. Their id is the stable contract used by persisted layouts,
 * their icon/label are static presentation defaults, and [surfaces] declares where
 * the action is allowed to be placed. Anything that varies with live state (e.g. the
 * VPN label reflecting a connected tunnel) is resolved by the renderer, not stored here.
 */
data class ChromeAction(
    val id: String,
    val icon: ImageVector,
    val surfaces: Set<ChromeSurface>,
    val labelRes: Int? = null,
    val labelLiteral: String? = null,
)

/**
 * Single source of truth for every chrome action the user can place.
 *
 * The order of [ALL] is the canonical order: it is the "append tools that are not yet
 * placed" order for the quick tools surface, so it must stay stable.
 */
object ChromeActionRegistry {

    /** Surfaces offered by all three bars (both bottom bars + the all-in-one address bar). */
    private val ALL_BARS = setOf(ChromeSurface.HOME_BAR, ChromeSurface.PAGE_BAR, ChromeSurface.ADDRESS_BAR)

    val ALL: List<ChromeAction> = listOf(
        ChromeAction(
            id = "image_grabber",
            icon = Icons.Rounded.Collections,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_image_grabber,
        ),
        ChromeAction(
            id = "page_inspector",
            icon = Icons.Rounded.Code,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_page_inspector,
        ),
        ChromeAction(
            id = "block_area",
            icon = Icons.Rounded.LayersClear,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_block_area,
        ),
        ChromeAction(
            id = "spoof_identity",
            icon = Icons.Rounded.Devices,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelLiteral = "Spoof Identity",
        ),
        ChromeAction(
            id = "force_zoom",
            icon = Icons.Rounded.ZoomIn,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_force_zoom,
        ),
        ChromeAction(
            id = "vpn",
            icon = Icons.Rounded.Public,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_vpn,
        ),
        ChromeAction(
            id = "torrent_downloader",
            icon = Icons.Rounded.Download,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelLiteral = "Torrent & Magnet",
        ),
        ChromeAction(
            id = "omni_config",
            icon = Icons.Rounded.Tune,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelLiteral = "omni:config",
        ),
        ChromeAction(
            id = "qr_scanner",
            icon = Icons.Rounded.QrCodeScanner,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_qr_scanner,
        ),
        ChromeAction(
            id = "safe_locker",
            icon = Icons.Rounded.Lock,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_safe_locker,
        ),
        ChromeAction(
            id = "translator",
            icon = Icons.Rounded.Translate,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_translator,
        ),
        ChromeAction(
            id = "edit_page",
            icon = Icons.Rounded.Edit,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_edit_page,
        ),
        ChromeAction(
            id = "save_pdf",
            icon = Icons.Rounded.Print,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_save_pdf,
        ),
        ChromeAction(
            id = "pin_web_app",
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_pin_web_app,
        ),
        ChromeAction(
            id = "auto_scroll",
            icon = Icons.Rounded.ArrowDownward,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_auto_scroll,
        ),
        ChromeAction(
            id = "qr_scan_page",
            icon = Icons.Rounded.CenterFocusWeak,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_qr_scan_page,
        ),
        ChromeAction(
            id = "qr_generator",
            icon = Icons.Rounded.QrCode2,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_qr_generator,
        ),
        ChromeAction(
            id = "console_log",
            icon = Icons.Rounded.Terminal,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_console_log,
        ),
        ChromeAction(
            id = "dev_notes",
            icon = Icons.Rounded.Description,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_dev_notes,
        ),
        ChromeAction(
            id = "site_style",
            icon = Icons.Rounded.Palette,
            surfaces = setOf(ChromeSurface.QUICK_TOOLS),
            labelRes = R.string.tool_site_style,
        ),
        // omni_beam is reachable from the all-in-one menu, not the quick tools sheet.
        ChromeAction(
            id = "omni_beam",
            icon = Icons.Rounded.Devices,
            surfaces = setOf(ChromeSurface.ALL_IN_ONE),
            labelLiteral = "Omni Beam",
        ),

        // ── Navigation-bar actions ────────────────────────────────────────────
        // Ids are stable and persist inside the user's layouts. HOME_BAR/PAGE_BAR are the
        // two bottom bars; ADDRESS_BAR is the all-in-one bar, split into a leading cluster
        // (before the omnibox) and a trailing cluster (after it).
        ChromeAction("back", Icons.AutoMirrored.Rounded.ArrowBack, setOf(ChromeSurface.PAGE_BAR, ChromeSurface.ADDRESS_BAR), labelLiteral = "Back"),
        ChromeAction("forward", Icons.AutoMirrored.Rounded.ArrowForward, setOf(ChromeSurface.PAGE_BAR, ChromeSurface.ADDRESS_BAR), labelLiteral = "Forward"),
        ChromeAction("reload", Icons.Rounded.Refresh, setOf(ChromeSurface.PAGE_BAR, ChromeSurface.ADDRESS_BAR), labelLiteral = "Reload"),
        ChromeAction("home", Icons.Rounded.Home, setOf(ChromeSurface.PAGE_BAR, ChromeSurface.ADDRESS_BAR), labelLiteral = "Home"),
        ChromeAction("share", Icons.Rounded.Share, setOf(ChromeSurface.PAGE_BAR, ChromeSurface.ADDRESS_BAR), labelLiteral = "Share"),
        ChromeAction(
            id = "tools",
            icon = BlackholeIcon,
            surfaces = ALL_BARS,
            labelLiteral = "Tools",
        ),
        ChromeAction("tabs", Icons.Rounded.Tab, setOf(ChromeSurface.PAGE_BAR, ChromeSurface.ADDRESS_BAR), labelLiteral = "Tabs"),
        ChromeAction(
            id = "menu",
            icon = Icons.Rounded.Menu,
            surfaces = ALL_BARS,
            labelLiteral = "Menu",
        ),
        ChromeAction(
            id = "quick_tools",
            icon = Icons.Rounded.GridView,
            surfaces = ALL_BARS,
            labelRes = R.string.quick_tools_title,
        ),
        ChromeAction(
            id = "customize_home",
            icon = Icons.Rounded.Palette,
            surfaces = setOf(ChromeSurface.HOME_BAR),
            labelRes = R.string.customize_home_cd,
        ),
        ChromeAction("news", Icons.AutoMirrored.Rounded.Article, setOf(ChromeSurface.HOME_BAR), labelLiteral = "News Center"),
        ChromeAction("speed_dial", Icons.Rounded.Speed, setOf(ChromeSurface.HOME_BAR, ChromeSurface.ADDRESS_BAR), labelLiteral = "Speed Dial"),
        ChromeAction("new_tab", Icons.Rounded.Add, ALL_BARS, labelLiteral = "New Tab"),
        ChromeAction("bookmarks", Icons.Rounded.Bookmark, ALL_BARS, labelLiteral = "Bookmarks"),
        ChromeAction("history", Icons.Rounded.History, ALL_BARS, labelLiteral = "History"),
        ChromeAction("downloads", Icons.Rounded.Download, ALL_BARS, labelLiteral = "Downloads"),
        ChromeAction("extensions", Icons.Rounded.Extension, ALL_BARS, labelLiteral = "Extensions"),
        ChromeAction("settings", Icons.Rounded.Settings, ALL_BARS, labelLiteral = "Settings"),
        ChromeAction("incognito", Icons.Rounded.VisibilityOff, ALL_BARS, labelLiteral = "Incognito"),

        // ── Home-screen sections ──────────────────────────────────────────────
        // Reorderable top-to-bottom blocks of the new-tab page. "section_" prefix keeps
        // their ids from colliding with the nav-bar actions above.
        ChromeAction("section_logo", Icons.Rounded.Wallpaper, setOf(ChromeSurface.HOME_SECTIONS), labelLiteral = "Logo"),
        ChromeAction("section_search", Icons.Rounded.Search, setOf(ChromeSurface.HOME_SECTIONS), labelLiteral = "Search Bar"),
        ChromeAction("section_shortcuts", Icons.Rounded.GridView, setOf(ChromeSurface.HOME_SECTIONS), labelLiteral = "Shortcuts"),
        ChromeAction("section_recents", Icons.Rounded.History, setOf(ChromeSurface.HOME_SECTIONS), labelLiteral = "Recently Visited"),
        ChromeAction("section_privacy", Icons.Rounded.Shield, setOf(ChromeSurface.HOME_SECTIONS), labelLiteral = "Privacy Stats"),
    )

    private val byId: Map<String, ChromeAction> = ALL.associateBy { it.id }

    /** Ids available to the quick tools surface, in canonical order. */
    val QUICK_TOOL_IDS: List<String> =
        ALL.filter { ChromeSurface.QUICK_TOOLS in it.surfaces }.map { it.id }

    fun find(id: String): ChromeAction? = byId[id]

    fun forSurface(surface: ChromeSurface): List<ChromeAction> =
        ALL.filter { surface in it.surfaces }

    /** Action ids available to a surface, in canonical order. */
    fun idsFor(surface: ChromeSurface): List<String> = forSurface(surface).map { it.id }

    /** Fallback icon so an unknown persisted id never crashes the chrome. */
    fun iconFor(id: String): ImageVector = byId[id]?.icon ?: Icons.Rounded.Build
}