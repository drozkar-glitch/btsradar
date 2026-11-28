package cz.mares.btsradar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun WifiAndZigbeeTab(
    state: WifiNetState,
    controller: WifiNetController
) {
    var subSection by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Prepinac 3 podsekci Wi-Fi & Zigbee
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubTabPill(
                title = "Aktivní & Mesh",
                selected = subSection == 0,
                onClick = { subSection = 0 },
                modifier = Modifier.weight(1f)
            )
            SubTabPill(
                title = "Zigbee 11–26",
                selected = subSection == 1,
                onClick = { subSection = 1 },
                modifier = Modifier.weight(1f)
            )
            SubTabPill(
                title = "Skener (${state.scannedAps.size})",
                selected = subSection == 2,
                onClick = { subSection = 2 },
                modifier = Modifier.weight(1f)
            )
        }

        when (subSection) {
            0 -> ActiveWifiMeshSection(state = state, controller = controller)
            1 -> ZigbeeInterferenceSection(state = state, controller = controller)
            else -> WifiScannerSection(state = state, controller = controller)
        }

        FooterCopyright()
    }
}

@Composable
private fun ActiveWifiMeshSection(
    state: WifiNetState,
    controller: WifiNetController
) {
    val wifi = state.activeWifi

    if (!state.isWifiEnabled) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Wi-Fi adaptér je vypnutý",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Zapni Wi-Fi v telefonu pro živou diagnostiku signálu, šířky kanálu, MLO linek a Mesh uzlů.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    if (!state.isWifiConnected || wifi == null) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Nepřipojeno k Wi-Fi síti",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Telefon momentálně není spojen s žádným Wi-Fi přístupovým bodem. Můžeš ale přejít do záložky Zigbee 11–26 nebo Skener a měřit okolní spektrum.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { controller.triggerWifiScan() },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Skenovat okolní Wi-Fi")
                }
            }
        }
        return
    }

    val quality = WifiSpectrumCalculator.evaluateWifiQuality(wifi.rssiDbm)
    val sigColor = signalColorFor(quality)
    val barProgress = (wifi.signalPercent / 100f).coerceIn(0.05f, 1f)
    var aliasDraft by remember(wifi.bssid, wifi.customAlias) { mutableStateOf(wifi.customAlias) }

    // Hlavni karta aktivni Wi-Fi
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = wifi.ssid,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    val nodeTitle = if (wifi.customAlias.isNotBlank()) {
                        "${wifi.customAlias} (${wifi.bssid})"
                    } else {
                        "BSSID ${wifi.bssid}"
                    }
                    Text(
                        text = nodeTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(
                    color = sigColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "${quality.label} (${wifi.signalPercent} %)",
                        color = sigColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${wifi.rssiDbm}",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = sigColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "dBm (RSSI)",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = wifi.standardLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${wifi.band.label} • Kanál ${wifi.channelNumber} (${wifi.channelWidthMhz} MHz)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barProgress)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(sigColor)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WifiMetricBox(
                    title = "TX Link",
                    value = "${wifi.txLinkSpeedMbps ?: wifi.linkSpeedMbps} Mbps",
                    modifier = Modifier.weight(1f)
                )
                WifiMetricBox(
                    title = "RX Link",
                    value = wifi.rxLinkSpeedMbps?.let { "$it Mbps" } ?: "${wifi.linkSpeedMbps} Mbps",
                    modifier = Modifier.weight(1f)
                )
                WifiMetricBox(
                    title = "Frekvence",
                    value = "${wifi.frequencyMhz} MHz",
                    modifier = Modifier.weight(1f)
                )
                WifiMetricBox(
                    title = "Šifrování",
                    value = wifi.securityLabel,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Wi-Fi 7 MLO linky (pokud AP i telefon jedou Multi-Link)
    if (wifi.mloLinks.isNotEmpty()) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Wi-Fi 7 Multi-Link Operation (MLO)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (wifi.mldMacAddress.isNotBlank()) {
                    Text(
                        text = "MLD MAC: ${wifi.mldMacAddress}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                wifi.mloLinks.forEach { link ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${link.bandLabel} • K${link.channel} (${if (link.isActive) "aktivní" else "záložní"})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${link.rssiDbm} dBm • ${link.txMbps ?: "---"} Mbps",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // Sitove adresy a pojmenovani aktualniho Mesh uzlu
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Síťové rozhraní a identifikace Mesh uzlu",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            WifiInfoRow("Lokální IPv4", wifi.ipv4Address)
            WifiInfoRow("Výchozí brána (Router)", wifi.gatewayIp)
            WifiInfoRow("DNS servery", wifi.dnsServers)
            WifiInfoRow("IPv6 adresa", wifi.ipv6Address)
            WifiInfoRow("Rozhraní / Max PHY", "${wifi.interfaceName} • max ${wifi.maxRxMbps ?: wifi.linkSpeedMbps} Mbps")

            if (wifi.bssid.length == 17) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "Vlastní název této jednotky (${wifi.bssid})",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = aliasDraft,
                        onValueChange = { aliasDraft = it },
                        placeholder = { Text("např. Deco Obývák / Chodba") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { controller.saveBssidAlias(wifi.bssid, aliasDraft) },
                        modifier = Modifier.heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Uložit")
                    }
                }
            }
        }
    }

    // 60s graf kolisani Wi-Fi signalu
    WifiRssiChartCard(samples = state.wifiHistory)

    // Log prepinani Mesh uzlu (Roaming)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mesh Roaming a přeladění (${state.roamEvents.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (state.roamEvents.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { controller.clearRoamingLog() },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Smazat")
                    }
                }
            }

            if (state.roamEvents.isEmpty()) {
                Text(
                    text = "Zatím bez přepnutí. Při přechodu po bytě zde uvidíš přesný čas a dBm, kdy telefon přeladil na jinou Mesh jednotku nebo pásmo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.roamEvents.take(12).forEach { ev ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = ev.eventType,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = ev.timeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (ev.fromNode == "---") "Uzel: ${ev.toNode}" else "${ev.fromNode}  →  ${ev.toNode}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = ev.details,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZigbeeInterferenceSection(
    state: WifiNetState,
    controller: WifiNetController
) {
    val selectedScore = state.zigbeeScores.firstOrNull { it.channel == state.selectedZigbeeChannel }
        ?: state.zigbeeScores.firstOrNull()

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Spektrum 2,4 GHz: Wi-Fi vs. Zigbee",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Sken ze vzduchu (${state.lastScanTimeText}) • IEEE 802.15.4 kanály 11–26",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { controller.triggerWifiScan() },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Přeměřit")
                }
            }

            Surface(
                color = Color(0xFF10B981).copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Nejčistší Zigbee kanály v tomto místě: ${state.recommendedZigbeeChannels.joinToString(", ") { "K$it" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            // Sloupcovy graf ruseni pro kanaly 11 az 26
            val primaryCol = MaterialTheme.colorScheme.primary
            val baseTrack = MaterialTheme.colorScheme.surface
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
            ) {
                val scores = state.zigbeeScores
                if (scores.isNotEmpty()) {
                    val slotW = size.width / scores.size.toFloat()
                    val barW = (slotW * 0.68f).coerceAtLeast(6f)
                    scores.forEachIndexed { idx, sc ->
                        val x = idx * slotW + (slotW - barW) / 2f
                        drawRoundRect(
                            color = baseTrack,
                            topLeft = Offset(x, 8f),
                            size = Size(barW, size.height - 16f),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        val frac = (sc.loadPercent / 100f).coerceIn(0.06f, 1f)
                        val barH = (size.height - 16f) * frac
                        val col = when {
                            sc.loadPercent < 25 -> Color(0xFF10B981)
                            sc.loadPercent < 55 -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }
                        drawRoundRect(
                            color = col,
                            topLeft = Offset(x, size.height - 8f - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        if (sc.channel == state.selectedZigbeeChannel) {
                            drawRoundRect(
                                color = primaryCol,
                                topLeft = Offset(x - 2f, 4f),
                                size = Size(barW + 4f, size.height - 8f),
                                cornerRadius = CornerRadius(10f, 10f),
                                style = Stroke(width = 3.5f)
                            )
                        }
                    }
                }
            }

            Text(
                text = "Klepni na číslo Zigbee kanálu (15, 20, 25 a 26 leží v mezerách mezi Wi-Fi 1 / 6 / 11):",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Dve rady tlacitek 11..18 a 19..26 (dotykova vyska 48 dp)
            listOf(11..18, 19..26).forEach { range ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    for (ch in range) {
                        val sc = state.zigbeeScores.firstOrNull { it.channel == ch }
                        val isSel = state.selectedZigbeeChannel == ch
                        val bg = when {
                            isSel -> MaterialTheme.colorScheme.primary
                            sc?.isSidebandGap == true -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                        val fg = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .clickable { controller.selectZigbeeChannel(ch) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$ch",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = fg
                                )
                                Text(
                                    text = "${sc?.loadPercent ?: 0}%",
                                    fontSize = 10.sp,
                                    color = fg.copy(alpha = 0.82f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedScore != null) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val loadColor = when {
                    selectedScore.loadPercent < 25 -> Color(0xFF10B981)
                    selectedScore.loadPercent < 55 -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF4444)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detail Zigbee kanálu ${selectedScore.channel} (${selectedScore.centerFreqMhz} MHz)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Zátěž ${selectedScore.loadPercent} %",
                        fontWeight = FontWeight.ExtraBold,
                        color = loadColor
                    )
                }

                WifiInfoRow(
                    "Pozice ve spektru",
                    if (selectedScore.isSidebandGap) "Mezera mezi Wi-Fi kanály 1 / 6 / 11 (ideální)" else "Přímý překryv s hlavním lalokem Wi-Fi"
                )
                WifiInfoRow(
                    "Nejsilnější 2,4GHz rušič",
                    selectedScore.strongestInterfererDbm?.let { "$it dBm" } ?: "Žádná rušící Wi-Fi v dosahu"
                )

                if (selectedScore.overlappingApNames.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Wi-Fi sítě zasahující do kanálu ${selectedScore.channel}:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    selectedScore.overlappingApNames.forEach { item ->
                        Text(
                            text = "• $item",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiScannerSection(
    state: WifiNetState,
    controller: WifiNetController
) {
    var bandFilter by remember { mutableIntStateOf(0) }
    var editingBssid by remember { mutableStateOf<String?>(null) }
    var aliasInput by remember { mutableStateOf("") }

    val filtered = remember(state.scannedAps, bandFilter) {
        when (bandFilter) {
            1 -> state.scannedAps.filter { it.band == WifiBandType.BAND_24 }
            2 -> state.scannedAps.filter { it.band == WifiBandType.BAND_5 }
            3 -> state.scannedAps.filter { it.band == WifiBandType.BAND_6 }
            else -> state.scannedAps
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Okolní Wi-Fi vysílače (${filtered.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Poslední sken: ${state.lastScanTimeText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { controller.triggerWifiScan() },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Skenovat")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Vše", "2,4 GHz", "5 GHz", "6 GHz").forEachIndexed { idx, label ->
                    SubTabPill(
                        title = label,
                        selected = bandFilter == idx,
                        onClick = { bandFilter = idx },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (filtered.isEmpty()) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = "V tomto pásmu zatím nebyly zachyceny žádné sítě. Klepni na Skenovat.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    } else {
        filtered.forEach { ap ->
            val q = WifiSpectrumCalculator.evaluateWifiQuality(ap.rssiDbm)
            val col = signalColorFor(q)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (editingBssid == ap.bssid) {
                                editingBssid = null
                            } else {
                                editingBssid = ap.bssid
                                aliasInput = ap.alias
                            }
                        }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val titleStr = if (ap.alias.isNotBlank()) "${ap.ssid} • ${ap.alias}" else ap.ssid
                            Text(
                                text = titleStr,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${ap.bssid} • ${ap.standardLabel} • ${ap.securityLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${ap.rssiDbm} dBm",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = col
                            )
                            Text(
                                text = "${ap.band.shortLabel} • K${ap.channelNumber} (${ap.channelWidthMhz} MHz)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (ap.isConnectedBssid || ap.isSameMeshSsid) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (ap.isConnectedBssid) {
                                BadgeTag("Aktivní spojení", Color(0xFF10B981))
                            } else if (ap.isSameMeshSsid) {
                                BadgeTag("Další Mesh uzel", MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    if (editingBssid == ap.bssid) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = aliasInput,
                                onValueChange = { aliasInput = it },
                                placeholder = { Text("Pojmenovat BSSID...") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    controller.saveBssidAlias(ap.bssid, aliasInput)
                                    editingBssid = null
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Uložit")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpeedAndPingTab(
    state: WifiNetState,
    controller: WifiNetController,
    btsState: BtsRadarState
) {
    val scroll = rememberScrollState()
    var targetDraft by remember(state.internetPingTarget) { mutableStateOf(state.internetPingTarget) }
    val isSpeedRunning = state.speedPhase == SpeedTestPhase.PING ||
        state.speedPhase == SpeedTestPhase.DOWNLOAD ||
        state.speedPhase == SpeedTestPhase.UPLOAD

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Karta Speedtestu
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Měření rychlosti (Speedtest)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = state.speedPhase.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = if (state.isWifiConnected) "Wi-Fi" else btsState.networkTypeDetail,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                SpeedometerGauge(
                    mbps = state.liveGaugeMbps,
                    phase = state.speedPhase,
                    progress = state.speedProgress
                )

                // Krivka prubehu aktualni faze mereni
                if (state.speedCurveSamples.size >= 2) {
                    val curveCol = if (state.speedPhase == SpeedTestPhase.UPLOAD) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        val maxVal = (state.speedCurveSamples.maxOrNull() ?: 10.0).coerceAtLeast(5.0)
                        val stepX = size.width / (state.speedCurveSamples.size - 1).coerceAtLeast(1)
                        val p = Path()
                        state.speedCurveSamples.forEachIndexed { idx, v ->
                            val x = idx * stepX
                            val y = size.height - ((v / maxVal).toFloat().coerceIn(0f, 1f) * (size.height - 6f)) - 3f
                            if (idx == 0) p.moveTo(x, y) else p.lineTo(x, y)
                        }
                        drawPath(p, color = curveCol, style = Stroke(width = 4f, cap = StrokeCap.Round))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WifiMetricBox(
                        title = "Download",
                        value = "${WifiSpectrumCalculator.formatMbps(state.downloadMbps)} Mbps",
                        modifier = Modifier.weight(1f)
                    )
                    WifiMetricBox(
                        title = "Upload",
                        value = "${WifiSpectrumCalculator.formatMbps(state.uploadMbps)} Mbps",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WifiMetricBox(
                        title = "Klidový Ping",
                        value = WifiSpectrumCalculator.formatMs(state.speedIdlePingMs),
                        modifier = Modifier.weight(1f)
                    )
                    WifiMetricBox(
                        title = "Ping v zátěži",
                        value = WifiSpectrumCalculator.formatMs(state.speedLoadedPingMs),
                        modifier = Modifier.weight(1f)
                    )
                    WifiMetricBox(
                        title = "Jitter",
                        value = WifiSpectrumCalculator.formatMs(state.speedJitterMs),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isSpeedRunning) {
                    OutlinedButton(
                        onClick = { controller.stopSpeedTest() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Zastavit měření", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            val hint = "${btsState.servingCells.firstOrNull()?.operatorName.orEmpty()} (${btsState.networkTypeDetail})".trim()
                            controller.startSpeedTest(hint)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Spustit Speedtest (Download + Upload)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Karta Dualniho Pingu (Router vs. Internet)
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Souběžný Ping & Jitter (Router vs. Internet)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Okamžitě odhalí, zda zpoždění vzniká doma na Wi-Fi trase k routeru, nebo až venku u operátora.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = targetDraft,
                        onValueChange = { targetDraft = it },
                        label = { Text("Cíl v internetu") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { controller.setInternetPingTarget(targetDraft) },
                        modifier = Modifier.heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Nastavit")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("1.1.1.1", "8.8.8.8", "seznam.cz", "google.cz").forEach { preset ->
                        OutlinedButton(
                            onClick = {
                                targetDraft = preset
                                controller.setInternetPingTarget(preset)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(preset, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { controller.toggleDualPing() },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(if (state.isDualPingRunning) "Zastavit Ping" else "Spustit duální Ping")
                    }
                    OutlinedButton(
                        onClick = { controller.resetPingStats() },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Vynulovat")
                    }
                }

                PingTargetSnapshotCard(snapshot = state.gatewayPing)
                PingTargetSnapshotCard(snapshot = state.internetPing)
            }
        }

        // Historie namerenych rychlosti
        if (state.speedHistory.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Historie měření (${state.speedHistory.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = { controller.clearSpeedHistory() },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Vymazat")
                        }
                    }

                    state.speedHistory.forEach { item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = item.connectionSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = item.timeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "↓ ${WifiSpectrumCalculator.formatMbps(item.downloadMbps)} Mbps  •  ↑ ${WifiSpectrumCalculator.formatMbps(item.uploadMbps)} Mbps",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Ping ${WifiSpectrumCalculator.formatMs(item.idlePingMs)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        FooterCopyright()
    }
}

@Composable
private fun SpeedometerGauge(
    mbps: Double,
    phase: SpeedTestPhase,
    progress: Float
) {
    val activeColor = when (phase) {
        SpeedTestPhase.UPLOAD -> MaterialTheme.colorScheme.secondary
        SpeedTestPhase.ERROR -> Color(0xFFEF4444)
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .size(190.dp)
            .padding(top = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)

            drawArc(
                color = trackColor,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // Nelinearni stupnice budiku do 1000 Mbps
            val norm = when {
                mbps <= 0.0 -> 0f
                mbps <= 100.0 -> (mbps / 100.0).toFloat() * 0.55f
                else -> (0.55f + ((mbps - 100.0) / 900.0).toFloat() * 0.45f).coerceAtMost(1f)
            }
            val sweep = (260f * norm).coerceIn(0f, 260f)
            if (sweep > 1f) {
                drawArc(
                    color = activeColor,
                    startAngle = 140f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = WifiSpectrumCalculator.formatMbps(mbps),
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = activeColor
            )
            Text(
                text = "Mbps",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (progress > 0f && progress < 1f) {
                Text(
                    text = "${(progress * 100f).roundToInt()} %",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PingTargetSnapshotCard(snapshot: PingStatsSnapshot) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = snapshot.targetLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Host: ${snapshot.targetHost} • Vzorky: ${snapshot.receivedCount}/${snapshot.sentCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = WifiSpectrumCalculator.formatMs(snapshot.lastMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Průměr: ${WifiSpectrumCalculator.formatMs(snapshot.avgMs)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Jitter: ${WifiSpectrumCalculator.formatMs(snapshot.jitterMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Ztráta: ${snapshot.lossPercent} %",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (snapshot.lossPercent > 0) Color(0xFFEF4444) else Color(0xFF10B981),
                    fontWeight = FontWeight.Bold
                )
            }

            if (snapshot.recentSamplesMs.isNotEmpty()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    val items = snapshot.recentSamplesMs
                    val maxMs = (items.filterNotNull().maxOrNull() ?: 50f).coerceAtLeast(20f)
                    val slotW = size.width / 40f
                    val barW = (slotW * 0.7f).coerceAtLeast(3f)

                    items.forEachIndexed { idx, sample ->
                        val x = idx * slotW
                        if (sample == null) {
                            drawRoundRect(
                                color = Color(0xFFEF4444),
                                topLeft = Offset(x, 4f),
                                size = Size(barW, size.height - 8f),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        } else {
                            val h = ((sample / maxMs).coerceIn(0.12f, 1f)) * (size.height - 6f)
                            val col = when {
                                sample < 25f -> Color(0xFF10B981)
                                sample < 70f -> Color(0xFFF59E0B)
                                else -> Color(0xFFEF4444)
                            }
                            drawRoundRect(
                                color = col,
                                topLeft = Offset(x, size.height - h),
                                size = Size(barW, h),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiRssiChartCard(samples: List<WifiRssiSample>) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val minVal = samples.minOfOrNull { it.rssiDbm }
            val maxVal = samples.maxOfOrNull { it.rssiDbm }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Vývoj Wi-Fi signálu (60 s)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (minVal != null && maxVal != null) "$minVal až $maxVal dBm" else "---",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val lineCol = MaterialTheme.colorScheme.secondary
            val gridCol = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
            ) {
                val topDbm = -35f
                val bottomDbm = -95f
                val span = topDbm - bottomDbm

                listOf(-50f, -67f, -80f).forEach { ref ->
                    val y = ((topDbm - ref) / span) * size.height
                    drawLine(gridCol, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                }

                if (samples.size >= 2) {
                    val p = Path()
                    val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
                    samples.forEachIndexed { idx, s ->
                        val cl = s.rssiDbm.toFloat().coerceIn(bottomDbm, topDbm)
                        val x = idx * stepX
                        val y = ((topDbm - cl) / span) * size.height
                        if (idx == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    drawPath(p, color = lineCol, style = Stroke(width = 5f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

@Composable
fun SubTabPill(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BadgeTag(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun WifiMetricBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WifiInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.58f)
        )
    }
}
