# Changelog

Every release needs a section `## <version>` here (the release workflow fails without one).
The section becomes the GitHub release text and is shown in the app after the update.

## 2.0.0

Komplett neu entwickelte Version von EzPlanner.

- Ausgabentypen: Fixkosten, Budget und Sonderausgaben (z. B. Werkstatt, Urlaub) – nur „Budget“ wird vom Monatsbudget abgezogen; eigene Typen lassen sich anlegen
- Daueraufträge werden am Fälligkeitstag ab 0 Uhr gebucht, nicht mehr erst zur Uhrzeit, zu der sie angelegt wurden; verpasste Termine werden nachgebucht
- Beim Dauerauftrag ist „Ausgabe“ vorausgewählt
- Fälligkeit monatlich, vierteljährlich, halbjährlich oder jährlich, optional mit Enddatum, pausierbar
- Spartöpfe mit Sparziel und Schuldentöpfe pro Person („schuldet mir“ / „ich schulde“) mit Teilrückzahlungen und Verlauf
- Kategorien mit Diagrammen: Ausgaben nach Kategorie und Einnahmen/Ausgaben der letzten 6 Monate
- Buchungen im Verlauf bearbeiten oder per Wischen löschen (mit Rückgängig)
- Backup und Wiederherstellung als JSON-Datei, Export aller Buchungen als CSV
- Automatische Updates direkt in der App
