package cz.mares.btsradar

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController { App() }

@Composable
actual fun rememberBtsScannerController(): BtsScannerController {
    return remember {
        object : BtsScannerController {
            override val state: StateFlow<BtsRadarState> = MutableStateFlow(
                BtsRadarState(
                    hasPermissions = true,
                    networkTypeDetail = "iOS neumožňuje čtení BTS z modemu",
                    statusMessage = "Nativní BTS skener vyžaduje Android (TelephonyManager)"
                )
            )
            override fun requestPermissionsAndStart() = Unit
            override fun refreshNow() = Unit
            override fun measurePingNow() = Unit
            override fun selectSimSubscription(subId: Int?) = Unit
            override fun openCsvImportPicker() = Unit
            override fun clearImportedCsv() = Unit
            override fun saveOpenCellIdKey(key: String) = Unit
            override fun clearRouteLog() = Unit
            override fun exportRouteLogCsv() = Unit
            override fun openTowerInExternalMap() = Unit
        }
    }
}

@Composable
actual fun BtsMapView(
    state: BtsRadarState,
    modifier: Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("Mapa BTS je dostupná v Android verzi.")
    }
}
