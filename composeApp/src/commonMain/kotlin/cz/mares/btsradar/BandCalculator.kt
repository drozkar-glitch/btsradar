package cz.mares.btsradar

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object BandCalculator {

    fun operatorNameFor(mcc: String, mnc: String, fallback: String): String {
        val cleanMnc = mnc.trim().padStart(2, '0')
        val code = "${mcc.trim()}-$cleanMnc"
        val known = when (code) {
            "230-01" -> "T-Mobile CZ"
            "230-02" -> "O2 CZ"
            "230-03" -> "Vodafone CZ"
            "230-04" -> "Nordic Telecom"
            "230-08" -> "Poda CZ"
            "262-01" -> "Telekom DE"
            "262-02" -> "Vodafone DE"
            "262-03" -> "o2 DE"
            "260-01" -> "Plus PL"
            "260-02" -> "T-Mobile PL"
            "260-03" -> "Orange PL"
            "260-06" -> "Play PL"
            "231-01" -> "Orange SK"
            "231-02" -> "Telekom SK"
            "231-06" -> "O2 SK"
            "232-01" -> "A1 AT"
            "232-03" -> "Magenta AT"
            "232-05" -> "Drei AT"
            else -> ""
        }
        return when {
            fallback.isNotBlank() && fallback != "null" -> fallback
            known.isNotEmpty() -> known
            mcc.isNotBlank() -> "Síť $code"
            else -> "Neznámý operátor"
        }
    }

    fun resolveBand(tech: RadioTech, channel: Int): BandDetails {
        if (channel <= 0 || channel == Int.MAX_VALUE) {
            return BandDetails("Neznámé pásmo", "---", 0.0, "---", "---")
        }
        return when (tech) {
            RadioTech.LTE_4G -> resolveLteEarfcn(channel)
            RadioTech.NR_5G -> resolveNrArfcn(channel)
            RadioTech.WCDMA_3G -> resolveWcdmaUarfcn(channel)
            RadioTech.GSM_2G -> resolveGsmArfcn(channel)
            RadioTech.UNKNOWN -> BandDetails("Neznámé", "---", 0.0, "---", "$channel")
        }
    }

    private fun resolveLteEarfcn(earfcn: Int): BandDetails {
        return when (earfcn) {
            in 0..599 -> BandDetails("LTE 2100", "B1", round1(2110.0 + 0.1 * earfcn), "FDD", "EARFCN $earfcn")
            in 1200..1949 -> BandDetails("LTE 1800", "B3", round1(1805.0 + 0.1 * (earfcn - 1200)), "FDD", "EARFCN $earfcn")
            in 2750..3449 -> BandDetails("LTE 2600", "B7", round1(2620.0 + 0.1 * (earfcn - 2750)), "FDD", "EARFCN $earfcn")
            in 3450..3799 -> BandDetails("LTE 900", "B8", round1(925.0 + 0.1 * (earfcn - 3450)), "FDD", "EARFCN $earfcn")
            in 6150..6449 -> BandDetails("LTE 800", "B20", round1(791.0 + 0.1 * (earfcn - 6150)), "FDD", "EARFCN $earfcn")
            in 9210..9659 -> BandDetails("LTE 700", "B28", round1(758.0 + 0.1 * (earfcn - 9210)), "FDD", "EARFCN $earfcn")
            in 9920..10359 -> BandDetails("LTE 1500 SDL", "B32", round1(1452.0 + 0.1 * (earfcn - 9920)), "SDL", "EARFCN $earfcn")
            in 37750..38249 -> BandDetails("LTE 2600 TDD", "B38", round1(2570.0 + 0.1 * (earfcn - 37750)), "TDD", "EARFCN $earfcn")
            in 38650..39649 -> BandDetails("LTE 2300 TDD", "B40", round1(2300.0 + 0.1 * (earfcn - 38650)), "TDD", "EARFCN $earfcn")
            in 41590..43589 -> BandDetails("LTE 3500 TDD", "B42", round1(3400.0 + 0.1 * (earfcn - 41590)), "TDD", "EARFCN $earfcn")
            in 43590..45589 -> BandDetails("LTE 3700 TDD", "B43", round1(3600.0 + 0.1 * (earfcn - 43590)), "TDD", "EARFCN $earfcn")
            else -> BandDetails("LTE kanál $earfcn", "LTE", 0.0, "---", "EARFCN $earfcn")
        }
    }

    private fun resolveNrArfcn(nrarfcn: Int): BandDetails {
        val mhz = when {
            nrarfcn in 0..599999 -> 0.005 * nrarfcn
            nrarfcn in 600000..2016666 -> 3000.0 + 0.015 * (nrarfcn - 600000)
            else -> 0.0
        }
        val rounded = round1(mhz)
        return when {
            mhz in 758.0..790.9 -> BandDetails("5G 700", "n28", rounded, "FDD", "NR-ARFCN $nrarfcn")
            mhz in 791.0..821.0 -> BandDetails("5G 800", "n20", rounded, "FDD", "NR-ARFCN $nrarfcn")
            mhz in 925.0..960.0 -> BandDetails("5G 900", "n8", rounded, "FDD", "NR-ARFCN $nrarfcn")
            mhz in 1805.0..1880.0 -> BandDetails("5G 1800", "n3", rounded, "FDD", "NR-ARFCN $nrarfcn")
            mhz in 2110.0..2170.0 -> BandDetails("5G 2100", "n1", rounded, "FDD", "NR-ARFCN $nrarfcn")
            mhz in 2570.0..2619.9 -> BandDetails("5G 2600 TDD", "n38", rounded, "TDD", "NR-ARFCN $nrarfcn")
            mhz in 2620.0..2690.0 -> BandDetails("5G 2600", "n7", rounded, "FDD", "NR-ARFCN $nrarfcn")
            mhz in 3300.0..3800.0 -> BandDetails("5G 3500 C-Band", "n78", rounded, "TDD", "NR-ARFCN $nrarfcn")
            mhz in 3800.1..4200.0 -> BandDetails("5G 3900", "n77", rounded, "TDD", "NR-ARFCN $nrarfcn")
            else -> BandDetails("5G NR", "NR", rounded, "---", "NR-ARFCN $nrarfcn")
        }
    }

    private fun resolveWcdmaUarfcn(uarfcn: Int): BandDetails {
        return when (uarfcn) {
            in 10562..10838 -> BandDetails("3G 2100", "U2100", round1(uarfcn / 5.0), "FDD", "UARFCN $uarfcn")
            in 2937..3088 -> BandDetails("3G 900", "U900", round1(uarfcn / 5.0 + 340.0), "FDD", "UARFCN $uarfcn")
            else -> BandDetails("3G UMTS", "3G", 0.0, "FDD", "UARFCN $uarfcn")
        }
    }

    private fun resolveGsmArfcn(arfcn: Int): BandDetails {
        return when (arfcn) {
            in 1..124 -> BandDetails("GSM 900", "G900", round1(935.0 + 0.2 * arfcn), "FDD", "ARFCN $arfcn")
            in 975..1023 -> BandDetails("E-GSM 900", "G900", round1(935.0 + 0.2 * (arfcn - 1024)), "FDD", "ARFCN $arfcn")
            in 512..885 -> BandDetails("DCS 1800", "G1800", round1(1805.2 + 0.2 * (arfcn - 512)), "FDD", "ARFCN $arfcn")
            else -> BandDetails("2G GSM", "GSM", 0.0, "FDD", "ARFCN $arfcn")
        }
    }

    fun evaluateQuality(rsrpDbm: Int, tech: RadioTech): SignalQualityLevel {
        if (rsrpDbm >= -30 || rsrpDbm <= -150) return SignalQualityLevel.NONE
        return if (tech == RadioTech.GSM_2G) {
            when {
                rsrpDbm >= -75 -> SignalQualityLevel.EXCELLENT
                rsrpDbm >= -88 -> SignalQualityLevel.GOOD
                rsrpDbm >= -98 -> SignalQualityLevel.FAIR
                else -> SignalQualityLevel.POOR
            }
        } else {
            when {
                rsrpDbm >= -85 -> SignalQualityLevel.EXCELLENT
                rsrpDbm >= -98 -> SignalQualityLevel.GOOD
                rsrpDbm >= -110 -> SignalQualityLevel.FAIR
                else -> SignalQualityLevel.POOR
            }
        }
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val r = 6371000.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2.0).pow(2) +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLon / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return (r * c).roundToInt()
    }

    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val phi1 = lat1 * PI / 180.0
        val phi2 = lat2 * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        val deg = (atan2(y, x) * 180.0 / PI + 360.0) % 360.0
        return deg.roundToInt() % 360
    }

    fun compassLabel(deg: Int): String {
        val dirs = listOf("S", "SV", "V", "JV", "J", "JZ", "Z", "SZ")
        val idx = (((deg % 360) + 22.5) / 45.0).toInt() % 8
        return dirs[idx]
    }

    fun formatFreqMhz(mhz: Double): String {
        if (mhz <= 0.0) return "---"
        val tenths = (mhz * 10.0).roundToInt()
        return if (tenths % 10 == 0) {
            "${tenths / 10} MHz"
        } else {
            "${tenths / 10}.${tenths % 10} MHz"
        }
    }

    fun formatCoord(value: Double?): String {
        if (value == null) return "---"
        val scaled = (value * 100000.0).roundToInt()
        val sign = if (scaled < 0) "-" else ""
        val absVal = kotlin.math.abs(scaled)
        val whole = absVal / 100000
        val frac = (absVal % 100000).toString().padStart(5, '0')
        return "$sign$whole.$frac"
    }

    fun buildRouteCsv(items: List<LoggedCellVisit>): String {
        val sb = StringBuilder()
        sb.appendLine("Prvni_cas;Posledni_cas;Vzorky;Operator;MCC_MNC;Sit;eNB_gNB;Sektor;CellID;TAC;PCI;Pasmo;Kanal;Max_RSRP_dBm;Posl_RSRP_dBm;Max_SINR_dB;Tel_Lat;Tel_Lon;Lokalita_BTS;Vzdalenost_m")
        for (it in items) {
            val cleanSite = it.towerSiteName.replace(';', ',')
            sb.appendLine(
                "${it.firstSeenText};${it.lastSeenText};${it.samplesCount};${it.operatorName};${it.mccMnc};" +
                    "${it.techLabel};${it.enbId};${it.sectorId};${it.cellId};${it.tac};${it.pci};" +
                    "${it.bandLabel};${it.channel};${it.bestRsrpDbm};${it.lastRsrpDbm};${it.bestSinrDb ?: ""};" +
                    "${formatCoord(it.userLat)};${formatCoord(it.userLon)};$cleanSite;${it.distanceMeters ?: ""}"
            )
        }
        return sb.toString()
    }

    fun parseTowerDatabase(rawText: String): List<TowerCsvEntry> {
        val lines = rawText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("#") }.toList()
        if (lines.isEmpty()) return emptyList()

        val result = ArrayList<TowerCsvEntry>(lines.size.coerceAtMost(80000))
        val firstLine = lines.first()
        val delimiter = if (firstLine.count { it == ';' } >= firstLine.count { it == ',' }) ';' else ','
        val firstCols = splitCsvLine(firstLine, delimiter).map { it.lowercase() }

        val hasHeader = firstCols.any {
            it.contains("lat") || it.contains("lon") || it.contains("cid") || it.contains("cell") ||
                it.contains("enb") || it.contains("lokalita") || it.contains("misto") || it.contains("gps")
        }

        if (!hasHeader && delimiter == ';' && firstCols.size >= 6 && firstCols[0].length in 5..6 && firstCols[0].all { it.isDigit() }) {
            // Format CLF v3.0 z GSMweb.cz (MCCMNC;CID;LAC;RNC;LAT;LON;RAT;DESCRIPTION;AZIMUTH)
            for (line in lines) {
                val cols = splitCsvLine(line, ';')
                if (cols.size < 6) continue
                val mccmnc = cols[0].trim()
                if (mccmnc.length < 5) continue
                val mcc = mccmnc.substring(0, 3)
                val mnc = mccmnc.substring(3).trimStart('0').ifEmpty { "0" }
                val cid = parseId(cols[1]) ?: continue
                val tac = parseId(cols[2])?.toInt() ?: 0
                val rncOrEnb = parseId(cols[3]) ?: 0L
                val lat = parseCoordinate(cols[4]) ?: continue
                val lon = parseCoordinate(cols[5]) ?: continue
                if (lat !in 40.0..60.0 || lon !in 5.0..30.0) continue
                val desc = cols.getOrNull(7)?.trim()?.trim('"') ?: ""
                val az = cols.getOrNull(8)?.trim()?.toIntOrNull()?.takeIf { it in 0..360 }
                val enb = when {
                    cid > 65535L -> cid shr 8
                    rncOrEnb > 0L -> rncOrEnb
                    else -> cid
                }
                result.add(TowerCsvEntry(mcc, mnc, cid, enb, tac, lat, lon, desc, az))
            }
            return result
        }

        val startIndex = if (hasHeader) 1 else 0
        val idxMcc = firstCols.indexOfFirst { it == "mcc" }
        val idxMnc = firstCols.indexOfFirst { it == "mnc" || it == "net" || it.contains("oper") }
        val idxCell = firstCols.indexOfFirst { it == "cell" || it == "cid" || it == "cellid" || it == "eci" || it == "nci" }
        val idxEnb = firstCols.indexOfFirst { it.contains("enb") || it.contains("gnb") }
        val idxTac = firstCols.indexOfFirst { it == "tac" || it == "lac" || it == "area" }
        val idxLat = firstCols.indexOfFirst { it == "lat" || it.contains("latitude") || it.contains("sirka") || it.contains("šířka") }
        val idxLon = firstCols.indexOfFirst { it == "lon" || it == "lng" || it.contains("longitude") || it.contains("delka") || it.contains("délka") }
        val idxGps = firstCols.indexOfFirst { it.contains("gps") || it.contains("sourad") || it.contains("souřad") }
        val idxName = firstCols.indexOfFirst { it.contains("lokal") || it.contains("misto") || it.contains("místo") || it.contains("nazev") || it.contains("název") || it.contains("obec") || it.contains("popis") || it == "description" }
        val idxAz = firstCols.indexOfFirst { it.contains("azim") }

        for (i in startIndex until lines.size) {
            val cols = splitCsvLine(lines[i], delimiter)
            if (cols.size < 3) continue

            val mcc = if (idxMcc >= 0) cols.getOrNull(idxMcc)?.trim().orEmpty().ifEmpty { "230" } else "230"
            val rawMnc = if (idxMnc >= 0) cols.getOrNull(idxMnc)?.trim().orEmpty() else ""
            val mnc = when {
                rawMnc.contains("t-mobile", ignoreCase = true) -> "1"
                rawMnc.contains("o2", ignoreCase = true) || rawMnc.contains("cetin", ignoreCase = true) -> "2"
                rawMnc.contains("vodafone", ignoreCase = true) -> "3"
                else -> rawMnc.trimStart('0').ifEmpty { "0" }
            }

            val cid = (if (idxCell >= 0) parseId(cols.getOrNull(idxCell)) else parseId(cols.getOrNull(0))) ?: 0L
            val enbRaw = if (idxEnb >= 0) parseId(cols.getOrNull(idxEnb)) else null
            val enb = enbRaw ?: if (cid > 65535L) (cid shr 8) else cid
            if (cid <= 0L && enb <= 0L) continue

            var lat: Double? = if (idxLat >= 0) parseCoordinate(cols.getOrNull(idxLat)) else null
            var lon: Double? = if (idxLon >= 0) parseCoordinate(cols.getOrNull(idxLon)) else null

            if ((lat == null || lon == null) && idxGps >= 0) {
                val pair = parseCombinedGps(cols.getOrNull(idxGps).orEmpty())
                if (pair != null) {
                    lat = pair.first
                    lon = pair.second
                }
            }
            val finalLat = lat ?: continue
            val finalLon = lon ?: continue
            if (finalLat !in -85.0..85.0 || finalLon !in -180.0..180.0) continue

            val tac = if (idxTac >= 0) parseId(cols.getOrNull(idxTac))?.toInt() ?: 0 else 0
            val name = if (idxName >= 0) cols.getOrNull(idxName)?.trim()?.trim('"').orEmpty() else ""
            val az = if (idxAz >= 0) cols.getOrNull(idxAz)?.trim()?.toIntOrNull()?.takeIf { it in 0..360 } else null

            result.add(TowerCsvEntry(mcc, mnc, if (cid > 0L) cid else enb, enb, tac, finalLat, finalLon, name, az))
        }
        return result
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val out = ArrayList<String>(12)
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    out.add(cur.toString())
                    cur.clear()
                }
                else -> cur.append(ch)
            }
        }
        out.add(cur.toString())
        return out
    }

    private fun parseId(raw: String?): Long? {
        val s = raw?.trim()?.trim('"').orEmpty()
        if (s.isEmpty()) return null
        return if (s.startsWith("0x", ignoreCase = true)) {
            s.substring(2).toLongOrNull(16)
        } else {
            s.toLongOrNull() ?: s.substringBefore('-').substringBefore('/').toLongOrNull()
        }
    }

    private fun parseCoordinate(raw: String?): Double? {
        val s = raw?.trim()?.trim('"')?.replace(',', '.').orEmpty()
        if (s.isEmpty()) return null
        s.toDoubleOrNull()?.let { return it }
        return parseDmsSingle(s)
    }

    private fun parseCombinedGps(raw: String): Pair<Double, Double>? {
        val clean = raw.trim().trim('"')
        if (clean.isEmpty()) return null
        val parts = clean.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
        if (parts.size == 2) {
            val la = parseCoordinate(parts[0])
            val lo = parseCoordinate(parts[1])
            if (la != null && lo != null) return la to lo
        }
        val nSplit = clean.indexOfFirst { it == 'N' || it == 'S' }
        if (nSplit > 0 && nSplit < clean.length - 2) {
            val la = parseDmsSingle(clean.substring(0, nSplit + 1))
            val lo = parseDmsSingle(clean.substring(nSplit + 1))
            if (la != null && lo != null) return la to lo
        }
        return null
    }

    private fun parseDmsSingle(dms: String): Double? {
        val nums = Regex("[0-9]+(?:\\.[0-9]+)?").findAll(dms.replace(',', '.')).mapNotNull { it.value.toDoubleOrNull() }.toList()
        if (nums.isEmpty()) return null
        val deg = nums[0]
        val min = nums.getOrNull(1) ?: 0.0
        val sec = nums.getOrNull(2) ?: 0.0
        val sign = if (dms.contains('S', ignoreCase = true) || dms.contains('W', ignoreCase = true) || dms.trim().startsWith('-')) -1.0 else 1.0
        return sign * (deg + min / 60.0 + sec / 3600.0)
    }

    private fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0
}
