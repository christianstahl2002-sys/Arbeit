# Schichtjournal – Android-Quellprojekt

Eine lokale Android-App für eine **manuell gestartete Schichtaufzeichnung**. Sie erfasst die Route des eigenen Geräts, Zeitblöcke außerhalb eines gespeicherten Dienststellen-Radius und längere Aufenthalte. Es findet keine Aufzeichnung außerhalb einer aktiven Schicht statt.

## Funktionsumfang

- Schicht **manuell starten und beenden**.
- Sichtbare, laufende Android-Benachrichtigung während der Aufnahme.
- GPS-Route während der aktiven Schicht (einstellbares Intervall 10 / 15 / 30 / 60 Sekunden).
- Dienststellen-Standort vor Ort speichern und Erkennungsradius (50–500 m) einstellen.
- Zeitblöcke außerhalb des Radius werden mit Zeitstempel für Verlassen und Rückkehr ermittelt.
- Hysterese gegen GPS-Flattern; der Radius-Übergang ist daher eine GPS-Schätzung und kein geeichter Zeitstempel.
- Längere Stillstände werden nach etwa zwei Minuten als Halt erkannt. Ankunftszeit ist der Beginn des ersten passenden GPS-Stillstandskandidaten; Abfahrt wird bei Weiterfahrt erkannt.
- Halte können manuell markiert und automatisch erkannte Halte nachträglich benannt werden.
- Vorinstallierter **lokaler POI-Katalog A4/A71** und optionaler weiterer POI-Import im Format `name;kategorie;latitude;longitude`. Ein passender Punkt im Umkreis von 250 m kann den Halt automatisch benennen.
- **Kilometrierungs-Referenzpunkte A4/A71** mit Streckenwerten, Quellenhinweisen und – wo vorhanden – Koordinaten. Aufenthalte und Routenpunkte erhalten einen nahegelegenen Kilometer-Referenzwert, sofern ein georeferenzierter Punkt innerhalb von 800 m liegt.
- Geografische Track-/Punkteansicht mit Start, Ende, Stopps und örtlich nahen POIs; darunter detaillierte Zeitlisten.
- CSV-Export mit Schichten, Außenzeitblöcken, Aufenthalten, Koordinaten, GPS-Punkten und Ereignissen.
- GPX-Export der letzten Schicht inklusive Wegpunkten für Stopps.
- Speicherung in einer privaten SQLite-Datenbank innerhalb der App. Keine Cloud, keine OpenAI-API, keine Karten-/Geocoding-Abfrage an externe Server.

## Regionaler POI-Katalog A4/A71 (Version 2.1)

Ein lokaler Katalog wird beim ersten Start automatisch eingespielt, sofern noch keine eigenen POIs importiert wurden. Enthalten sind öffentliche Rastanlagen/PWC-Punkte im Korridor A4 Hessen-Grenze bis Nohra und A71 Arnstadt bis Erfurt-Mittelhausen. Die Karte markiert Katalogpunkte in der Nähe des aufgezeichneten GPS-Verlaufs; ohne Schicht zeigt sie eine Übersicht der Katalogpunkte.

Vorinstallierte Punkte: Werratal, Hainich Nord/Süd, Hörselgau, Willroder Forst Nord, Eichelborn Nord/Süd, Erfurter Becken West/Ost und Dornheimer Rieth West/Ost. Quellen und Koordinaten stehen in `POI_Quellen_A4_A71.md`.

## Autobahnkilometrierung (Version 2.3)

Die App enthält ausgewählte Strecken-/Anlagenreferenzen für die A4 (Landesgrenze Hessen/Thüringen bis Nohra) und A71 (Arnstadt bis Erfurt-Mittelhausen). Als einheitliche Referenzquelle wird im Paket die aktuelle Streckenübersicht des Autobahnatlas Online verwendet. Am A4-Kreuz Erfurt ist z. B. km 221,9 hinterlegt; am A71-Kreuz Erfurt km 77,8. Die älteren Werte aus dem BASt-Verzeichnis (Stand 2016) können abweichen und sind in `KILOMETRIERUNG_A4_A71.md` dokumentiert.

**Neu: BKG-Kilometerpunkte importieren.** Das BKG stellt über POI-Open/KmBAB ganzzahlige Kilometerpunkte mit Autobahnbezeichnung und Richtungsangabe bereit. Version 2.3 kann einen gespeicherten BKG-GeoJSON-/ArcGIS-JSON- oder CSV-Export importieren. Der Importfilter behält A4 km 190–335 und A71 km 50–90; die Daten werden lokal im privaten App-Speicher abgelegt. Für Koordinaten wird WGS84/EPSG:4326 erwartet. Die App benötigt dafür keine Netzwerkberechtigung und überträgt keine GPS- oder Schichtdaten.

