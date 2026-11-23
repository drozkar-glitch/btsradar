package cz.mares.btsradar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthWcdma
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@SuppressLint("MissingPermission", "NewApi")
private class AndroidBtsScannerController(
    private val context: Context,
    private val scope: CoroutineScope
) : BtsScannerController {

    private val prefs = context.getSharedPreferences("bts_radar_prefs", Context.MODE_PRIVATE)
    private val csvStorageFile = File(context.filesDir, "imported_bts_db.txt")

    private val _state = MutableStateFlow(
        BtsRadarState(
            openCellIdApiKey = prefs.getString("opencellid_key", "").orEmpty()
        )
    )
    override val state: StateFlow<BtsRadarState> = _state.asStateFlow()

    var permissionRequester: (() -> Unit)? = null
    var csvImportLauncher: (() -> Unit)? = null
    var csvExportLauncher: ((String) -> Unit)? = null

    private var scanJob: Job? = null
    private var pingJob: Job? = null
    private var locationStarted = false
    private var displayCallbackRegistered = false
    private var currentOverrideLabel: String = ""
    private var isEndcNsaActive: Boolean = false
    private var isLteCaActive: Boolean = false

    private val csvEntries = ArrayList<TowerCsvEntry>()
    private val onlineTowerCache = HashMap<String, TowerLocation>()
    private val failedLookupKeys = HashSet<String>()
    private val historyBuffer = ArrayDeque<SignalSample>()
    private val routeVisitMap = LinkedHashMap<String, LoggedCellVisit>()

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val baseTelephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            val acc = if (loc.hasAccuracy()) loc.accuracy.roundToInt() else null
            val spd = if (loc.hasSpeed()) (loc.speed * 3.6f).roundToInt() else null
            _state.update { old ->
                val dist = old.activeTowerLocation?.let {
                    BandCalculator.distanceMeters(loc.latitude, loc.longitude, it.lat, it.lon)
                }
                val brg = old.activeTowerLocation?.let {
                    BandCalculator.bearingDegrees(loc.latitude, loc.longitude, it.lat, it.lon)
                }
                old.copy(
                    userLat = loc.latitude,
                    userLon = loc.longitude,
                    userAccuracyMeters = acc,
                    userSpeedKmh = spd,
                    distanceToTowerMeters = dist,
                    bearingToTowerDeg = brg
                )
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private var displayCallbackObj: Any? = null

    init {
        scope.launch(Dispatchers.IO) {
            if (csvStorageFile.exists()) {
                runCatching {
                    val parsed = BandCalculator.parseTowerDatabase(csvStorageFile.readText())
                    synchronized(csvEntries) {
                        csvEntries.clear()
                        csvEntries.addAll(parsed)
                    }
                    _state.update { it.copy(importedCsvCount = parsed.size) }
                }
            }
        }
        checkAndAutoStart()
    }

    fun checkAndAutoStart() {
        val granted = hasRequiredPermissions()
        _state.update { it.copy(hasPermissions = granted) }
        if (granted) {
            startEngine()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val phone = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        return fine && phone
    }

    override fun requestPermissionsAndStart() {
        if (hasRequiredPermissions()) {
            _state.update { it.copy(hasPermissions = true) }
            startEngine()
        } else {
            permissionRequester?.invoke()
        }
    }

    override fun refreshNow() {
        if (!hasRequiredPermissions()) {
            requestPermissionsAndStart()
            return
        }
        scope.launch { pollModemOnce(forceFresh = true) }
    }

    override fun measurePingNow() {
        scope.launch {
            val ms = measureTcpLatencyMs()
            _state.update {
                it.copy(
                    pingMs = ms,
                    statusMessage = if (ms != null) "Odezva sítě: $ms ms" else "Test pingu selhal (offline)"
                )
            }
        }
    }

    override fun selectSimSubscription(subId: Int?) {
        unregisterDisplayCallback()
        _state.update { it.copy(selectedSubId = subId) }
        registerDisplayCallbackIfSupported()
        scope.launch { pollModemOnce(forceFresh = true) }
    }

    override fun openCsvImportPicker() {
        csvImportLauncher?.invoke()
    }

    fun handleImportedCsvUri(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            _state.update { it.copy(statusMessage = "Načítám databázi vysílačů...") }
            runCatching {
                val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val text = decodeBestCharset(rawBytes)
                val parsed = BandCalculator.parseTowerDatabase(text)
                if (parsed.isNotEmpty()) {
                    csvStorageFile.writeText(text)
                    synchronized(csvEntries) {
                        csvEntries.clear()
                        csvEntries.addAll(parsed)
                    }
                    failedLookupKeys.clear()
                    _state.update {
                        it.copy(
                            importedCsvCount = parsed.size,
                            statusMessage = "Načteno ${parsed.size} záznamů BTS z CSV/CLF"
                        )
                    }
                    pollModemOnce(forceFresh = false)
                } else {
                    _state.update { it.copy(statusMessage = "V souboru nebyly rozpoznány žádné BTS souřadnice") }
                }
            }.onFailure { e ->
                _state.update { it.copy(statusMessage = "Chyba čtení CSV: ${e.message ?: "neznámá chyba"}") }
            }
        }
    }

    private fun decodeBestCharset(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) {
            runCatching { String(bytes, java.nio.charset.Charset.forName("windows-1250")) }.getOrDefault(utf8)
        } else {
            utf8
        }
    }

    override fun clearImportedCsv() {
        scope.launch(Dispatchers.IO) {
            runCatching { if (csvStorageFile.exists()) csvStorageFile.delete() }
            synchronized(csvEntries) { csvEntries.clear() }
            _state.update {
                it.copy(
                    importedCsvCount = 0,
                    activeTowerLocation = null,
                    statusMessage = "Lokální CSV databáze BTS byla vymazána"
                )
            }
        }
    }

    override fun saveOpenCellIdKey(key: String) {
        val clean = key.trim()
        prefs.edit().putString("opencellid_key", clean).apply()
        failedLookupKeys.clear()
        _state.update {
            it.copy(
                openCellIdApiKey = clean,
                statusMessage = if (clean.isNotEmpty()) "OpenCelliD API klíč uložen" else "OpenCelliD klíč odebrán (použije se BeaconDB)"
            )
        }
        refreshNow()
    }

    override fun clearRouteLog() {
        synchronized(routeVisitMap) { routeVisitMap.clear() }
        _state.update { it.copy(routeLog = emptyList(), statusMessage = "Záznam trasy vymazán") }
    }

    override fun exportRouteLogCsv() {
        val currentLog = _state.value.routeLog
        if (currentLog.isEmpty()) {
            _state.update { it.copy(statusMessage = "Záznam trasy je zatím prázdný") }
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        csvExportLauncher?.invoke("bts_trasa_$stamp.csv")
    }

    fun writeExportCsvToUri(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val csv = BandCalculator.buildRouteCsv(_state.value.routeLog)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write( byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) )
                    out.write(csv.toByteArray(Charsets.UTF_8))
                }
                _state.update { it.copy(statusMessage = "CSV soubor trasy úspěšně uložen") }
            }.onFailure {
                _state.update { it.copy(statusMessage = "Nepodařilo se uložit CSV export") }
            }
        }
    }

    override fun openTowerInExternalMap() {
        val tower = _state.value.activeTowerLocation ?: return
        val label = tower.siteName.ifBlank { "BTS vysílač" }
        val uri = Uri.parse("geo:${tower.lat},${tower.lon}?q=${tower.lat},${tower.lon}(${Uri.encode(label)})")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun startEngine() {
        startLocationUpdates()
        registerDisplayCallbackIfSupported()

        if (scanJob?.isActive != true) {
            scanJob = scope.launch {
                _state.update { it.copy(isScanning = true) }
                var tick = 0
                while (isActive) {
                    pollModemOnce(forceFresh = (tick % 2 == 0))
                    tick++
                    delay(1500L)
                }
            }
        }

        if (pingJob?.isActive != true) {
            pingJob = scope.launch {
                while (isActive) {
                    val ms = measureTcpLatencyMs()
                    _state.update { it.copy(pingMs = ms) }
                    delay(6000L)
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (locationStarted || !hasRequiredPermissions()) return
        runCatching {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (prov in providers) {
                if (locationManager.isProviderEnabled(prov)) {
                    locationManager.requestLocationUpdates(prov, 1500L, 1f, locationListener, Looper.getMainLooper())
                    locationManager.getLastKnownLocation(prov)?.let { locationListener.onLocationChanged(it) }
                }
            }
            locationStarted = true
        }
    }

    private fun activeTelephonyManager(): TelephonyManager {
        val subId = _state.value.selectedSubId
        return if (subId != null) {
            runCatching { baseTelephony.createForSubscriptionId(subId) }.getOrDefault(baseTelephony)
        } else {
            baseTelephony
        }
    }

    private fun registerDisplayCallbackIfSupported() {
        if (displayCallbackRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !hasRequiredPermissions()) return
        runCatching {
            val tm = activeTelephonyManager()
            val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                    when (telephonyDisplayInfo.overrideNetworkType) {
                        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> {
                            currentOverrideLabel = "5G NSA (EN-DC)"
                            isEndcNsaActive = true
                            isLteCaActive = true
                        }
                        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> {
                            currentOverrideLabel = "5G+ NR Advanced"
                            isEndcNsaActive = true
                            isLteCaActive = true
                        }
                        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> {
                            currentOverrideLabel = "4G+ LTE-A (CA)"
                            isEndcNsaActive = false
                            isLteCaActive = true
                        }
                        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> {
                            currentOverrideLabel = "4G+ LTE Adv Pro"
                            isEndcNsaActive = false
                            isLteCaActive = true
                        }
                        else -> {
                            currentOverrideLabel = ""
                            isEndcNsaActive = false
                            isLteCaActive = false
                        }
                    }
                }
            }
            tm.registerTelephonyCallback(ContextCompat.getMainExecutor(context), cb)
            displayCallbackObj = cb
            displayCallbackRegistered = true
        }
    }

    private fun unregisterDisplayCallback() {
        if (!displayCallbackRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            val cb = displayCallbackObj as? TelephonyCallback
            if (cb != null) {
                activeTelephonyManager().unregisterTelephonyCallback(cb)
            }
        }
        displayCallbackObj = null
        displayCallbackRegistered = false
    }

    private suspend fun pollModemOnce(forceFresh: Boolean) {
        if (!hasRequiredPermissions()) return

        val sims = readActiveSimSlots()
        val chosenSub = _state.value.selectedSubId ?: sims.firstOrNull()?.subId
        val tm = if (chosenSub != null) {
            runCatching { baseTelephony.createForSubscriptionId(chosenSub) }.getOrDefault(baseTelephony)
        } else {
            baseTelephony
        }
        val activeSimSlotIndex = sims.firstOrNull { it.subId == chosenSub }?.slotIndex ?: 1

        if (forceFresh && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                tm.requestCellInfoUpdate(
                    ContextCompat.getMainExecutor(context),
                    object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                            scope.launch { processRawCellList(tm, sims, chosenSub, activeSimSlotIndex, cellInfo) }
                        }
                        override fun onError(errorCode: Int, detail: Throwable?) {
                            scope.launch {
                                val fallback = runCatching { tm.allCellInfo ?: emptyList() }.getOrDefault(emptyList())
                                processRawCellList(tm, sims, chosenSub, activeSimSlotIndex, fallback)
                            }
                        }
                    }
                )
                return
            }
        }

        val rawList = withContext(Dispatchers.IO) {
            runCatching { tm.allCellInfo ?: emptyList() }.getOrDefault(emptyList())
        }
        processRawCellList(tm, sims, chosenSub, activeSimSlotIndex, rawList)
    }

    private suspend fun processRawCellList(
        tm: TelephonyManager,
        sims: List<SimSlotInfo>,
        chosenSub: Int?,
        simSlotIndex: Int,
        rawCells: List<CellInfo>
    ) {
        val nowMs = System.currentTimeMillis()
        val opFallback = runCatching { tm.networkOperatorName.orEmpty() }.getOrDefault("")
        val plmn = runCatching { tm.networkOperator.orEmpty() }.getOrDefault("")
        val defaultMcc = if (plmn.length >= 5) plmn.substring(0, 3) else ""
        val defaultMnc = if (plmn.length >= 5) plmn.substring(3) else ""

        val parsed = ArrayList<CellMeasurement>(rawCells.size + 1)
        for (info in rawCells) {
            parseSingleCellInfo(info, simSlotIndex, opFallback, defaultMcc, defaultMnc, nowMs)?.let {
                parsed.add(it)
            }
        }

        // Doplneni ziveho 5G NSA nosice ze SignalStrength na Samsung S24 Ultra, pokud modem vraci v allCellInfo jen 4G kotvu
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && parsed.none { it.tech == RadioTech.NR_5G && it.isRegistered }) {
            runCatching {
                val nrStrengths = tm.signalStrength?.getCellSignalStrengths(CellSignalStrengthNr::class.java).orEmpty()
                val validNr = nrStrengths.firstOrNull { it.ssRsrp in -145..-35 }
                if (validNr != null) {
                    val anchor = parsed.firstOrNull { it.isRegistered && it.tech == RadioTech.LTE_4G }
                    val mcc = anchor?.mcc ?: defaultMcc
                    val mnc = anchor?.mnc ?: defaultMnc
                    parsed.add(
                        0,
                        CellMeasurement(
                            idKey = "5g_nsa_${anchor?.enbOrGnbId ?: 0L}",
                            tech = RadioTech.NR_5G,
                            isRegistered = true,
                            isEndc5gNsa = true,
                            simSlot = simSlotIndex,
                            operatorName = BandCalculator.operatorNameFor(mcc, mnc, opFallback),
                            mcc = mcc,
                            mnc = mnc,
                            cellId = anchor?.cellId ?: 0L,
                            enbOrGnbId = anchor?.enbOrGnbId ?: 0L,
                            sectorId = anchor?.sectorId ?: 0,
                            tacOrLac = anchor?.tacOrLac ?: 0,
                            pciOrPsc = anchor?.pciOrPsc ?: 0,
                            channelNumber = 0,
                            bandwidthKhz = 0,
                            bandInfo = BandDetails("5G NR (NSA nosič)", "5G NSA", 0.0, "EN-DC", "Kotva eNB ${anchor?.enbOrGnbId ?: "---"}"),
                            rsrpDbm = validNr.ssRsrp,
                            rsrqDb = validNr.ssRsrq.takeIf { it in -45..20 },
                            sinrDb = validNr.ssSinr.takeIf { it in -30..50 },
                            rssiDbm = null,
                            cqi = null,
                            timingAdvance = anchor?.timingAdvance,
                            taDistanceMeters = anchor?.taDistanceMeters,
                            asuLevel = validNr.asuLevel.coerceAtLeast(0),
                            bars = validNr.level.coerceIn(0, 4),
                            timestampMs = nowMs
                        )
                    )
                }
            }
        }

        val serving = parsed.filter { it.isRegistered }
            .sortedByDescending { if (it.tech == RadioTech.NR_5G) 1 else 0 }
        val neighbors = parsed.filter { !it.isRegistered }
            .sortedByDescending { it.rsrpDbm }

        val primaryForSignal = serving.firstOrNull()
        val primaryWithId = serving.firstOrNull { it.cellId > 0L && it.enbOrGnbId > 0L } ?: primaryForSignal

        if (primaryForSignal != null && primaryForSignal.rsrpDbm in -150..-35) {
            synchronized(historyBuffer) {
                historyBuffer.addLast(
                    SignalSample(
                        timestampMs = nowMs,
                        rsrpDbm = primaryForSignal.rsrpDbm,
                        sinrDb = primaryForSignal.sinrDb,
                        tech = primaryForSignal.tech,
                        bandCode = primaryForSignal.bandInfo.bandCode
                    )
                )
                while (historyBuffer.size > 60) {
                    historyBuffer.removeFirst()
                }
            }
        }

        val resolvedTower = if (primaryWithId != null) {
            resolveTowerCoordinates(primaryWithId)
        } else {
            null
        }

        val curLat = _state.value.userLat
        val curLon = _state.value.userLon
        val distMeters = if (curLat != null && curLon != null && resolvedTower != null) {
            BandCalculator.distanceMeters(curLat, curLon, resolvedTower.lat, resolvedTower.lon)
        } else {
            primaryWithId?.taDistanceMeters
        }
        val bearingDeg = if (curLat != null && curLon != null && resolvedTower != null) {
            BandCalculator.bearingDegrees(curLat, curLon, resolvedTower.lat, resolvedTower.lon)
        } else {
            null
        }

        val timeText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(nowMs))

        if (primaryWithId != null && primaryWithId.cellId > 0L) {
            recordCellVisit(primaryWithId, timeText, curLat, curLon, resolvedTower, distMeters)
        }

        val netTypeLabel = buildNetworkSummaryLabel(tm, serving)
        val caActive = isLteCaActive || serving.size > 1
        val dataLabel = readDataConnectionLabel()

        val historyCopy = synchronized(historyBuffer) { historyBuffer.toList() }
        val routeCopy = synchronized(routeVisitMap) { routeVisitMap.values.toList().asReversed() }

        _state.update { old ->
            old.copy(
                simSlots = sims,
                selectedSubId = chosenSub,
                networkTypeDetail = netTypeLabel,
                isCarrierAggregation = caActive,
                dataStateLabel = dataLabel,
                servingCells = serving,
                neighborCells = neighbors,
                historySamples = historyCopy,
                routeLog = routeCopy,
                activeTowerLocation = resolvedTower,
                distanceToTowerMeters = distMeters,
                bearingToTowerDeg = bearingDeg,
                lastUpdatedText = timeText
            )
        }
    }

    private fun recordCellVisit(
        cell: CellMeasurement,
        timeText: String,
        userLat: Double?,
        userLon: Double?,
        tower: TowerLocation?,
        distMeters: Int?
    ) {
        val key = "${cell.mcc}-${cell.mnc}-${cell.cellId}-${cell.tech.shortLabel}"
        synchronized(routeVisitMap) {
            val existing = routeVisitMap[key]
            if (existing == null) {
                routeVisitMap[key] = LoggedCellVisit(
                    key = key,
                    firstSeenText = timeText,
                    lastSeenText = timeText,
                    samplesCount = 1,
                    operatorName = cell.operatorName,
                    mccMnc = "${cell.mcc}-${cell.mnc}",
                    techLabel = if (isEndcNsaActive && cell.tech == RadioTech.LTE_4G) "4G+5G NSA" else cell.tech.shortLabel,
                    enbId = cell.enbOrGnbId,
                    sectorId = cell.sectorId,
                    cellId = cell.cellId,
                    tac = cell.tacOrLac,
                    pci = cell.pciOrPsc,
                    bandLabel = "${cell.bandInfo.bandCode} (${BandCalculator.formatFreqMhz(cell.bandInfo.frequencyMhz)})",
                    channel = cell.channelNumber,
                    bestRsrpDbm = cell.rsrpDbm,
                    lastRsrpDbm = cell.rsrpDbm,
                    bestSinrDb = cell.sinrDb,
                    userLat = userLat,
                    userLon = userLon,
                    towerSiteName = tower?.siteName.orEmpty(),
                    distanceMeters = distMeters
                )
            } else {
                val bestRsrp = maxOf(existing.bestRsrpDbm, cell.rsrpDbm)
                val bestSinr = when {
                    existing.bestSinrDb == null -> cell.sinrDb
                    cell.sinrDb == null -> existing.bestSinrDb
                    else -> maxOf(existing.bestSinrDb, cell.sinrDb)
                }
                routeVisitMap[key] = existing.copy(
                    lastSeenText = timeText,
                    samplesCount = existing.samplesCount + 1,
                    bestRsrpDbm = bestRsrp,
                    lastRsrpDbm = cell.rsrpDbm,
                    bestSinrDb = bestSinr,
                    userLat = userLat ?: existing.userLat,
                    userLon = userLon ?: existing.userLon,
                    towerSiteName = tower?.siteName?.ifBlank { existing.towerSiteName } ?: existing.towerSiteName,
                    distanceMeters = distMeters ?: existing.distanceMeters
                )
            }
        }
    }

    private fun parseSingleCellInfo(
        info: CellInfo,
        simSlot: Int,
        opFallback: String,
        defaultMcc: String,
        defaultMnc: String,
        nowMs: Long
    ): CellMeasurement? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr) {
            val id = info.cellIdentity as? CellIdentityNr ?: return null
            val sig = info.cellSignalStrength as? CellSignalStrengthNr ?: return null
            val mcc = id.mccString?.takeIf { it.isNotBlank() } ?: if (info.isRegistered) defaultMcc else ""
            val mnc = id.mncString?.takeIf { it.isNotBlank() } ?: if (info.isRegistered) defaultMnc else ""
            val nci = validLong(id.nci) ?: 0L
            val gnb = if (nci > 0L) (nci shr 12) else 0L
            val sector = if (nci > 0L) (nci and 0xFFFL).toInt() else 0
            val tac = validInt(id.tac) ?: 0
            val pci = validInt(id.pci) ?: 0
            val nrarfcn = validInt(id.nrarfcn) ?: 0
            val rsrp = sig.ssRsrp.takeIf { it in -150..-30 } ?: sig.dbm.takeIf { it in -150..-30 } ?: -120
            return CellMeasurement(
                idKey = "nr_${nci}_${pci}_$nrarfcn",
                tech = RadioTech.NR_5G,
                isRegistered = info.isRegistered,
                isEndc5gNsa = isEndcNsaActive,
                simSlot = simSlot,
                operatorName = BandCalculator.operatorNameFor(mcc, mnc, opFallback),
                mcc = mcc,
                mnc = mnc,
                cellId = nci,
                enbOrGnbId = gnb,
                sectorId = sector,
                tacOrLac = tac,
                pciOrPsc = pci,
                channelNumber = nrarfcn,
                bandwidthKhz = 0,
                bandInfo = BandCalculator.resolveBand(RadioTech.NR_5G, nrarfcn),
                rsrpDbm = rsrp,
                rsrqDb = sig.ssRsrq.takeIf { it in -45..20 },
                sinrDb = sig.ssSinr.takeIf { it in -30..50 },
                rssiDbm = null,
                cqi = null,
                timingAdvance = null,
                taDistanceMeters = null,
                asuLevel = sig.asuLevel.coerceAtLeast(0),
                bars = sig.level.coerceIn(0, 4),
                timestampMs = nowMs
            )
        }

        return when (info) {
            is CellInfoLte -> {
                val id: CellIdentityLte = info.cellIdentity
                val sig: CellSignalStrengthLte = info.cellSignalStrength
                val mcc = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else validInt(id.mcc)?.toString())
                    ?.takeIf { it.isNotBlank() } ?: if (info.isRegistered) defaultMcc else ""
                val mnc = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else validInt(id.mnc)?.toString())
                    ?.takeIf { it.isNotBlank() } ?: if (info.isRegistered) defaultMnc else ""
                val ci = validInt(id.ci)?.toLong()?.takeIf { it > 0L } ?: 0L
                val enb = if (ci > 0L) (ci shr 8) else 0L
                val sector = if (ci > 0L) (ci and 0xFFL).toInt() else 0
                val tac = validInt(id.tac) ?: 0
                val pci = validInt(id.pci) ?: 0
                val earfcn = validInt(id.earfcn) ?: 0
                val bw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) (validInt(id.bandwidth) ?: 0) else 0
                val rsrp = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sig.rsrp else sig.dbm)
                    .takeIf { it in -150..-30 } ?: sig.dbm.takeIf { it in -150..-30 } ?: -120
                val rsrq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sig.rsrq.takeIf { it in -45..20 } else null
                val rawSinr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sig.rssnr else Int.MAX_VALUE
                val sinr = when {
                    rawSinr in -30..50 -> rawSinr
                    rawSinr in -300..500 -> rawSinr / 10
                    else -> null
                }
                val rssi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) sig.rssi.takeIf { it in -120..-30 } else null
                val cqi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sig.cqi.takeIf { it in 0..15 } else null
                val ta = sig.timingAdvance.takeIf { it in 0..1282 }
                val taDist = ta?.let { (it * 78.125).roundToInt() }

                CellMeasurement(
                    idKey = "lte_${ci}_${pci}_$earfcn",
                    tech = RadioTech.LTE_4G,
                    isRegistered = info.isRegistered,
                    isEndc5gNsa = info.isRegistered && isEndcNsaActive,
                    simSlot = simSlot,
                    operatorName = BandCalculator.operatorNameFor(mcc, mnc, opFallback),
                    mcc = mcc,
                    mnc = mnc,
                    cellId = ci,
                    enbOrGnbId = enb,
                    sectorId = sector,
                    tacOrLac = tac,
                    pciOrPsc = pci,
                    channelNumber = earfcn,
                    bandwidthKhz = bw,
                    bandInfo = BandCalculator.resolveBand(RadioTech.LTE_4G, earfcn),
                    rsrpDbm = rsrp,
                    rsrqDb = rsrq,
                    sinrDb = sinr,
                    rssiDbm = rssi,
                    cqi = cqi,
                    timingAdvance = ta,
                    taDistanceMeters = taDist,
                    asuLevel = sig.asuLevel.coerceAtLeast(0),
                    bars = sig.level.coerceIn(0, 4),
                    timestampMs = nowMs
                )
            }

            is CellInfoWcdma -> {
                val id: CellIdentityWcdma = info.cellIdentity
                val sig: CellSignalStrengthWcdma = info.cellSignalStrength
                val mcc = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else validInt(id.mcc)?.toString())
                    ?.takeIf { it.isNotBlank() } ?: if (info.isRegistered) defaultMcc else ""
                val mnc = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else validInt(id.mnc)?.toString())
                    ?.takeIf { it.isNotBlank() } ?: if (info.isRegistered) defaultMnc else ""
                val cid = validInt(id.cid)?.toLong()?.takeIf { it > 0L } ?: 0L
                val rnc = if (cid > 65535L) (cid shr 16) else cid
                val sector = if (cid > 65535L) (cid and 0xFFFFL).toInt() else 0
                val lac = validInt(id.lac) ?: 0
                val psc = validInt(id.psc) ?: 0
                val uarfcn = validInt(id.uarfcn) ?: 0
                val dbm = sig.dbm.takeIf { it in -130..-30 } ?: -115

                CellMeasurement(
                    idKey = "3g_${cid}_${psc}_$uarfcn",
                    tech = RadioTech.WCDMA_3G,
                    isRegistered = info.isRegistered,
                    isEndc5gNsa = false,
                    simSlot = simSlot,
                    operatorName = BandCalculator.operatorNameFor(mcc, mnc, opFallback),
                    mcc = mcc,
                    mnc = mnc,
                    cellId = cid,
                    enbOrGnbId = rnc,
                    sectorId = sector,
                    tacOrLac = lac,
                    pciOrPsc = psc,
                    channelNumber = uarfcn,
                    bandwidthKhz = 5000,
                    bandInfo = BandCalculator.resolveBand(RadioTech.WCDMA_3G, uarfcn),
                    rsrpDbm = dbm,
                    rsrqDb = null,
                    sinrDb = null,
                    rssiDbm = dbm,
                    cqi = null,
                    timingAdvance = null,
                    taDistanceMeters = null,
                    asuLevel = sig.asuLevel.coerceAtLeast(0),
                    bars = sig.level.coerceIn(0, 4),
                    timestampMs = nowMs
                )
            }

            is CellInfoGsm -> {
                val id: CellIdentityGsm = info.cellIdentity
                val sig: CellSignalStrengthGsm = info.cellSignalStrength
                val mcc = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else validInt(id.mcc)?.toString())
                    ?.takeIf { it.isNotBlank() } ?: if (info.isRegistered) defaultMcc else ""
                val mnc = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else validInt(id.mnc)?.toString())
                    ?.takeIf { it.isNotBlank() } ?: if (info.isRegistered) defaultMnc else ""
                val cid = validInt(id.cid)?.toLong()?.takeIf { it > 0L } ?: 0L
                val lac = validInt(id.lac) ?: 0
                val bsic = validInt(id.bsic) ?: 0
                val arfcn = validInt(id.arfcn) ?: 0
                val dbm = sig.dbm.takeIf { it in -130..-30 } ?: -110
                val ta = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) sig.timingAdvance.takeIf { it in 0..63 } else null
                val taDist = ta?.let { it * 554 }

                CellMeasurement(
                    idKey = "2g_${cid}_${bsic}_$arfcn",
                    tech = RadioTech.GSM_2G,
                    isRegistered = info.isRegistered,
                    isEndc5gNsa = false,
                    simSlot = simSlot,
                    operatorName = BandCalculator.operatorNameFor(mcc, mnc, opFallback),
                    mcc = mcc,
                    mnc = mnc,
                    cellId = cid,
                    enbOrGnbId = cid,
                    sectorId = 0,
                    tacOrLac = lac,
                    pciOrPsc = bsic,
                    channelNumber = arfcn,
                    bandwidthKhz = 200,
                    bandInfo = BandCalculator.resolveBand(RadioTech.GSM_2G, arfcn),
                    rsrpDbm = dbm,
                    rsrqDb = null,
                    sinrDb = null,
                    rssiDbm = dbm,
                    cqi = null,
                    timingAdvance = ta,
                    taDistanceMeters = taDist,
                    asuLevel = sig.asuLevel.coerceAtLeast(0),
                    bars = sig.level.coerceIn(0, 4),
                    timestampMs = nowMs
                )
            }

            else -> null
        }
    }

    private suspend fun resolveTowerCoordinates(cell: CellMeasurement): TowerLocation? {
        val normMcc = cell.mcc.trim()
        val normMnc = cell.mnc.trim().trimStart('0').ifEmpty { "0" }

        // 1. Lokalni CSV / GSMweb databaze (presny sektor nebo stejny eNB stozar)
        synchronized(csvEntries) {
            if (csvEntries.isNotEmpty()) {
                val exact = csvEntries.firstOrNull {
                    it.mcc == normMcc && it.mnc == normMnc && it.cellId == cell.cellId && cell.cellId > 0L
                }
                if (exact != null) {
                    return TowerLocation(
                        lat = exact.lat,
                        lon = exact.lon,
                        source = "CSV databáze (přesný sektor)",
                        siteName = exact.siteName.ifBlank { "BTS eNB ${cell.enbOrGnbId}" },
                        addressOrNote = "Sektor ${cell.sectorId} • ${cell.bandInfo.bandName}",
                        accuracyMeters = 25,
                        azimuthDeg = exact.azimuthDeg
                    )
                }
                val sameMast = csvEntries.firstOrNull {
                    it.mcc == normMcc && it.mnc == normMnc && cell.enbOrGnbId > 0L && it.enbId == cell.enbOrGnbId
                }
                if (sameMast != null) {
                    return TowerLocation(
                        lat = sameMast.lat,
                        lon = sameMast.lon,
                        source = "CSV databáze (shodný stožár eNB)",
                        siteName = sameMast.siteName.ifBlank { "BTS eNB ${cell.enbOrGnbId}" },
                        addressOrNote = "Stožár eNB ${cell.enbOrGnbId} • ${cell.bandInfo.bandName}",
                        accuracyMeters = 40,
                        azimuthDeg = sameMast.azimuthDeg
                    )
                }
            }
        }

        if (normMcc.isEmpty() || cell.cellId <= 0L || cell.tacOrLac <= 0) return null
        val cacheKey = "${normMcc}_${normMnc}_${cell.tacOrLac}_${cell.cellId}"
        onlineTowerCache[cacheKey]?.let { return it }
        if (failedLookupKeys.contains(cacheKey)) return null

        return withContext(Dispatchers.IO) {
            val apiKey = _state.value.openCellIdApiKey
            if (apiKey.isNotBlank()) {
                queryOpenCellId(apiKey, normMcc, normMnc, cell)?.let { found ->
                    onlineTowerCache[cacheKey] = found
                    return@withContext found
                }
            }

            queryBeaconDb(normMcc, normMnc, cell)?.let { found ->
                onlineTowerCache[cacheKey] = found
                return@withContext found
            }

            failedLookupKeys.add(cacheKey)
            null
        }
    }

    private fun queryOpenCellId(apiKey: String, mcc: String, mnc: String, cell: CellMeasurement): TowerLocation? {
        return runCatching {
            val radio = when (cell.tech) {
                RadioTech.NR_5G -> "NR"
                RadioTech.LTE_4G -> "LTE"
                RadioTech.WCDMA_3G -> "UMTS"
                else -> "GSM"
            }
            val urlStr = "https://opencellid.org/cell/get?key=${Uri.encode(apiKey)}&mcc=$mcc&mnc=$mnc&lac=${cell.tacOrLac}&cellid=${cell.cellId}&radio=$radio&format=json"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                if (json.has("lat") && json.has("lon")) {
                    val lat = json.getDouble("lat")
                    val lon = json.getDouble("lon")
                    val range = json.optInt("range", 500)
                    return TowerLocation(
                        lat = lat,
                        lon = lon,
                        source = "OpenCelliD",
                        siteName = "${cell.operatorName} • eNB ${cell.enbOrGnbId}",
                        addressOrNote = "Cell ID ${cell.cellId} (TAC ${cell.tacOrLac})",
                        accuracyMeters = range,
                        azimuthDeg = null
                    )
                }
            }
            null
        }.getOrNull()
    }

    private fun queryBeaconDb(mcc: String, mnc: String, cell: CellMeasurement): TowerLocation? {
        return runCatching {
            val radio = when (cell.tech) {
                RadioTech.LTE_4G, RadioTech.NR_5G -> "lte"
                RadioTech.WCDMA_3G -> "wcdma"
                else -> "gsm"
            }
            val cellObj = JSONObject().apply {
                put("radioType", radio)
                put("mobileCountryCode", mcc.toInt())
                put("mobileNetworkCode", mnc.toInt())
                put("locationAreaCode", cell.tacOrLac)
                put("cellId", cell.cellId)
                if (cell.pciOrPsc > 0) put("psc", cell.pciOrPsc)
                put("signalStrength", cell.rsrpDbm)
            }
            val payload = JSONObject().apply {
                put("considerIp", false)
                put("cellTowers", JSONArray().put(cellObj))
            }.toString()

            val conn = (URL("https://api.beacondb.net/v1/geolocate").openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "BTSRadarPM/1.0")
            }
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val loc = json.optJSONObject("location") ?: return null
                val lat = loc.getDouble("lat")
                val lon = loc.getDouble("lng")
                val acc = json.optDouble("accuracy", 600.0).roundToInt()
                return TowerLocation(
                    lat = lat,
                    lon = lon,
                    source = "BeaconDB",
                    siteName = "${cell.operatorName} • eNB ${cell.enbOrGnbId}",
                    addressOrNote = "Odhad buňky ${cell.cellId} (±$acc m)",
                    accuracyMeters = acc,
                    azimuthDeg = null
                )
            }
            null
        }.getOrNull()
    }

    private fun readActiveSimSlots(): List<SimSlotInfo> {
        return runCatching {
            val subs = subscriptionManager.activeSubscriptionInfoList.orEmpty()
            subs.map { info ->
                val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.mccString.orEmpty() else info.mcc.toString()
                val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.mncString.orEmpty() else info.mnc.toString()
                SimSlotInfo(
                    subId = info.subscriptionId,
                    slotIndex = info.simSlotIndex + 1,
                    carrierName = info.carrierName?.toString().orEmpty().ifBlank { "SIM ${info.simSlotIndex + 1}" },
                    mccMnc = if (mcc.isNotBlank()) "$mcc-$mnc" else ""
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun buildNetworkSummaryLabel(tm: TelephonyManager, serving: List<CellMeasurement>): String {
        if (currentOverrideLabel.isNotBlank()) return currentOverrideLabel
        val hasNr = serving.any { it.tech == RadioTech.NR_5G }
        val hasLte = serving.any { it.tech == RadioTech.LTE_4G }
        if (hasNr && hasLte) return "5G NSA (LTE + NR)"
        if (hasNr) return "5G SA (Standalone)"
        if (hasLte) return if (serving.size > 1) "4G+ LTE-A" else "4G LTE"
        return runCatching {
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
                TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_GSM -> "2G GSM"
                else -> serving.firstOrNull()?.tech?.label ?: "Mobilní síť"
            }
        }.getOrDefault("Mobilní síť")
    }

    private fun readDataConnectionLabel(): String {
        return runCatching {
            val net = connectivityManager.activeNetwork ?: return "Nepřipojeno"
            val caps = connectivityManager.getNetworkCapabilities(net) ?: return "Nepřipojeno"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobilní data aktivní"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi (modem měří BTS)"
                else -> "Připojeno"
            }
        }.getOrDefault("---")
    }

    private suspend fun measureTcpLatencyMs(): Int? = withContext(Dispatchers.IO) {
        val targets = listOf("1.1.1.1" to 53, "8.8.8.8" to 53)
        for ((host, port) in targets) {
            val res = runCatching {
                val start = System.nanoTime()
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(host, port), 2000)
                }
                ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1)
            }.getOrNull()
            if (res != null) return@withContext res
        }
        null
    }

    private fun validInt(v: Int): Int? = if (v == Int.MAX_VALUE || v == Int.MIN_VALUE || v < 0) null else v
    private fun validLong(v: Long): Long? = if (v == Long.MAX_VALUE || v == Long.MIN_VALUE || v <= 0L) null else v

    fun dispose() {
        scanJob?.cancel()
        pingJob?.cancel()
        unregisterDisplayCallback()
        if (locationStarted) {
            runCatching { locationManager.removeUpdates(locationListener) }
            locationStarted = false
        }
    }
}

