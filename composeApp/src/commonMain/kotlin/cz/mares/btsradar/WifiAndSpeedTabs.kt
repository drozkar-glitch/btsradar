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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubTabPill(
                title = "Aktivní",
                selected = subSection == 0,
                onClick = { subSection = 0 },
                modifier = Modifier.weight(1f)
            )
            SubTabPill(
                title = "Zigbee",
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
                    text = "Telefon momentálně není spojen s žádným Wi-Fi přístupovým bodem. Můžeš ale přejít do záložky Zigbee nebo Skener a měřit okolní spektrum.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { controller.triggerWifiScan() },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Skenovat okolní Wi-Fi", maxLines = 1, softWrap = false)
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = wifi.ssid,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = sigColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "${wifi.signalPercent}\u00A0% • ${quality.label}",
                        color = sigColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${wifi.rssiDbm}",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        color = sigColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "dBm",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = wifi.standardLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${wifi.band.shortLabel} • K${wifi.channelNumber} (${wifi.channelWidthMhz}\u00A0MHz)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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

            // Prehledna mrizka 2x2 misto 4 uzkych sloupcu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WifiMetricBox(
                    title = "TX rychlost linky",
                    value = "${wifi.txLinkSpeedMbps ?: wifi.linkSpeedMbps}\u00A0Mbps",
                    modifier = Modifier.weight(1f)
                )
                WifiMetricBox(
                    title = "RX rychlost linky",
                    value = "${wifi.rxLinkSpeedMbps ?: wifi.linkSpeedMbps}\u00A0Mbps",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WifiMetricBox(
                    title = "Frekvence",
                    value = "${wifi.frequencyMhz}\u00A0MHz",
                    modifier = Modifier.weight(1f)
                )
                WifiMetricBox(
                    title = "Zabezpečení",
                    value = wifi.securityLabel,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Wi-Fi 7 MLO linky
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${link.bandLabel} • K${link.channel} (${if (link.isActive) "aktivní" else "záložní"})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${link.rssiDbm}\u00A0dBm • ${link.txMbps ?: "---"}\u00A0Mbps",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
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
                text = "Síťové rozhraní a Mesh uzel",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            WifiInfoRow("Lokální IPv4", wifi.ipv4Address)
            WifiInfoRow("Výchozí brána", wifi.gatewayIp)
            WifiInfoRow("DNS servery", wifi.dnsServers)
            WifiInfoRow("IPv6 adresa", wifi.ipv6Address)
            WifiInfoRow("Rozhraní / Max PHY", "${wifi.interfaceName} • max ${wifi.maxRxMbps ?: wifi.linkSpeedMbps}\u00A0Mbps")

            if (wifi.bssid.length == 17) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "Vlastní název jednotky (${wifi.bssid})",
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
                        placeholder = { Text("např. Deco Obývák") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { controller.saveBssidAlias(wifi.bssid, aliasDraft) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        modifier = Modifier.heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Uložit", maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }

    // 60s graf kolisani Wi-Fi signalu (opraveny strop -20 dBm a nesrazeny nadpis)
    WifiRssiChartCard(samples = state.wifiHistory)

    // Log prepinani Mesh uzlu (Roaming) - opraveny radek s tlacitkem Smazat
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mesh Roaming a přeladění (${state.roamEvents.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (state.roamEvents.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { controller.clearRoamingLog() },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Smazat", maxLines = 1, softWrap = false)
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
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ev.eventType,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = ev.timeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Text(
                                text = if (ev.fromNode == "---") "Uzel: ${ev.toNode}" else "${ev.fromNode} → ${ev.toNode}",
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "2,4 GHz: Wi-Fi vs. Zigbee",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Sken ${state.lastScanTimeText} • kanály 11 až 26",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { controller.triggerWifiScan() },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Přeměřit", maxLines = 1, softWrap = false)
                }
            }

            Surface(
                color = Color(0xFF10B981).copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Nejčistší Zigbee kanály zde: ${state.recommendedZigbeeChannels.joinToString(", ") { "K$it" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

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
                text = "Klepni na Zigbee kanál (15, 20, 25 a 26 leží v mezerách mezi Wi-Fi 1 / 6 / 11):",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Cista mrizka 4x4 (siroka tlacitka bez zalamovani procent)
            listOf(11..14, 15..18, 19..22, 23..26).forEach { range ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                .heightIn(min = 50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(bg)
                                .clickable { controller.selectZigbeeChannel(ch) }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "K$ch",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = fg,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "${sc?.loadPercent ?: 0}\u00A0%",
                                    fontSize = 11.sp,
                                    color = fg.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    softWrap = false
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Zigbee kanál ${selectedScore.channel} (${selectedScore.centerFreqMhz}\u00A0MHz)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Zátěž ${selectedScore.loadPercent}\u00A0%",
                        fontWeight = FontWeight.ExtraBold,
                        color = loadColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                WifiInfoRow(
                    "Pozice ve spektru",
                    if (selectedScore.isSidebandGap) "Mezera mezi Wi-Fi 1 / 6 / 11 (ideální)" else "Přímý překryv s lalokem Wi-Fi"
                )
                WifiInfoRow(
                    "Nejsilnější rušič",
                    selectedScore.strongestInterfererDbm?.let { "$it\u00A0dBm" } ?: "Žádná rušící Wi-Fi v dosahu"
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
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { controller.triggerWifiScan() },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Skenovat", maxLines = 1, softWrap = false)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Vše", "2.4G", "5G", "6G").forEachIndexed { idx, label ->
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${ap.rssiDbm}\u00A0dBm",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = col,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "${ap.band.shortLabel} • K${ap.channelNumber} (${ap.channelWidthMhz}M)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
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
                                placeholder = { Text("Název BSSID...") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    controller.saveBssidAlias(ap.bssid, aliasInput)
                                    editingBssid = null
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Uložit", maxLines = 1, softWrap = false)
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Speedtest",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = state.speedPhase.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isWifiConnected) "Wi-Fi" else btsState.networkTypeDetail,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                SpeedometerGauge(
                    mbps = state.liveGaugeMbps,
                    phase = state.speedPhase,
                    progress = state.speedProgress
                )

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

                // Cista mrizka 2x2 pro vysledky Speedtestu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WifiMetricBox(
                        title = "Download",
                        value = "${WifiSpectrumCalculator.formatMbps(state.downloadMbps)}\u00A0Mbps",
                        modifier = Modifier.weight(1f)
                    )
                    WifiMetricBox(
                        title = "Upload",
                        value = "${WifiSpectrumCalculator.formatMbps(state.uploadMbps)}\u00A0Mbps",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WifiMetricBox(
                        title = "Klidový / Zátěž Ping",
                        value = "${WifiSpectrumCalculator.formatMs(state.speedIdlePingMs)} / ${WifiSpectrumCalculator.formatMs(state.speedLoadedPingMs)}",
                        modifier = Modifier.weight(1f)
                    )
                    WifiMetricBox(
                        title = "Jitter (kolísání)",
                        value = WifiSpectrumCalculator.formatMs(state.speedJitterMs),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isSpeedRunning) {
                    OutlinedButton(
                        onClick = { controller.stopSpeedTest() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Zastavit měření", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                } else {
                    Button(
                        onClick = {
                            val hint = "${btsState.servingCells.firstOrNull()?.operatorName.orEmpty()} (${btsState.networkTypeDetail})".trim()
                            controller.startSpeedTest(hint)
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Spustit Speedtest",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
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
                    text = "Odhalí, zda zpoždění vzniká doma na Wi-Fi trase k routeru, nebo až venku u operátora.",
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
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        modifier = Modifier.heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Nastavit", maxLines = 1, softWrap = false)
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
                            Text(preset, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { controller.toggleDualPing() },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = if (state.isDualPingRunning) "Zastavit Ping" else "Spustit Ping",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = { controller.resetPingStats() },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Vynulovat", maxLines = 1, softWrap = false)
                    }
                }

                PingTargetSnapshotCard(snapshot = state.gatewayPing)
                PingTargetSnapshotCard(snapshot = state.internetPing)
            }
        }

        // Historie namerenych rychlosti (opraveny radek s tlacitkem Vymazat)
        if (state.speedHistory.isNotEmpty()) {
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Historie měření (${state.speedHistory.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { controller.clearSpeedHistory() },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Vymazat", maxLines = 1, softWrap = false)
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
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.connectionSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.timeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "↓ ${WifiSpectrumCalculator.formatMbps(item.downloadMbps)} • ↑ ${WifiSpectrumCalculator.formatMbps(item.uploadMbps)}\u00A0Mbps",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = WifiSpectrumCalculator.formatMs(item.idlePingMs),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
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
            .size(196.dp)
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
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = activeColor,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = "Mbps",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
            if (progress > 0f && progress < 1f) {
                Text(
                    text = "${(progress * 100f).roundToInt()}\u00A0%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = snapshot.targetLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Host: ${snapshot.targetHost} • ${snapshot.receivedCount}/${snapshot.sentCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = WifiSpectrumCalculator.formatMs(snapshot.lastMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Průměr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    Text(WifiSpectrumCalculator.formatMs(snapshot.avgMs), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Jitter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    Text(WifiSpectrumCalculator.formatMs(snapshot.jitterMs), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Ztráta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    Text(
                        text = "${snapshot.lossPercent}\u00A0%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (snapshot.lossPercent > 0) Color(0xFFEF4444) else Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
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
                            val col =流速Color(sample)
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

private fun 流速Color(sampleMs: Float): Color {
    return when {
        sampleMs < 25f -> Color(0xFF10B981)
        sampleMs < 70f -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val minVal = samples.minOfOrNull { it.rssiDbm }
            val maxVal = samples.maxOfOrNull { it.rssiDbm }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vývoj Wi-Fi signálu (60 s)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (minVal != null && maxVal != null) "$minVal až $maxVal\u00A0dBm" else "---",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            val lineCol = MaterialTheme.colorScheme.secondary
            val gridCol = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
            ) {
                val padY = 8.dp.toPx()
                val usableH = (size.height - padY * 2f).coerceAtLeast(10f)
                val topDbm = -20f
                val bottomDbm = -95f
                val span = topDbm - bottomDbm

                listOf(-35f, -58f, -80f).forEach { ref ->
                    val y = padY + ((topDbm - ref) / span) * usableH
                    drawLine(gridCol, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                }

                if (samples.size >= 2) {
                    val p = Path()
                    val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
                    samples.forEachIndexed { idx, s ->
                        val cl = s.rssiDbm.toFloat().coerceIn(bottomDbm, topDbm)
                        val x = idx * stepX
                        val y = padY + ((topDbm - cl) / span) * usableH
                        if (idx == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    drawPath(p, color = lineCol, style = Stroke(width = 5f, cap = StrokeCap.Round))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("-20\u00A0dBm (u AP)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                Text("-60\u00A0dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                Text("-95\u00A0dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
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
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
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
            maxLines = 1,
            softWrap = false,
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WifiInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f)
        )
    }
}
