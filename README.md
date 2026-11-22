# EzPlanner

Einfache Android-App für die eigene Finanzplanung – alles wird manuell gepflegt, keine Bankanbindung, alle Daten bleiben auf dem Gerät.

- Kontostand aus allen Buchungen (Gehalt rein, Fixkosten raus), jederzeit per „Kontostand setzen“ korrigierbar
- Monatsbudget mit Warnschwelle – belastet nur durch Ausgaben vom Typ „Budget“, nicht durch Fixkosten oder Sonderausgaben
- Daueraufträge: monatlich, vierteljährlich, halbjährlich, jährlich; gebucht am Fälligkeitstag ab 0 Uhr
- Spartöpfe (z. B. Notgroschen) und Schuldentöpfe pro Person
- Auswertungen pro Monat mit Diagrammen, Verlauf mit Bearbeiten/Löschen
- Backup (JSON) und CSV-Export

**Download:** die aktuelle APK (`EzPlanner-<version>.apk`) liegt unter [Releases](https://github.com/Wuerfelhusten/EzPlanner/releases/latest). Einmal installiert, aktualisiert sich die App selbst.

## Build

Voraussetzungen: JDK 17+ und Android SDK.

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew assembleRelease      # signiert mit keystore.properties, falls vorhanden
./gradlew testDebugUnitTest
```

`keystore.properties` (nicht in git): `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

## Release

1. Version in `gradle.properties` erhöhen (`ezplanner.versionName=MAJOR.MINOR.PATCH`).
2. Abschnitt `## <version>` in `CHANGELOG.md` ergänzen.
3. Committen, Tag `v<version>` pushen – der Workflow `.github/workflows/release.yml` testet, baut die signierte APK, prüft Signatur, Version und Paketname und veröffentlicht das GitHub-Release.

Einmalig: Signing-Secrets mit `tools\release\set-github-secrets.ps1` setzen.
`tools\release\show-signing-key.ps1` zeigt die Signing-Daten zum Ablegen im Passwortmanager.
