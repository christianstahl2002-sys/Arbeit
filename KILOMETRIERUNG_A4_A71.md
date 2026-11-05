# Autobahnkilometrierung – A4 und A71 (Schichtjournal 2.3)

## Auswahl des Kartenausschnitts

- **A4:** Landesgrenze Hessen/Thüringen im Raum Wildeck bis Nohra bei Weimar, Referenzbereich etwa km 329,5 bis km 200,3.
- **A71:** Arnstadt-Süd bis Erfurt-Mittelhausen, Referenzbereich etwa km 87,2 bis km 57,5.

Die Orts- und Anschlussstellenwerte in dieser Tabelle stammen aus den aktuellen Streckenübersichten des Autobahnatlas Online. Sie sind Stationsreferenzen, keine Garantie dafür, dass jede Koordinate exakt auf der Kilometrierungstafel oder in der Mitte einer Richtungsfahrbahn liegt.

## A4 – Hessen/Thüringen-Grenze bis Nohra

| Bezugspunkt | km | Hinweis |
|---|---:|---|
| Wildeck-Obersuhl | 331,1 | Anschlussstelle |
| Landesgrenze HE/TH (Wildeck) | 329,5 |  |
| Gerstungen | 328,6 | Anschlussstelle |
| Landesgrenze TH/HE | 322,3 |  |
| Wommener Dreieck | (321,5) | Atlas zeigt Wert in Klammern; vor Ort prüfen |
| Wommen | 320,2 | Im Atlas als Wegfall Frühjahr 2026 markiert; nur historische Referenz |
| Herleshausen | 315,8 | Anschlussstelle |
| Raststätte Werratal | 315,5 | Rastanlage |
| Autohof Eisenach | 279,5 | auffälliger Stationssprung im Verzeichnis |
| Eisenach-West | 278,3 | Anschlussstelle |
| Eisenach-Ost | 267,6 | Anschlussstelle |
| PWC Hainich | 265,4 | Rastanlage/PWC |
| Sättelstädt | 258,2 | Anschlussstelle |
| Waltershausen | 252,3 | Anschlussstelle |
| Raststätte Hörselgau | 249,4 | Rastanlage |
| Gotha-Boxberg | 247,5 | Anschlussstelle |
| Gotha | 239,2 | Anschlussstelle |
| PWC Drei Gleichen | 232,7 | Rastanlage/PWC |
| Wandersleben | 231,2 | Anschlussstelle |
| Neudietendorf | 223,5 | Anschlussstelle |
| Autobahnkreuz Erfurt (A4) | 221,9 | Kreuzmittelpunkt nur näherungsweise koordiniert |
| Erfurt-West | 217,9 | Anschlussstelle |
| PWC Willroder Forst | 213,5 | Rastanlage/PWC |
| Erfurt-Ost | 208,4 | Anschlussstelle |
| Erfurt-Vieselbach | 205,1 | Anschlussstelle |
| Raststätte Eichelborn | 203,5 | Rastanlage |
| Nohra | 200,3 | Anschlussstelle |

**Wichtiger Hinweis:** Die Streckenübersicht weist zwischen Werratal (km 315,5) und Autohof Eisenach (km 279,5) einen auffälligen Stationssprung aus. Daher interpoliert die App zwischen diesen Werten nicht.

## A71 – Arnstadt bis Erfurt-Mittelhausen

| Bezugspunkt | km |
|---|---:|
| Arnstadt-Süd | 87,2 |
| PWC Dornheimer Rieth | 86,2 |
| Arnstadt-Nord | 84,2 |
| Autobahnkreuz Erfurt (A71) | 77,8 |
| PWC Erfurter Becken | 72,3 |
| Erfurt-Bindersleben | 70,2 |
| Erfurt-Gispersleben | 59,6 |
| Erfurt-Mittelhausen | 57,5 |

Auf der Fahrt von Arnstadt nach Mittelhausen nehmen die Stationswerte in diesem Abschnitt ab.

### Quellenabweichung zum älteren BASt-Verzeichnis

