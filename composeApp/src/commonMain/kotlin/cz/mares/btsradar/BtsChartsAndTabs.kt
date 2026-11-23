package cz.mares.btsradar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun signalColorFor(level: SignalQualityLevel): Color {
    return when (level) {
        SignalQualityLevel.EXCELLENT -> Color(0xFF10B981)
        SignalQualityLevel.GOOD -> Color(0xFF22C55E)
        SignalQualityLevel.FAIR -> Color(0xFFF59E0B)
        SignalQualityLevel.POOR -> Color(0xFFEF4444)
        SignalQualityLevel.NONE -> Color(0xFF94A3B8)
    }
}

@Composable
fun LiveSignalTab(
    state: BtsRadarState,
    controller: BtsScannerController
) {
    val scroll = rememberScrollState()
    val primary = state.servingCells.firstOrNull()
    val idSource = state.servingCells.firstOrNull { it.cellId > 0L } ?: primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (primary == null) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Načítám data z modemu...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ujisti se, že máš v telefonu zapnutou polohu (GPS), bez které Android skryje identifikátory BTS buněk.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { controller.refreshNow() },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Zkusit načíst znovu")
                    }
                }
            }
        } else {
            val quality = BandCalculator.evaluateQuality(primary.rsrpDbm, primary.tech)
            val sigColor = signalColorFor(quality)
            val progress = ((primary.rsrpDbm + 130).toFloat() / 65f).coerceIn(0.04f, 1f)

            // Hlavni karta sily signalu
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
                                text = primary.operatorName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "PLMN ${primary.mcc}-${primary.mnc.padStart(2, '0')} • SIM ${primary.simSlot} • ${state.dataStateLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = sigColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = quality.label,
                                color = sigColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${primary.rsrpDbm}",
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Black,
                                color = sigColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "dBm (${if (primary.tech == RadioTech.NR_5G) "SS-RSRP" else "RSRP"})",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = primary.bandInfo.bandName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${primary.bandInfo.bandCode} • ${BandCalculator.formatFreqMhz(primary.bandInfo.frequencyMhz)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Barevna stupnice dBm
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(sigColor)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricMiniBox(
                            title = "RSRQ",
                            value = primary.rsrqDb?.let { "$it dB" } ?: "---",
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniBox(
                            title = "SINR",
                            value = primary.sinrDb?.let { "$it dB" } ?: "---",
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniBox(
                            title = "RSSI / ASU",
                            value = primary.rssiDbm?.let { "$it dBm" } ?: "${primary.asuLevel} ASU",
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniBox(
                            title = "Ping",
                            value = state.pingMs?.let { "$it ms" } ?: "---",
                            modifier = Modifier
                                .weight(1f)
                                .clickable { controller.measurePingNow() }
                        )
                    }
                }
            }

            // Inzenyrske parametry BTS vysilace
            if (idSource != null) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Identifikace aktivní BTS a kanálu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        val bwText = if (idSource.bandwidthKhz >= 1000) "${idSource.bandwidthKhz / 1000} MHz" else "---"
                        val taText = if (idSource.timingAdvance != null && idSource.taDistanceMeters != null) {
                            "TA ${idSource.timingAdvance} (~${idSource.taDistanceMeters} m)"
                        } else {
                            "Neaktivní (klid dat)"
                        }

                        InfoRow("Číslo vysílače (eNB / gNB)", if (idSource.enbOrGnbId > 0L) "${idSource.enbOrGnbId}" else "---")
                        InfoRow("Sektor / Cell ID (ECI)", "Sektor ${idSource.sectorId}  •  CID ${idSource.cellId}")
                        InfoRow("Oblast (TAC / LAC) & PCI", "TAC ${idSource.tacOrLac}  •  PCI ${idSource.pciOrPsc}")
                        InfoRow("Kanál & Duplex", "${idSource.bandInfo.channelLabel} (${idSource.bandInfo.duplexMode})")
                        InfoRow("Šířka kanálu / CQI", "$bwText  •  CQI ${idSource.cqi?.toString() ?: "---"}")
                        InfoRow("Timing Advance (rádio)", taText)

                        val tower = state.activeTowerLocation
                        if (tower != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            InfoRow("Lokalita BTS (${tower.source})", tower.siteName)
                            val distStr = state.distanceToTowerMeters?.let { d ->
                                val brg = state.bearingToTowerDeg
                                if (brg != null) "$d m (${BandCalculator.compassLabel(brg)} $brg°)" else "$d m"
                            } ?: "---"
                            InfoRow("Vzdušná vzdálenost & směr", distStr)
                        }
                    }
                }
            }

            // Agregace pasem / 5G NSA kotva
            if (state.servingCells.size > 1) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Aktivní nosiče (5G NSA / Carrier Aggregation)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        state.servingCells.forEach { sc ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${sc.tech.shortLabel} • ${sc.bandInfo.bandCode} (${BandCalculator.formatFreqMhz(sc.bandInfo.frequencyMhz)}) • PCI ${sc.pciOrPsc}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${sc.rsrpDbm} dBm",
                                    fontWeight = FontWeight.Bold,
                                    color = signalColorFor(BandCalculator.evaluateQuality(sc.rsrpDbm, sc.tech))
                                )
                            }
                        }
                    }
                }
            }

            // Zivy 60s graf signalu
            SignalHistoryChartCard(samples = state.historySamples)
        }

        FooterCopyright()
    }
}

