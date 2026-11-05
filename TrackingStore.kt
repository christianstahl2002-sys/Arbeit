package de.example.aussendienst

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import kotlin.math.*

internal data class ShiftRecord(
    val id: Long,
    val startMillis: Long,
    val endMillis: Long?,
    val officeLat: Double,
    val officeLon: Double,
    val radiusMeters: Float
)

internal data class TrackPoint(
    val timeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float
)

internal data class KnownPoi(
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double
)

internal data class VisitStop(
    val id: Long,
    val arrivalMillis: Long,
    val departureMillis: Long?,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val label: String,
    val source: String
)

internal data class LocationEvent(
    val timeMillis: Long,
    val type: String,
    val source: String,
    val latitude: Double?,
    val longitude: Double?
)

internal data class OutsideInterval(val startMillis: Long, val endMillis: Long?)

internal class TrackingStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    private val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun saveOffice(location: Location) {
        settings.edit().putLong("office_lat", java.lang.Double.doubleToRawLongBits(location.latitude))
            .putLong("office_lon", java.lang.Double.doubleToRawLongBits(location.longitude)).apply()
    }
    fun officeLocation(): Location? {
        if (!settings.contains("office_lat") || !settings.contains("office_lon")) return null
        return Location("saved-office").apply {
            latitude = java.lang.Double.longBitsToDouble(settings.getLong("office_lat", 0L))
            longitude = java.lang.Double.longBitsToDouble(settings.getLong("office_lon", 0L))
        }
    }
    fun radiusMeters(): Float = settings.getFloat("office_radius", 150f)
    fun setRadiusMeters(value: Float) { settings.edit().putFloat("office_radius", value.coerceIn(50f, 500f)).apply() }
    fun intervalSeconds(): Int = settings.getInt("interval_seconds", 15).coerceIn(10, 60)
    fun setIntervalSeconds(value: Int) { settings.edit().putInt("interval_seconds", value.coerceIn(10, 60)).apply() }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE shifts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            start_ms INTEGER NOT NULL,
            end_ms INTEGER,
            office_lat REAL NOT NULL,
            office_lon REAL NOT NULL,
            radius_m REAL NOT NULL,
            outside_state INTEGER,
            active_stop_id INTEGER,
            candidate_start_ms INTEGER,
            candidate_lat REAL,
            candidate_lon REAL
        )""")
        db.execSQL("""CREATE TABLE track_points (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            shift_id INTEGER NOT NULL,
            time_ms INTEGER NOT NULL,
            lat REAL NOT NULL,
            lon REAL NOT NULL,
            accuracy REAL NOT NULL,
            speed REAL NOT NULL,
            FOREIGN KEY(shift_id) REFERENCES shifts(id) ON DELETE CASCADE
        )""")
        db.execSQL("CREATE INDEX idx_points_shift_time ON track_points(shift_id, time_ms)")
        db.execSQL("""CREATE TABLE events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            shift_id INTEGER NOT NULL,
            time_ms INTEGER NOT NULL,
            type TEXT NOT NULL,
            source TEXT NOT NULL,
            lat REAL,
            lon REAL,
            FOREIGN KEY(shift_id) REFERENCES shifts(id) ON DELETE CASCADE
        )""")
        db.execSQL("CREATE INDEX idx_events_shift_time ON events(shift_id, time_ms)")
        db.execSQL("""CREATE TABLE stops (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            shift_id INTEGER NOT NULL,
            arrival_ms INTEGER NOT NULL,
            departure_ms INTEGER,
            lat REAL NOT NULL,
            lon REAL NOT NULL,
            category TEXT NOT NULL,
            label TEXT NOT NULL,
            source TEXT NOT NULL,
            FOREIGN KEY(shift_id) REFERENCES shifts(id) ON DELETE CASCADE
        )""")
        db.execSQL("CREATE INDEX idx_stops_shift_arrival ON stops(shift_id, arrival_ms)")
        db.execSQL("""CREATE TABLE pois (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            category TEXT NOT NULL,
            lat REAL NOT NULL,
            lon REAL NOT NULL
        )""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // The first distributed version uses a dedicated v2 database. Keep this migration safe.
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS track_points")
            db.execSQL("DROP TABLE IF EXISTS events")
            db.execSQL("DROP TABLE IF EXISTS stops")
            db.execSQL("DROP TABLE IF EXISTS shifts")
            db.execSQL("DROP TABLE IF EXISTS pois")
            onCreate(db)
        }
    }

    @Synchronized
    fun startShift(officeLat: Double, officeLon: Double, radiusMeters: Float, firstLocation: Location?): Long {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("start_ms", now)
            put("office_lat", officeLat)
            put("office_lon", officeLon)
            put("radius_m", radiusMeters)
        }
        if (firstLocation != null) {
            val outside = distanceMeters(firstLocation.latitude, firstLocation.longitude, officeLat, officeLon) > radiusMeters
            values.put("outside_state", if (outside) 1 else 0)
        }
        val id = db.insertOrThrow("shifts", null, values)
        if (firstLocation != null) {
            insertPoint(db, id, firstLocation)
            val outside = distanceMeters(firstLocation.latitude, firstLocation.longitude, officeLat, officeLon) > radiusMeters
            if (outside) insertEvent(db, id, now, EVENT_EXIT, "Schichtstart außerhalb des Dienststellenbereichs", firstLocation.latitude, firstLocation.longitude)
        }
        return id
    }

    @Synchronized
    fun endShift(atMillis: Long = System.currentTimeMillis()) {
        val shift = activeShift() ?: return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val activeStopId = shiftLong(db, shift.id, "active_stop_id")
            if (activeStopId != null) {
                val cv = ContentValues().apply { put("departure_ms", atMillis) }
                db.update("stops", cv, "id=? AND departure_ms IS NULL", arrayOf(activeStopId.toString()))
            }
            val cv = ContentValues().apply { put("end_ms", atMillis); putNull("active_stop_id") }
            db.update("shifts", cv, "id=?", arrayOf(shift.id.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun activeShift(): ShiftRecord? = readableDatabase.rawQuery(
        "SELECT id,start_ms,end_ms,office_lat,office_lon,radius_m FROM shifts WHERE end_ms IS NULL ORDER BY id DESC LIMIT 1", null
    ).use { c -> if (!c.moveToFirst()) null else shiftFrom(c) }

    fun shifts(limit: Int = 20): List<ShiftRecord> = readableDatabase.rawQuery(
        "SELECT id,start_ms,end_ms,office_lat,office_lon,radius_m FROM shifts ORDER BY start_ms DESC LIMIT ?", arrayOf(limit.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(shiftFrom(c)) } }

    @Synchronized
    fun recordLocation(location: Location) {
        val shift = activeShift() ?: return
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return
        if (location.hasAccuracy() && location.accuracy > 100f) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            insertPoint(db, shift.id, location)
            val distance = distanceMeters(location.latitude, location.longitude, shift.officeLat, shift.officeLon)
            val outsideState = shiftInt(db, shift.id, "outside_state")
            if (outsideState == null) {
                val outside = distance > shift.radiusMeters
                setShiftValue(db, shift.id, "outside_state", if (outside) 1 else 0)
                if (outside) insertEvent(db, shift.id, location.time, EVENT_EXIT, "Erster Standortpunkt außerhalb des Dienststellenbereichs", location.latitude, location.longitude)
            } else if (outsideState == 0 && distance > shift.radiusMeters + 35f) {
                setShiftValue(db, shift.id, "outside_state", 1)
                insertEvent(db, shift.id, location.time, EVENT_EXIT, "Automatisch anhand des Dienststellen-Radius erkannt", location.latitude, location.longitude)
            } else if (outsideState == 1 && distance < (shift.radiusMeters - 20f).coerceAtLeast(20f)) {
                setShiftValue(db, shift.id, "outside_state", 0)
                insertEvent(db, shift.id, location.time, EVENT_ENTER, "Automatisch anhand des Dienststellen-Radius erkannt", location.latitude, location.longitude)
            }
            updateStopDetection(db, shift.id, location)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun updateStopDetection(db: SQLiteDatabase, shiftId: Long, location: Location) {
        val speed = if (location.hasSpeed()) location.speed else derivedSpeed(db, shiftId, location)
        val activeStopId = shiftLong(db, shiftId, "active_stop_id")
        if (activeStopId != null) {
            val active = stopById(db, activeStopId)
            if (active != null) {
                val fromStop = distanceMeters(location.latitude, location.longitude, active.latitude, active.longitude)
                if (fromStop > STOP_EXIT_DISTANCE_M || speed > STOP_DEPARTURE_SPEED_MPS) {
                    val cv = ContentValues().apply { put("departure_ms", location.time) }
                    db.update("stops", cv, "id=? AND departure_ms IS NULL", arrayOf(activeStopId.toString()))
                    val clear = ContentValues().apply { putNull("active_stop_id"); putNull("candidate_start_ms"); putNull("candidate_lat"); putNull("candidate_lon") }
                    db.update("shifts", clear, "id=?", arrayOf(shiftId.toString()))
                }
            }
            return
        }

        if (speed <= STOP_CANDIDATE_SPEED_MPS && (!location.hasAccuracy() || location.accuracy <= 35f)) {
            val candidateStart = shiftLong(db, shiftId, "candidate_start_ms")
            val candidateLat = shiftDouble(db, shiftId, "candidate_lat")
            val candidateLon = shiftDouble(db, shiftId, "candidate_lon")
            if (candidateStart == null || candidateLat == null || candidateLon == null ||
                distanceMeters(location.latitude, location.longitude, candidateLat, candidateLon) > STOP_CLUSTER_DISTANCE_M
            ) {
                val cv = ContentValues().apply {
                    put("candidate_start_ms", location.time)
                    put("candidate_lat", location.latitude)
                    put("candidate_lon", location.longitude)
                }
                db.update("shifts", cv, "id=?", arrayOf(shiftId.toString()))
            } else if (location.time - candidateStart >= STOP_CONFIRM_MILLIS) {
                val match = nearestPoi(db, candidateLat, candidateLon)
                val cv = ContentValues().apply {
                    put("shift_id", shiftId)
                    put("arrival_ms", candidateStart)
                    put("lat", candidateLat)
                    put("lon", candidateLon)
                    put("category", match?.second ?: "Halt (automatisch erkannt)")
                    put("label", match?.first ?: "Unbenannter Halt")
                    put("source", "Automatische Stillstandserkennung; bitte prüfen")
                }
                val stopId = db.insertOrThrow("stops", null, cv)
                setShiftValue(db, shiftId, "active_stop_id", stopId)
            }
        } else {
            val clear = ContentValues().apply { putNull("candidate_start_ms"); putNull("candidate_lat"); putNull("candidate_lon") }
            db.update("shifts", clear, "id=?", arrayOf(shiftId.toString()))
        }
    }

    private fun derivedSpeed(db: SQLiteDatabase, shiftId: Long, loc: Location): Float {
        val prev = db.rawQuery("SELECT time_ms,lat,lon FROM track_points WHERE shift_id=? ORDER BY time_ms DESC LIMIT 2", arrayOf(shiftId.toString())).use { c ->
            if (c.moveToFirst() && c.moveToNext()) Triple(c.getLong(0), c.getDouble(1), c.getDouble(2)) else null
        } ?: return 0f
        val seconds = (loc.time - prev.first) / 1000.0
        if (seconds <= 0.0) return 0f
        return (distanceMeters(loc.latitude, loc.longitude, prev.second, prev.third) / seconds).toFloat()
    }

    @Synchronized
    fun markStop(label: String, category: String, location: Location): Long? {
        val shift = activeShift() ?: return null
        val match = nearestPoi(readableDatabase, location.latitude, location.longitude)
        val finalLabel = label.ifBlank { match?.first ?: "Manuell markierter Halt" }
        val finalCategory = category.ifBlank { match?.second ?: "Kontrollstelle / Rastplatz" }
        val db = writableDatabase
        val activeId = shiftLong(db, shift.id, "active_stop_id")
        if (activeId != null) {
            val update = ContentValues().apply { put("label", finalLabel); put("category", finalCategory); put("source", "Automatisch erkannt und manuell bestätigt/benannt") }
            db.update("stops", update, "id=?", arrayOf(activeId.toString()))
            return activeId
        }
        val values = ContentValues().apply {
            put("shift_id", shift.id); put("arrival_ms", System.currentTimeMillis())
            put("lat", location.latitude); put("lon", location.longitude)
            put("category", finalCategory); put("label", finalLabel); put("source", "Manuell markiert")
        }
        val stopId = db.insert("stops", null, values)
        if (stopId > 0) {
            val shiftValues = ContentValues().apply { put("active_stop_id", stopId); putNull("candidate_start_ms"); putNull("candidate_lat"); putNull("candidate_lon") }
            db.update("shifts", shiftValues, "id=?", arrayOf(shift.id.toString()))
        }
        return stopId.takeIf { it > 0 }
    }

    @Synchronized
    fun updateStopLabel(stopId: Long, label: String, category: String) {
        val cv = ContentValues().apply { put("label", label); put("category", category) }
        writableDatabase.update("stops", cv, "id=?", arrayOf(stopId.toString()))
    }

    fun poiCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM pois", null).use { c ->
        if (c.moveToFirst()) c.getInt(0) else 0
    }

    fun allPois(): List<KnownPoi> = readableDatabase.rawQuery(
        "SELECT name,category,lat,lon FROM pois ORDER BY name", null
    ).use { c -> buildList { while (c.moveToNext()) add(KnownPoi(c.getString(0), c.getString(1), c.getDouble(2), c.getDouble(3))) } }

    fun stopsForShift(shiftId: Long): List<VisitStop> = readableDatabase.rawQuery(
        "SELECT id,arrival_ms,departure_ms,lat,lon,category,label,source FROM stops WHERE shift_id=? ORDER BY arrival_ms", arrayOf(shiftId.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(stopFrom(c)) } }

    fun pointsForShift(shiftId: Long): List<TrackPoint> = readableDatabase.rawQuery(
        "SELECT time_ms,lat,lon,accuracy,speed FROM track_points WHERE shift_id=? ORDER BY time_ms", arrayOf(shiftId.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(TrackPoint(c.getLong(0), c.getDouble(1), c.getDouble(2), c.getFloat(3), c.getFloat(4))) } }

    fun eventsForShift(shiftId: Long): List<LocationEvent> = readableDatabase.rawQuery(
        "SELECT time_ms,type,source,lat,lon FROM events WHERE shift_id=? ORDER BY time_ms", arrayOf(shiftId.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(LocationEvent(c.getLong(0), c.getString(1), c.getString(2), if (c.isNull(3)) null else c.getDouble(3), if (c.isNull(4)) null else c.getDouble(4))) } }

    fun outsideIntervals(shift: ShiftRecord): List<OutsideInterval> {
        var open: Long? = null
        val result = mutableListOf<OutsideInterval>()
        eventsForShift(shift.id).forEach { event ->
            when (event.type) {
                EVENT_EXIT -> if (open == null) open = event.timeMillis
                EVENT_ENTER -> if (open != null) { result += OutsideInterval(open!!, event.timeMillis); open = null }
            }
        }
        val isOutside = readableDatabase.rawQuery("SELECT outside_state FROM shifts WHERE id=?", arrayOf(shift.id.toString())).use { c -> c.moveToFirst() && !c.isNull(0) && c.getInt(0) == 1 }
        if (open != null && isOutside) result += OutsideInterval(open!!, shift.endMillis ?: System.currentTimeMillis())
        return result
    }

    @Synchronized
    fun importPois(csv: String): Int {
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            csv.lineSequence().drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val cells = line.split(';').map { it.trim().trim('"') }
                if (cells.size < 3) return@forEach
                val name = cells[0]
                val category: String
                val latText: String
                val lonText: String
                if (cells.size >= 4) { category = cells[1].ifBlank { "Rastanlage" }; latText = cells[2]; lonText = cells[3] }
                else { category = "Rastanlage"; latText = cells[1]; lonText = cells[2] }
                val lat = latText.replace(',', '.').toDoubleOrNull()
                val lon = lonText.replace(',', '.').toDoubleOrNull()
                if (name.isBlank() || lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@forEach
                val cv = ContentValues().apply { put("name", name); put("category", category); put("lat", lat); put("lon", lon) }
                db.insert("pois", null, cv)
                added++
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return added
    }

    private fun nearestPoi(db: SQLiteDatabase, lat: Double, lon: Double): Pair<String, String>? {
        var best: Pair<String, String>? = null
        var bestDistance = POI_MATCH_DISTANCE_M
        var secondBestDistance = Float.MAX_VALUE
        db.rawQuery("SELECT name,category,lat,lon FROM pois", null).use { c ->
            while (c.moveToNext()) {
                val distance = distanceMeters(lat, lon, c.getDouble(2), c.getDouble(3))
                if (distance < bestDistance) {
                    secondBestDistance = bestDistance
                    bestDistance = distance
                    best = c.getString(0) to c.getString(1)
                } else if (distance < secondBestDistance) {
                    secondBestDistance = distance
                }
            }
        }
        // Do not guess a direction when two nearby facilities are almost equally plausible.
        return if (best != null && secondBestDistance - bestDistance >= POI_AMBIGUITY_MARGIN_M) best else null
    }

    private fun stopById(db: SQLiteDatabase, id: Long): VisitStop? = db.rawQuery(
        "SELECT id,arrival_ms,departure_ms,lat,lon,category,label,source FROM stops WHERE id=?", arrayOf(id.toString())
    ).use { c -> if (c.moveToFirst()) stopFrom(c) else null }

    private fun insertPoint(db: SQLiteDatabase, shiftId: Long, loc: Location) {
        val cv = ContentValues().apply {
            put("shift_id", shiftId); put("time_ms", loc.time); put("lat", loc.latitude); put("lon", loc.longitude)
            put("accuracy", if (loc.hasAccuracy()) loc.accuracy else -1f); put("speed", if (loc.hasSpeed()) loc.speed else -1f)
        }
        db.insert("track_points", null, cv)
    }

    private fun insertEvent(db: SQLiteDatabase, shiftId: Long, time: Long, type: String, source: String, lat: Double, lon: Double) {
        val cv = ContentValues().apply { put("shift_id", shiftId); put("time_ms", time); put("type", type); put("source", source); put("lat", lat); put("lon", lon) }
        db.insert("events", null, cv)
    }

    private fun setShiftValue(db: SQLiteDatabase, shiftId: Long, column: String, value: Any?) {
        val cv = ContentValues().apply {
            when (value) { null -> putNull(column); is Int -> put(column, value); is Long -> put(column, value); is Double -> put(column, value) }
        }
        db.update("shifts", cv, "id=?", arrayOf(shiftId.toString()))
    }

    private fun shiftInt(db: SQLiteDatabase, id: Long, col: String): Int? = db.rawQuery("SELECT $col FROM shifts WHERE id=?", arrayOf(id.toString())).use { c -> if (!c.moveToFirst() || c.isNull(0)) null else c.getInt(0) }
    private fun shiftLong(db: SQLiteDatabase, id: Long, col: String): Long? = db.rawQuery("SELECT $col FROM shifts WHERE id=?", arrayOf(id.toString())).use { c -> if (!c.moveToFirst() || c.isNull(0)) null else c.getLong(0) }
    private fun shiftDouble(db: SQLiteDatabase, id: Long, col: String): Double? = db.rawQuery("SELECT $col FROM shifts WHERE id=?", arrayOf(id.toString())).use { c -> if (!c.moveToFirst() || c.isNull(0)) null else c.getDouble(0) }

    private fun shiftFrom(c: android.database.Cursor) = ShiftRecord(c.getLong(0), c.getLong(1), if (c.isNull(2)) null else c.getLong(2), c.getDouble(3), c.getDouble(4), c.getFloat(5))
    private fun stopFrom(c: android.database.Cursor) = VisitStop(c.getLong(0), c.getLong(1), if (c.isNull(2)) null else c.getLong(2), c.getDouble(3), c.getDouble(4), c.getString(5), c.getString(6), c.getString(7))

    companion object {
        const val EVENT_EXIT = "EXIT"
        const val EVENT_ENTER = "ENTER"
        const val STOP_CONFIRM_MILLIS = 120_000L
        const val STOP_CANDIDATE_SPEED_MPS = 1.8f
        const val STOP_DEPARTURE_SPEED_MPS = 5.5f
        const val STOP_CLUSTER_DISTANCE_M = 70f
        const val STOP_EXIT_DISTANCE_M = 100f
        const val POI_MATCH_DISTANCE_M = 250f
        const val POI_AMBIGUITY_MARGIN_M = 60f
        private const val DB_NAME = "aussendienst_tracker_v2.db"
        private const val DB_VERSION = 2

        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val r = FloatArray(1)
            Location.distanceBetween(lat1, lon1, lat2, lon2, r)
            return r[0]
        }
    }
}
