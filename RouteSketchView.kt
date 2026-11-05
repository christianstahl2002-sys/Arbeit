package de.example.aussendienst

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Offline geographic plot of the recorded track and the locally bundled A4/A71 POI catalogue.
 * It is intentionally not presented as a full street basemap; no tiles or network map requests are used.
 */
class RouteSketchView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var points: List<TrackPoint> = emptyList()
    private var stops: List<VisitStop> = emptyList()
    private var pois: List<KnownPoi> = emptyList()
    private var kilometerReferences: List<AutobahnKmReference> = emptyList()
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF176B87.toInt(); style = Paint.Style.STROKE; strokeWidth = dp(3f); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5EAF0.toInt(); strokeWidth = dp(1f) }
    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF25834B.toInt(); style = Paint.Style.FILL }
    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB34040.toInt(); style = Paint.Style.FILL }
    private val stopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDB8B21.toInt(); style = Paint.Style.FILL }
    private val poiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6A4C93.toInt(); style = Paint.Style.FILL }
    private val kmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB65A28.toInt(); style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF415164.toInt(); textSize = dp(11f) }
    private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEFFFFFFF.toInt(); style = Paint.Style.FILL }

    fun setRoute(points: List<TrackPoint>, stops: List<VisitStop>, pois: List<KnownPoi>, kilometerReferences: List<AutobahnKmReference>) {
        this.points = points
        this.stops = stops
        this.pois = pois
        this.kilometerReferences = kilometerReferences
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFFF7F9FB.toInt())
        val left = dp(18f); val right = width - dp(18f); val top = dp(18f); val bottom = height - dp(31f)
        for (i in 0..4) {
            val x = left + (right - left) * i / 4f
            val y = top + (bottom - top) * i / 4f
            canvas.drawLine(x, top, x, bottom, gridPaint)
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        // With no recorded track, show the whole configured local POI corridor.
        // During a shift, only POIs near the recorded track are shown to preserve useful zoom.
        val visiblePois = if (points.size >= 2) {
            pois.filter { poi -> points.any { point ->
                TrackingStore.distanceMeters(point.latitude, point.longitude, poi.latitude, poi.longitude) <= 1800f
            } }
        } else pois

        val visibleKm = kilometerReferences.mapNotNull { ref ->
            val lat = ref.latitude ?: return@mapNotNull null
            val lon = ref.longitude ?: return@mapNotNull null
            val nearestDistance = if (points.size < 2) 0f else points.minOf { point ->
                TrackingStore.distanceMeters(point.latitude, point.longitude, lat, lon)
            }
            if (points.size < 2 || nearestDistance <= 1800f) Pair(ref, nearestDistance) else null
        }.sortedBy { it.second }.take(if (points.size < 2) 18 else 28).map { it.first }
        if (points.size < 2 && visiblePois.isEmpty() && visibleKm.isEmpty()) {
            textPaint.textSize = dp(13f)
            canvas.drawText("Noch keine Streckenpunkte oder POIs vorhanden", left + dp(4f), height / 2f, textPaint)
            return
        }
        val latitudes = (points.map { it.latitude } + visiblePois.map { it.latitude } + visibleKm.mapNotNull { it.latitude })
        val longitudes = (points.map { it.longitude } + visiblePois.map { it.longitude } + visibleKm.mapNotNull { it.longitude })
        val meanLat = latitudes.average()
        val coords = points.map { Pair(it.longitude * cos(Math.toRadians(meanLat)), -it.latitude) }
        val poiCoords = visiblePois.map { Pair(it.longitude * cos(Math.toRadians(meanLat)), -it.latitude) }
        val kmCoords = visibleKm.mapNotNull { ref ->
            val lat = ref.latitude ?: return@mapNotNull null
            val lon = ref.longitude ?: return@mapNotNull null
            Pair(lon * cos(Math.toRadians(meanLat)), -lat)
        }
        val allX = coords.map { it.first } + poiCoords.map { it.first } + kmCoords.map { it.first }
        val allY = coords.map { it.second } + poiCoords.map { it.second } + kmCoords.map { it.second }
        val minX = allX.minOrNull() ?: return; val maxX = allX.maxOrNull() ?: return
        val minY = allY.minOrNull() ?: return; val maxY = allY.maxOrNull() ?: return
        val spanX = max(maxX - minX, 0.00001); val spanY = max(maxY - minY, 0.00001)
        val scale = min((right - left) / spanX.toFloat(), (bottom - top) / spanY.toFloat())
        val drawnW = spanX.toFloat() * scale; val drawnH = spanY.toFloat() * scale
        val offsetX = left + ((right - left) - drawnW) / 2f
        val offsetY = top + ((bottom - top) - drawnH) / 2f
        fun project(lat: Double, lon: Double): Pair<Float, Float> {
            val x = lon * cos(Math.toRadians(meanLat))
            val y = -lat
            return Pair(offsetX + ((x - minX).toFloat() * scale), offsetY + ((y - minY).toFloat() * scale))
        }

        if (points.size >= 2) {
            val path = Path()
            coords.forEachIndexed { index, pair ->
                val x = offsetX + (pair.first - minX).toFloat() * scale
                val y = offsetY + (pair.second - minY).toFloat() * scale
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, routePaint)
            val first = project(points.first().latitude, points.first().longitude)
            val last = project(points.last().latitude, points.last().longitude)
            canvas.drawCircle(first.first, first.second, dp(6f), startPaint)
            canvas.drawCircle(last.first, last.second, dp(6f), endPaint)
            stops.forEachIndexed { index, stop ->
                val xy = project(stop.latitude, stop.longitude)
                canvas.drawCircle(xy.first, xy.second, dp(5f), stopPaint)
                canvas.drawText("${index + 1}", xy.first + dp(7f), xy.second - dp(6f), textPaint)
            }
        }

        visiblePois.forEach { poi ->
            val xy = project(poi.latitude, poi.longitude)
            canvas.drawCircle(xy.first, xy.second, dp(4.5f), poiPaint)
            val label = poi.name
            val labelWidth = textPaint.measureText(label) + dp(8f)
            val labelTop = xy.second - dp(18f)
            canvas.drawRoundRect(xy.first + dp(6f), labelTop, xy.first + dp(6f) + labelWidth, labelTop + dp(18f), dp(4f), dp(4f), labelBg)
            canvas.drawText(label, xy.first + dp(10f), labelTop + dp(13f), textPaint)
        }


        visibleKm.forEach { ref ->
            val lat = ref.latitude ?: return@forEach
            val lon = ref.longitude ?: return@forEach
            val xy = project(lat, lon)
            canvas.drawCircle(xy.first, xy.second, dp(5.5f), kmPaint)
            val label = "${ref.autobahn} km ${AutobahnKilometer.formatKm(ref.kilometer)}"
            val labelWidth = textPaint.measureText(label) + dp(8f)
            val labelTop = xy.second + dp(4f)
            canvas.drawRoundRect(xy.first + dp(6f), labelTop, xy.first + dp(6f) + labelWidth, labelTop + dp(18f), dp(4f), dp(4f), labelBg)
            canvas.drawText(label, xy.first + dp(10f), labelTop + dp(13f), kmPaint)
        }

        textPaint.textSize = dp(10f)
        if (points.size >= 2) {
            canvas.drawText("Start", left, bottom + dp(18f), startPaint)
            canvas.drawText("Letzter Punkt", right - dp(74f), bottom + dp(18f), endPaint)
        } else {
            canvas.drawText("Kilometer-Referenzpunkte A4 / A71 · keine Straßenkarte", left, bottom + dp(18f), textPaint)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