@Composable
private fun SignalHistoryChartCard(samples: List<SignalSample>) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val minDbm = samples.minOfOrNull { it.rsrpDbm }
            val maxDbm = samples.maxOfOrNull { it.rsrpDbm }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vývoj signálu (posledních 60 vzorků)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (minDbm != null && maxDbm != null) "$minDbm až $maxDbm dBm" else "---",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val lineColor = MaterialTheme.colorScheme.primary
            val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                val w = size.width
                val h = size.height
                val topDbm = -60f
                val bottomDbm = -125f
                val span = topDbm - bottomDbm

                listOf(-75f, -95f, -110f).forEach { ref ->
                    val y = ((topDbm - ref) / span) * h
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.5f
                    )
                }

                if (samples.size >= 2) {
                    val path = Path()
                    val stepX = w / (samples.size - 1).coerceAtLeast(1)
                    samples.forEachIndexed { idx, s ->
                        val clamped = s.rsrpDbm.toFloat().coerceIn(bottomDbm, topDbm)
                        val x = idx * stepX
                        val y = ((topDbm - clamped) / span) * h
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("-60 dBm (silný)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("-95 dBm (střední)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("-125 dBm (slabý)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun NeighborsTab(state: BtsRadarState) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Okolní buňky a sektory v dosahu (${state.neighborCells.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (state.neighborCells.isEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "Modem momentálně nehlásí žádné vedlejší buňky (často se objeví při přesunu nebo mimo čistý 5G zámek).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            state.neighborCells.forEach { cell ->
                val q = BandCalculator.evaluateQuality(cell.rsrpDbm, cell.tech)
                val col = signalColorFor(q)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "${cell.tech.shortLabel} • ${cell.bandInfo.bandName} (${cell.bandInfo.bandCode})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "PCI ${cell.pciOrPsc} • ${cell.bandInfo.channelLabel} • ${BandCalculator.formatFreqMhz(cell.bandInfo.frequencyMhz)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (cell.enbOrGnbId > 0L) {
                                Text(
                                    text = "eNB ${cell.enbOrGnbId} • CID ${cell.cellId} • TAC ${cell.tacOrLac}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${cell.rsrpDbm} dBm",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = col
                            )
                            cell.rsrqDb?.let {
                                Text(
                                    text = "RSRQ $it dB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
fun MapAndDbTab(
    state: BtsRadarState,
    controller: BtsScannerController
) {
    val scroll = rememberScrollState()
    var apiKeyDraft by remember(state.openCellIdApiKey) { mutableStateOf(state.openCellIdApiKey) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            BtsMapView(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(22.dp))
            )
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Poloha telefonu a vysílače",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                InfoRow(
                    "Moje GPS",
                    "${BandCalculator.formatCoord(state.userLat)}, ${BandCalculator.formatCoord(state.userLon)}" +
                        (state.userAccuracyMeters?.let { " (±$it m)" } ?: "")
                )

                val tower = state.activeTowerLocation
                if (tower != null) {
                    InfoRow("Vysílač", tower.siteName)
                    InfoRow("Zdroj dat", tower.source)
                    InfoRow("GPS BTS", "${BandCalculator.formatCoord(tower.lat)}, ${BandCalculator.formatCoord(tower.lon)}")
                    val distStr = state.distanceToTowerMeters?.let { d ->
                        val brg = state.bearingToTowerDeg
                        if (brg != null) "$d m • směr ${BandCalculator.compassLabel(brg)} ($brg°)" else "$d m"
                    } ?: "---"
                    InfoRow("Vzdálenost", distStr)
                    tower.azimuthDeg?.let { az ->
                        InfoRow("Azimut sektoru", "$az° (${BandCalculator.compassLabel(az)})")
                    }

                    OutlinedButton(
                        onClick = { controller.openTowerInExternalMap() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Otevřít vysílač v externí mapě")
                    }
                } else {
                    Text(
                        text = "Souřadnice aktuální BTS zatím nejsou v online BeaconDB keši. Pro 100% přesné české lokality nahraj níže CSV/CLF export z GSMweb.cz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                Text(
                    text = "Offline databáze českých BTS (GSMweb / ČTÚ)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Načteno v paměti: ${state.importedCsvCount} sektorů (podporuje CLF v3.0 i CSV se středníky/čárkami).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { controller.openCsvImportPicker() },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Nahrát CSV / CLF")
                    }
                    if (state.importedCsvCount > 0) {
                        OutlinedButton(
                            onClick = { controller.clearImportedCsv() },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Smazat")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Volitelný OpenCelliD API klíč (jinak běží BeaconDB zdarma)",
                    style = MaterialTheme.typography.labelLarge
                )
                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    placeholder = { Text("pk.xxx... (nepovinné)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { controller.saveOpenCellIdKey(apiKeyDraft) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Uložit API klíč")
                }
            }
        }

        FooterCopyright()
    }
}

@Composable
fun RouteLogTab(
    state: BtsRadarState,
    controller: BtsScannerController
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                    Text(
                        text = "Zachycené BTS po trase (${state.routeLog.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = state.userSpeedKmh?.let { "$it km/h" } ?: "0 km/h",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { controller.exportRouteLogCsv() },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Exportovat do CSV")
                    }
                    OutlinedButton(
                        onClick = { controller.clearRouteLog() },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Vymazat")
                    }
                }
            }
        }

        state.routeLog.forEach { item ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${item.techLabel} • eNB ${item.enbId} (sektor ${item.sectorId})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Max ${item.bestRsrpDbm} dBm",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "${item.operatorName} • ${item.bandLabel} • PCI ${item.pci} • TAC ${item.tac}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.towerSiteName.isNotBlank()) {
                        Text(
                            text = "BTS: ${item.towerSiteName}" + (item.distanceMeters?.let { " ($it m)" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "Čas ${item.firstSeenText} – ${item.lastSeenText} (${item.samplesCount}×)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        FooterCopyright()
    }
}

@Composable
private fun MetricMiniBox(
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
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.44f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.56f)
        )
    }
}

@Composable
fun FooterCopyright() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "© 2026 Created by Pavel Mareš",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RadarTabIcon(index: Int, tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.4f * density, cap = StrokeCap.Round)
        when (index) {
            0 -> {
                // Carky signalu
                val bars = listOf(0.32f, 0.54f, 0.76f, 1.0f)
                bars.forEachIndexed { i, ratio ->
                    val x = w * (0.16f + i * 0.22f)
                    drawLine(tint, Offset(x, h * 0.9f), Offset(x, h * (0.9f - 0.75f * ratio)), strokeWidth = 3f * density, cap = StrokeCap.Round)
                }
            }
            1 -> {
                // Radar okolnich bunek
                drawCircle(tint, radius = w * 0.42f, style = stroke)
                drawCircle(tint, radius = w * 0.16f)
            }
            2 -> {
                // Mapa a stozar
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.2f, h * 0.88f)
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.8f, h * 0.88f)
                    moveTo(w * 0.32f, h * 0.6f)
                    lineTo(w * 0.68f, h * 0.6f)
                }
                drawPath(p, tint, style = stroke)
                drawCircle(tint, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.15f))
            }
            else -> {
                // Zaznam trasy
                for (i in 0..2) {
                    val y = h * (0.24f + i * 0.26f)
                    drawLine(tint, Offset(w * 0.15f, y), Offset(w * 0.88f, y), strokeWidth = 2.5f * density, cap = StrokeCap.Round)
                }
            }
        }
    }
}
