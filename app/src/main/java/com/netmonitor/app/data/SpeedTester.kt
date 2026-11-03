package com.netmonitor.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class Phase { IDLE, PING, DOWNLOAD, UPLOAD, DONE, ERROR }

enum class ServerKind { CLOUDFLARE, OOKLA }

data class SpeedServer(
    val id: String,
    val name: String,
    val detail: String,
    val kind: ServerKind,

    val uploadUrl: String = "",
    val pingMs: Int? = null,
)

data class ClientInfo(
    val isp: String = "",
    val ip: String = "",
    val place: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
) {
    val loaded: Boolean get() = ip.isNotBlank()
}

data class SpeedTestState(
    val phase: Phase = Phase.IDLE,
    val liveMbps: Double = 0.0,
    val progress: Float = 0f,
    val downMbps: Double? = null,
    val upMbps: Double? = null,
    val pingMs: Int? = null,
    val jitterMs: Int? = null,
    val serverName: String = "",
    val error: String? = null,
) {
    val running: Boolean get() = phase == Phase.PING || phase == Phase.DOWNLOAD || phase == Phase.UPLOAD
}

object SpeedTester {
    private const val BASE = "https://speed.cloudflare.com"
    private const val AUTO_ID = "auto"
    private const val DOWN_CHUNK = 25_000_000
    private const val UP_CHUNK = 2_000_000
    private const val DOWN_THREADS = 4
    private const val UP_THREADS = 3
    private const val DOWN_MS = 8_000L
    private const val UP_MS = 7_000L
    private const val WARMUP_MS = 1_000L
    private const val PING_COUNT = 10

    private val AUTO = SpeedServer(AUTO_ID, "Automatic", "Cloudflare · automatic", ServerKind.CLOUDFLARE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var infoJob: Job? = null
    private var coloMap: Map<String, String>? = null

    private val _state = MutableStateFlow(SpeedTestState())
    val state: StateFlow<SpeedTestState> = _state.asStateFlow()

    private val _client = MutableStateFlow(ClientInfo())
    val client: StateFlow<ClientInfo> = _client.asStateFlow()

    private val _servers = MutableStateFlow(listOf(AUTO))
    val servers: StateFlow<List<SpeedServer>> = _servers.asStateFlow()

    private val _selectedId = MutableStateFlow(AUTO_ID)
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SpeedServer>>(emptyList())
    val searchResults: StateFlow<List<SpeedServer>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var searchJob: Job? = null

    fun select(id: String) {
        if (_state.value.running) return
        if (_servers.value.none { it.id == id }) {
            val found = _searchResults.value.firstOrNull { it.id == id } ?: return
            _servers.value = _servers.value + found
        }
        _selectedId.value = id
    }

    fun search(query: String) {
        searchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            _searchResults.value = emptyList()
            _searching.value = false
            return
        }
        searchJob = scope.launch {
            _searching.value = true
            try {
                val found = fetchOokla(_client.value, "&search=" + URLEncoder.encode(q, "UTF-8"), 30, 12)
                _searchResults.value = found
            } finally {
                _searching.value = false
            }
        }
    }

    private fun current(): SpeedServer =
        _servers.value.firstOrNull { it.id == _selectedId.value } ?: AUTO

    fun start() {
        if (job?.isActive == true) return
        if (!_client.value.loaded) refreshInfo()
        job = scope.launch { run() }
    }

    fun cancel() {
        job?.cancel()
    }

    fun clearResult() {
        val p = _state.value.phase
        if (p == Phase.DONE || p == Phase.ERROR) _state.value = SpeedTestState()
    }

