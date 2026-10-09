package edu.gatech.cs8803.roomsense.data.server

import android.content.Context
import edu.gatech.cs8803.roomsense.domain.model.RoomModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tiny HTTP server bound on all interfaces so a laptop that joined the
 * hotspot can open the responder page. Assets are local. Nothing is loaded
 * from a CDN, because the hotspot has no internet.
 */
class ResponderHttpServer(
    context: Context,
    private val snapshot: () -> ResponderSnapshot,
) {
    private val appContext = context.applicationContext
    private val pool = Executors.newFixedThreadPool(4)
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    var boundPort: Int = -1
        private set

    fun start(preferredPort: Int = 8088): Int {
        stop()
        var port = preferredPort
        var last: Exception? = null
        repeat(12) {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("0.0.0.0", port))
                serverSocket = socket
                boundPort = socket.localPort
                running.set(true)
                acceptThread = Thread({
                    while (running.get()) {
                        try {
                            val client = socket.accept()
                            pool.execute { handle(client) }
                        } catch (_: Exception) {
                            if (!running.get() || socket.isClosed) break
                        }
                    }
                }, "roomsense-http").apply {
                    isDaemon = true
                    start()
                }
                return boundPort
            } catch (error: Exception) {
                last = error
                port += 1
            }
        }
        throw last ?: IllegalStateException("Could not bind the responder server")
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread = null
        boundPort = -1
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 4_000
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            val request = reader.readLine() ?: return
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val parts = request.split(" ")
            if (parts.size < 2 || !parts[0].equals("GET", ignoreCase = true)) {
                write(socket, 405, "Method Not Allowed", "text/plain; charset=utf-8", "GET only".toByteArray())
                return
            }
            when (val path = parts[1].substringBefore('?').substringBefore('#').ifEmpty { "/" }) {
                "/", "/index.html" -> write(
                    socket,
                    200,
                    "OK",
                    "text/html; charset=utf-8",
                    pageBytes(),
                )
                "/api/status" -> write(
                    socket,
                    200,
                    "OK",
                    "application/json; charset=utf-8",
                    statusJson(snapshot()).toByteArray(Charsets.UTF_8),
                )
                "/floorplan" -> {
                    val file = floorFile()
                    if (file == null) {
                        write(socket, 404, "Not Found", "text/plain; charset=utf-8", "No floor plan".toByteArray())
                    } else {
                        val bytes = file.readBytes()
                        write(socket, 200, "OK", sniff(bytes), bytes)
                    }
                }
                else -> write(socket, 404, "Not Found", "text/plain; charset=utf-8", "Not found".toByteArray())
            }
        } catch (_: Exception) {
            runCatching {
                write(socket, 500, "Error", "text/plain; charset=utf-8", "Error".toByteArray())
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun pageBytes(): ByteArray {
        return runCatching {
            appContext.assets.open("responder/index.html").use { it.readBytes() }
        }.getOrElse {
            FALLBACK.toByteArray(Charsets.UTF_8)
        }
    }

    private fun floorFile(): File? {
        val path = snapshot().floorPlanPath ?: return null
        val file = File(path)
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    private fun sniff(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) return "image/png"
        if (bytes.size >= 4 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte()) return "image/gif"
        return "application/octet-stream"
    }

    private fun write(socket: Socket, status: Int, reason: String, contentType: String, body: ByteArray) {
        val header = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        val out = socket.getOutputStream()
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.write(body)
        out.flush()
    }

    companion object {
        private const val FALLBACK =
            "<!doctype html><title>Room Sense SOS</title><p>Responder page missing.</p>"

        fun statusJson(snapshot: ResponderSnapshot): String {
            val rooms = JSONArray()
            snapshot.rooms.forEach { room -> rooms.put(roomJson(room)) }
            return JSONObject()
                .put("incidentCode", snapshot.code)
                .put("roomId", snapshot.roomId ?: "")
                .put("roomName", snapshot.roomName ?: "")
                .put("confidence", snapshot.confidence.toDouble())
                .put("updatedAt", snapshot.updatedAt)
                .put("ssid", snapshot.ssid)
                .put("demoMode", snapshot.demoMode)
                .put("hasFloorPlan", snapshot.hasFloorPlan)
                .put("rooms", rooms)
                .toString()
        }

        private fun roomJson(room: RoomModel): JSONObject {
            return JSONObject()
                .put("id", room.id)
                .put("name", room.name)
                .put("x", room.x.toDouble())
                .put("y", room.y.toDouble())
                .put("w", room.width.toDouble())
                .put("h", room.height.toDouble())
                .put("color", String.format("#%06X", 0xFFFFFF and room.color))
        }
    }
}
