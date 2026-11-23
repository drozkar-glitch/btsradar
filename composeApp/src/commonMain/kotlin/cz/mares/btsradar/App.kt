package cz.mares.btsradar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
        val controller = rememberBtsScannerController()
        val state by controller.state.collectAsState()
        var selectedTab by remember { mutableIntStateOf(0) }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                TopRadarBar(
                    state = state,
                    controller = controller
                )

                if (state.statusMessage.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = state.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (!state.hasPermissions) {
                        PermissionRequestCard(onGrantClick = { controller.requestPermissionsAndStart() })
                    } else {
                        when (selectedTab) {
                            0 -> LiveSignalTab(state = state, controller = controller)
                            1 -> NeighborsTab(state = state)
                            2 -> MapAndDbTab(state = state, controller = controller)
                            else -> RouteLogTab(state = state, controller = controller)
                        }
                    }
                }

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    val tabs = listOf("Signál", "Okolí (${state.neighborCells.size})", "Mapa BTS", "Trasa (${state.routeLog.size})")
                    tabs.forEachIndexed { idx, label ->
                        val active = selectedTab == idx
                        NavigationBarItem(
                            selected = active,
                            onClick = { selectedTab = idx },
                            icon = {
                                RadarTabIcon(
                                    index = idx,
                                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = { Text(label, maxLines = 1) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopRadarBar(
    state: BtsRadarState,
    controller: BtsScannerController
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BTS Radar PM",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                val caTag = if (state.isCarrierAggregation) " • CA aktivní" else ""
                Text(
                    text = "${state.networkTypeDetail}$caTag • ${state.lastUpdatedText}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedButton(
                onClick = { controller.refreshNow() },
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Obnovit")
            }
        }

        if (state.simSlots.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.simSlots.forEach { slot ->
                    val isSelected = state.selectedSubId == slot.subId
                    if (isSelected) {
                        Button(
                            onClick = { controller.selectSimSubscription(slot.subId) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("SIM ${slot.slotIndex}: ${slot.carrierName}")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { controller.selectSimSubscription(slot.subId) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("SIM ${slot.slotIndex}: ${slot.carrierName}")
                        }
                    }
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
                    text = "Přístup k modemu a přesné poloze",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Android zpřístupní čísla BTS vysílačů (eNB/gNB ID, Cell ID, PCI, kanál EARFCN/NR-ARFCN a sousední buňky) pouze při povolené přesné poloze a stavu telefonu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onGrantClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Povolit a spustit BTS skener", fontWeight = FontWeight.Bold)
                }
                FooterCopyright()
            }
        }
    }
}
