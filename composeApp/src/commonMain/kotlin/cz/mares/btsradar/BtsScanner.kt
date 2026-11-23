package cz.mares.btsradar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

interface BtsScannerController {
    val state: StateFlow<BtsRadarState>
    fun requestPermissionsAndStart()
    fun refreshNow()
    fun measurePingNow()
    fun selectSimSubscription(subId: Int?)
    fun openCsvImportPicker()
    fun clearImportedCsv()
    fun saveOpenCellIdKey(key: String)
    fun clearRouteLog()
    fun exportRouteLogCsv()
    fun openTowerInExternalMap()
}

@Composable
expect fun rememberBtsScannerController(): BtsScannerController

@Composable
expect fun BtsMapView(
    state: BtsRadarState,
    modifier: Modifier = Modifier
)