Bei einem GPS-Punkt, Aufenthalt oder Export ordnet die App den nächstgelegenen georeferenzierten Kilometerpunkt innerhalb von 800 m zu, soweit ein importierter BKG-Punkt oder eine lokale Anlagenreferenz vorhanden ist. Richtungsangaben werden aus dem BKG-Datensatz angezeigt, sofern sie mitgeliefert wurden. Es wird nicht linear zwischen beliebigen Streckenreferenzen interpoliert. BKG-Punkte sind laut Produktbeschreibung berechnete 1-km-Punkte und werden nicht vor Ort mit jeder Kilometrierungstafel abgeglichen. Treffer deshalb insbesondere bei parallelen Richtungsfahrbahnen prüfen.

Die Importanleitung steht in `BKG_KILOMETRIERUNG_IMPORT.md`; die ausgewählten Punkte und Quellen sind in `KILOMETRIERUNG_A4_A71.md` dokumentiert. Die App wird nicht mit einer bundesweiten BKG-Datei ausgeliefert; du kannst den regionalen Export nach dem Speichern in der App importieren.

## Rastanlagen automatisch benennen

Die App kann nicht ohne Weiteres zuverlässig wissen, wie ein Rastplatz vor Ort heißt. Eine umgebungsabhängige Online-Suche würde Einsatz-/Standortdaten an einen Kartendienst übertragen. Daher wird standardmäßig **keine** solche Abfrage gemacht.

Stattdessen können autorisierte, dienstlich freigegebene Punkte lokal importiert werden. Beispiel für eine CSV-Datei (UTF-8, Semikolon als Trenner, Koordinaten mit Dezimalpunkt):

```csv
name;kategorie;latitude;longitude
Rastanlage Beispiel Nord;Rastanlage;50.123456;10.123456
Autobahnparkplatz Beispiel;Parkplatz;50.234567;10.234567
Kontrollstelle Beispiel;Kontrollstelle;50.345678;10.345678
```

Die Beispielorte und Koordinaten sind Platzhalter, keine realen Rastanlagen. Für echte Zuordnungen bitte nur eine freigegebene und aktuelle Punkteliste deiner Dienststelle verwenden. Die App ordnet einen erkannten Halt dem nächsten importierten Punkt innerhalb von 250 m zu; die Zuordnung muss geprüft werden, insbesondere bei parallel verlaufenden Richtungsfahrbahnen oder Rastanlagen gegenüber.

## Projekt bauen

1. Android Studio öffnen und den Ordner `AussendienstTracker` importieren.
2. Android SDK API 35 und JDK 17 installieren.
3. Gradle-Synchronisierung ausführen.
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)** wählen.
5. Die Debug-APK liegt normalerweise unter `app/build/outputs/apk/debug/app-debug.apk`.

Dieses Quellprojekt wurde in der bereitgestellten Laufzeitumgebung nicht mit einem Android SDK gebaut oder auf einem physischen Samsung-Gerät getestet. Eine installierbare APK ist deshalb nicht enthalten.

## Ersteinrichtung auf dem Handy

1. App installieren und öffnen.
2. An der Dienststelle stehen und **Dienststellen-Standort hier speichern** antippen.
3. Erkennungsradius passend zum Gelände einstellen; mit 150 m beginnen und vor Nutzung testen.
4. GPS-Abtastrate wählen. 10–15 Sekunden liefern mehr Details, erhöhen aber den Akkuverbrauch.
5. Zu Schichtbeginn **Schicht starten** antippen. Android muss präzisen Standort erlauben.
6. Die dauerhaft sichtbare Benachrichtigung zeigt an, dass die Schichtaufzeichnung läuft.
7. Bei Bedarf an einer Rastanlage/Kontrollstelle **Ankunft / Kontrollstelle jetzt markieren** antippen oder einen automatisch erkannten Halt nachträglich benennen.
8. Bei Schichtende **Schicht beenden** antippen.
9. CSV- oder GPX-Protokoll über die Android-Dateiauswahl exportieren.

