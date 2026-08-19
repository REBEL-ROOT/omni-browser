package com.rebelroot.omni.torrent

import android.content.Context
import android.os.Environment
import android.util.Log
import com.frostwire.jlibtorrent.AlertListener
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SessionParams
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentStatus
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.AlertType
import com.frostwire.jlibtorrent.alerts.StateUpdateAlert
import com.frostwire.jlibtorrent.alerts.TorrentErrorAlert
import com.frostwire.jlibtorrent.alerts.TorrentFinishedAlert
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-app BitTorrent engine backed by jlibtorrent.
 *
 * A clicked/pasted `magnet:` or `.torrent` link is downloaded directly by the
 * app (no external torrent client required). Progress is published to
 * [activeTorrents] so the UI can show a live download view.
 *
 * If the native library fails to load (e.g. on an unsupported ABI) [isAvailable]
 * becomes `false` and callers should fall back to handing the link to an
 * installed torrent app.
 */
object TorrentEngine {

    private const val TAG = "TorrentEngine"

    private var sessionManager: SessionManager? = null
    private val started = AtomicBoolean(false)
    private var updaterJob: kotlinx.coroutines.Job? = null

    /** Whether the native libtorrent session could be initialised. */
    var isAvailable = false
        private set

    private val statuses = ConcurrentHashMap<String, TorrentStatus>()
    private val names = ConcurrentHashMap<String, String>()
    private val errors = ConcurrentHashMap<String, String>()

    private val _activeTorrents = MutableStateFlow<List<TorrentProgress>>(emptyList())
    val activeTorrents: StateFlow<List<TorrentProgress>> = _activeTorrents.asStateFlow()

    @Synchronized
    fun ensureStarted(context: Context): Boolean {
        if (started.get() && sessionManager?.isRunning() == true) {
            startUpdater()
            return isAvailable
        }
        return try {
            val settingsDir = File(context.filesDir, "torrent_session").also { it.mkdirs() }
            val sp = SettingsPack()
            // Enable the DHT so magnet links can resolve their metadata.
            sp.enableDht(true)
            // Listen on a random port on all interfaces.
            sp.listenInterfaces("0.0.0.0:0,[::]:0")
            val params = SessionParams(sp)
            val sm = SessionManager()
            sm.start(params)
            sm.addListener(alertListener)
            sessionManager = sm
            isAvailable = true
            started.set(true)
            startUpdater()
            Log.i(TAG, "libtorrent session started")
            true
        } catch (t: Throwable) {
            isAvailable = false
            started.set(false)
            Log.e(TAG, "Failed to start libtorrent session", t)
            false
        }
    }

