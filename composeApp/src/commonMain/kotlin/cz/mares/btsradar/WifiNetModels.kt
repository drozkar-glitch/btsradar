package cz.mares.btsradar

import kotlin.math.abs
import kotlin.math.roundToInt

enum class WifiBandType(val label: String, val shortLabel: String) {
    BAND_24("2,4 GHz", "2.4G"),
    BAND_5("5 GHz", "5G"),
    BAND_6("6 GHz", "6G"),
    UNKNOWN("Neznámé pásmo", "---")
}

data class MloLinkItem(
    val linkId: Int,
    val bandLabel: String,
    val channel: Int,
    val bssid: String,
    val rssiDbm: Int,
    val txMbps: Int?,
    val rxMbps: Int?,
    val isActive: Boolean
)

data class ActiveWifiDetails(
    val ssid: String,
    val bssid: String,
    val customAlias: String,
    val rssiDbm: Int,
    val signalPercent: Int,
    val standardLabel: String,
    val band: WifiBandType,
    val frequencyMhz: Int,
    val channelNumber: Int,
    val channelWidthMhz: Int,
    val linkSpeedMbps: Int,
    val txLinkSpeedMbps: Int?,
    val rxLinkSpeedMbps: Int?,
    val maxTxMbps: Int?,
    val maxRxMbps: Int?,
    val securityLabel: String,
    val ipv4Address: String,
    val ipv6Address: String,
    val gatewayIp: String,
    val dnsServers: String,
    val interfaceName: String,
    val mldMacAddress: String,
    val mloLinks: List<MloLinkItem>
)

data class ScannedWifiAp(
    val ssid: String,
    val bssid: String,
    val alias: String,
    val rssiDbm: Int,
    val band: WifiBandType,
    val frequencyMhz: Int,
    val centerFreqMhz: Int,
    val channelNumber: Int,
    val channelWidthMhz: Int,
    val standardLabel: String,
    val securityLabel: String,
    val isConnectedBssid: Boolean,
    val isSameMeshSsid: Boolean
)

data class WifiRssiSample(
    val timestampMs: Long,
    val rssiDbm: Int,
    val bssid: String,
    val band: WifiBandType
)

data class WifiRoamEvent(
    val timeText: String,
    val eventType: String,
    val fromNode: String,
    val toNode: String,
    val details: String
)

data class ZigbeeChannelScore(
    val channel: Int,
    val centerFreqMhz: Int,
    val loadPercent: Int,
    val strongestInterfererDbm: Int?,
    val overlappingApNames: List<String>,
    val isSidebandGap: Boolean
)

data class PingStatsSnapshot(
    val targetHost: String,
    val targetLabel: String,
    val sentCount: Int = 0,
    val receivedCount: Int = 0,
    val lossPercent: Int = 0,
    val lastMs: Float? = null,
    val avgMs: Float? = null,
    val minMs: Float? = null,
    val maxMs: Float? = null,
    val jitterMs: Float? = null,
    val recentSamplesMs: List<Float?> = emptyList()
)

enum class SpeedTestPhase(val label: String) {
    IDLE("Připraven k měření"),
    PING("Měřím klidovou odezvu a jitter..."),
    DOWNLOAD("Měřím rychlost stahování (Download)..."),
    UPLOAD("Měřím rychlost nahrávání (Upload)..."),
    FINISHED("Měření rychlosti dokončeno"),
    ERROR("Test se nezdařil (zkontroluj připojení)")
}

data class SpeedTestRecord(
    val timeText: String,
    val connectionSummary: String,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val idlePingMs: Float,
    val loadedPingMs: Float?,
    val jitterMs: Float
)

data class WifiNetState(
    val isWifiEnabled: Boolean = true,
    val isWifiConnected: Boolean = false,
    val activeWifi: ActiveWifiDetails? = null,
    val scannedAps: List<ScannedWifiAp> = emptyList(),
    val wifiHistory: List<WifiRssiSample> = emptyList(),
    val roamEvents: List<WifiRoamEvent> = emptyList(),
    val bssidAliases: kotlin.collections.Map<String, String> = emptyMap(),
    val selectedZigbeeChannel: Int = 20,
    val zigbeeScores: List<ZigbeeChannelScore> = emptyList(),
    val recommendedZigbeeChannels: List<Int> = listOf(25, 20, 15),
    val lastScanTimeText: String = "--:--:--",
    val scanThrottledHint: Boolean = false,
    val isDualPingRunning: Boolean = false,
    val internetPingTarget: String = "1.1.1.1",
    val gatewayPing: PingStatsSnapshot = PingStatsSnapshot(
        targetHost = "---",
        targetLabel = "Lokální brána (Router)"
    ),
    val internetPing: PingStatsSnapshot = PingStatsSnapshot(
        targetHost = "1.1.1.1",
        targetLabel = "Internet"
    ),
    val speedPhase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val speedProgress: Float = 0f,
    val liveGaugeMbps: Double = 0.0,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val speedIdlePingMs: Float? = null,
    val speedLoadedPingMs: Float? = null,
    val speedJitterMs: Float? = null,
    val speedCurveSamples: List<Double> = emptyList(),
    val speedHistory: List<SpeedTestRecord> = emptyList(),
    val statusBanner: String = ""
)

object WifiSpectrumCalculator {

    fun freqToBand(freqMhz: Int): WifiBandType {
        return when (freqMhz) {
            in 2400..2500 -> WifiBandType.BAND_24
            in 4900..5900 -> WifiBandType.BAND_5
            in 5925..7125 -> WifiBandType.BAND_6
            else -> WifiBandType.UNKNOWN
        }
    }