Das ältere BASt-Autobahnverzeichnis (Stand 01.09.2016) nennt am Kreuz Erfurt auf der A71 78,2 km statt 77,8 km, am PWC Dornheimer Rieth 85,8 km statt 86,2 km und an Arnstadt-Süd 87,4 km statt 87,2 km. Die App verwendet in Version 2.3 die Werte der Autobahnatlas-Online-Streckenübersicht; die älteren BASt-Werte werden als Quellenabweichung dokumentiert und nicht vermischt.

## Vollständige Kilometrierung – BKG-Import

Für eine feinere automatische Zuordnung ist der BKG-Datensatz **POI-Open / KmBAB** vorgesehen. Er enthält für jeden ganzen Autobahnkilometer einen georeferenzierten Punkt mit Autobahnbezeichnung und Richtungsangabe. Das BKG beschreibt mehr als 13.000 Punkte bundesweit. Die Geometrien werden abschnittsweise entlang eines abgeleiteten Autobahn-Achsennetzes berechnet; Kilometrierungssprünge und Richtungsverläufe werden berücksichtigt. Das BKG weist zugleich darauf hin, dass die berechneten Werte nicht vor Ort mit jeder Kilometrierungstafel abgeglichen werden.

Die App 2.3 kann einen BKG-GeoJSON-/ArcGIS-JSON- oder CSV-Export lokal importieren. Der Import wird auf folgende Korridore begrenzt:

- A4: km 190 bis 335
- A71: km 50 bis 90

Für GeoJSON/ArcGIS-JSON muss die Ausgabe in **WGS84 / EPSG:4326** erfolgen. Bei CSV werden die Felder `Bez`, `Kilometer`, `Richtung`, `X`, `Y` (X = Länge, Y = Breite) bzw. `longitude`, `latitude` erwartet. Daten in Web Mercator (EPSG:3857) werden bewusst abgewiesen, damit keine falschen Positionen entstehen.

### Import über den BKG-Dienst

1. Den BKG-Dienst `KmBAB` öffnen: https://services2.arcgis.com/jUpNdisbWqRpMo35/ArcGIS/rest/services/KmBAB/FeatureServer/0/query
2. Im Feld **Where** eintragen: `Bez IN ('A4','A71')`.
3. Bei **Out Fields** eintragen: `Bez,Kilometer,Richtung`.
4. **Return Geometry** aktivieren und **Output Spatial Reference** auf `4326` setzen.
5. Als Format **GeoJSON** wählen, Ergebnisdatei speichern und in der App über „BKG-Kilometrierung importieren“ auswählen.

Die App sendet dafür keine Standortdaten an den BKG-Dienst. Der Import erfolgt manuell durch Auswahl einer zuvor gespeicherten Datei. Nach dem Import liegen die Kilometerpunkte lokal im App-Dateispeicher.

## Verhalten der App

- Ohne BKG-Import zeigt die App ausgewählte Anschlussstellen-/Rastanlagen-Referenzen.
- Mit BKG-Import kann die App den nächstgelegenen ganzzahligen Kilometerpunkt mit Richtungsangabe einem GPS-Punkt, Halt und Export zuordnen, sofern der Punkt innerhalb von 800 m liegt.
- Auf der schematischen Streckenansicht werden nur nahe Kilometerpunkte eingeblendet, damit die Karte lesbar bleibt.
- Die App rechnet nicht linear zwischen beliebigen Referenzen hoch und behauptet keine amtliche GPS-Kilometrierung.
- Richtungsfahrbahnen und Anschlussstellen können dicht beieinanderliegen. Automatische Treffer sind deshalb Orientierungshinweise und müssen geprüft werden.

## Quellen

1. Autobahnatlas Online, A4: https://www.autobahnatlas-online.de/A4.htm
2. Autobahnatlas Online, A71: https://www.autobahnatlas-online.de/A71.htm
3. BASt, Autobahnverzeichnis (historischer Stand 01.09.2016): https://bast.opus.hbz-nrw.de/files/1716/v273_averz_internet_pdf.pdf
4. BKG, POI-Open Produktdokumentation, Produktstand Juni 2025: https://sg.geodatenzentrum.de/web_public/gdz/dokumentation/deu/poi-open.pdf
5. BKG/ArcGIS, KmBAB Feature Service: https://services2.arcgis.com/jUpNdisbWqRpMo35/ArcGIS/rest/services/KmBAB/FeatureServer
