/*
 * Omni Browser - Offline AI model platform tests
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.ai

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/**
 * Minimal local HTTP server used to test resumable model downloads.
 * Serves a fixed byte array and honours `Range` requests (returns 206 with the
 * requested sub-range), mirroring a real model host.
 */
class LocalModelServer(private val data: ByteArray) {
    private lateinit var server: HttpServer
    var lastRange: String? = null
        private set

    fun start(): Int {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/model.bin") { exchange ->
            val range = exchange.requestHeaders["Range"]?.firstOrNull()
            lastRange = range
            val (start, end) = parseRange(range)
            val body = if (range == null) data else data.copyOfRange(start, end + 1)
            exchange.responseHeaders.add("Content-Length", body.size.toString())
            if (range == null) {
                exchange.sendResponseHeaders(200, body.size.toLong())
            } else {
                exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${data.size}")
                exchange.sendResponseHeaders(206, body.size.toLong())
            }
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        return server.address.port
    }

    fun stop() {
        if (::server.isInitialized) server.stop(0)
    }

    private fun parseRange(range: String?): Pair<Int, Int> {
        if (range == null) return 0 to data.size - 1
        val spec = range.removePrefix("bytes=")
        val (s, e) = spec.split("-", limit = 2)
        val start = s.toInt()
        val end = if (e.isBlank()) data.size - 1 else e.toInt()
        return start to end
    }
}
