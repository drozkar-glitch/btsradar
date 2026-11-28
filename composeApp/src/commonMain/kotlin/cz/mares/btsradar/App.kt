package cz.mares.btsradar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val DarkPalette = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF041E2B),
    secondary = Color(0xFF2DD4BF),
    background = Color(0xFF09111E),
    surface = Color(0xFF0F1B2E),
    surfaceVariant = Color(0xFF16263F),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8)
)

private val LightPalette = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF0D9488),
    background = Color(0xFFF1F5F9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2E8F0),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun App() {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkPalette else LightPalette

    MaterialTheme(colorScheme = colors) {
        val btsController = rememberBtsScannerController()
        val btsState by btsController.state.collectAsState()

        val wifiController = rememberWifiNetController()
        val wifiState by wifiController.state.collectAsState()

        var selectedTab by remember { mutableIntStateOf(0) }
        var btsSubView by remember { mutableIntStateOf(0) }

        val activeBanner = if (selectedTab == 1 || selectedTab == 2) {
            wifiState.statusBanner.ifBlank { btsState.statusMessage }
        } else {
            btsState.statusMessage.ifBlank { wifiState.statusBanner }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                TopAnalyzerBar(
                    selectedTab = selectedTab,
                    btsState = btsState,
                    wifiState = wifiState,
                    btsController = btsController,
                    wifiController = wifiController
                )

                if (activeBanner.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = activeBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (!btsState.hasPermissions && selectedTab != 2) {
                        PermissionRequestCard(onGrantClick = { btsController.requestPermissionsAndStart() })
                    } else {
                        when (selectedTab) {
                            0 -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SubTabPill(
                                            title = "Aktivní BTS",
                                            selected = btsSubView == 0,
                                            onClick = { btsSubView = 0 },
                                            modifier = Modifier.weight(1f)
                                        )
                                        SubTabPill(
                                            title = "Okolí (${btsState.neighborCells.size})",
                                            selected = btsSubView == 1,
                                            onClick = { btsSubView = 1 },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (btsSubView == 0) {
                                            LiveSignalTab(state = btsState, controller = btsController)
                                        } else {
                                            NeighborsTab(state = btsState)
                                        }
                                    }
                                }
                            }
                            1 -> WifiAndZigbeeTab(state = wifiState, controller = wifiController)
                            2 -> SpeedAndPingTab(state = wifiState, controller = wifiController, btsState = btsState)
                            3 -> MapAndDbTab(state = btsState, controller = btsController)
                            else -> RouteLogTab(state = btsState, controller = btsController)
                        }
                    }
                }

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    val navLabels = listOf(
                        "BTS",
                        "Wi-Fi",
                        "Test",
                        "Mapa",
                        "Trasa"
                    )
                    navLabels.forEachIndexed { idx, label ->
                        val active = selectedTab == idx
                        NavigationBarItem(
                            selected = active,
                            onClick = { selectedTab = idx },
                            icon = {
                                MainNavCanvasIcon(
                                    index = idx,
                                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopAnalyzerBar(
    selectedTab: Int,
    btsState: BtsRadarState,
    wifiState: WifiNetState,
    btsController: BtsScannerController,
    wifiController: WifiNetController
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BTS Radar PM",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = if ((selectedTab == 1 || selectedTab == 2) && wifiState.isWifiConnected && wifiState.activeWifi != null) {
                    val aw = wifiState.activeWifi
                    "${aw.ssid} • ${aw.band.shortLabel} K${aw.channelNumber} • ${aw.rssiDbm}\u00A0dBm"
                } else {
                    val caTag = if (btsState.isCarrierAggregation) " • CA" else ""
                    "${btsState.networkTypeDetail}$caTag • ${btsState.lastUpdatedText}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            if (selectedTab == 1) {
                OutlinedButton(
                    onClick = { wifiController.triggerWifiScan() },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Sken Wi-Fi", maxLines = 1, softWrap = false)
                }
            } else {
                OutlinedButton(
                    onClick = { btsController.refreshNow() },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Obnovit", maxLines = 1, softWrap = false)
                }
            }
        }

        if (selectedTab == 0 && btsState.simSlots.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                btsState.simSlots.forEach { slot ->
                    val isSelected = btsState.selectedSubId == slot.subId
                    if (isSelected) {
                        Button(
                            onClick = { btsController.selectSimSubscription(slot.subId) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("SIM ${slot.slotIndex}: ${slot.carrierName}", maxLines = 1, softWrap = false)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { btsController.selectSimSubscription(slot.subId) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("SIM ${slot.slotIndex}: ${slot.carrierName}", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainNavCanvasIcon(index: Int, tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.3f * density, cap = StrokeCap.Round)
        when (index) {
            0 -> {
                val bars = listOf(0.32f, 0.54f, 0.76f, 1.0f)
                bars.forEachIndexed { i, ratio ->
                    val x = w * (0.16f + i * 0.22f)
                    drawLine(tint, Offset(x, h * 0.9f), Offset(x, h * (0.9f - 0.75f * ratio)), strokeWidth = 2.8f * density, cap = StrokeCap.Round)
                }
            }
            1 -> {
                drawArc(tint, startAngle = 215f, sweepAngle = 110f, useCenter = false, topLeft = Offset(w * 0.08f, h * 0.16f), size = Size(w * 0.84f, h * 0.84f), style = stroke)
                drawArc(tint, startAngle = 215f, sweepAngle = 110f, useCenter = false, topLeft = Offset(w * 0.24f, h * 0.36f), size = Size(w * 0.52f, h * 0.52f), style = stroke)
                drawCircle(tint, radius = w * 0.09f, center = Offset(w * 0.5f, h * 0.78f))
            }
            2 -> {
                drawArc(tint, startAngle = 150f, sweepAngle = 240f, useCenter = false, topLeft = Offset(w * 0.1f, h * 0.14f), size = Size(w * 0.8f, h * 0.8f), style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.56f), Offset(w * 0.76f, h * 0.30f), strokeWidth = 2.4f * density, cap = StrokeCap.Round)
            }
            3 -> {
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.2f, h * 0.88f)
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.8f, h * 0.88f)
                    moveTo(w * 0.32f, h * 0.6f)
                    lineTo(w * 0.68f, h * 0.6f)
                }
                drawPath(p, tint, style = stroke)
                drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.5f, h * 0.15f))
            }
            else -> {
                for (i in 0..2) {
                    val y = h * (0.24f + i * 0.26f)
                    drawLine(tint, Offset(w * 0.15f, y), Offset(w * 0.88f, y), strokeWidth = 2.4f * density, cap = StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestCard(onGrantClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Přístup k modemu, Wi-Fi a přesné poloze",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Android zpřístupní čísla BTS vysílačů, BSSID jednotlivých Mesh jednotek a sken 2,4 / 5 / 6 GHz spektra pouze při povolené přesné poloze a stavu telefonu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onGrantClick,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Povolit a spustit analyzátor", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
                FooterCopyright()
            }
        }
    }
}