@Composable
actual fun rememberBtsScannerController(): BtsScannerController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { AndroidBtsScannerController(context.applicationContext, scope) }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        controller.checkAndAutoStart()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) controller.handleImportedCsvUri(uri)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) controller.writeExportCsvToUri(uri)
    }

    LaunchedEffect(controller) {
        controller.permissionRequester = {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE
                )
            )
        }
        controller.csvImportLauncher = {
            importLauncher.launch("*/*")
        }
        controller.csvExportLauncher = { suggestedName ->
            exportLauncher.launch(suggestedName)
        }
        controller.checkAndAutoStart()
    }

    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }

    return controller
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
actual fun BtsMapView(
    state: BtsRadarState,
    modifier: Modifier
) {
    val darkTheme = isSystemInDarkTheme()
    val primary = state.servingCells.firstOrNull()
    val tower = state.activeTowerLocation

    val jsUpdateCall = remember(
        state.userLat,
        state.userLon,
        tower?.lat,
        tower?.lon,
        tower?.siteName,
        primary?.enbOrGnbId,
        primary?.bandInfo?.bandCode,
        primary?.rsrpDbm,
        darkTheme
    ) {
        val uLat = state.userLat ?: 50.4253
        val uLon = state.userLon ?: 14.2617
        val hasUser = state.userLat != null && state.userLon != null
        val hasTower = tower != null
        val tLat = tower?.lat ?: 0.0
        val tLon = tower?.lon ?: 0.0
        val safeTitle = (tower?.siteName ?: "BTS").replace("'", "").replace("\"", "")
        val safeSub = "${primary?.bandInfo?.bandCode ?: ""} • ${primary?.rsrpDbm ?: ""} dBm".replace("'", "")
        "if(window.updateRadarMap){window.updateRadarMap($hasUser,$uLat,$uLon,$hasTower,$tLat,$tLon,'$safeTitle','$safeSub',$darkTheme);}"
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(jsUpdateCall, null)
                    }
                }
                loadDataWithBaseURL("https://unpkg.com/", buildLeafletHtml(), "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.evaluateJavascript(jsUpdateCall, null)
        }
    )
}

