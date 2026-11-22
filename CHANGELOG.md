# Changelog

Every release needs a section `## <version>` here (the release workflow fails without one).
The section becomes the GitHub release text and is shown in the app after the update.

## 2.1.0

- Neues Sankey-Diagramm „Geldfluss“ in den Auswertungen: Einkommen → Ausgabentypen, Sparen und Übrig, wahlweise für Monat oder Jahr und aufgeschlüsselt nach Kategorien; lässt sich als Bild teilen
- Sparen ist jetzt ein eigener Bereich: Geld in Spar- und Schuldentöpfe zählt nicht mehr als Ausgabe, Entnahmen nicht mehr als Einkommen
- Auswertungen rechnen geplante Daueraufträge mit ein (z. B. Gehalt am Monatsende) – abschaltbar
- Daueraufträge sind nach Einkommen, Ausgaben und Sparen gruppiert, darin nach Intervall (monatlich, vierteljährlich, halbjährlich, jährlich); sortierbar nach Betrag, Name oder Fälligkeit
- „Demnächst fällig“ aus der Übersicht entfernt
- Behoben: Tippen auf ein Datumsfeld öffnete keinen Kalender

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