    fun refreshInfo() {
        if (_state.value.running || infoJob?.isActive == true) return
        infoJob = scope.launch {
            _refreshing.value = true
            try {
                _client.update { it.copy(loading = true, failed = false) }
                val fetched = fetchClient()
                if (fetched != null) {
                    _client.value = fetched
                } else {
                    _client.update { it.copy(loading = false, failed = !it.loaded) }
                }

                if (coloMap == null) loadColoMap()
                val auto = probeCloudflare() ?: AUTO
                _servers.value = (listOf(auto) + _servers.value.filter { it.kind == ServerKind.OOKLA && it.id != AUTO_ID })
                    .distinctBy { it.id }

                val ookla = fetchOokla(_client.value, "", 100, 24)
                val best = ookla.firstOrNull { it.pingMs != null }
                val top = best?.copy(id = AUTO_ID, detail = listOf("Automatic", best.detail).filter { it.isNotBlank() }.joinToString(" · ")) ?: auto
                val keep = _servers.value.firstOrNull {
                    it.id == _selectedId.value && it.id != AUTO_ID && it.kind == ServerKind.OOKLA &&
                        ookla.none { o -> o.id == it.id }
                }
                _servers.value = (listOf(top) + ookla.filter { it.id != best?.id } + listOfNotNull(keep))
                    .distinctBy { it.id }
                if (_servers.value.none { it.id == _selectedId.value }) _selectedId.value = AUTO_ID
            } catch (_: Exception) {
                _client.update { it.copy(loading = false, failed = !it.loaded) }
            } finally {
                _refreshing.value = false
            }
        }
    }

    private fun fetchClient(): ClientInfo? {
        val providers = listOf<() -> ClientInfo?>(
            { fromOokla() },
            { fromCloudflare() },
            { fromIpWho() },
            { fromIpApi() },
        )
        var best: ClientInfo? = null
        for (provider in providers) {
            val r = provider() ?: continue
            val b = best
            best = if (b == null) r else b.copy(
                isp = b.isp.ifBlank { r.isp },
                place = b.place.ifBlank { r.place },
                lat = b.lat ?: r.lat,
                lon = b.lon ?: r.lon,
            )
            if (best.isp.isNotBlank() && best.place.isNotBlank()) break
        }
        return best
    }

    private fun fromOokla(): ClientInfo? = try {
        val x = httpText("https://www.speedtest.net/speedtest-config.php", 6_000) ?: return null
        val tag = Regex("<client\\s[^>]*>").find(x)?.value ?: return null
        fun attr(n: String) = Regex("\\b$n=\"([^\"]*)\"").find(tag)?.groupValues?.get(1).orEmpty()
        val ip = attr("ip")
        if (ip.isBlank()) null else ClientInfo(
            isp = attr("isp"),
            ip = ip,
            lat = attr("lat").toDoubleOrNull(),
            lon = attr("lon").toDoubleOrNull(),
        )
    } catch (_: Exception) {
        null
    }

    private fun fromCloudflare(): ClientInfo? = try {
        val j = JSONObject(httpText("$BASE/meta") ?: return null)
        val ip = j.optString("clientIp")
        if (ip.isBlank()) null else ClientInfo(
            isp = j.optString("asOrganization"),
            ip = ip,
            place = place(j.optString("city"), countryName(j.optString("country"))),
            lat = j.optString("latitude").toDoubleOrNull(),
            lon = j.optString("longitude").toDoubleOrNull(),
        )
    } catch (_: Exception) {
        null
    }

    private fun fromIpWho(): ClientInfo? = try {
        val j = JSONObject(httpText("https://ipwho.is/") ?: return null)
        val ip = j.optString("ip")
        if (ip.isBlank() || !j.optBoolean("success", true)) null else {
            val conn = j.optJSONObject("connection")
            ClientInfo(
                isp = conn?.optString("isp").orEmpty().ifBlank { conn?.optString("org").orEmpty() },
                ip = ip,
                place = place(j.optString("city"), j.optString("country")),
                lat = j.optDouble("latitude").takeIf { !it.isNaN() },
                lon = j.optDouble("longitude").takeIf { !it.isNaN() },
            )
        }
    } catch (_: Exception) {
        null
    }

    private fun fromIpApi(): ClientInfo? = try {
        val j = JSONObject(httpText("https://ipapi.co/json/") ?: return null)
        val ip = j.optString("ip")
        if (ip.isBlank()) null else ClientInfo(
            isp = j.optString("org"),
            ip = ip,
            place = place(j.optString("city"), j.optString("country_name")),
            lat = j.optDouble("latitude").takeIf { !it.isNaN() },
            lon = j.optDouble("longitude").takeIf { !it.isNaN() },
        )
    } catch (_: Exception) {
        null
    }

    private fun place(city: String, country: String): String =
        listOf(city, country).filter { it.isNotBlank() && it != "null" }.joinToString(", ")

