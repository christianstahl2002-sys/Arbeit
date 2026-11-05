package de.example.aussendienst

import android.content.Context
import android.location.Location
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Locally bundled reference points and optional official whole-kilometre points.
 * The app never interpolates a continuous kilometre value from GPS alone.
 */
data class AutobahnKmReference(
    val name: String,
    val autobahn: String,
    val kilometer: Double,
    val latitude: Double?,
    val longitude: Double?,
    val source: String,
    val note: String,
    val direction: String = ""
)

data class AutobahnKmMatch(
    val reference: AutobahnKmReference,
    val distanceMeters: Float
)

data class AutobahnKmImportSummary(
    val count: Int,
    val a4Count: Int,
    val a71Count: Int
)

object AutobahnKilometer {
    private const val IMPORT_FILE = "autobahn_km_bkg_import.csv"
    private const val BKG_SOURCE = "BKG POI-Open KmBAB"

    fun load(context: Context): List<AutobahnKmReference> {
        val bundled = runCatching {
            context.assets.open("autobahn_kilometer_a4_a71.csv").bufferedReader(Charsets.UTF_8).use { reader ->
                parseAppCsv(reader.readText())
            }
        }.getOrDefault(emptyList())
        val imported = runCatching {
            val file = File(context.filesDir, IMPORT_FILE)
            if (file.exists()) parseAppCsv(file.readText(Charsets.UTF_8)) else emptyList()
        }.getOrDefault(emptyList())
        return (bundled + imported).distinctBy {
            listOf(it.autobahn, formatKm(it.kilometer), it.direction, it.latitude?.toString(), it.longitude?.toString()).joinToString("|")
        }
    }

