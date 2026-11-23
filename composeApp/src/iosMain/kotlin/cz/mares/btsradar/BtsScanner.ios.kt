package cz.mares.btsradar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private class IosDemoBtsScanner : BtsScanner {
    private val demoServing = ServingCellData(
        operatorName = "T-Mobile CZ",
        mccMnc = "230-01",
        networkLabel = "5G NSA (LTE kotva + NR)",
        radioType = "lte",
        bandLabel = "B3 1800 (1845.0 MHz) + n78 (3540.0 MHz)",
        bandwidthMhz = 20,
        channelNumber = 1600,
        enbOrGnbId = 14250L,
        sectorId = 1,
        cellId = 3648001L,
        tacOrLac = 24100,
        pci = 184,
        rsrpDbm = -79,
        rsrqDb = -8,
        sinrDb = 21,
        rssiDbm = -53,
        cqi = 14,
        nrSsRsrpDbm = -83,
        nrSsSinrDb = 19,
        nrPci = 412,
        nrChannel = 636000,
        nrBandLabel = "n78 (3500) (3540.0 MHz)",
        timingAdvance = 7,
        taDistanceMeters = 547
    )

    override val state: StateFlow<RadarState> = MutableStateFlow(
        RadarState(
            hasPermissions = true,
            simSlots = listOf(SimSlotInfo(1, 1, "T-Mobile CZ")),
            selectedSubId = 1,
            serving = demoServing,
            neighbors = listOf(
                NeighborCellData("5G NR", 412, 636000, "n78 (3500) (3540.0 MHz)", -83, -9, true),
                NeighborCellData("4G LTE", 185, 1600, "B3 1800 (1845.0 MHz)", -88, -11, false),
                NeighborCellData("4G LTE", 92, 6300, "B20 800 (806.0 MHz)", -75, -9, false)
            ),
            historySamples = listOf(
                SignalSample(-84, 17), SignalSample(-82, 18), SignalSample(-80, 20),
                SignalSample(-79, 21), SignalSample(-81, 19), SignalSample(-78, 22),
                SignalSample(-79, 21), SignalSample(-77, 23), SignalSample(-79, 21)
            ),
            phoneLat = 50.42530,
            phoneLon = 14.26180,
            phoneGpsAccuracyM = 4,
            towerLocation = TowerLocation(50.42940, 14.25620, "Ukázkový režim iOS", "Roudnice nad Labem, věž", 30),
            pingMs = 19,
            statusMessage = "Ukázkový režim (iOS neumožňuje přímé čtení BTS registrů)",
            logEntries = listOf(
                BtsLogEntry("12:00:00", "T-Mobile CZ", "5G NSA", "B3 + n78", "14250-1", 3648001L, 184, -79, 21, 50.4253, 14.2618, "Roudnice nad Labem, věž")
            ),
            lastUpdatedText = "12:00:00"
        )
    )

    override fun requestPermissions() {}
    override fun refreshNow() {}
    override fun selectSim(subId: Int?) {}
    override fun importLocalCsv() {}
    override fun saveOpenCellIdKey(key: String) {}
    override fun shareOrCopyLogCsv() {}
    override fun clearLog() {}
    override suspend fun fetchOsmTile(z: Int, x: Int, y: Int): ImageBitmap? = null
}

@Composable
actual fun rememberBtsScanner(): BtsScanner = remember { IosDemoBtsScanner() }