    private fun countryName(code: String): String =
        if (code.length == 2) Locale("", code).getDisplayCountry(Locale.ENGLISH).ifBlank { code } else code

    private fun probeCloudflare(): SpeedServer? = try {
        var colo: String? = null
        var best = Double.MAX_VALUE
        repeat(4) { i ->
            val c = open("$BASE/__down?bytes=0", 4_000)
            val t0 = System.nanoTime()
            c.responseCode
            c.inputStream.use { it.readBytes() }
            val ms = (System.nanoTime() - t0) / 1e6
            if (i == 0) colo = coloOf(c) else best = min(best, ms)
        }
        AUTO.copy(name = coloName(colo), pingMs = max(1, best.roundToInt()))
    } catch (_: Exception) {
        null
    }

    private suspend fun fetchOokla(
        client: ClientInfo,
        extra: String,
        limit: Int,
        pingCount: Int,
    ): List<SpeedServer> {
        val text = httpText("https://www.speedtest.net/api/js/servers?engine=js&limit=$limit$extra", 10_000)
            ?: return emptyList()
        val arr = try { JSONArray(text) } catch (_: Exception) { return emptyList() }
        val raw = (0 until arr.length()).mapNotNull { i -> parseOokla(arr.optJSONObject(i), client) }
            .distinctBy { it.id }
        val head = raw.take(pingCount)
        val tail = raw.drop(pingCount)
        val pinged = coroutineScope {
            head.map { s -> async { s.copy(pingMs = pingServer(s)) } }.awaitAll()
        }.filter { it.pingMs != null }.sortedBy { it.pingMs }
        return pinged + tail
    }

    private fun parseOokla(o: JSONObject?, client: ClientInfo): SpeedServer? {
        o ?: return null
        val url = o.optString("url")
        if (!url.contains("upload.php")) return null
        val https = when (val v = o.opt("https_functional")) {
            is Boolean -> v
            is Number -> v.toInt() == 1
            is String -> v == "1" || v.equals("true", true)
            else -> false
        }
        val upload = if (https) url.replaceFirst("http://", "https://") else url
        val km = haversine(client.lat, client.lon, o.optDouble("lat"), o.optDouble("lon"))
            ?: o.optDouble("distance").takeIf { !it.isNaN() && it > 0 }
        val city = place(o.optString("name"), o.optString("country"))
        val sponsor = o.optString("sponsor")
        val detail = listOfNotNull(
            city.takeIf { sponsor.isNotBlank() },
            km?.let { "${it.roundToInt()} km" },
        ).joinToString(" · ")
        return SpeedServer(
            id = "ookla:" + o.optString("id"),
            name = sponsor.ifBlank { city },
            detail = detail,
            kind = ServerKind.OOKLA,
            uploadUrl = upload,
        )
    }