private fun buildLeafletHtml(): String {
    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
html, body, #map { margin:0; padding:0; width:100%; height:100%; background:#0B1320; }
.user-dot { width:16px; height:16px; border-radius:50%; background:#38BDF8; border:3px solid #FFFFFF; box-shadow:0 0 12px #0284C7; }
.bts-dot { width:20px; height:20px; border-radius:6px; background:#10B981; border:2px solid #FFFFFF; box-shadow:0 0 14px #10B981; }
</style>
</head>
<body>
<div id="map"></div>
<script>
var map = L.map('map', { zoomControl: true }).setView([50.4253, 14.2617], 13);
var lightTiles = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: 'OSM' });
var darkTiles = L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', { maxZoom: 19, attribution: 'OSM' });
var currentDark = null;
var userMarker = null;
var towerMarker = null;
var linkLine = null;
var centeredOnce = false;

window.updateRadarMap = function(hasUser, uLat, uLon, hasTower, tLat, tLon, tTitle, tSub, isDark) {
  if (currentDark !== isDark) {
    currentDark = isDark;
    if (isDark) { map.removeLayer(lightTiles); darkTiles.addTo(map); }
    else { map.removeLayer(darkTiles); lightTiles.addTo(map); }
  }
  if (hasUser) {
    var uIcon = L.divIcon({ className: 'user-dot', iconSize: [16, 16], iconAnchor: [8, 8] });
    if (!userMarker) { userMarker = L.marker([uLat, uLon], { icon: uIcon }).addTo(map).bindPopup('Můj telefon'); }
    else { userMarker.setLatLng([uLat, uLon]); }
  }
  if (hasTower) {
    var bIcon = L.divIcon({ className: 'bts-dot', iconSize: [20, 20], iconAnchor: [10, 10] });
    var pop = '<b>' + tTitle + '</b><br/>' + tSub;
    if (!towerMarker) { towerMarker = L.marker([tLat, tLon], { icon: bIcon }).addTo(map).bindPopup(pop); }
    else { towerMarker.setLatLng([tLat, tLon]); towerMarker.setPopupContent(pop); }
  } else if (towerMarker) {
    map.removeLayer(towerMarker); towerMarker = null;
  }
  if (hasUser && hasTower) {
    if (!linkLine) {
      linkLine = L.polyline([[uLat, uLon], [tLat, tLon]], { color: '#00E5FF', weight: 3, dashArray: '7,7' }).addTo(map);
    } else {
      linkLine.setLatLngs([[uLat, uLon], [tLat, tLon]]);
    }
    if (!centeredOnce) {
      centeredOnce = true;
      map.fitBounds([[uLat, uLon], [tLat, tLon]], { padding: [45, 45], maxZoom: 16 });
    }
  } else {
    if (linkLine) { map.removeLayer(linkLine); linkLine = null; }
    if (hasUser && !centeredOnce) { centeredOnce = true; map.setView([uLat, uLon], 15); }
  }
};
</script>
</body>
</html>
""".trimIndent()
}
