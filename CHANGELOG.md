# Changelog

Every release needs a section `## <version>` here (the release workflow fails without one).
The section becomes the GitHub release text and is shown in the app after the update.

## 2.4.0

- Topf bearbeiten: Der aktuelle Stand lässt sich direkt ändern – nur der Topf wird angepasst, ohne Einzahlung, Entnahme, Einkommen oder Ausgabe

## 2.3.0

- Neuer Ausgabentyp „Investieren“ neben Sonderausgaben – Geld für ETF oder Aktien wird in Auswertung, Geldfluss und Aufträgen gesondert aufgelistet und zählt nicht zum Monatsbudget
- Aufträge: Ausgaben sind nach Ausgabentyp gegliedert (Fixkosten, Budget, Sonderausgaben, Investieren …), jeweils mit Summe pro Monat
- Aufträge: neuer Bereich „Geplante Einzelbuchungen“ – einmalige Buchungen mit Datum in der Zukunft (z. B. Sonderausgaben) anzeigen, antippen und bearbeiten, oder direkt neu planen

## 2.2.0

- Dauerauftrag bearbeiten: neue Option „Diese Buchung auch anpassen“ – korrigiert die letzte bereits gebuchte Buchung mit (z. B. falsch eingetragenes Gehalt); bei geändertem Datum wird sie zum neuen Termin neu gebucht
- Wird nur das Datum eines Dauerauftrags verschoben, wird derselbe Monat nicht mehr ein zweites Mal gebucht
- Daueraufträge direkt in Spartöpfe: Art „Sparen“ im Dauerauftrag, und im Topf gibt es „Dauerauftrag einrichten“ samt Liste der Daueraufträge dieses Topfs
- Ein neu angelegter Spartopf wird im Formular automatisch ausgewählt
- Behoben: Buchungen mit Datum in der Zukunft wurden sofort vom Kontostand abgezogen – sie zählen jetzt erst ab ihrem Datum und sind im Verlauf als „geplant“ markiert
- Übersicht zeigt zusätzlich den voraussichtlichen Kontostand am Monatsende (inkl. geplanter Buchungen und Daueraufträge)

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
