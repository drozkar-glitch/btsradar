package cz.mares.btsradar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
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
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Zkusit načíst znovu", maxLines = 1, softWrap = false)
                    }
                }
            }
        } else {
            val quality = BandCalculator.evaluateQuality(primary.rsrpDbm, primary.tech)
            val sigColor = signalColorFor(quality)
            val progress = ((primary.rsrpDbm + 130).toFloat() / 65f).coerceIn(0.04f, 1f)

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
                                text = primary.operatorName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "PLMN ${primary.mcc}-${primary.mnc.padStart(2, '0')} • SIM ${primary.simSlot} • ${state.dataStateLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = sigColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = quality.label,
                                color = sigColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${primary.rsrpDbm}",
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
                                text = primary.bandInfo.bandName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${primary.bandInfo.bandCode} • ${BandCalculator.formatFreqMhz(primary.bandInfo.frequencyMhz)}",
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
                                .fillMaxWidth(progress)
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
                        MetricMiniBox(
                            title = "RSRQ (kvalita)",
                            value = primary.rsrqDb?.let { "$it\u00A0dB" } ?: "---",
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniBox(
                            title = "SINR (odstup šumu)",
                            value = primary.sinrDb?.let { "$it\u00A0dB" } ?: "---",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricMiniBox(
                            title = "RSSI / ASU",
                            value = primary.rssiDbm?.let { "$it\u00A0dBm" } ?: "${primary.asuLevel}\u00A0ASU",
                            modifier = Modifier.weight(1f)
                        )
                        MetricMiniBox(
                            title = "Ping (klepni)",
                            value = state.pingMs?.let { "$it\u00A0ms" } ?: "---",
                            modifier = Modifier
                                .weight(1f)
                                .clickable { controller.measurePingNow() }
                        )
                    }
                }
            }

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

                        val bwText = if (idSource.bandwidthKhz >= 1000) "${idSource.bandwidthKhz / 1000}\u00A0MHz" else "---"
                        val taText = if (idSource.timingAdvance != null && idSource.taDistanceMeters != null) {
                            "TA ${idSource.timingAdvance} (~${idSource.taDistanceMeters}\u00A0m)"
                        } else {
                            "Neaktivní (klid dat)"
                        }

                        InfoRow("Číslo vysílače (eNB / gNB)", if (idSource.enbOrGnbId > 0L) "${idSource.enbOrGnbId}" else "---")
                        InfoRow("Sektor / Cell ID", "Sektor ${idSource.sectorId} • CID ${idSource.cellId}")
                        InfoRow("TAC / LAC & PCI", "TAC ${idSource.tacOrLac} • PCI ${idSource.pciOrPsc}")
                        InfoRow("Kanál & Duplex", "${idSource.bandInfo.channelLabel} (${idSource.bandInfo.duplexMode})")
                        InfoRow("Šířka kanálu / CQI", "$bwText • CQI ${idSource.cqi?.toString() ?: "---"}")
                        InfoRow("Timing Advance", taText)

                        val tower = state.activeTowerLocation
                        if (tower != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            InfoRow("Lokalita BTS", "${tower.siteName} (${tower.source})")
                            val distStr = state.distanceToTowerMeters?.let { d ->
                                val brg = state.bearingToTowerDeg
                                if (brg != null) "$d\u00A0m (${BandCalculator.compassLabel(brg)} $brg°)" else "$d\u00A0m"
                            } ?: "---"
                            InfoRow("Vzdálenost & směr", distStr)
                        }
                    }
                }
            }

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
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${sc.tech.shortLabel} • ${sc.bandInfo.bandCode} (${BandCalculator.formatFreqMhz(sc.bandInfo.frequencyMhz)}) • PCI ${sc.pciOrPsc}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${sc.rsrpDbm}\u00A0dBm",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = signalColorFor(BandCalculator.evaluateQuality(sc.rsrpDbm, sc.tech))
                                )
                            }
                        }
                    }
                }
            }

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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vývoj signálu (60 vzorků)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (minDbm != null && maxDbm != null) "$minDbm až $maxDbm\u00A0dBm" else "---",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            val lineColor = MaterialTheme.colorScheme.primary
            val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
            ) {
                val padY = 8.dp.toPx()
                val usableH = (size.height - padY * 2f).coerceAtLeast(10f)
                val topDbm = -45f
                val bottomDbm = -130f
                val span = topDbm - bottomDbm

                listOf(-65f, -90f, -115f).forEach { ref ->
                    val y = padY + ((topDbm - ref) / span) * usableH
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5f
                    )
                }

                if (samples.size >= 2) {
                    val path = Path()
                    val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
                    samples.forEachIndexed { idx, s ->
                        val clamped = s.rsrpDbm.toFloat().coerceIn(bottomDbm, topDbm)
                        val x = idx * stepX
                        val y = padY + ((topDbm - clamped) / span) * usableH
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
                Text("-50\u00A0dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                Text("-90\u00A0dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                Text("-125\u00A0dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
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
            text = "Okolní buňky v dosahu (${state.neighborCells.size})",
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
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${cell.rsrpDbm}\u00A0dBm",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = col,
                                maxLines = 1,
                                softWrap = false
                            )
                            cell.rsrqDb?.let {
                                Text(
                                    text = "RSRQ $it\u00A0dB",
                                    style = MaterialTheme.typography.labelSmall,
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
                        (state.userAccuracyMeters?.let { " (±$it\u00A0m)" } ?: "")
                )

                val tower = state.activeTowerLocation
                if (tower != null) {
                    InfoRow("Vysílač", tower.siteName)
                    InfoRow("Zdroj dat", tower.source)
                    InfoRow("GPS BTS", "${BandCalculator.formatCoord(tower.lat)}, ${BandCalculator.formatCoord(tower.lon)}")
                    val distStr = state.distanceToTowerMeters?.let { d ->
                        val brg = state.bearingToTowerDeg
                        if (brg != null) "$d\u00A0m • směr ${BandCalculator.compassLabel(brg)} ($brg°)" else "$d\u00A0m"
                    } ?: "---"
                    InfoRow("Vzdálenost", distStr)
                    tower.azimuthDeg?.let { az ->
                        InfoRow("Azimut sektoru", "$az° (${BandCalculator.compassLabel(az)})")
                    }

                    OutlinedButton(
                        onClick = { controller.openTowerInExternalMap() },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Otevřít v externí mapě", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
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
                    text = "Načteno v paměti: ${state.importedCsvCount} sektorů (CLF v3.0 i CSV).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { controller.openCsvImportPicker() },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Nahrát CSV / CLF", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    if (state.importedCsvCount > 0) {
                        OutlinedButton(
                            onClick = { controller.clearImportedCsv() },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Smazat", maxLines = 1, softWrap = false)
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
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Uložit API klíč", maxLines = 1, softWrap = false)
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Zachycené BTS po trase (${state.routeLog.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.userSpeedKmh?.let { "$it\u00A0km/h" } ?: "0\u00A0km/h",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { controller.exportRouteLogCsv() },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Export do CSV", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { controller.clearRouteLog() },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Vymazat", maxLines = 1, softWrap = false)
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.techLabel} • eNB ${item.enbId} (s.${item.sectorId})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Max ${item.bestRsrpDbm}\u00A0dBm",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Text(
                        text = "${item.operatorName} • ${item.bandLabel} • PCI ${item.pci} • TAC ${item.tac}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.towerSiteName.isNotBlank()) {
                        Text(
                            text = "BTS: ${item.towerSiteName}" + (item.distanceMeters?.let { " ($it\u00A0m)" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "Čas ${item.firstSeenText} až ${item.lastSeenText} (${item.samplesCount}×)",
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
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.44f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
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