    private fun startUpdater() {
        if (updaterJob == null || updaterJob?.isActive != true) {
            updaterJob = kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                while (isActive) {
                    kotlinx.coroutines.delay(1000)
                    try {
                        sessionManager?.postTorrentUpdates()
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    /** Start downloading a magnet link or a `.torrent` URL. Returns false if the engine is unavailable. */
    fun startDownload(link: String, context: Context): Boolean {
        if (!ensureStarted(context)) return false
        val extractedName = if (link.startsWith("magnet:", ignoreCase = true)) {
            val dnMatch = Regex("""dn=([^&]+)""").find(link)?.groupValues?.get(1)?.let {
                runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
            }
            dnMatch ?: "Torrent Download"
        } else {
            link.substringAfterLast("/").substringBefore("?").ifBlank { "Torrent Download" }
        }

        val hashMatch = if (link.startsWith("magnet:", ignoreCase = true)) {
            Regex("""xt=urn:btih:([^&]+)""").find(link)?.groupValues?.get(1)?.lowercase()
        } else null

        if (hashMatch != null) {
            names[hashMatch] = extractedName
            // Publish initial progress so UI gets instant feedback
            val initialItem = TorrentProgress(
                infoHash = hashMatch,
                name = extractedName,
                progress = 0f,
                downloadRate = 0,
                uploadRate = 0,
                numPeers = 0,
                totalDone = 0L,
                total = 0L,
                state = "Downloading metadata",
                hasMetadata = false,
                isFinished = false
            )
            _activeTorrents.value = listOf(initialItem) + _activeTorrents.value.filter { it.infoHash != hashMatch }
        }

        return try {
            sessionManager?.download(link, saveDir(context))
            Log.i(TAG, "Started download for: $link")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "download() failed for: $link", t)
            false
        }
    }

    fun pause(infoHash: String) {
        val sm = sessionManager ?: return
        statuses[infoHash]?.let { 
            runCatching { sm.find(it.infoHash())?.pause() }
        }
    }

    fun resume(infoHash: String) {
        val sm = sessionManager ?: return
        statuses[infoHash]?.let { 
            runCatching { sm.find(it.infoHash())?.resume() }
        }
    }

    fun remove(infoHash: String) {
        val sm = sessionManager ?: return
        statuses[infoHash]?.let { 
            runCatching { sm.remove(sm.find(it.infoHash())) }
        }
        statuses.remove(infoHash)
        names.remove(infoHash)
        errors.remove(infoHash)
        publish()
    }

    /** Directory where torrent content is written. */
    fun saveDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Torrents")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Best-effort lookup of the first downloaded file for a finished torrent. */
    fun firstFile(context: Context, infoHash: String): File? {
        val base = saveDir(context)
        val candidates = mutableListOf<File>()
        // libtorrent usually creates a sub-folder named after the torrent.
        File(base, infoHash).listFiles()?.let { candidates += it.toList() }
        base.listFiles()?.filter { it.isDirectory }?.forEach { d ->
            d.listFiles()?.let { candidates += it.toList() }
        }
        return candidates.filter { it.isFile }.maxByOrNull { it.length() }
    }

    private val alertListener = object : AlertListener {
        override fun types(): IntArray = intArrayOf(
            AlertType.STATE_UPDATE.swig(),
            AlertType.TORRENT_FINISHED.swig(),
            AlertType.TORRENT_ERROR.swig(),
            AlertType.METADATA_RECEIVED.swig(),
            AlertType.ADD_TORRENT.swig(),
            AlertType.PIECE_FINISHED.swig(),
            AlertType.BLOCK_FINISHED.swig()
        )

        override fun alert(a: Alert<*>?) {
            if (a == null) return
            try {
                when (a) {
                    is StateUpdateAlert -> {
                        for (st in a.status()) {
                            val hash = runCatching { st.infoHash()?.toHex()?.lowercase() }.getOrNull()
                            if (hash != null) {
                                statuses[hash] = st
                            }
                        }
                        publish()
                    }
                    is TorrentFinishedAlert -> {
                        val hash = runCatching { a.handle()?.infoHash()?.toHex()?.lowercase() }.getOrNull()
                        if (hash != null) {
                            statuses[hash]?.let { statuses[hash] = it }
                        }
                        publish()
                    }
                    is TorrentErrorAlert -> {
                        val hash = runCatching { a.handle()?.infoHash()?.toHex()?.lowercase() }.getOrNull()
                        if (hash != null) {
                            errors[hash] = runCatching { a.error()?.message() }.getOrNull() ?: "Torrent error"
                        }
                        publish()
                    }
                    else -> {
                        // For metadata received or pieces finished, refresh status if handle available
                        val handle = runCatching { 
                            (a as? com.frostwire.jlibtorrent.alerts.TorrentAlert<*>)?.handle() 
                        }.getOrNull()
                        val hash = runCatching { handle?.infoHash()?.toHex()?.lowercase() }.getOrNull()
                        if (hash != null && handle != null) {
                            runCatching { handle.status() }?.getOrNull()?.let {
                                statuses[hash] = it
                                publish()
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error in torrent alert processing", t)
            }
        }
    }

    private fun publish() {
        val list = statuses.values.mapNotNull { st ->
            try {
                val hash = runCatching { st.infoHash()?.toHex()?.lowercase() }.getOrNull() ?: return@mapNotNull null
                val displayName = names[hash] 
                    ?: (if (st.hasMetadata()) {
                        names[hash] ?: "Torrent ($hash)"
                    } else "Fetching metadata…")

                TorrentProgress(
                    infoHash = hash,
                    name = displayName,
                    progress = st.progress(),
                    downloadRate = st.downloadRate(),
                    uploadRate = st.uploadRate(),
                    numPeers = st.numPeers(),
                    totalDone = st.totalDone(),
                    total = st.total(),
                    state = stateLabel(st.state()),
                    hasMetadata = st.hasMetadata(),
                    isFinished = st.isFinished(),
                    errorMessage = errors[hash]
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Error generating torrent progress", t)
                null
            }
        }
        _activeTorrents.value = list
    }

    private fun stateLabel(state: TorrentStatus.State): String = when (state) {
        TorrentStatus.State.CHECKING_FILES -> "Checking files"
        TorrentStatus.State.DOWNLOADING_METADATA -> "Downloading metadata"
        TorrentStatus.State.DOWNLOADING -> "Downloading"
        TorrentStatus.State.FINISHED -> "Finished"
        TorrentStatus.State.SEEDING -> "Seeding"
        TorrentStatus.State.ALLOCATING -> "Allocating"
        TorrentStatus.State.CHECKING_RESUME_DATA -> "Checking resume data"
        TorrentStatus.State.UNKNOWN -> "Queued"
    }
}

data class TorrentProgress(
    val infoHash: String,
    val name: String,
    val progress: Float,        // 0.0f .. 1.0f
    val downloadRate: Int,      // bytes / second
    val uploadRate: Int,        // bytes / second
    val numPeers: Int,
    val totalDone: Long,
    val total: Long,
    val state: String,
    val hasMetadata: Boolean,
    val isFinished: Boolean,
    val errorMessage: String? = null
)