    /**
     * Import BKG KmBAB GeoJSON or ArcGIS JSON. Coordinates must already be WGS84 (longitude/latitude).
     * Only the user's requested corridor is retained: A4 km 190-335 and A71 km 50-90.
     */
    fun importBkgJson(context: Context, jsonText: String): AutobahnKmImportSummary {
        val root = JSONObject(jsonText)
        val features = root.optJSONArray("features") ?: throw IllegalArgumentException("Keine Feature-Liste gefunden. Bitte BKG-GeoJSON oder ArcGIS-JSON wählen.")
        val parsed = mutableListOf<AutobahnKmReference>()
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val properties = feature.optJSONObject("properties")
                ?: feature.optJSONObject("attributes")
                ?: continue
            val geometry = feature.optJSONObject("geometry") ?: continue
            val motorway = normalizeAutobahn(firstString(properties, "Bez", "Autobahn", "BAB")) ?: continue
            val km = firstDouble(properties, "Kilometer", "km", "Kilometrierung") ?: continue
            val direction = firstString(properties, "Richtung", "Direction")
            val coords = geometry.optJSONArray("coordinates")
            val x: Double
            val y: Double
            if (coords != null && coords.length() >= 2) {
                x = coords.optDouble(0, Double.NaN)
                y = coords.optDouble(1, Double.NaN)
            } else {
                x = geometry.optDouble("x", Double.NaN)
                y = geometry.optDouble("y", Double.NaN)
            }
            if (!isWgs84(x, y)) continue
            if (!inRequestedCorridor(motorway, km)) continue
            val kmLabel = formatKm(km)
            parsed += AutobahnKmReference(
                name = "$motorway km $kmLabel",
                autobahn = motorway,
                kilometer = km,
                latitude = y,
                longitude = x,
                source = BKG_SOURCE,
                note = "BKG-Kilometerpunkt; automatisch berechnet, vor Ort nicht abgeglichen",
                direction = direction
            )
        }
        return saveImported(context, parsed)
    }

    /** Import BKG CSV with Bez, Kilometer, Richtung and WGS84 X/Y or longitude/latitude columns. */
    fun importBkgCsv(context: Context, csvText: String): AutobahnKmImportSummary {
        val lines = csvText.removePrefix("\uFEFF").lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) throw IllegalArgumentException("Die CSV-Datei enthält keine Datenzeilen.")
        val delimiter = if (countOutsideQuotes(lines.first(), ';') > countOutsideQuotes(lines.first(), ',')) ';' else ','
        val header = splitCsvLine(lines.first(), delimiter).map { normalizeHeader(it) }
        fun index(vararg names: String): Int = header.indexOfFirst { it in names.map(::normalizeHeader) }
        val motorwayIndex = index("Bez", "Autobahn", "BAB")
        val kmIndex = index("Kilometer", "km", "Kilometrierung")
        val directionIndex = index("Richtung", "Direction")
        val longitudeIndex = index("longitude", "lon", "x")
        val latitudeIndex = index("latitude", "lat", "y")
        if (motorwayIndex < 0 || kmIndex < 0 || longitudeIndex < 0 || latitudeIndex < 0) {
            throw IllegalArgumentException("CSV-Spalten fehlen. Benötigt werden Bez, Kilometer, Richtung sowie X/Y oder longitude/latitude. Koordinaten müssen WGS84 sein (outSR=4326).")
        }
        val parsed = mutableListOf<AutobahnKmReference>()
        lines.drop(1).forEach { line ->
            val row = splitCsvLine(line, delimiter)
            fun cell(i: Int): String = row.getOrNull(i)?.trim().orEmpty()
            val motorway = normalizeAutobahn(cell(motorwayIndex)) ?: return@forEach
            val km = cell(kmIndex).replace(',', '.').toDoubleOrNull() ?: return@forEach
            val lon = cell(longitudeIndex).replace(',', '.').toDoubleOrNull() ?: return@forEach
            val lat = cell(latitudeIndex).replace(',', '.').toDoubleOrNull() ?: return@forEach
            if (!isWgs84(lon, lat) || !inRequestedCorridor(motorway, km)) return@forEach
            val direction = if (directionIndex >= 0) cell(directionIndex) else ""
            parsed += AutobahnKmReference(
                name = "$motorway km ${formatKm(km)}",
                autobahn = motorway,
                kilometer = km,
                latitude = lat,
                longitude = lon,
                source = BKG_SOURCE,
                note = "BKG-Kilometerpunkt; automatisch berechnet, vor Ort nicht abgeglichen",
                direction = direction
            )
        }
        return saveImported(context, parsed)
    }

    private fun saveImported(context: Context, points: List<AutobahnKmReference>): AutobahnKmImportSummary {
        if (points.isEmpty()) {
            throw IllegalArgumentException("Keine passenden A4-/A71-Kilometerpunkte mit WGS84-Koordinaten gefunden. Bitte den Korridor A4 km 190–335 / A71 km 50–90 und EPSG:4326 / WGS84 exportieren.")
        }
        val unique = points.distinctBy {
            listOf(it.autobahn, formatKm(it.kilometer), it.direction, it.latitude?.let { v -> String.format(Locale.US, "%.6f", v) }, it.longitude?.let { v -> String.format(Locale.US, "%.6f", v) }).joinToString("|")
        }
        val file = File(context.filesDir, IMPORT_FILE)
        file.writeText(buildString {
            append("name;autobahn;km;latitude;longitude;quelle;hinweis;richtung\n")
            unique.forEach { p ->
                append(listOf(p.name, p.autobahn, p.kilometer.toString(), p.latitude.toString(), p.longitude.toString(), p.source, p.note, p.direction).joinToString(";") { csvCell(it) })
                append('\n')
            }
        }, Charsets.UTF_8)
        return AutobahnKmImportSummary(unique.size, unique.count { it.autobahn == "A4" }, unique.count { it.autobahn == "A71" })
    }

    private fun parseAppCsv(text: String): List<AutobahnKmReference> {
        val rows = text.removePrefix("\uFEFF").lineSequence().filter { it.isNotBlank() }.toList()
        if (rows.size < 2) return emptyList()
        val header = splitCsvLine(rows.first(), ';').map { normalizeHeader(it) }
        val idx = { varargNames: Array<out String> -> header.indexOfFirst { it in varargNames.map(::normalizeHeader) } }
        val nameI = idx(arrayOf("name")); val roadI = idx(arrayOf("autobahn", "bez")); val kmI = idx(arrayOf("km", "kilometer"))
        val latI = idx(arrayOf("latitude", "lat")); val lonI = idx(arrayOf("longitude", "lon")); val sourceI = idx(arrayOf("quelle", "source")); val noteI = idx(arrayOf("hinweis", "note")); val directionI = idx(arrayOf("richtung", "direction"))
        if (roadI < 0 || kmI < 0) return emptyList()
        return rows.drop(1).mapNotNull { line ->
            val cells = splitCsvLine(line, ';')
            fun cell(i: Int) = if (i >= 0) cells.getOrNull(i)?.trim().orEmpty() else ""
            val road = normalizeAutobahn(cell(roadI)) ?: return@mapNotNull null
            val km = cell(kmI).replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            AutobahnKmReference(
                name = cell(nameI).ifBlank { "$road km ${formatKm(km)}" },
                autobahn = road,
                kilometer = km,
                latitude = cell(latI).takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull(),
                longitude = cell(lonI).takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull(),
                source = cell(sourceI).ifBlank { "Lokale Referenz" },
                note = cell(noteI),
                direction = cell(directionI)
            )
        }
    }

    private fun firstString(obj: JSONObject, vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
        if (!obj.has(key) || obj.isNull(key)) null else obj.opt(key)?.toString()?.takeIf { it.isNotBlank() }
    }.orEmpty()

    private fun firstDouble(obj: JSONObject, vararg keys: String): Double? = keys.firstNotNullOfOrNull { key ->
        if (!obj.has(key) || obj.isNull(key)) null else obj.optDouble(key, Double.NaN).takeIf { it.isFinite() }
    }

    private fun normalizeAutobahn(raw: String): String? {
        val match = Regex("A\\s*(\\d+)", RegexOption.IGNORE_CASE).find(raw.trim()) ?: return null
        return "A${match.groupValues[1]}"
    }

    private fun inRequestedCorridor(road: String, km: Double): Boolean = when (road) {
        "A4" -> km in 190.0..335.0
        "A71" -> km in 50.0..90.0
        else -> false
    }

    private fun isWgs84(lon: Double, lat: Double): Boolean = lon.isFinite() && lat.isFinite() && lon in -180.0..180.0 && lat in -90.0..90.0

    private fun normalizeHeader(value: String): String = value.trim().removePrefix("\uFEFF").trim('"').lowercase(Locale.ROOT).replace(Regex("[\\s_]+"), "")

    private fun countOutsideQuotes(value: String, target: Char): Int {
        var quoted = false; var count = 0; var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '"') {
                if (quoted && i + 1 < value.length && value[i + 1] == '"') i++ else quoted = !quoted
            } else if (!quoted && c == target) count++
            i++
        }
        return count
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (quoted && i + 1 < line.length && line[i + 1] == '"') { cell.append('"'); i++ }
                else quoted = !quoted
            } else if (c == delimiter && !quoted) {
                result += cell.toString(); cell.setLength(0)
            } else cell.append(c)
            i++
        }
        result += cell.toString()
        return result
    }

    private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    /** Returns the closest referenced point per motorway, but only inside the specified radius. */
    fun nearby(
        latitude: Double,
        longitude: Double,
        references: List<AutobahnKmReference>,
        maxDistanceMeters: Float = 800f
    ): List<AutobahnKmMatch> {
        val candidates = references.mapNotNull { ref ->
            val lat = ref.latitude ?: return@mapNotNull null
            val lon = ref.longitude ?: return@mapNotNull null
            val out = FloatArray(1)
            Location.distanceBetween(latitude, longitude, lat, lon, out)
            if (out[0] <= maxDistanceMeters) AutobahnKmMatch(ref, out[0]) else null
        }
        return candidates.groupBy { "${it.reference.autobahn}|${it.reference.direction}" }
            .values.mapNotNull { group -> group.minByOrNull { it.distanceMeters } }
            .sortedBy { it.reference.autobahn }
    }

    fun formatKm(value: Double): String = String.format(Locale.GERMANY, "%.1f", value)
    fun formatDistance(value: Float): String = "${value.toInt()} m"
    fun label(reference: AutobahnKmReference): String = buildString {
        append("${reference.autobahn} km ${formatKm(reference.kilometer)}")
        if (reference.direction.isNotBlank()) append(" · Richtung ${reference.direction}")
    }
}