    fun freqToChannel(freqMhz: Int): Int {
        return when (freqMhz) {
            2484 -> 14
            in 2412..2472 -> (freqMhz - 2407) / 5
            in 5000..5895 -> (freqMhz - 5000) / 5
            in 5955..7115 -> (freqMhz - 5950) / 5
            else -> 0
        }
    }

    fun rssiToPercent(rssiDbm: Int): Int {
        return when {
            rssiDbm >= -50 -> 100
            rssiDbm <= -100 -> 0
            else -> ((rssiDbm + 100) * 2).coerceIn(0, 100)
        }
    }

    fun evaluateWifiQuality(rssiDbm: Int): SignalQualityLevel {
        return when {
            rssiDbm >= -60 -> SignalQualityLevel.EXCELLENT
            rssiDbm >= -70 -> SignalQualityLevel.GOOD
            rssiDbm >= -80 -> SignalQualityLevel.FAIR
            rssiDbm > -115 -> SignalQualityLevel.POOR
            else -> SignalQualityLevel.NONE
        }
    }

    fun analyzeZigbeeChannels(
        scannedAps: List<ScannedWifiAp>,
        activeWifi: ActiveWifiDetails?
    ): List<ZigbeeChannelScore> {
        val aps24 = ArrayList<ScannedWifiAp>()
        aps24.addAll(scannedAps.filter { it.band == WifiBandType.BAND_24 })

        if (activeWifi != null && activeWifi.band == WifiBandType.BAND_24) {
            val alreadyPresent = aps24.any { it.bssid.equals(activeWifi.bssid, ignoreCase = true) }
            if (!alreadyPresent && activeWifi.frequencyMhz in 2400..2500) {
                aps24.add(
                    ScannedWifiAp(
                        ssid = activeWifi.ssid,
                        bssid = activeWifi.bssid,
                        alias = activeWifi.customAlias,
                        rssiDbm = activeWifi.rssiDbm,
                        band = WifiBandType.BAND_24,
                        frequencyMhz = activeWifi.frequencyMhz,
                        centerFreqMhz = activeWifi.frequencyMhz,
                        channelNumber = activeWifi.channelNumber,
                        channelWidthMhz = activeWifi.channelWidthMhz,
                        standardLabel = activeWifi.standardLabel,
                        securityLabel = activeWifi.securityLabel,
                        isConnectedBssid = true,
                        isSameMeshSsid = true
                    )
                )
            }
        }

        val out = ArrayList<ZigbeeChannelScore>(16)
        for (zbCh in 11..26) {
            val zbFreq = 2405 + 5 * (zbCh - 11)
            var loadSum = 0.0
            var strongestDbm: Int? = null
            val overlappingNames = ArrayList<String>()

            for (ap in aps24) {
                val centerMhz = if (ap.channelWidthMhz >= 40 && ap.centerFreqMhz in 2412..2472) {
                    ap.centerFreqMhz
                } else {
                    ap.frequencyMhz
                }
                val halfSpan = if (ap.channelWidthMhz >= 40) 19.0 else 10.5
                val distMhz = abs(zbFreq - centerMhz).toDouble()

                if (distMhz <= halfSpan + 2.0) {
                    val envelope = when {
                        distMhz <= halfSpan - 2.0 -> 1.0
                        else -> ((halfSpan + 2.5 - distMhz) / 4.5).coerceIn(0.18, 1.0)
                    }
                    val powerNorm = ((ap.rssiDbm + 100).coerceIn(4, 62)) / 58.0
                    loadSum += powerNorm * envelope * 82.0

                    if (strongestDbm == null || ap.rssiDbm > strongestDbm) {
                        strongestDbm = ap.rssiDbm
                    }
                    val labelName = ap.alias.ifBlank { ap.ssid.ifBlank { ap.bssid } }
                    val itemDesc = "$labelName (K${ap.channelNumber}, ${ap.rssiDbm} dBm)"
                    if (overlappingNames.size < 6 && !overlappingNames.contains(itemDesc)) {
                        overlappingNames.add(itemDesc)
                    }
                }
            }

            out.add(
                ZigbeeChannelScore(
                    channel = zbCh,
                    centerFreqMhz = zbFreq,
                    loadPercent = loadSum.roundToInt().coerceIn(0, 100),
                    strongestInterfererDbm = strongestDbm,
                    overlappingApNames = overlappingNames,
                    isSidebandGap = zbCh == 15 || zbCh == 20 || zbCh == 25 || zbCh == 26
                )
            )
        }
        return out
    }

    fun pickBestZigbeeChannels(scores: List<ZigbeeChannelScore>): List<Int> {
        if (scores.isEmpty()) return listOf(25, 20, 15)
        return scores.sortedWith(
            compareBy<ZigbeeChannelScore> { s ->
                val bonus = when (s.channel) {
                    15, 20, 25 -> -4
                    26 -> -1
                    11 -> 0
                    else -> 3
                }
                s.loadPercent + bonus
            }.thenBy { it.strongestInterfererDbm ?: -120 }
        ).take(3).map { it.channel }
    }

    fun formatMbps(value: Double?): String {
        if (value == null || value < 0.0) return "---"
        return if (value >= 100.0) {
            "${value.roundToInt()}"
        } else {
            val tenths = (value * 10.0).roundToInt()
            "${tenths / 10}.${tenths % 10}"
        }
    }

    fun formatMs(value: Float?): String {
        if (value == null || value < 0f) return "---"
        return if (value >= 20f) {
            "${value.roundToInt()} ms"
        } else {
            val tenths = (value * 10f).roundToInt()
            "${tenths / 10}.${tenths % 10} ms"
        }
    }
}
