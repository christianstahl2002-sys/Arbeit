# Lokaler Rastanlagenkatalog A4/A71 – Quellen und Hinweise

Der Katalog wird ausschließlich lokal in der App gespeichert und dient zur Vorschlagsbenennung erkannter Stopps.
Die Koordinaten sind Näherungspunkte aus den jeweils angegebenen Verzeichnissen/Quellen; sie ersetzen keine amtliche Dienststellen-Punkteliste. Besonders bei gegenüberliegenden Richtungsfahrbahnen und parallelen Zufahrten muss jeder Treffer geprüft werden.

## A4

- Rastanlage Werratal: 51.006114, 10.183371 — https://www.autobahn-baustellen.de/rastanlage/a4-werratal/
- PWC Hainich Nord: 50.992588, 10.445750 — https://www.autobahn-baustellen.de/rastanlage/a4-pwc-hainich-nord/
- PWC Hainich Süd: 50.992654, 10.439191 — https://www.autobahn-baustellen.de/rastanlage/a4-pwc-hainich-sued/
- Rastanlage Hörselgau: 50.912840, 10.587330 — https://trucker-treffpunkt.de/park-und-rast/rastanlage/a4/hoerselgau
- PWC Willroder Forst Nord: 50.911157, 11.072748 — https://www.autobahn-baustellen.de/rastanlage/a4-pwc-willroder-forst-nord/
- Eichelborn Nord: 50.949600, 11.197200 — https://spritfuchs.vercel.app/tankstelle/8b5e52ac-30e7-441d-8319-7c6eb2a20c50
- Eichelborn Süd: 50.948194, 11.196690 — https://www.autobahn-baustellen.de/rastanlage/a4-tr-eichelborn-sued/

## A71

- PWC Erfurter Becken West: 50.939572, 10.953774 — https://www.goingelectric.de/stromtankstellen/Deutschland/Erfurt/Rastplatz-Erfurter-Becken-West-A71/109130/
- PWC Erfurter Becken Ost: 50.939318, 10.955802 — https://www.goingelectric.de/stromtankstellen/Deutschland/Erfurt/Rastplatz-Erfurter-Becken-Ost-A71/109131/
- PWC Dornheimer Rieth West: 50.837878, 11.010209 — https://www.goingelectric.de/stromtankstellen/Deutschland/Dornheim/Rastplatz-Dornheimer-Rieth-West-A71/106007/
- PWC Dornheimer Rieth Ost: 50.836590, 11.011610 — https://www.goingelectric.de/stromtankstellen/Deutschland/Dornheim/Rastplatz-Dornheimer-Rieth-Ost-A71/106006/

Die Existenz der PWC-Anlagen und ihre Autobahn-Kilometrierung sind außerdem in den öffentlich zugänglichen Verzeichnissen für A4/A71 aufgeführt: https://www.autobahnatlas-online.de/A4.htm und https://www.autobahnatlas-online.de/A71.htm.

## Nicht enthalten

- PWC Drei Gleichen, PWC Willroder Forst Süd, weitere Einsatz-/Kontrollstellen: im ersten Paket bewusst nicht mit geratenen Koordinaten angelegt. Sie können manuell als Halt gespeichert oder nach Import einer dienstlich freigegebenen Koordinatenliste ergänzt werden.
- Keine Polizeidienststellen, Einsatzorte, taktischen Kontrollstellen oder nicht öffentlichen Punkte sind vorbefüllt.

## Lizenz / Aktualität

Dies ist ein zusammengesetzter POI-Katalog mit Quellenangaben und Näherungskoordinaten. Die genutzten Quellen können ihre Angaben ändern. Vor dienstlicher Nutzung muss die Liste gegen eine aktuelle, dienstlich freigegebene Datenquelle geprüft werden. OpenStreetMap-Daten stehen unter ODbL; eine echte Offline-Straßenkarte mit OSM-Straßenlayer ist in diesem Paket noch nicht enthalten.

## Automatische Zuordnung

Ein Halt wird nur dann automatisch benannt, wenn ein Katalogpunkt höchstens 250 m entfernt ist und kein zweiter Punkt nahezu gleich plausibel liegt (Abstand der beiden Treffer unter 60 m = keine automatische Auswahl). Dadurch sollen insbesondere Verwechslungen zwischen nahe beieinanderliegenden Richtungsfahrbahnen vermieden werden. Ein manueller Name bleibt jederzeit möglich.


## Kilometrierung (v2.3)

Die ausgewählten Stationswerte in `app/src/main/assets/autobahn_kilometer_a4_a71.csv` folgen den Streckenübersichten des Autobahnatlas Online:

- A4: https://www.autobahnatlas-online.de/A4.htm — u. a. Werratal 315,5; Hainich 265,4; Hörselgau 249,4; Kreuz Erfurt 221,9; Willroder Forst 213,5; Eichelborn 203,5; Nohra 200,3.
- A71: https://www.autobahnatlas-online.de/A71.htm — u. a. Mittelhausen 57,5; Erfurter Becken 72,3; Kreuz Erfurt 77,8; Arnstadt-Nord 84,2; Dornheimer Rieth 86,2; Arnstadt-Süd 87,2.

Ältere BASt-Angaben weichen auf der A71 an einzelnen Punkten ab (Stand des BASt-Verzeichnisses: 01.09.2016). Die abweichenden Werte sind in `KILOMETRIERUNG_A4_A71.md` ausdrücklich dokumentiert. Der BKG-Dienst POI-Open/KmBAB stellt darüber hinaus georeferenzierte ganze Kilometerpunkte mit Richtungsangabe bereit; diese können in Version 2.3 lokal importiert werden:

- Service: https://services2.arcgis.com/jUpNdisbWqRpMo35/ArcGIS/rest/services/KmBAB/FeatureServer
- BKG-Produktdokumentation: https://sg.geodatenzentrum.de/web_public/gdz/dokumentation/deu/poi-open.pdf

Die importierten Kilometerpunkte sind berechnete Referenzpunkte und laut BKG nicht vor Ort mit jeder Kilometrierungstafel abgeglichen. Eine dienstliche Prüfung ist deshalb erforderlich.
