# BKG-Kilometrierung in Schichtjournal importieren

Schichtjournal 2.3 kann eine lokal gespeicherte Datei aus dem BKG-Dienst `KmBAB` importieren. Das ist der vorgesehene Weg, um die ganzen Autobahnkilometer einschließlich Richtungsangabe in die App zu übernehmen.

## Datei im BKG-Dienst erzeugen

Öffne den BKG-Query-Dienst:

https://services2.arcgis.com/jUpNdisbWqRpMo35/ArcGIS/rest/services/KmBAB/FeatureServer/0/query

Trage diese Werte ein:

| Feld | Wert |
|---|---|
| Where | `Bez IN ('A4','A71')` |
| Out Fields | `Bez,Kilometer,Richtung` |
| Return Geometry | `true` |
| Output Spatial Reference | `4326` |
| Format | `GeoJSON` |

Dann die Abfrage ausführen und das Ergebnis als `.geojson` oder `.json` speichern. Der Service stellt ganze Kilometerpunkte samt Autobahnbezeichnung und Richtung bereit. Die App übernimmt aus der Datei automatisch nur A4 km 190–335 und A71 km 50–90.

## In der App importieren

1. Schichtjournal öffnen.
2. Unter **Autobahnkilometrierung A4 / A71** die Schaltfläche **BKG-Kilometrierung (GeoJSON / CSV) importieren** wählen.
3. Die gespeicherte Datei auswählen.
4. Die App zeigt an, wie viele A4- und A71-Punkte übernommen wurden.

Die Datei wird lokal im privaten App-Speicher abgelegt und beim nächsten Start erneut geladen. Es gibt keine automatische Netzwerkverbindung und keine Übertragung von GPS- oder Schichtdaten.

## Alternative CSV

Ein CSV-Export wird unterstützt, wenn er mindestens diese Spalten besitzt:

`Bez,Kilometer,Richtung,X,Y`

X muss die geografische Länge und Y die geografische Breite im Koordinatensystem WGS84 / EPSG:4326 enthalten. Exportierte Web-Mercator-Werte (EPSG:3857) werden nicht akzeptiert.

## Fachlicher Hinweis

Der BKG-Datensatz enthält berechnete Kilometerpunkte im 1-km-Abstand. Laut Produktdokumentation werden die Kilometerpunkte abschnittsweise entlang eines abgeleiteten Autobahn-Achsennetzes erzeugt; Richtungsverlauf und Kilometrierungssprünge werden berücksichtigt. Das BKG gleicht die berechnete Lage aber nicht mit jeder Kilometrierungstafel vor Ort ab. Für ein dienstliches Protokoll daher die Zuordnung und die tatsächliche Tafel vor Ort prüfen.
