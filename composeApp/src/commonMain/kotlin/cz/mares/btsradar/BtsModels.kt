package cz.mares.btsradar

enum class RadioTech(val label: String, val shortLabel: String) {
    NR_5G("5G New Radio", "5G NR"),
    LTE_4G("4G LTE", "4G LTE"),
    WCDMA_3G("3G UMTS", "3G"),
    GSM_2G("2G GSM / EDGE", "2G GSM"),
    UNKNOWN("Neznámá síť", "---")
}

enum class SignalQualityLevel(val label: String) {
    EXCELLENT("Výborný"),
    GOOD("Dobrý"),
    FAIR("Slabší"),
    POOR("Velmi slabý"),
    NONE("Bez signálu")
}

data class SimSlotInfo(
    val subId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val mccMnc: String
)

data class BandDetails(
    val bandName: String,
    val bandCode: String,
    val frequencyMhz: Double,
    val duplexMode: String,
    val channelLabel: String
)

data class CellMeasurement(
    val idKey: String,
    val tech: RadioTech,
    val isRegistered: Boolean,
    val isEndc5gNsa: Boolean,
    val simSlot: Int,
    val operatorName: String,
    val mcc: String,
    val mnc: String,
    val cellId: Long,
    val enbOrGnbId: Long,
    val sectorId: Int,
    val tacOrLac: Int,
    val pciOrPsc: Int,
    val channelNumber: Int,
    val bandwidthKhz: Int,
    val bandInfo: BandDetails,
    val rsrpDbm: Int,
    val rsrqDb: Int?,
    val sinrDb: Int?,
    val rssiDbm: Int?,
    val cqi: Int?,
    val timingAdvance: Int?,
    val taDistanceMeters: Int?,
    val asuLevel: Int,
    val bars: Int,
    val timestampMs: Long
)

data class TowerLocation(
    val lat: Double,
    val lon: Double,
    val source: String,
    val siteName: String,
    val addressOrNote: String,
    val accuracyMeters: Int?,
    val azimuthDeg: Int?
)

data class SignalSample(
    val timestampMs: Long,
    val rsrpDbm: Int,
    val sinrDb: Int?,
    val tech: RadioTech,
    val bandCode: String
)

data class LoggedCellVisit(
    val key: String,
    val firstSeenText: String,
    val lastSeenText: String,
    val samplesCount: Int,
    val operatorName: String,
    val mccMnc: String,
    val techLabel: String,
    val enbId: Long,
    val sectorId: Int,
    val cellId: Long,
    val tac: Int,
    val pci: Int,
    val bandLabel: String,
    val channel: Int,
    val bestRsrpDbm: Int,
    val lastRsrpDbm: Int,
    val bestSinrDb: Int?,
    val userLat: Double?,
    val userLon: Double?,
    val towerSiteName: String,
    val distanceMeters: Int?
)

data class TowerCsvEntry(
    val mcc: String,
    val mnc: String,
    val cellId: Long,
    val enbId: Long,
    val tac: Int,
    val lat: Double,
    val lon: Double,
    val siteName: String,
    val azimuthDeg: Int?
)

data class BtsRadarState(
    val hasPermissions: Boolean = false,
    val isScanning: Boolean = false,
    val simSlots: List<SimSlotInfo> = emptyList(),
    val selectedSubId: Int? = null,
    val networkTypeDetail: String = "Čekám na oprávnění",
    val isCarrierAggregation: Boolean = false,
    val dataStateLabel: String = "---",
    val pingMs: Int? = null,
    val servingCells: List<CellMeasurement> = emptyList(),
    val neighborCells: List<CellMeasurement> = emptyList(),
    val historySamples: List<SignalSample> = emptyList(),
    val routeLog: List<LoggedCellVisit> = emptyList(),
    val userLat: Double? = null,
    val userLon: Double? = null,
    val userAccuracyMeters: Int? = null,
    val userSpeedKmh: Int? = null,
    val activeTowerLocation: TowerLocation? = null,
    val distanceToTowerMeters: Int? = null,
    val bearingToTowerDeg: Int? = null,
    val importedCsvCount: Int = 0,
    val openCellIdApiKey: String = "",
    val statusMessage: String = "",
    val lastUpdatedText: String = "--:--:--"
)
