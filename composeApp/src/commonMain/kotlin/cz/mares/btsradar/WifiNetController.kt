package cz.mares.btsradar

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

interface WifiNetController {
    val state: StateFlow<WifiNetState>
    fun triggerWifiScan()
    fun saveBssidAlias(bssid: String, alias: String)
    fun clearRoamingLog()
    fun selectZigbeeChannel(channel: Int)
    fun setInternetPingTarget(host: String)
    fun toggleDualPing()
    fun resetPingStats()
    fun startSpeedTest(connectionHint: String)
    fun stopSpeedTest()
    fun clearSpeedHistory()
}

@Composable
expect fun rememberWifiNetController(): WifiNetController
