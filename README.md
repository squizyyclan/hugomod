# HugoSMP Utils (Fabric, Minecraft 1.21.4, nur Client)

Läuft rein clientseitig, auf dem Server muss nichts installiert werden.

## Jar bauen (Variante A: GitHub, nichts lokal installieren)
1. Neues GitHub-Repo anlegen, den Inhalt dieses Ordners hochladen (inkl. `.github/`).
2. Reiter **Actions** -> Workflow **build** -> nach dem Durchlauf unter **Artifacts** `hugosmp-utils` herunterladen.
3. Die Datei `hugosmp-utils-1.0.0.jar` (nicht `-sources`) in den `mods`-Ordner legen.

## Jar bauen (Variante B: lokal)
JDK 21 + Gradle 8.12 installieren, im Ordner `gradle build` ausführen. Jar liegt in `build/libs/`.

## Voraussetzungen im Spiel
Fabric Loader (>= 0.16) + Fabric API für 1.21.4.

## Features
- **Zahlen-Kürzel**: `150k`, `1.5m`, `2kk`, `3mio`, `1b`, `2mrd` werden im Chat und in allen Befehlen zu ausgeschriebenen Zahlen. Auch im Pay-Menü.
- **/pay** öffnet ein Menü (Spielerliste mit Suche, Betrag, Schnellwahl 1k/10k/100k/1m, Bestätigung bei hohen Beträgen). `/pay Name 150k` geht weiterhin normal an den Server.
- **/order <item>** schickt `/order <item>` an den Server, wählt im Menü die Order mit dem höchsten Preis pro Item, legt passende Items aus dem Inventar ein und bestätigt.
- **Werkzeug-Schutz**: Werkzeuge/Waffen sind gesperrt, sobald die Rest-Haltbarkeit <= Grenzwert ist (Standard 10). Optional automatisch `/home <name>`. Taste "Überbrücken" (unbelegt, in den Steuerungen setzbar) hebt die Sperre solange sie gehalten wird.
- **Einstellungen**: Taste `H` oder `/hugosmp` (Tabs Allgemein / Pay / Order / Werkzeug). Datei: `config/hugosmputils.json`.
- **Extras**: Haltbarkeits-Anzeige unter dem Fadenkreuz, `/calc 150k*3+2m`, Pay-Menü merkt sich letzten Empfänger/Betrag, optionale Taste zum Öffnen des Pay-Menüs.

## Wichtig zu /order
Wie das Order-Menü auf HugoSMP aufgebaut ist, ist nicht bekannt. Die Mod sucht den Preis pro Item in der Lore der Menü-Items (Zeilen mit "Price/Preis/each/pro/$") und den Bestätigen-Button am Namen ("Confirm/Bestätigen/...") bzw. an grünen Glasscheiben.
Passt das nicht: Befehl, Menü-Titel und Wartezeit lassen sich in den Einstellungen (Tab Order) ändern. Im Zweifel Screenshot + Lore-Text des Menüs schicken, dann passe ich die Erkennung an.
Prüfe vorher die Serverregeln zu Automatisierung/Makros.

## Grenzen
- Kürzel werden in Chat, Befehlen und den Mod-Menüs umgewandelt, nicht in Amboss-/Schild-Textfeldern.
- Auf 1.21.4 ausgelegt; für andere Versionen müssen die Versionen in `gradle.properties` und evtl. Details im Code angepasst werden.