    private fun haversine(lat1: Double?, lon1: Double?, lat2: Double, lon2: Double): Double? {
        if (lat1 == null || lon1 == null || lat2.isNaN() || lon2.isNaN()) return null
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun pingServer(s: SpeedServer): Int? = try {
        val base = s.uploadUrl.replace("upload.php", "latency.txt")
        var best = Double.MAX_VALUE
        repeat(3) { i ->
            val c = open("$base?x=${System.nanoTime()}", 3_000)
            val t0 = System.nanoTime()
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.use { it.readBytes() }
            if (i > 0) best = min(best, (System.nanoTime() - t0) / 1e6)
        }
        max(1, best.roundToInt())
    } catch (_: Exception) {
        null
    }

    private class Endpoints(val ping: () -> String, val down: () -> String, val up: () -> String)

    private fun endpoints(s: SpeedServer): Endpoints =
        if (s.kind == ServerKind.CLOUDFLARE) {
            Endpoints(
                ping = { "$BASE/__down?bytes=0" },
                down = { "$BASE/__down?bytes=$DOWN_CHUNK" },
                up = { "$BASE/__up" },
            )
        } else {
            val file = ooklaDownFile(s)
            Endpoints(
                ping = { s.uploadUrl.replace("upload.php", "latency.txt") + "?x=" + System.nanoTime() },
                down = { file + "?x=" + System.nanoTime() },
                up = { s.uploadUrl },
            )
        }

    private fun ooklaDownFile(s: SpeedServer): String {
        for (size in listOf(4000, 3000, 2000, 1000)) {
            val u = s.uploadUrl.replace("upload.php", "random${size}x$size.jpg")
            val c = open("$u?x=${System.nanoTime()}", 5_000)
            try {
                if (c.responseCode == 200) return u
            } catch (_: IOException) {
            } finally {
                c.disconnect()
            }
        }
        return s.uploadUrl.replace("upload.php", "random4000x4000.jpg")
    }

    private suspend fun run() {
        val server = current()
        _state.value = SpeedTestState(phase = Phase.PING, serverName = server.name)
        try {
            val ep = endpoints(server)
            measurePing(ep, server)
            val down = measureDown(ep)

            _state.update { it.copy(downMbps = down, liveMbps = 0.0, progress = 0f) }
            delay(1500)
            val up = measureUp(ep)

            _state.update { it.copy(phase = Phase.DONE, upMbps = up, liveMbps = 0.0, progress = 1f) }
        } catch (e: CancellationException) {
            _state.update { it.copy(phase = Phase.IDLE, liveMbps = 0.0, progress = 0f) }
            throw e
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    phase = Phase.ERROR,
                    liveMbps = 0.0,
                    error = "Test failed. Check your connection and try again.",
                )
            }
        }
    }

    private suspend fun measurePing(ep: Endpoints, server: SpeedServer) {
        val rtts = ArrayList<Double>()
        repeat(PING_COUNT + 1) { i ->
            currentCoroutineContext().ensureActive()
            val c = open(ep.ping())
            val t0 = System.nanoTime()
            c.responseCode
            c.inputStream.use { it.readBytes() }
            val ms = (System.nanoTime() - t0) / 1e6
            if (i == 0) {
                if (server.kind == ServerKind.CLOUDFLARE) {
                    _state.update { it.copy(serverName = coloName(coloOf(c))) }
                }
            } else {
                rtts += ms
            }
            _state.update { it.copy(progress = (i + 1f) / (PING_COUNT + 1)) }
        }
        val sorted = rtts.sorted()
        val median = sorted[sorted.size / 2]
        var jitter = 0.0
        for (i in 1 until rtts.size) jitter += abs(rtts[i] - rtts[i - 1])
        jitter /= max(1, rtts.size - 1)
        _state.update {
            it.copy(pingMs = max(1, median.roundToInt()), jitterMs = jitter.roundToInt())
        }
    }

    private suspend fun measureDown(ep: Endpoints): Double = coroutineScope {
        val total = AtomicLong(0)
        val start = System.nanoTime()
        val endAt = start + DOWN_MS * 1_000_000
        _state.update { it.copy(phase = Phase.DOWNLOAD, liveMbps = 0.0, progress = 0f) }

        val workers = (1..DOWN_THREADS).map {
            async {
                try {
                    val buf = ByteArray(64 * 1024)
                    while (isActive && System.nanoTime() < endAt) {
                        val c = open(ep.down())
                        try {
                            c.inputStream.use { s ->
                                while (isActive && System.nanoTime() < endAt) {
                                    val n = s.read(buf)
                                    if (n < 0) break
                                    total.addAndGet(n.toLong())
                                }
                            }
                        } finally {
                            c.disconnect()
                        }
                    }
                } catch (_: IOException) {
                }
            }
        }

        val result = sample(total, start, endAt, Phase.DOWNLOAD)
        workers.forEach { it.await() }
        if (total.get() == 0L) throw IOException("No data received")
        result
    }

    private suspend fun measureUp(ep: Endpoints): Double = coroutineScope {
        val total = AtomicLong(0)
        val start = System.nanoTime()
        val endAt = start + UP_MS * 1_000_000
        _state.update { it.copy(phase = Phase.UPLOAD, liveMbps = 0.0, progress = 0f) }

        val workers = (1..UP_THREADS).map {
            async {
                try {
                    val buf = ByteArray(32 * 1024)
                    while (isActive && System.nanoTime() < endAt) {
                        val c = open(ep.up())
                        try {
                            c.requestMethod = "POST"
                            c.doOutput = true
                            c.setFixedLengthStreamingMode(UP_CHUNK)
                            c.outputStream.use { os ->
                                var sent = 0
                                while (sent < UP_CHUNK && isActive && System.nanoTime() < endAt) {
                                    val n = min(buf.size, UP_CHUNK - sent)
                                    os.write(buf, 0, n)
                                    sent += n
                                    total.addAndGet(n.toLong())
                                }
                            }
                            c.responseCode
                        } finally {
                            c.disconnect()
                        }
                    }
                } catch (_: IOException) {
                }
            }
        }

        val result = sample(total, start, endAt, Phase.UPLOAD)
        workers.forEach { it.await() }
        if (total.get() == 0L) throw IOException("No data sent")
        result
    }

    private suspend fun sample(total: AtomicLong, start: Long, endAt: Long, phase: Phase): Double {
        val times = ArrayDeque<Long>()
        val counts = ArrayDeque<Long>()
        times.addLast(start)
        counts.addLast(0L)
        var shown = 0.0
        var warmBytes = -1L
        var warmTime = 0L

        while (true) {
            delay(100)
            val now = System.nanoTime()
            val bytes = total.get()
            times.addLast(now)
            counts.addLast(bytes)
            while (times.size > 2 && now - times.first() > 800_000_000L) {
                times.removeFirst()
                counts.removeFirst()
            }
            val dt = (now - times.first()) / 1e9
            val inst = if (dt > 0) (counts.last() - counts.first()) * 8.0 / dt / 1e6 else 0.0
            shown = if (shown == 0.0) inst else shown * 0.6 + inst * 0.4
            if (warmBytes < 0 && now - start >= WARMUP_MS * 1_000_000) {
                warmBytes = bytes
                warmTime = now
            }
            val progress = ((now - start) / (endAt - start).toFloat()).coerceIn(0f, 1f)
            _state.update { it.copy(phase = phase, liveMbps = shown, progress = progress) }
            if (now >= endAt) break
        }

        val now = System.nanoTime()
        val bytes = total.get()
        val secs = (now - warmTime) / 1e9
        return if (warmBytes >= 0 && secs > 0.5) {
            (bytes - warmBytes) * 8.0 / secs / 1e6
        } else {
            bytes * 8.0 / ((now - start) / 1e9) / 1e6
        }
    }

    private fun open(url: String, timeout: Int = 8_000): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeout
            readTimeout = timeout
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) NetMonitor")
            if (url.startsWith(BASE)) {
                setRequestProperty("Referer", "$BASE/")
                setRequestProperty("Origin", BASE)
            }
        }

    private fun httpText(url: String, timeout: Int = 8_000): String? = try {
        val c = open(url, timeout)
        try {
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    private fun coloOf(c: HttpURLConnection): String? =
        c.getHeaderField("cf-ray")?.substringAfterLast('-')?.uppercase()

    private fun coloName(colo: String?): String {
        if (colo.isNullOrBlank()) return "Automatic"
        return coloMap?.get(colo) ?: COLOS[colo] ?: "Cloudflare ($colo)"
    }

    private fun loadColoMap() {
        try {
            val arr = JSONArray(httpText("$BASE/locations") ?: return)
            val map = HashMap<String, String>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val iata = o.optString("iata")
                if (iata.isBlank()) continue
                map[iata] = place(o.optString("city"), countryName(o.optString("cca2")))
            }
            if (map.isNotEmpty()) coloMap = map
        } catch (_: Exception) {
        }
    }

    private val COLOS = mapOf(
        "DAC" to "Dhaka, Bangladesh",
        "CGP" to "Chittagong, Bangladesh",
        "CCU" to "Kolkata, India",
        "DEL" to "Delhi, India",
        "BOM" to "Mumbai, India",
        "MAA" to "Chennai, India",
        "BLR" to "Bengaluru, India",
        "SIN" to "Singapore",
        "KUL" to "Kuala Lumpur, Malaysia",
        "BKK" to "Bangkok, Thailand",
        "HKG" to "Hong Kong",
        "NRT" to "Tokyo, Japan",
        "DXB" to "Dubai, UAE",
        "LHR" to "London, UK",
        "FRA" to "Frankfurt, Germany",
        "AMS" to "Amsterdam, Netherlands",
        "CDG" to "Paris, France",
        "IAD" to "Ashburn, USA",
        "LAX" to "Los Angeles, USA",
        "SJC" to "San Jose, USA",
        "EWR" to "Newark, USA",
    )
}