Die App startet den Location-Foreground-Service aus der sichtbaren Aktivität heraus. Dafür ist kein pauschaler „Standort immer erlauben“-Zugriff erforderlich; es bleiben die Standortberechtigung und die sichtbare laufende Android-Benachrichtigung. Wird die App vom Nutzer zwangsbeendet, das Gerät ausgeschaltet oder der Standortzugriff entzogen, kann die Erfassung unterbrochen werden.

## Grenzen und dienstliche Nutzung

- GPS-Zeitpunkte können verzögert oder ungenau sein, besonders in Tunneln, unter Überführungen, bei schlechtem Empfang oder auf parallelen Fahrbahnen.
- Die Außenzeit wird über eine Radiusüberschreitung mit 35 m zusätzlicher Auslöseschwelle und 20 m Rückkehr-Hysterese erkannt. Die tatsächliche Grenze kann daher abweichen.
- Automatische Stillstandserkennung kann auch einen längeren Stau oder eine Verkehrskontrolle im fließenden Verkehr als Halt vorschlagen.
- Die Kartendarstellung in Version 2.3 zeigt GPS-Track, vorinstallierte Rastanlagen-Punkte und georeferenzierte Kilometer-Referenzpunkte geografisch; eine vollständige Offline-Straßenkarte und Navigation sind noch nicht enthalten.
- Die App ist ein Hilfsprotokoll, kein geeichtes oder rechtlich/dienstlich freigegebenes Zeiterfassungssystem. Vor dienstlicher Verwendung müssen Datenschutz, IT-Sicherheitsvorgaben, Aufbewahrung und Freigabe mit der zuständigen Stelle geklärt werden. Standort- und Einsatzdaten nicht in private Cloud-Dienste oder nicht freigegebene Apps übertragen.

## Datenquellen Kilometrierung

- A4: Autobahnatlas Online, Streckenübersicht A4 (Anschlussstellen und Rastanlagen mit publizierten Stationswerten).
- A71: Bundesanstalt für Straßenwesen (BASt), Autobahnverzeichnis (u. a. Anschlussstellen, PWC Erfurter Becken, Kreuz Erfurt und PWC Dornheimer Rieth).
- Koordinaten für die regionalen Rastanlagenpunkte stammen aus dem bereits mitgelieferten regionalen POI-Katalog. Die Koordinate des Autobahnkreuzes ist ein gerundeter Kreuzmittelpunkt und keine Fahrstreifen- oder Markierungstafelkoordinate.

## Technik

- Kotlin, Android API 26–35, JDK 17
- Google Play services Location / Fused Location Provider
- Android Location Foreground Service (`foregroundServiceType="location"`)
- SQLite lokal auf dem Gerät
- Keine Netzwerkberechtigung in der App erforderlich

## APK automatisch über GitHub Actions bauen

Dieses Projekt enthält unter `.github/workflows/build-apk.yml` einen Build-Workflow. Er baut bei Push auf `main`/`master` sowie manuell eine Debug-APK.

1. Lege auf GitHub ein **privates** neues Repository an (dienstliche Standortsoftware/Quellcode vorsorglich privat halten).
2. Lade den **Inhalt dieses Projektordners** in das Repository hoch. Im Stammverzeichnis müssen `settings.gradle.kts`, `build.gradle.kts` und `app/` liegen; der Ordner `.github/workflows/` muss ebenfalls mit hochgeladen werden.
3. Öffne im Repository den Reiter **Actions** und wähle **Build Android APK**. Bei Bedarf **Run workflow** starten.
4. Nach erfolgreichem Lauf den Bereich **Artifacts** öffnen und `Schichtjournal-debug-APK` herunterladen.
5. ZIP entpacken und `app-debug.apk` auf das Android-Gerät übertragen. Android kann vor der Installation die Freigabe „Unbekannte Apps installieren“ verlangen.

Die so erzeugte APK ist eine **Debug-Testversion**, nicht signiert für eine Veröffentlichung im Play Store. Die GitHub-Aktion baut nur den Quellcode. Schicht- und GPS-Daten werden durch diesen Build nicht nach GitHub übertragen; sie entstehen erst bei der Nutzung der App auf dem Gerät. Den Quellcode trotzdem nur in einem privaten Repository ablegen.

Der Workflow wurde hier vorbereitet, aber wegen fehlendem Android-SDK/Gradle und fehlendem Netzwerkzugriff in dieser Laufzeit nicht ausgeführt. Der erste Online-Build kann deshalb noch Buildfehler zeigen; insbesondere ist vor einem dienstlichen Einsatz ein Test auf deinem Samsung erforderlich.
