package cz.mares.btsradar

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

@SuppressLint("MissingPermission", "NewApi")
private class AndroidWifiNetController(
    private val context: Context,
    private val scope: CoroutineScope
) : WifiNetController {

    private val prefs = context.getSharedPreferences("bts_radar_wifi_prefs", Context.MODE_PRIVATE)
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val aliasMap = LinkedHashMap<String, String>()
    private val wifiRssiHistory = ArrayDeque<WifiRssiSample>()
    private val roamLog = ArrayDeque<WifiRoamEvent>()
    private val speedRecords = ArrayList<SpeedTestRecord>()

    private val gwPingHistory = ArrayDeque<Float?>()
    private var gwSent = 0
    private var gwReceived = 0
    private var gwMin: Float? = null
    private var gwMax: Float? = null

    private val netPingHistory = ArrayDeque<Float?>()
    private var netSent = 0
    private var netReceived = 0
    private var netMin: Float? = null
    private var netMax: Float? = null

    private var monitorJob: Job? = null
    private var dualPingJob: Job? = null
    private var speedTestJob: Job? = null

    private var callbackWifiNetwork: Network? = null
    private var callbackWifiInfo: WifiInfo? = null
    private var networkCallbackObj: ConnectivityManager.NetworkCallback? = null
    private var receiverRegistered = false

    private var lastRoamBssid: String = ""
    private var lastRoamBand: WifiBandType = WifiBandType.UNKNOWN
    private var lastRoamRssi: Int = -100
    private var lastRoamSsid: String = ""

    private val initialZigbee = WifiSpectrumCalculator.analyzeZigbeeChannels(emptyList(), null)

    private val _state = MutableStateFlow(
        WifiNetState(
            internetPingTarget = prefs.getString("ping_target", "1.1.1.1").orEmpty().ifBlank { "1.1.1.1" },
            selectedZigbeeChannel = prefs.getInt("selected_zb_ch", 20).coerceIn(11, 26),
            zigbeeScores = initialZigbee,
            recommendedZigbeeChannels = WifiSpectrumCalculator.pickBestZigbeeChannels(initialZigbee)
        )
    )
    override val state: StateFlow<WifiNetState> = _state.asStateFlow()

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, true) ?: true
            } else {
                true
            }
            scope.launch {
                processScanResults(scanUpdated = success)
            }
        }
    }

    init {
        loadSavedAliasesAndHistory()
        registerWifiCallbacks()
        startWifiLiveMonitor()
        scope.launch {
            delay(400L)
            processScanResults(scanUpdated = true)
            triggerWifiScan()
        }
    }

    private fun loadSavedAliasesAndHistory() {
        runCatching {
            val rawAliases = prefs.getString("bssid_aliases_json", "").orEmpty()
            if (rawAliases.isNotBlank()) {
                val obj = JSONObject(rawAliases)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.optString(k, "").trim()
                    if (k.isNotBlank() && v.isNotBlank()) {
                        aliasMap[k.uppercase(Locale.US)] = v
                    }
                }
            }
        }
        runCatching {
            val rawHist = prefs.getString("speed_history_json", "").orEmpty()
            if (rawHist.isNotBlank()) {
                val arr = JSONArray(rawHist)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    speedRecords.add(
                        SpeedTestRecord(
                            timeText = o.optString("time", ""),
                            connectionSummary = o.optString("conn", ""),
                            downloadMbps = o.optDouble("dl", 0.0),
                            uploadMbps = o.optDouble("ul", 0.0),
                            idlePingMs = o.optDouble("ping", 0.0).toFloat(),
                            loadedPingMs = if (o.has("loaded")) o.optDouble("loaded").toFloat() else null,
                            jitterMs = o.optDouble("jitter", 0.0).toFloat()
                        )
                    )
                }
            }
        }
        _state.update {
            it.copy(
                bssidAliases = HashMap(aliasMap),
                speedHistory = speedRecords.toList(),
                internetPing = it.internetPing.copy(targetHost = it.internetPingTarget)
            )
        }
    }

    private fun persistAliases() {
        runCatching {
            val obj = JSONObject()
            for ((k, v) in aliasMap) {
                obj.put(k, v)
            }
            prefs.edit().putString("bssid_aliases_json", obj.toString()).apply()
        }
    }

    private fun persistSpeedHistory() {
        runCatching {
            val arr = JSONArray()
            for (rec in speedRecords.take(15)) {
                val o = JSONObject().apply {
                    put("time", rec.timeText)
                    put("conn", rec.connectionSummary)
                    put("dl", rec.downloadMbps)
                    put("ul", rec.uploadMbps)
                    put("ping", rec.idlePingMs.toDouble())
                    if (rec.loadedPingMs != null) put("loaded", rec.loadedPingMs.toDouble())
                    put("jitter", rec.jitterMs.toDouble())
                }
                arr.put(o)
            }
            prefs.edit().putString("speed_history_json", arr.toString()).apply()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun registerWifiCallbacks() {
        runCatching {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            ContextCompat.registerReceiver(
                context,
                scanReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true
        }

        runCatching {
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val cb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                    override fun onAvailable(network: Network) {
                        callbackWifiNetwork = network
                    }

                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        callbackWifiNetwork = network
                        val info = networkCapabilities.transportInfo as? WifiInfo
                        if (info != null) {
                            callbackWifiInfo = info
                        }
                    }

                    override fun onLost(network: Network) {
                        if (callbackWifiNetwork == network) {
                            callbackWifiNetwork = null
                            callbackWifiInfo = null
                        }
                    }
                }
            } else {
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        callbackWifiNetwork = network
                    }

                    override fun onLost(network: Network) {
                        if (callbackWifiNetwork == network) {
                            callbackWifiNetwork = null
                        }
                    }
                }
            }
            connectivityManager.registerNetworkCallback(req, cb)
            networkCallbackObj = cb
        }
    }

    private fun startWifiLiveMonitor() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            var tick = 0
            while (isActive) {
                pollActiveWifiConnection()
                if (tick > 0 && tick % 25 == 0) {
                    processScanResults(scanUpdated = true)
                }
                tick++
                delay(1000L)
            }
        }
    }

    private suspend fun pollActiveWifiConnection() = withContext(Dispatchers.IO) {
        val enabled = runCatching { wifiManager.isWifiEnabled }.getOrDefault(false)
        if (!enabled) {
            if (lastRoamBssid.isNotEmpty()) {
                recordRoamEvent("Odpojeno", formatNodeLabel(lastRoamBssid, lastRoamBand), "---", "Wi-Fi adaptér vypnut")
                lastRoamBssid = ""
            }
            _state.update {
                it.copy(
                    isWifiEnabled = false,
                    isWifiConnected = false,
                    activeWifi = null
                )
            }
            return@withContext
        }

        val physWifiNet = findPhysicalWifiNetwork()
        val linkProps: LinkProperties? = physWifiNet?.let {
            runCatching { connectivityManager.getLinkProperties(it) }.getOrNull()
        }

        @Suppress("DEPRECATION")
        val legacyInfo: WifiInfo? = runCatching { wifiManager.connectionInfo }.getOrNull()
        val cbInfo: WifiInfo? = callbackWifiInfo

        val chosenInfo = pickBestWifiInfo(cbInfo, legacyInfo)
        val validRssi = chosenInfo?.rssi ?: -127
        val rawBssid = chosenInfo?.bssid?.uppercase(Locale.US).orEmpty()
        val hasRealConnection = physWifiNet != null && chosenInfo != null && validRssi > -125 && chosenInfo.networkId != -1 ||
            (physWifiNet != null && cbInfo != null && validRssi > -125)

        if (!hasRealConnection || chosenInfo == null) {
            if (lastRoamBssid.isNotEmpty()) {
                recordRoamEvent("Odpojeno", formatNodeLabel(lastRoamBssid, lastRoamBand), "---", "Spojení s AP ukončeno")
                lastRoamBssid = ""
            }
            _state.update {
                it.copy(
                    isWifiEnabled = true,
                    isWifiConnected = false,
                    activeWifi = null
                )
            }
            return@withContext
        }

        val cleanSsid = cleanSsidString(chosenInfo.ssid)
        val cleanBssid = if (rawBssid.isBlank() || rawBssid == "02:00:00:00:00:00") "Skryté BSSID (povol GPS)" else rawBssid
        val alias = aliasMap[cleanBssid].orEmpty()

        val freq = chosenInfo.frequency.takeIf { it > 0 } ?: 0
        val band = WifiSpectrumCalculator.freqToBand(freq)
        val channel = WifiSpectrumCalculator.freqToChannel(freq)

        val matchedScan = _state.value.scannedAps.firstOrNull {
            cleanBssid.length == 17 && it.bssid.equals(cleanBssid, ignoreCase = true)
        }
        val widthMhz = matchedScan?.channelWidthMhz ?: inferWidthFromLinkSpeed(chosenInfo, band)
        val standardStr = resolveWifiStandard(chosenInfo, matchedScan, band)
        val securityStr = resolveWifiSecurity(chosenInfo, matchedScan)

        val txSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            chosenInfo.txLinkSpeedMbps.takeIf { it > 0 }
        } else null
        val rxSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            chosenInfo.rxLinkSpeedMbps.takeIf { it > 0 }
        } else null
        val maxTx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            chosenInfo.maxSupportedTxLinkSpeedMbps.takeIf { it > 0 }
        } else null
        val maxRx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            chosenInfo.maxSupportedRxLinkSpeedMbps.takeIf { it > 0 }
        } else null

        val (ipv4, ipv6, gwIp, dnsStr, iface) = extractLinkAddresses(linkProps)
        val (mldMac, mloItems) = extractMloDetails(chosenInfo)

        val activeDetails = ActiveWifiDetails(
            ssid = cleanSsid,
            bssid = cleanBssid,
            customAlias = alias,
            rssiDbm = validRssi,
            signalPercent = WifiSpectrumCalculator.rssiToPercent(validRssi),
            standardLabel = standardStr,
            band = band,
            frequencyMhz = freq,
            channelNumber = channel,
            channelWidthMhz = widthMhz,
            linkSpeedMbps = chosenInfo.linkSpeed.coerceAtLeast(0),
            txLinkSpeedMbps = txSpeed,
            rxLinkSpeedMbps = rxSpeed,
            maxTxMbps = maxTx,
            maxRxMbps = maxRx,
            securityLabel = securityStr,
            ipv4Address = ipv4,
            ipv6Address = ipv6,
            gatewayIp = gwIp,
            dnsServers = dnsStr,
            interfaceName = iface,
            mldMacAddress = mldMac,
            mloLinks = mloItems
        )

        // Detekce Mesh roamingu a zmeny pasma
        if (cleanBssid.length == 17) {
            if (lastRoamBssid.isEmpty()) {
                recordRoamEvent(
                    "Připojeno",
                    "---",
                    formatNodeLabel(cleanBssid, band),
                    "$cleanSsid • K$channel (${widthMhz} MHz) • $validRssi dBm"
                )
            } else if (!lastRoamBssid.equals(cleanBssid, ignoreCase = true)) {
                val type = if (lastRoamSsid == cleanSsid) "Mesh Roaming" else "Změna Wi-Fi"
                recordRoamEvent(
                    type,
                    formatNodeLabel(lastRoamBssid, lastRoamBand),
                    formatNodeLabel(cleanBssid, band),
                    "$lastRoamRssi dBm -> $validRssi dBm • K$channel (${band.shortLabel})"
                )
            } else if (lastRoamBand != band && band != WifiBandType.UNKNOWN) {
                recordRoamEvent(
                    "Přeladění pásma",
                    formatNodeLabel(lastRoamBssid, lastRoamBand),
                    formatNodeLabel(cleanBssid, band),
                    "${lastRoamBand.shortLabel} -> ${band.shortLabel} (K$channel • $validRssi dBm)"
                )
            }
            lastRoamBssid = cleanBssid
            lastRoamBand = band
            lastRoamRssi = validRssi
            lastRoamSsid = cleanSsid
        }

        val now = System.currentTimeMillis()
        synchronized(wifiRssiHistory) {
            wifiRssiHistory.addLast(
                WifiRssiSample(
                    timestampMs = now,
                    rssiDbm = validRssi,
                    bssid = cleanBssid,
                    band = band
                )
            )
            while (wifiRssiHistory.size > 60) {
                wifiRssiHistory.removeFirst()
            }
        }

        val histCopy = synchronized(wifiRssiHistory) { wifiRssiHistory.toList() }
        val roamCopy = synchronized(roamLog) { roamLog.toList() }

        _state.update { old ->
            val updatedScans = old.scannedAps.map { ap ->
                ap.copy(
                    alias = aliasMap[ap.bssid].orEmpty(),
                    isConnectedBssid = ap.bssid.equals(cleanBssid, ignoreCase = true),
                    isSameMeshSsid = cleanSsid.isNotBlank() && ap.ssid == cleanSsid
                )
            }
            val zbScores = WifiSpectrumCalculator.analyzeZigbeeChannels(updatedScans, activeDetails)
            val zbBest = WifiSpectrumCalculator.pickBestZigbeeChannels(zbScores)
            val gwSnap = if (gwIp != "---" && old.gatewayPing.targetHost != gwIp) {
                old.gatewayPing.copy(targetHost = gwIp)
            } else {
                old.gatewayPing
            }
            old.copy(
                isWifiEnabled = true,
                isWifiConnected = true,
                activeWifi = activeDetails,
                scannedAps = updatedScans,
                wifiHistory = histCopy,
                roamEvents = roamCopy,
                zigbeeScores = zbScores,
                recommendedZigbeeChannels = zbBest,
                gatewayPing = gwSnap
            )
        }
    }

    private fun findPhysicalWifiNetwork(): Network? {
        callbackWifiNetwork?.let { net ->
            val caps = runCatching { connectivityManager.getNetworkCapabilities(net) }.getOrNull()
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return net
            }
        }
        return runCatching {
            @Suppress("DEPRECATION")
            connectivityManager.allNetworks.firstOrNull { net ->
                val caps = connectivityManager.getNetworkCapabilities(net)
                caps != null &&
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
        }.getOrNull()
    }

    private fun pickBestWifiInfo(cbInfo: WifiInfo?, legacyInfo: WifiInfo?): WifiInfo? {
        if (cbInfo == null) return legacyInfo
        if (legacyInfo == null) return cbInfo
        val cbValidBssid = !cbInfo.bssid.isNullOrBlank() && cbInfo.bssid != "02:00:00:00:00:00"
        val legValidBssid = !legacyInfo.bssid.isNullOrBlank() && legacyInfo.bssid != "02:00:00:00:00:00"
        return when {
            cbValidBssid -> cbInfo
            legValidBssid -> legacyInfo
            else -> cbInfo
        }
    }

    private fun cleanSsidString(raw: String?): String {
        val s = raw?.trim()?.removeSurrounding("\"").orEmpty()
        return if (s.isBlank() || s.equals("<unknown ssid>", ignoreCase = true)) {
            "Wi-Fi síť (SSID skryté)"
        } else {
            s
        }
    }

    private fun formatNodeLabel(bssid: String, band: WifiBandType): String {
        val alias = aliasMap[bssid.uppercase(Locale.US)]
        val shortMac = if (bssid.length >= 8) "..${bssid.takeLast(8)}" else bssid
        val base = if (!alias.isNullOrBlank()) "$alias ($shortMac)" else bssid
        return if (band != WifiBandType.UNKNOWN) "$base [${band.shortLabel}]" else base
    }

    private fun recordRoamEvent(type: String, from: String, to: String, details: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        synchronized(roamLog) {
            roamLog.addFirst(
                WifiRoamEvent(
                    timeText = stamp,
                    eventType = type,
                    fromNode = from,
                    toNode = to,
                    details = details
                )
            )
            while (roamLog.size > 40) {
                roamLog.removeLast()
            }
        }
    }

    private data class LinkAddrPack(
        val ipv4: String,
        val ipv6: String,
        val gateway: String,
        val dns: String,
        val iface: String
    )

    private fun extractLinkAddresses(lp: LinkProperties?): LinkAddrPack {
        var ipv4 = "---"
        var ipv6 = "---"
        var gw = "---"
        var dns = "---"
        val iface = lp?.interfaceName.orEmpty().ifBlank { "wlan0" }

        if (lp != null) {
            for (la in lp.linkAddresses) {
                val addr = la.address
                if (addr is Inet4Address && ipv4 == "---") {
                    ipv4 = "${addr.hostAddress ?: "---"}/${la.prefixLength}"
                } else if (addr is Inet6Address && !addr.isLinkLocalAddress && ipv6 == "---") {
                    ipv6 = addr.hostAddress?.substringBefore('%') ?: "---"
                }
            }
            if (ipv6 == "---") {
                lp.linkAddresses.firstOrNull { it.address is Inet6Address }?.address?.hostAddress?.let {
                    ipv6 = it.substringBefore('%')
                }
            }
            for (route in lp.routes) {
                val rGw = route.gateway
                if (route.isDefaultRoute && rGw is Inet4Address && !rGw.isAnyLocalAddress) {
                    gw = rGw.hostAddress ?: "---"
                    break
                }
            }
            val dnsList = lp.dnsServers.mapNotNull { it.hostAddress?.substringBefore('%') }.take(3)
            if (dnsList.isNotEmpty()) {
                dns = dnsList.joinToString(", ")
            }
        }

        if (gw == "---") {
            @Suppress("DEPRECATION")
            val dhcpGw = runCatching { wifiManager.dhcpInfo?.gateway ?: 0 }.getOrDefault(0)
            if (dhcpGw != 0) {
                gw = intToIpv4(dhcpGw)
            }
        }

        return LinkAddrPack(ipv4, ipv6, gw, dns, iface)
    }

    private fun intToIpv4(ip: Int): String {
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }

    private fun extractMloDetails(info: WifiInfo): Pair<String, List<MloLinkItem>> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return "" to emptyList()
        }
        return runCatching {
            val mld = info.apMldMacAddress?.toString()?.uppercase(Locale.US).orEmpty()
            val links = info.associatedMloLinks.orEmpty().map { link ->
                val bandStr = when (link.band) {
                    1 -> "2,4 GHz"
                    2, 4, 6 -> "5 GHz"
                    8 -> "6 GHz"
                    else -> "MLO"
                }
                val mac = link.apMacAddress?.toString()?.uppercase(Locale.US).orEmpty()
                val tx = link.txLinkSpeedMbps.takeIf { it > 0 }
                val rx = link.rxLinkSpeedMbps.takeIf { it > 0 }
                val active = link.state == 3
                MloLinkItem(
                    linkId = link.linkId,
                    bandLabel = bandStr,
                    channel = link.channel,
                    bssid = mac,
                    rssiDbm = link.rssi,
                    txMbps = tx,
                    rxMbps = rx,
                    isActive = active
                )
            }
            mld to links
        }.getOrDefault("" to emptyList())
    }

    private fun resolveWifiStandard(info: WifiInfo, scan: ScannedWifiAp?, band: WifiBandType): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return when (info.wifiStandard) {
                8 -> "Wi-Fi 7 (802.11be)"
                6 -> if (band == WifiBandType.BAND_6) "Wi-Fi 6E (802.11ax)" else "Wi-Fi 6 (802.11ax)"
                5 -> "Wi-Fi 5 (802.11ac)"
                4 -> "Wi-Fi 4 (802.11n)"
                1 -> "802.11a/b/g"
                else -> scan?.standardLabel ?: "Wi-Fi"
            }
        }
        return scan?.standardLabel ?: "Wi-Fi"
    }

    private fun resolveScanStandard(sr: ScanResult, band: WifiBandType): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return when (sr.wifiStandard) {
                8 -> "Wi-Fi 7"
                6 -> if (band == WifiBandType.BAND_6) "Wi-Fi 6E" else "Wi-Fi 6"
                5 -> "Wi-Fi 5"
                4 -> "Wi-Fi 4"
                1 -> "Legacy"
                else -> "Wi-Fi"
            }
        }
        return "Wi-Fi"
    }

    private fun resolveWifiSecurity(info: WifiInfo, scan: ScannedWifiAp?): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val byType = when (info.currentSecurityType) {
                WifiInfo.SECURITY_TYPE_SAE -> "WPA3-SAE"
                WifiInfo.SECURITY_TYPE_PSK -> "WPA2-PSK"
                WifiInfo.SECURITY_TYPE_OWE -> "OWE (Enhanced Open)"
                WifiInfo.SECURITY_TYPE_EAP -> "WPA2-Enterprise"
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "WPA3-Enterprise"
                WifiInfo.SECURITY_TYPE_WEP -> "WEP"
                WifiInfo.SECURITY_TYPE_OPEN -> "Otevřená (Open)"
                else -> ""
            }
            if (byType.isNotBlank()) return byType
        }
        return scan?.securityLabel ?: "---"
    }

    private fun parseCapabilitiesSecurity(caps: String?): String {
        val c = caps.orEmpty().uppercase(Locale.US)
        return when {
            c.contains("SAE") && c.contains("PSK") -> "WPA2/WPA3"
            c.contains("SAE") || c.contains("WPA3") -> "WPA3"
            c.contains("OWE") -> "OWE"
            c.contains("EAP") -> "Enterprise"
            c.contains("RSN") || c.contains("WPA2") -> "WPA2"
            c.contains("WPA") -> "WPA"
            c.contains("WEP") -> "WEP"
            else -> "Open"
        }
    }

    private fun inferWidthFromLinkSpeed(info: WifiInfo, band: WifiBandType): Int {
        val spd = info.linkSpeed
        return when {
            band == WifiBandType.BAND_6 && spd >= 2400 -> 320
            spd >= 1200 -> 160
            band != WifiBandType.BAND_24 && spd >= 400 -> 80
            spd >= 150 -> 40
            else -> 20
        }
    }

    override fun triggerWifiScan() {
        scope.launch(Dispatchers.IO) {
            if (!hasLocationPermission()) {
                _state.update { it.copy(statusBanner = "Pro sken okolních Wi-Fi a Zigbee kanálů povol přesnou polohu.") }
                return@launch
            }
            @Suppress("DEPRECATION")
            val accepted = runCatching { wifiManager.startScan() }.getOrDefault(false)
            processScanResults(scanUpdated = accepted)
            if (!accepted) {
                _state.update {
                    it.copy(
                        scanThrottledHint = true,
                        statusBanner = "Zobrazena mezipaměť Wi-Fi (Android omezuje sken na 4x za 2 min – lze vypnout ve Vývojářských možnostech)."
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        scanThrottledHint = false,
                        statusBanner = "Skenování 2,4 / 5 / 6 GHz spektra spuštěno..."
                    )
                }
            }
        }
    }

    private suspend fun processScanResults(scanUpdated: Boolean) = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext
        val rawResults = runCatching { wifiManager.scanResults.orEmpty() }.getOrDefault(emptyList())
        val active = _state.value.activeWifi

        val mapped = rawResults.mapNotNull { sr ->
            val bssid = sr.BSSID?.uppercase(Locale.US).orEmpty()
            if (bssid.isBlank()) return@mapNotNull null
            @Suppress("DEPRECATION")
            val legacySsid = sr.SSID.orEmpty()
            val rawSsid = if (legacySsid.isNotBlank()) {
                legacySsid
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sr.wifiSsid?.toString()?.removeSurrounding("\"").orEmpty()
            } else {
                ""
            }
            val cleanSsid = rawSsid.trim().ifBlank { "(Skryté SSID)" }
            val freq = sr.frequency
            val band = WifiSpectrumCalculator.freqToBand(freq)
            val ch = WifiSpectrumCalculator.freqToChannel(freq)
            val widthMhz = when (sr.channelWidth) {
                ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                ScanResult.CHANNEL_WIDTH_160MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
                5 -> 320
                else -> 20
            }
            val center = if (sr.centerFreq0 > 0) sr.centerFreq0 else freq

            ScannedWifiAp(
                ssid = cleanSsid,
                bssid = bssid,
                alias = aliasMap[bssid].orEmpty(),
                rssiDbm = sr.level,
                band = band,
                frequencyMhz = freq,
                centerFreqMhz = center,
                channelNumber = ch,
                channelWidthMhz = widthMhz,
                standardLabel = resolveScanStandard(sr, band),
                securityLabel = parseCapabilitiesSecurity(sr.capabilities),
                isConnectedBssid = active != null && active.bssid.equals(bssid, ignoreCase = true),
                isSameMeshSsid = active != null && active.ssid == cleanSsid
            )
        }.sortedByDescending { it.rssiDbm }

        val zbScores = WifiSpectrumCalculator.analyzeZigbeeChannels(mapped, active)
        val zbRec = WifiSpectrumCalculator.pickBestZigbeeChannels(zbScores)
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        _state.update { old ->
            old.copy(
                scannedAps = mapped,
                zigbeeScores = zbScores,
                recommendedZigbeeChannels = zbRec,
                lastScanTimeText = if (scanUpdated || old.lastScanTimeText == "--:--:--") timeStr else old.lastScanTimeText,
                statusBanner = if (scanUpdated) "Nalezeno ${mapped.size} Wi-Fi vysílačů v okolí" else old.statusBanner
            )
        }
    }

    override fun saveBssidAlias(bssid: String, alias: String) {
        val key = bssid.trim().uppercase(Locale.US)
        if (key.length != 17) return
        val clean = alias.trim()
        if (clean.isEmpty()) {
            aliasMap.remove(key)
        } else {
            aliasMap[key] = clean
        }
        persistAliases()
        _state.update { old ->
            val updatedActive = old.activeWifi?.let { aw ->
                if (aw.bssid.equals(key, ignoreCase = true)) aw.copy(customAlias = clean) else aw
            }
            val updatedScans = old.scannedAps.map { ap ->
                if (ap.bssid.equals(key, ignoreCase = true)) ap.copy(alias = clean) else ap
            }
            old.copy(
                activeWifi = updatedActive,
                scannedAps = updatedScans,
                bssidAliases = HashMap(aliasMap),
                statusBanner = if (clean.isNotEmpty()) "Uzel $key pojmenován jako \"$clean\"" else "Pojmenování uzlu $key odstraněno"
            )
        }
    }

    override fun clearRoamingLog() {
        synchronized(roamLog) { roamLog.clear() }
        _state.update { it.copy(roamEvents = emptyList(), statusBanner = "Historie přepínání Mesh uzlů vymazána") }
    }

    override fun selectZigbeeChannel(channel: Int) {
        val ch = channel.coerceIn(11, 26)
        prefs.edit().putInt("selected_zb_ch", ch).apply()
        _state.update { it.copy(selectedZigbeeChannel = ch) }
    }

    override fun setInternetPingTarget(host: String) {
        val clean = host.trim().ifBlank { "1.1.1.1" }
        prefs.edit().putString("ping_target", clean).apply()
        resetPingBuffers(onlyInternet = true, newTarget = clean)
    }

    override fun toggleDualPing() {
        if (dualPingJob?.isActive == true) {
            dualPingJob?.cancel()
            dualPingJob = null
            _state.update { it.copy(isDualPingRunning = false, statusBanner = "Kontinuální ping zastaven") }
        } else {
            startDualPingLoop()
        }
    }

    override fun resetPingStats() {
        resetPingBuffers(onlyInternet = false, newTarget = _state.value.internetPingTarget)
    }

    private fun resetPingBuffers(onlyInternet: Boolean, newTarget: String) {
        synchronized(netPingHistory) {
            netPingHistory.clear()
            netSent = 0
            netReceived = 0
            netMin = null
            netMax = null
        }
        if (!onlyInternet) {
            synchronized(gwPingHistory) {
                gwPingHistory.clear()
                gwSent = 0
                gwReceived = 0
                gwMin = null
                gwMax = null
            }
        }
        _state.update { old ->
            val gwHost = old.activeWifi?.gatewayIp ?: "---"
            old.copy(
                internetPingTarget = newTarget,
                gatewayPing = if (onlyInternet) old.gatewayPing else PingStatsSnapshot(gwHost, "Lokální brána (Router)"),
                internetPing = PingStatsSnapshot(newTarget, "Internet ($newTarget)")
            )
        }
    }

    private fun startDualPingLoop() {
        dualPingJob?.cancel()
        dualPingJob = scope.launch {
            _state.update { it.copy(isDualPingRunning = true, statusBanner = "Běží souběžný ping na router i internet...") }
            while (isActive) {
                val gwHost = _state.value.activeWifi?.gatewayIp?.takeIf { it != "---" && it.isNotBlank() }
                val netHost = _state.value.internetPingTarget.ifBlank { "1.1.1.1" }

                val gwDeferred = async(Dispatchers.IO) {
                    if (gwHost != null) measureHostLatencyMs(gwHost, isLocalGateway = true) else null
                }
                val netDeferred = async(Dispatchers.IO) {
                    measureHostLatencyMs(netHost, isLocalGateway = false)
                }

                val gwMs = gwDeferred.await()
                val netMs = netDeferred.await()

                val gwSnapshot = if (gwHost != null) {
                    updateRollingPingStats(
                        host = gwHost,
                        label = "Lokální brána (Router)",
                        sampleMs = gwMs,
                        isGateway = true
                    )
                } else {
                    PingStatsSnapshot("---", "Lokální brána (jen na Wi-Fi)")
                }

                val netSnapshot = updateRollingPingStats(
                    host = netHost,
                    label = "Internet ($netHost)",
                    sampleMs = netMs,
                    isGateway = false
                )

                _state.update {
                    it.copy(
                        gatewayPing = gwSnapshot,
                        internetPing = netSnapshot
                    )
                }
                delay(1000L)
            }
        }
    }

    private fun updateRollingPingStats(
        host: String,
        label: String,
        sampleMs: Float?,
        isGateway: Boolean
    ): PingStatsSnapshot {
        val deque = if (isGateway) gwPingHistory else netPingHistory
        synchronized(deque) {
            if (isGateway) {
                gwSent++
                if (sampleMs != null) {
                    gwReceived++
                    gwMin = gwMin?.let { minOf(it, sampleMs) } ?: sampleMs
                    gwMax = gwMax?.let { maxOf(it, sampleMs) } ?: sampleMs
                }
            } else {
                netSent++
                if (sampleMs != null) {
                    netReceived++
                    netMin = netMin?.let { minOf(it, sampleMs) } ?: sampleMs
                    netMax = netMax?.let { maxOf(it, sampleMs) } ?: sampleMs
                }
            }

            deque.addLast(sampleMs)
            while (deque.size > 40) {
                deque.removeFirst()
            }

            val samplesCopy = deque.toList()
            val valid = samplesCopy.filterNotNull()
            val avg = if (valid.isNotEmpty()) valid.sum() / valid.size.toFloat() else null
            val jitter = computeJitter(valid)
            val sent = if (isGateway) gwSent else netSent
            val rec = if (isGateway) gwReceived else netReceived
            val loss = if (sent > 0) (((sent - rec) * 100f) / sent.toFloat()).roundToInt().coerceIn(0, 100) else 0

            return PingStatsSnapshot(
                targetHost = host,
                targetLabel = label,
                sentCount = sent,
                receivedCount = rec,
                lossPercent = loss,
                lastMs = sampleMs,
                avgMs = avg,
                minMs = if (isGateway) gwMin else netMin,
                maxMs = if (isGateway) gwMax else netMax,
                jitterMs = jitter,
                recentSamplesMs = samplesCopy
            )
        }
    }

    private fun computeJitter(validSamples: List<Float>): Float? {
        if (validSamples.size < 2) return null
        var diffSum = 0f
        for (i in 1 until validSamples.size) {
            diffSum += abs(validSamples[i] - validSamples[i - 1])
        }
        return diffSum / (validSamples.size - 1).toFloat()
    }

    private suspend fun measureHostLatencyMs(rawHost: String, isLocalGateway: Boolean): Float? {
        val cleanHost = rawHost.trim().replace(Regex("[^a-zA-Z0-9.:-]"), "")
        if (cleanHost.isEmpty()) return null

        // 1. Nativni ICMP ping pro presnou sub-ms odezvu na routeru i internetu
        val icmpMs = withTimeoutOrNull(1400L) {
            withContext(Dispatchers.IO) {
                var proc: Process? = null
                try {
                    proc = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "1", cleanHost)
                        .redirectErrorStream(true)
                        .start()
                    val output = proc.inputStream.bufferedReader().use { it.readText() }
                    val exit = proc.waitFor()
                    if (exit == 0) {
                        val m = Regex("time=([0-9]+(?:\\.[0-9]+)?)").find(output)
                        m?.groupValues?.getOrNull(1)?.toFloatOrNull()
                    } else {
                        null
                    }
                } catch (_: Throwable) {
                    null
                } finally {
                    runCatching { proc?.destroy() }
                }
            }
        }
        if (icmpMs != null) return icmpMs

        // 2. TCP fallback, pokud sit blokuje ICMP pakety
        val ports = if (isLocalGateway) listOf(80, 443, 53) else listOf(443, 53, 80)
        for (port in ports) {
            val tcpMs = withContext(Dispatchers.IO) {
                runCatching {
                    val startNs = System.nanoTime()
                    Socket().use { sock ->
                        sock.connect(InetSocketAddress(cleanHost, port), 1100)
                    }
                    (System.nanoTime() - startNs) / 1_000_000f
                }.getOrNull()
            }
            if (tcpMs != null) return tcpMs
        }
        return null
    }

    override fun startSpeedTest(connectionHint: String) {
        if (speedTestJob?.isActive == true) return
        speedTestJob = scope.launch {
            try {
                _state.update {
                    it.copy(
                        speedPhase = SpeedTestPhase.PING,
                        speedProgress = 0.04f,
                        liveGaugeMbps = 0.0,
                        downloadMbps = null,
                        uploadMbps = null,
                        speedIdlePingMs = null,
                        speedLoadedPingMs = null,
                        speedJitterMs = null,
                        speedCurveSamples = emptyList(),
                        statusBanner = "Spouštím měření odezvy a rychlosti (Cloudflare)..."
                    )
                }

                // Faze 1: Klidovy Ping + Jitter (6 vzorku)
                val pingSamples = ArrayList<Float>(6)
                repeat(6) { idx ->
                    val ms = measureHostLatencyMs("1.1.1.1", isLocalGateway = false)
                        ?: measureCloudflareHttpRttMs()
                    if (ms != null) pingSamples.add(ms)
                    _state.update { s ->
                        s.copy(speedProgress = 0.04f + (idx + 1) * 0.02f)
                    }
                    delay(140L)
                }
                if (pingSamples.isEmpty()) {
                    _state.update {
                        it.copy(
                            speedPhase = SpeedTestPhase.ERROR,
                            speedProgress = 0f,
                            statusBanner = "Nelze navázat spojení se serverem – zkontroluj internet."
                        )
                    }
                    return@launch
                }

                val idlePing = pingSamples.minOrNull() ?: pingSamples.average().toFloat()
                val jitter = computeJitter(pingSamples) ?: 0.5f

                // Faze 2: Download (4 paralelni proudy po dobu 7 sekund + mereni loaded pingu)
                _state.update {
                    it.copy(
                        speedPhase = SpeedTestPhase.DOWNLOAD,
                        speedIdlePingMs = idlePing,
                        speedJitterMs = jitter,
                        speedProgress = 0.18f,
                        speedCurveSamples = emptyList()
                    )
                }

                val dlDurationMs = 7000L
                val dlWarmupMs = 450L
                val dlTotalBytes = AtomicLong(0L)
                val dlSteadyBytes = AtomicLong(0L)
                val loadedPings = ArrayList<Float>()
                val dlCurve = ArrayList<Double>()
                val dlStartMs = System.currentTimeMillis()
                val dlDeadlineMs = dlStartMs + dlDurationMs

                coroutineScope {
                    val workers = (0 until 4).map { workerIdx ->
                        async(Dispatchers.IO) {
                            val buf = ByteArray(65536)
                            while (isActive && System.currentTimeMillis() < dlDeadlineMs) {
                                runCatching {
                                    val nonce = Random.nextLong(100_000L, 9_999_999L)
                                    val url = URL("https://speed.cloudflare.com/__down?bytes=25000000&r=${workerIdx}_$nonce")
                                    val conn = (url.openConnection() as HttpURLConnection).apply {
                                        connectTimeout = 3500
                                        readTimeout = 3500
                                        useCaches = false
                                        setRequestProperty("Accept-Encoding", "identity")
                                        setRequestProperty("User-Agent", "BTSRadarPM/1.1")
                                    }
                                    conn.inputStream.use { input ->
                                        while (isActive && System.currentTimeMillis() < dlDeadlineMs) {
                                            val n = input.read(buf)
                                            if (n <= 0) break
                                            dlTotalBytes.addAndGet(n.toLong())
                                            if (System.currentTimeMillis() - dlStartMs >= dlWarmupMs) {
                                                dlSteadyBytes.addAndGet(n.toLong())
                                            }
                                        }
                                    }
                                    conn.disconnect()
                                }
                            }
                        }
                    }

                    val loadedPingWorker = async(Dispatchers.IO) {
                        delay(900L)
                        while (isActive && System.currentTimeMillis() < dlDeadlineMs - 400L) {
                            measureHostLatencyMs("1.1.1.1", isLocalGateway = false)?.let {
                                synchronized(loadedPings) { loadedPings.add(it) }
                            }
                            delay(1100L)
                        }
                    }

                    var prevBytes = 0L
                    var prevTs = dlStartMs
                    while (isActive && System.currentTimeMillis() < dlDeadlineMs) {
                        delay(220L)
                        val now = System.currentTimeMillis()
                        val curBytes = dlTotalBytes.get()
                        val dtSec = ((now - prevTs).coerceAtLeast(1L)) / 1000.0
                        val instantMbps = ((curBytes - prevBytes) * 8.0) / (dtSec * 1_000_000.0)
                        prevBytes = curBytes
                        prevTs = now

                        dlCurve.add(instantMbps.coerceAtLeast(0.0))
                        val frac = ((now - dlStartMs).toFloat() / dlDurationMs.toFloat()).coerceIn(0f, 1f)
                        val lPingAvg = synchronized(loadedPings) {
                            if (loadedPings.isNotEmpty()) loadedPings.average().toFloat() else null
                        }
                        _state.update {
                            it.copy(
                                liveGaugeMbps = instantMbps.coerceAtLeast(0.0),
                                speedProgress = 0.18f + frac * 0.42f,
                                speedLoadedPingMs = lPingAvg,
                                speedCurveSamples = dlCurve.toList()
                            )
                        }
                    }

                    workers.forEach { it.cancel() }
                    loadedPingWorker.cancel()
                }

                val dlElapsedSteadySec = ((System.currentTimeMillis() - (dlStartMs + dlWarmupMs)).coerceAtLeast(500L)) / 1000.0
                val finalDownloadMbps = if (dlSteadyBytes.get() > 0L) {
                    (dlSteadyBytes.get() * 8.0) / (dlElapsedSteadySec * 1_000_000.0)
                } else {
                    (dlTotalBytes.get() * 8.0) / (7.0 * 1_000_000.0)
                }

                if (dlTotalBytes.get() <= 0L) {
                    _state.update {
                        it.copy(
                            speedPhase = SpeedTestPhase.ERROR,
                            speedProgress = 0f,
                            statusBanner = "Stahování dat selhalo – server neodpověděl."
                        )
                    }
                    return@launch
                }

                // Faze 3: Upload (3 paralelni proudy po dobu 6 sekund s potvrzenym HTTP 200)
                val ulCurve = ArrayList<Double>()
                _state.update {
                    it.copy(
                        speedPhase = SpeedTestPhase.UPLOAD,
                        downloadMbps = finalDownloadMbps,
                        liveGaugeMbps = 0.0,
                        speedProgress = 0.62f,
                        speedCurveSamples = emptyList()
                    )
                }

                val ulDurationMs = 6000L
                val ulConfirmedBytes = AtomicLong(0L)
                val ulStreamedBytes = AtomicLong(0L)
                val ulStartMs = System.currentTimeMillis()
                val ulDeadlineMs = ulStartMs + ulDurationMs
                val initialChunkSize = if (finalDownloadMbps >= 40.0) 524_288 else 262_144
                val sharedPayload = ByteArray(1_048_576) { (it and 0x7F).toByte() }

                coroutineScope {
                    val ulWorkers = (0 until 3).map {
                        async(Dispatchers.IO) {
                            var chunkSize = initialChunkSize
                            while (isActive && System.currentTimeMillis() < ulDeadlineMs) {
                                runCatching {
                                    val reqStart = System.currentTimeMillis()
                                    val conn = (URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection).apply {
                                        connectTimeout = 3500
                                        readTimeout = 4000
                                        requestMethod = "POST"
                                        doOutput = true
                                        useCaches = false
                                        setFixedLengthStreamingMode(chunkSize)
                                        setRequestProperty("Content-Type", "application/octet-stream")
                                        setRequestProperty("User-Agent", "BTSRadarPM/1.1")
                                    }
                                    conn.outputStream.use { out ->
                                        var offset = 0
                                        val step = 32768
                                        while (offset < chunkSize && isActive) {
                                            val len = minOf(step, chunkSize - offset)
                                            out.write(sharedPayload, offset, len)
                                            offset += len
                                            ulStreamedBytes.addAndGet(len.toLong())
                                        }
                                        out.flush()
                                    }
                                    val code = conn.responseCode
                                    conn.disconnect()
                                    if (code in 200..299) {
                                        ulConfirmedBytes.addAndGet(chunkSize.toLong())
                                        val took = System.currentTimeMillis() - reqStart
                                        if (took < 180L && chunkSize < 1_048_576) {
                                            chunkSize = 1_048_576
                                        }
                                    }
                                }
                            }
                        }
                    }

                    var prevBytes = 0L
                    var prevTs = ulStartMs
                    while (isActive && System.currentTimeMillis() < ulDeadlineMs) {
                        delay(220L)
                        val now = System.currentTimeMillis()
                        val curBytes = maxOf(ulConfirmedBytes.get(), (ulStreamedBytes.get() * 0.85).toLong())
                        val dtSec = ((now - prevTs).coerceAtLeast(1L)) / 1000.0
                        val instantMbps = ((curBytes - prevBytes).coerceAtLeast(0L) * 8.0) / (dtSec * 1_000_000.0)
                        prevBytes = curBytes
                        prevTs = now

                        ulCurve.add(instantMbps)
                        val frac = ((now - ulStartMs).toFloat() / ulDurationMs.toFloat()).coerceIn(0f, 1f)
                        _state.update {
                            it.copy(
                                liveGaugeMbps = instantMbps,
                                speedProgress = 0.62f + frac * 0.36f,
                                speedCurveSamples = ulCurve.toList()
                            )
                        }
                    }

                    ulWorkers.awaitAll()
                }

                val ulTotalSec = ((System.currentTimeMillis() - ulStartMs).coerceAtLeast(800L)) / 1000.0
                val effectiveUlBytes = if (ulConfirmedBytes.get() > 0L) ulConfirmedBytes.get() else (ulStreamedBytes.get() / 2L)
                val finalUploadMbps = (effectiveUlBytes * 8.0) / (ulTotalSec * 1_000_000.0)
                val finalLoadedPing = synchronized(loadedPings) {
                    if (loadedPings.isNotEmpty()) loadedPings.average().toFloat() else null
                }

                val activeWifi = _state.value.activeWifi
                val connDesc = if (_state.value.isWifiConnected && activeWifi != null) {
                    val node = activeWifi.customAlias.ifBlank { activeWifi.ssid }
                    "Wi-Fi: $node (${activeWifi.band.shortLabel} K${activeWifi.channelNumber})"
                } else {
                    connectionHint.ifBlank { "Mobilní data" }
                }

                val stamp = SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date())
                val record = SpeedTestRecord(
                    timeText = stamp,
                    connectionSummary = connDesc,
                    downloadMbps = finalDownloadMbps,
                    uploadMbps = finalUploadMbps,
                    idlePingMs = idlePing,
                    loadedPingMs = finalLoadedPing,
                    jitterMs = jitter
                )
                speedRecords.add(0, record)
                while (speedRecords.size > 15) {
                    speedRecords.removeAt(speedRecords.lastIndex)
                }
                persistSpeedHistory()

                _state.update {
                    it.copy(
                        speedPhase = SpeedTestPhase.FINISHED,
                        speedProgress = 1f,
                        liveGaugeMbps = finalDownloadMbps,
                        downloadMbps = finalDownloadMbps,
                        uploadMbps = finalUploadMbps,
                        speedLoadedPingMs = finalLoadedPing,
                        speedHistory = speedRecords.toList(),
                        statusBanner = "Speedtest hotov: ↓ ${WifiSpectrumCalculator.formatMbps(finalDownloadMbps)} Mbps • ↑ ${WifiSpectrumCalculator.formatMbps(finalUploadMbps)} Mbps"
                    )
                }
            } catch (_: CancellationException) {
                _state.update {
                    it.copy(
                        speedPhase = SpeedTestPhase.IDLE,
                        speedProgress = 0f,
                        liveGaugeMbps = 0.0,
                        statusBanner = "Měření rychlosti zastaveno"
                    )
                }
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        speedPhase = SpeedTestPhase.ERROR,
                        speedProgress = 0f,
                        statusBanner = "Chyba měření: ${e.message ?: "spojení přerušeno"}"
                    )
                }
            }
        }
    }

    private suspend fun measureCloudflareHttpRttMs(): Float? = withContext(Dispatchers.IO) {
        runCatching {
            val t0 = System.nanoTime()
            val conn = (URL("https://speed.cloudflare.com/__down?bytes=0").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                useCaches = false
            }
            conn.responseCode
            conn.disconnect()
            (System.nanoTime() - t0) / 1_000_000f
        }.getOrNull()
    }

    override fun stopSpeedTest() {
        speedTestJob?.cancel()
        speedTestJob = null
    }

    override fun clearSpeedHistory() {
        speedRecords.clear()
        prefs.edit().remove("speed_history_json").apply()
        _state.update { it.copy(speedHistory = emptyList(), statusBanner = "Historie Speedtestů vymazána") }
    }

    fun dispose() {
        monitorJob?.cancel()
        dualPingJob?.cancel()
        speedTestJob?.cancel()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(scanReceiver) }
            receiverRegistered = false
        }
        networkCallbackObj?.let { cb ->
            runCatching { connectivityManager.unregisterNetworkCallback(cb) }
        }
        networkCallbackObj = null
    }
}

@Composable
actual fun rememberWifiNetController(): WifiNetController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { AndroidWifiNetController(context.applicationContext, scope) }

    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }

    return controller
}
