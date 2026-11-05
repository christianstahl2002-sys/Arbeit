package de.example.aussendienst

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.io.OutputStreamWriter
import java.time.Instant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var store: TrackingStore
    private lateinit var statusText: TextView
    private lateinit var officeText: TextView
    private lateinit var shiftText: TextView
    private lateinit var radiusText: TextView
    private lateinit var blocksBox: LinearLayout
    private lateinit var stopsBox: LinearLayout
    private lateinit var routeView: RouteSketchView
    private var kilometerReferences: List<AutobahnKmReference> = emptyList()
    private lateinit var kilometerBox: LinearLayout
    private lateinit var radiusSeek: SeekBar
    private var receiverRegistered = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            toast("Standortberechtigung erteilt.")
            render()
        } else toast("Für die Streckenaufzeichnung ist präziser Standortzugriff erforderlich.")
    }
    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) writeCsv(uri) }
    private val createGpx = registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri -> if (uri != null) writeGpx(uri) }
    private val importPois = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importPoiFile(uri) }
    private val importKmRefs = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importKmReferenceFile(uri) }
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { render() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TrackingStore(this)
        kilometerReferences = AutobahnKilometer.load(this)
        // Seed the local A4/A71 catalogue once, but never overwrite a user-imported POI list.
        if (store.poiCount() == 0) {
            runCatching {
                assets.open("rastanlagen_a4_a71_thueringen.csv").bufferedReader(Charsets.UTF_8).use { reader ->
                    store.importPois(reader.readText())
                }
            }
        }
        buildUi()
        render()
        askForNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) {
            val filter = IntentFilter(ShiftLocationService.ACTION_DATA_UPDATED)
            ContextCompat.registerReceiver(this, updateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
            render()
        }
    }

    override fun onPause() {
        if (receiverRegistered) {
            unregisterReceiver(updateReceiver)
            receiverRegistered = false
        }
        super.onPause()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(32)) }
        scroll.addView(page)
        setContentView(scroll)

        page.addView(TextView(this).apply {
            text = "Schichtjournal"
            textSize = 29f
            setTextColor(0xFF18334D.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        page.addView(body("Fahrtverlauf, Zeitblöcke außerhalb der Dienststelle und Aufenthalte – nur während einer von dir gestarteten Schicht."))

        addSection(page, "Schichtstatus")
        statusText = body("")
        page.addView(statusText)
        shiftText = body("")
        page.addView(shiftText)
        addButton(page, "Schicht starten") { startShift() }
        addButton(page, "Schicht beenden") { confirmEndShift() }

        addSection(page, "Dienststelle und Erfassung")
        officeText = body("")
        page.addView(officeText)
        addButton(page, "Dienststellen-Standort hier speichern") { captureOffice() }
        page.addView(body("Der Radius dient zur Erkennung der Zeitblöcke außerhalb der Dienststelle. Stelle ihn passend zum Gelände ein."))
        radiusText = body("")
        page.addView(radiusText)
        radiusSeek = SeekBar(this).apply {
            max = 450
            progress = (store.radiusMeters().toInt() - 50).coerceIn(0, 450)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 50
                    radiusText.text = "Radius: $value m"
                    if (fromUser) store.setRadiusMeters(value.toFloat())
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        page.addView(radiusSeek)
        page.addView(body("GPS-Schwankungen werden mit einer kleinen Hysterese abgefedert. Die protokollierten Grenzzeiten können trotzdem einige Sekunden oder länger vom tatsächlichen Überfahren des Radius abweichen."))

        page.addView(body("GPS-Abtastrate (kürzer = mehr Details, höherer Akkuverbrauch):"))
        val intervalSpinner = Spinner(this)
        intervalSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("10 Sekunden", "15 Sekunden", "30 Sekunden", "60 Sekunden"))
        val intervalValues = listOf(10, 15, 30, 60)
        intervalSpinner.setSelection(intervalValues.indexOf(store.intervalSeconds()).coerceAtLeast(0))
        intervalSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { store.setIntervalSeconds(intervalValues[position]) }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        page.addView(intervalSpinner)

        addSection(page, "Streckenverlauf & Rastanlagen-Punkte")
        page.addView(body("Offline-Ansicht des GPS-Verlaufs und der vorinstallierten Rastanlagen-Punkte für A4/A71. Keine vollständige Straßenkarte und keine Navigation; alle Punkte werden lokal gezeichnet."))
        routeView = RouteSketchView(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(230)) }
        page.addView(routeView)

        addSection(page, "Autobahnkilometrierung A4 / A71")
        page.addView(body("Die Liste zeigt belegte Kilometer-Referenzpunkte im gewählten Korridor. Bei einem Aufenthalt wird ein Referenzpunkt nur dann zugeordnet, wenn seine hinterlegte Koordinate in der Nähe liegt. Zwischen den Punkten wird bewusst kein scheinbar exakter Kilometerwert hochgerechnet."))
        kilometerBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(kilometerBox)
        page.addView(body("Für eine genauere Zuordnung kannst du die offiziellen BKG-Kilometerpunkte (ganze Kilometer mit Richtungsangabe) als GeoJSON oder CSV importieren. Die Datei muss WGS84/EPSG:4326-Koordinaten enthalten. Der Import wird nur lokal gespeichert; A4 wird auf km 190–335 und A71 auf km 50–90 begrenzt. Bei dicht nebeneinanderliegenden Richtungsfahrbahnen können mehrere Richtungstreffer erscheinen – bitte mit der Kilometrierungstafel abgleichen."))
        addButton(page, "BKG-Kilometrierung (GeoJSON / CSV) importieren") {
            importKmRefs.launch(arrayOf("application/geo+json", "application/json", "text/*", "application/octet-stream"))
        }

        addSection(page, "Zeitblöcke außerhalb der Dienststelle")
        blocksBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(blocksBox)

        addSection(page, "Aufenthalte / Rastplätze / Kontrollstellen")
        page.addView(body("Lokaler POI-Katalog für A4 und A71 ist vorinstalliert. Er dient zur Orientierung und zur möglichen automatischen Benennung von Halten; jede Zuordnung bitte prüfen."))
        page.addView(body("Längere Stillstände werden automatisch als Halt erkannt. Ein Halt wird erst nach etwa zwei Minuten Stillstand angelegt und ist zunächst nur ein Vorschlag – bitte Ort und Zeit prüfen. Mit der manuellen Markierung kannst du einen konkreten Ankunftspunkt setzen."))
        addButton(page, "Ankunft / Kontrollstelle jetzt markieren") { markStopNow() }
        stopsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(stopsBox)
        addButton(page, "Lokale Liste mit Rastanlagen / Kontrollpunkten importieren") {
            importPois.launch(arrayOf("text/*", "text/csv", "application/csv"))
        }
        page.addView(body("Importformat: name;kategorie;latitude;longitude – Koordinaten mit Dezimalpunkt. Die Liste bleibt auf dem Gerät und ermöglicht die automatische Zuordnung eines erkannten Halts zum nächstgelegenen eingetragenen Punkt (bis 250 m; bei unklaren nahen Treffern erfolgt keine automatische Benennung)."))

        addSection(page, "Export")
        addButton(page, "Schichtprotokoll als CSV exportieren") {
            createCsv.launch("schichtjournal_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMANY).format(Date())}.csv")
        }
        addButton(page, "Strecke als GPX exportieren") {
            createGpx.launch("schicht_route_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMANY).format(Date())}.gpx")
        }

        addSection(page, "Datenschutz und Genauigkeit")
        page.addView(body("Die Aufzeichnung startet erst nach deiner Schicht-Start-Taste und endet mit Schicht beenden. Währenddessen zeigt Android dauerhaft eine Standort-Benachrichtigung. Route, Ereignisse und Stopps bleiben in der privaten App-Datenbank auf diesem Handy; es gibt keine Cloud-Synchronisierung und keine Karten- oder Geocoding-Abfragen an externe Server. Ein importierter POI-Katalog ist nötig, damit Rastanlagen automatisch namentlich zugeordnet werden können. Ohne Katalog bleibt ein Halt ein Koordinatenpunkt."))
        page.addView(body("Diese App ist ein persönliches Hilfsprotokoll, kein geeichtes oder dienstlich freigegebenes Erfassungssystem. GPS kann in Tunneln, unter Brücken, an hohen Gebäuden oder bei schlechtem Empfang abweichen. Automatisch erkannte Stopps und Zeitblöcke vor einer dienstlichen Verwendung immer prüfen und gemäß den Vorgaben deiner Dienststelle behandeln."))
    }

    private fun startShift() {
        if (!hasFineLocation()) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        if (store.activeShift() != null) { toast("Es läuft bereits eine Schichtaufzeichnung."); return }
        val office = store.officeLocation()
        if (office == null) { toast("Bitte zuerst an der Dienststelle den Standort speichern."); return }
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location == null) {
                    toast("GPS-Position konnte nicht bestimmt werden. Bitte Standortdienste aktivieren und erneut starten.")
                    return@addOnSuccessListener
                }
                store.startShift(office.latitude, office.longitude, store.radiusMeters(), location)
                try {
                    ContextCompat.startForegroundService(this, Intent(this, ShiftLocationService::class.java).setAction(ShiftLocationService.ACTION_START))
                    render()
                    toast("Schicht gestartet. Die Standortaufzeichnung läuft sichtbar im Vordergrunddienst.")
                } catch (e: Exception) {
                    store.endShift()
                    toast("Standortdienst konnte nicht gestartet werden: ${e.localizedMessage ?: "Berechtigung prüfen"}")
                    render()
                }
            }
            .addOnFailureListener { toast("Aktueller Standort nicht verfügbar: ${it.localizedMessage ?: "Fehler"}") }
    }

    private fun confirmEndShift() {
        if (store.activeShift() == null) { toast("Es läuft keine Schichtaufzeichnung."); return }
        AlertDialog.Builder(this)
            .setTitle("Schicht beenden?")
            .setMessage("Die GPS-Aufzeichnung wird sofort beendet. Bereits erfasste Daten bleiben lokal gespeichert.")
            .setNegativeButton("Weiter erfassen", null)
            .setPositiveButton("Schicht beenden") { _, _ ->
                store.endShift()
                startService(Intent(this, ShiftLocationService::class.java).setAction(ShiftLocationService.ACTION_STOP))
                render()
                toast("Schicht beendet. Es werden keine weiteren Standortpunkte erfasst.")
            }.show()
    }

    private fun captureOffice() {
        if (!hasFineLocation()) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location == null) toast("Kein Standort verfügbar. Bitte GPS aktivieren und erneut versuchen.")
                else {
                    store.saveOffice(location)
                    render()
                    toast("Dienststellen-Standort lokal gespeichert.")
                }
            }
            .addOnFailureListener { toast("Standort konnte nicht gespeichert werden: ${it.localizedMessage ?: "Fehler"}") }
    }

    private fun markStopNow() {
        val shift = store.activeShift()
        if (shift == null) { toast("Bitte zuerst eine Schicht starten."); return }
        if (!hasFineLocation()) { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)); return }
        val input = EditText(this).apply { hint = "z. B. Rastanlage Thüringer Tor Süd" }
        val category = EditText(this).apply { hint = "Kategorie: Rastplatz, Rasthof, Kontrollstelle …" }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(6), dp(18), 0); addView(input); addView(category) }
        AlertDialog.Builder(this)
            .setTitle("Ankunft markieren")
            .setMessage("Der aktuelle Standort und die Uhrzeit werden gespeichert. Beim Losfahren wird die Abfahrt automatisch ergänzt.")
            .setView(box)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Standort markieren") { _, _ ->
                LocationServices.getFusedLocationProviderClient(this)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                    .addOnSuccessListener { location ->
                        if (location == null) toast("Aktueller Standort nicht verfügbar.")
                        else {
                            val id = store.markStop(input.text.toString().trim(), category.text.toString().trim(), location)
                            if (id != null) { render(); toast("Aufenthalt erfasst.") } else toast("Aufenthalt konnte nicht gespeichert werden.")
                        }
                    }
            }.show()
    }

    private fun editStop(stop: VisitStop) {
        val name = EditText(this).apply { setText(stop.label); hint = "Ort / Rastanlage" }
        val category = EditText(this).apply { setText(stop.category); hint = "Kategorie" }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(6), dp(18), 0); addView(name); addView(category) }
        AlertDialog.Builder(this).setTitle("Aufenthalt bearbeiten").setView(box)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern") { _, _ -> store.updateStopLabel(stop.id, name.text.toString().trim(), category.text.toString().trim()); render() }
            .show()
    }

    private fun render() {
        if (!::statusText.isInitialized) return
        val shift = store.activeShift() ?: store.shifts(1).firstOrNull()
        val active = store.activeShift() != null
        statusText.text = if (active) "● AUFZEICHNUNG LÄUFT" else "○ Keine aktive Schicht"
        statusText.setTextColor(if (active) 0xFF157447.toInt() else 0xFF80551D.toInt())
        val office = store.officeLocation()
        officeText.text = if (office == null) "Dienststellen-Standort: noch nicht gespeichert" else "Dienststellen-Standort gespeichert · ${"%.5f".format(Locale.US, office.latitude)}, ${"%.5f".format(Locale.US, office.longitude)}"
        radiusText.text = "Radius: ${store.radiusMeters().toInt()} m"
        if (::radiusSeek.isInitialized) radiusSeek.progress = (store.radiusMeters().toInt() - 50).coerceIn(0, 450)
        if (shift == null) {
            shiftText.text = "Noch keine Schicht erfasst."
            routeView.setRoute(emptyList(), emptyList(), store.allPois(), kilometerReferences)
            renderKilometerReferences(null)
            blocksBox.removeAllViews(); stopsBox.removeAllViews()
            return
        }
        val endText = shift.endMillis?.let { formatDateTime(it) } ?: "läuft seit ${formatTime(shift.startMillis)}"
        shiftText.text = "Beginn: ${formatDateTime(shift.startMillis)}\nEnde: $endText\nStreckenpunkte: ${store.pointsForShift(shift.id).size}"
        val points = store.pointsForShift(shift.id)
        val stops = store.stopsForShift(shift.id)
        routeView.setRoute(points, stops, store.allPois(), kilometerReferences)
        renderKilometerReferences(points.lastOrNull())
        renderOutsideBlocks(shift)
        renderStops(stops)
    }

    private fun renderKilometerReferences(currentPoint: TrackPoint?) {
        if (!::kilometerBox.isInitialized) return
        kilometerBox.removeAllViews()
        val bkgPoints = kilometerReferences.filter { it.source.contains("BKG POI-Open", ignoreCase = true) }
        if (bkgPoints.isNotEmpty()) {
            val a4Count = bkgPoints.count { it.autobahn == "A4" }
            val a71Count = bkgPoints.count { it.autobahn == "A71" }
            kilometerBox.addView(body("BKG-Kilometerpunkte lokal importiert: ${bkgPoints.size} (A4: $a4Count, A71: $a71Count). Die Richtung wird – sofern in der Quelldatei vorhanden – beim Treffer mit angezeigt."))
        } else {
            kilometerBox.addView(body("Noch keine vollständigen BKG-Kilometerpunkte importiert. Die folgende Liste enthält nur ausgewählte Strecken- und Anlagenreferenzen."))
        }
        val curated = kilometerReferences.filterNot { it.source.contains("BKG POI-Open", ignoreCase = true) }
        val grouped = curated.groupBy { it.autobahn }.toSortedMap()
        grouped.forEach { (road, refs) ->
            val sorted = refs.sortedByDescending { it.kilometer }
            val lines = sorted.joinToString("\n") {
                val historical = when {
                    it.note.contains("BASt-Verzeichnis 2016", ignoreCase = true) -> " (BASt 2016: ${it.note.substringAfter("abweichend ").trim()})"
                    it.note.contains("Wegfall", ignoreCase = true) -> " (historische Referenz / Wegfall vermerkt)"
                    it.note.contains("Klammern", ignoreCase = true) -> " (Atlaswert in Klammern)"
                    else -> ""
                }
                "${it.name}: km ${AutobahnKilometer.formatKm(it.kilometer)}$historical"
            }
            kilometerBox.addView(body("$road\n$lines"))
        }
        if (currentPoint != null) {
            val matches = AutobahnKilometer.nearby(currentPoint.latitude, currentPoint.longitude, kilometerReferences, 800f)
            val currentText = if (matches.isEmpty()) {
                "Letzter GPS-Punkt: kein Kilometrierungspunkt im Umkreis von 800 m. Bei importierten BKG-Punkten sollte ein ganzer Kilometer in der Nähe zugeordnet werden können; GPS-Lücken sind möglich."
            } else {
                "Nächster Kilometer-Referenzpunkt: " + matches.joinToString(" · ") {
                    "${AutobahnKilometer.label(it.reference)} (${AutobahnKilometer.formatDistance(it.distanceMeters)})"
                }
            }
            kilometerBox.addView(body(currentText))
        }
    }

    private fun renderOutsideBlocks(shift: ShiftRecord) {
        blocksBox.removeAllViews()
        val intervals = store.outsideIntervals(shift)
        if (intervals.isEmpty()) {
            blocksBox.addView(body("Noch kein Verlassen der Dienststelle erkannt."))
            return
        }
        intervals.forEachIndexed { index, item ->
            val end = item.endMillis
            val duration = end?.let { durationText((it - item.startMillis).coerceAtLeast(0)) } ?: "läuft aktuell"
            val text = TextView(this).apply {
                text = "${index + 1}. ${formatTime(item.startMillis)} – ${end?.let { formatTime(it) } ?: "offen"}\nDauer: $duration"
                textSize = 15f; setTextColor(0xFF23384B.toInt()); setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundColor(0xFFF0F4F7.toInt())
            }
            blocksBox.addView(text, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(7) })
        }
        val total = intervals.sumOf { ((it.endMillis ?: System.currentTimeMillis()) - it.startMillis).coerceAtLeast(0) }
        blocksBox.addView(body("Gesamte Zeit außerhalb der Dienststelle: ${durationText(total)}"))
    }

    private fun renderStops(stops: List<VisitStop>) {
        stopsBox.removeAllViews()
        if (stops.isEmpty()) { stopsBox.addView(body("Noch keine Aufenthalte erkannt oder markiert.")); return }
        stops.forEachIndexed { index, stop ->
            val end = stop.departureMillis
            val duration = end?.let { durationText((it - stop.arrivalMillis).coerceAtLeast(0)) } ?: "Aufenthalt läuft / Abfahrt noch offen"
            val kmMatches = AutobahnKilometer.nearby(stop.latitude, stop.longitude, kilometerReferences, 800f)
            val kmText = if (kmMatches.isEmpty()) "Kilometrierung: kein Referenzpunkt in max. 800 m" else
                kmMatches.joinToString("\n") { "Kilometerbezug: ${AutobahnKilometer.label(it.reference)} · ${AutobahnKilometer.formatDistance(it.distanceMeters)} entfernt" }
            val row = Button(this).apply {
                isAllCaps = false
                textSize = 14f
                text = "${index + 1}. ${stop.label}\n${stop.category}\nAnkunft: ${formatDateTime(stop.arrivalMillis)}\nAbfahrt: ${end?.let { formatDateTime(it) } ?: "offen"}\nDauer: $duration\n$kmText\n${"%.5f".format(Locale.US, stop.latitude)}, ${"%.5f".format(Locale.US, stop.longitude)}\nTippen zum Bearbeiten"
                setOnClickListener { editStop(stop) }
            }
            stopsBox.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
        }
    }

    private fun importPoiFile(uri: Uri) {
        runCatching { contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("Datei konnte nicht geöffnet werden") }
            .onSuccess { text ->
                val count = store.importPois(text)
                toast("$count lokale Punkte importiert. Künftige Halte in der Nähe können automatisch benannt werden.")
            }
            .onFailure { toast("Import fehlgeschlagen: ${it.localizedMessage ?: "Dateiformat prüfen"}") }
    }

    private fun importKmReferenceFile(uri: Uri) {
        runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Datei konnte nicht geöffnet werden")
        }.onSuccess { text ->
            runCatching {
                if (text.trimStart().startsWith("{")) AutobahnKilometer.importBkgJson(this, text)
                else AutobahnKilometer.importBkgCsv(this, text)
            }.onSuccess { result ->
                kilometerReferences = AutobahnKilometer.load(this)
                render()
                toast("${result.count} BKG-Kilometerpunkte importiert (A4: ${result.a4Count}, A71: ${result.a71Count}). Speicherung nur auf diesem Gerät.")
            }.onFailure { toast("Kilometrierungsimport fehlgeschlagen: ${it.localizedMessage ?: "Dateiformat prüfen"}") }
        }.onFailure { toast("Datei konnte nicht gelesen werden: ${it.localizedMessage ?: "Fehler"}") }
    }

    private fun writeCsv(uri: Uri) {
        runCatching {
            contentResolver.openOutputStream(uri)?.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                    writer.write("\uFEFFSCHICHTEN\r\nSchicht_ID;Beginn;Ende;Dienststellenradius_m\r\n")
                    store.shifts(500).sortedBy { it.startMillis }.forEach { shift ->
                        writer.write("${shift.id};${formatDateTime(shift.startMillis)};${shift.endMillis?.let { formatDateTime(it) } ?: ""};${shift.radiusMeters.toInt()}\r\n")
                    }
                    store.shifts(500).sortedBy { it.startMillis }.forEach { shift ->
                        writer.write("\r\nZEITBLOECKE_AUSSERHALB;Schicht_ID=${shift.id}\r\nVon;Bis;Dauer_Minuten\r\n")
                        store.outsideIntervals(shift).forEach { interval ->
                            val end = interval.endMillis
                            writer.write("${formatDateTime(interval.startMillis)};${end?.let { formatDateTime(it) } ?: ""};${end?.let { ((it - interval.startMillis) / 60_000).toString() } ?: ""}\r\n")
                        }
                        writer.write("\r\nAUFENTHALTE;Schicht_ID=${shift.id}\r\nOrt;Kategorie;Ankunft;Abfahrt;Dauer_Minuten;Latitude;Longitude;Erfassungsart;Kilometerreferenz\r\n")
                        store.stopsForShift(shift.id).forEach { stop ->
                            val end = stop.departureMillis
                            val minutes = end?.let { ((it - stop.arrivalMillis).coerceAtLeast(0) / 60_000).toString() } ?: ""
                            val kmText = AutobahnKilometer.nearby(stop.latitude, stop.longitude, kilometerReferences, 800f).joinToString(" | ") {
                                "${AutobahnKilometer.label(it.reference)} (${it.reference.name}; ${AutobahnKilometer.formatDistance(it.distanceMeters)})"
                            }
                            writer.write("${csv(stop.label)};${csv(stop.category)};${formatDateTime(stop.arrivalMillis)};${end?.let { formatDateTime(it) } ?: ""};$minutes;${stop.latitude};${stop.longitude};${csv(stop.source)};${csv(kmText)}\r\n")
                        }
                        writer.write("\r\nROUTENPUNKTE;Schicht_ID=${shift.id}\r\nZeit;Latitude;Longitude;Genauigkeit_m;Geschwindigkeit_m_s;Kilometerreferenz\r\n")
                        store.pointsForShift(shift.id).forEach { point ->
                            val kmText = AutobahnKilometer.nearby(point.latitude, point.longitude, kilometerReferences, 800f).joinToString(" | ") {
                                "${AutobahnKilometer.label(it.reference)} (${it.reference.name}; ${AutobahnKilometer.formatDistance(it.distanceMeters)})"
                            }
                            writer.write("${formatDateTime(point.timeMillis)};${point.latitude};${point.longitude};${point.accuracyMeters};${point.speedMps};${csv(kmText)}\r\n")
                        }
                        writer.write("\r\nEREIGNISSE;Schicht_ID=${shift.id}\r\nTyp;Zeit;Hinweis;Latitude;Longitude\r\n")
                        store.eventsForShift(shift.id).forEach { event ->
                            writer.write("${event.type};${formatDateTime(event.timeMillis)};${csv(event.source)};${event.latitude ?: ""};${event.longitude ?: ""}\r\n")
                        }
                    }
                }
            } ?: error("Datei konnte nicht geöffnet werden")
        }.onSuccess { toast("CSV-Protokoll wurde exportiert.") }
            .onFailure { toast("CSV-Export fehlgeschlagen: ${it.localizedMessage ?: "Fehler"}") }
    }

    private fun writeGpx(uri: Uri) {
        val shift = store.shifts(1).firstOrNull()
        if (shift == null) { toast("Noch keine Route vorhanden."); return }
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"Schichtjournal\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
                store.stopsForShift(shift.id).forEach { stop ->
                    val kmText = AutobahnKilometer.nearby(stop.latitude, stop.longitude, kilometerReferences, 800f).joinToString("; ") {
                        "${AutobahnKilometer.label(it.reference)} (${it.reference.name})"
                    }
                    val description = listOf(stop.category, kmText).filter { it.isNotBlank() }.joinToString(" · ")
                    writer.write("<wpt lat=\"${stop.latitude}\" lon=\"${stop.longitude}\"><time>${Instant.ofEpochMilli(stop.arrivalMillis)}</time><name>${xml(stop.label)}</name><desc>${xml(description)}</desc></wpt>\n")
                }
                writer.write("<trk><name>Schicht ${formatDateTime(shift.startMillis)}</name><trkseg>\n")
                store.pointsForShift(shift.id).forEach { point ->
                    writer.write("<trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\"><time>${Instant.ofEpochMilli(point.timeMillis)}</time></trkpt>\n")
                }
                writer.write("</trkseg></trk>\n</gpx>")
            } ?: error("Datei konnte nicht geöffnet werden")
        }.onSuccess { toast("GPX-Route wurde exportiert.") }
            .onFailure { toast("GPX-Export fehlgeschlagen: ${it.localizedMessage ?: "Fehler"}") }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 44)
        }
    }

    private fun hasFineLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun addSection(parent: LinearLayout, title: String) {
        parent.addView(TextView(this).apply {
            text = title; textSize = 19f; setTextColor(0xFF18334D.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(20), 0, dp(7))
        })
    }
    private fun body(text: String) = TextView(this).apply {
        this.text = text; textSize = 14f; setTextColor(0xFF536273.toInt()); setPadding(0, dp(4), 0, dp(8))
    }
    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply { text = label; isAllCaps = false; textSize = 15f; setOnClickListener { action() } },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3); bottomMargin = dp(3) })
    }
    private fun formatDateTime(time: Long) = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date(time))
    private fun formatTime(time: Long) = SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date(time))
    private fun durationText(millis: Long): String {
        val minutes = millis / 60_000
        return "${minutes / 60} Std. ${minutes % 60} Min."
    }
    private fun csv(value: String) = "\"${value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ")}\""
    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
