# Spec: Player Time Manager (Welt- und Dimensions-Zeitlimits)

**Stand:** 2026-10-13, Ergebnis des Brainstormings mit dem User. **Status:** zur Durchsicht.
**Anlass:** [DimensionAccessManager GH#7](https://github.com/GeheimagentNr1/DimensionAccessManager/issues/7) (Feerjais, 2026-10-09): Zeit pro Dimension begrenzen, Timer läuft nur in der Dimension, bei Ablauf automatischer Teleport in eine konfigurierbare Dimension, Befehle zum Hinzufügen/Abziehen von Restzeit. Umgesetzt als **eigenständiger Mod**, nicht als Teil von DimensionAccessManager.

> **Name (entschieden 2026-10-14): Player Time Manager** - Mod-ID `player_time_manager`, Package `de.geheimagentnr1.player_time_manager`, Befehl `/playtime` (kein Vanilla-Befehl dieses Namens), Projekt-Slug `player-time-manager` auf CurseForge und Modrinth (frei, geprüft 2026-10-14; Titel „Player Time Manager“ nirgends vergeben), Repo `GeheimagentNr1/PlayerTimeManager`. Ähnlich klingend, aber andere Plattformen: das Paper/Purpur-Plugin „PlayTimeManager“ (Modrinth-Slug `playtimemanager`, Spielzeit-Tracker ohne Limits), der Fabric-Mod „Server Playtime Manager“ und der Forge-1.20.1-Mod „TimeWatcherCore“ (beide Tageslimit). Kein technischer Konflikt, nur Verwechslungsgefahr bei der Suche - in der Mod-Beschreibung „NeoForge“ und die Dimensions-Limits hervorheben.

## 1. Ziel und Rahmen

- Server-Admins begrenzen, wie lange Spieler **insgesamt in der Welt** (z. B. pro Tag) und **in einzelnen Dimensionen** spielen dürfen. Die Zeit wird automatisch nach Zeitplan aufgefüllt und kann per Befehl pro Spieler angepasst werden.
- **Loader/Versionen:** NeoForge, Minecraft **1.21.1 bis 26.3**.
- **Seiten:** Logik **rein serverseitig**, kein Client-Mod nötig (Vanilla-Clients können beitreten). Keine Dist-Beschränkung, der Mod läuft auch im **Singleplayer** (integrierter Server). `displayTest = IGNORE_SERVER_VERSION`.
- **Alles bei gestopptem Server konfigurierbar** über die TOML-Config; Befehle ändern dieselben Werte und schreiben sie in die Datei zurück.
- **Nicht im Umfang:** AFK-Erkennung; unterschiedliche Limits pro Spieler (alle Spieler haben dieselben Limits, nur die Restzeit unterscheidet sich); eigene Client-Anzeige; in der Config anpassbare Meldungstexte; Spielerlisten pro Dimension; Integrationen (Scoreboard, DiscordIntegration) - mögliche spätere Erweiterungen.

## 2. Begriffe

- **Bereich:** entweder die **Welt** (alle Dimensionen zusammen) oder eine **Dimension** aus der Config.
- **Limit:** Zeitwert eines Bereichs, der beim Refill gesetzt bzw. addiert wird (für alle Spieler gleich).
- **Restzeit:** pro Spieler und Bereich verbleibende Zeit.
- **Refill:** automatisches Auffüllen der Restzeit nach Cron-Zeitplan.
- **Zielpunkt:** Ort, an den ein Spieler bei abgelaufener Dimensionszeit teleportiert wird.

## 3. Zeitzählung

- **Welt-Limit und Dimensions-Limit laufen parallel:** Das Welt-Limit zählt in allen Dimensionen, das Limit einer Dimension zusätzlich nur dort. Was zuerst 0 erreicht, löst seine Folge aus (Abschnitt 6). Laufen beide gleichzeitig ab, gewinnt das Welt-Limit (Kick).
- Gezählt wird **einmal pro Sekunde** (alle 20 Server-Ticks) die **tatsächlich vergangene Echtzeit** seit der letzten Zählung (`System.currentTimeMillis()`-Differenz), nicht die Tickzahl - Server-Lag verlängert die Spielzeit nicht. Gezählt wird nur für Spieler, die online sind.
- **Keine Zählung** (Zeit pausiert), wenn:
  - der Spieler OP ist (Rechtestufe des Servers für OPs) und `ops_exempt = true` (Standard),
  - der Spieler in `exempt_players` steht oder `limited_players` nicht leer ist und er nicht darin steht,
  - der Spieler im Kreativmodus ist und `pause_in_creative = true` (Standard) bzw. im Zuschauermodus und `pause_in_spectator = true` (Standard),
  - der Server keine Ticks ausführt (Singleplayer-Pause; die Zeitmessung beginnt nach einer Pause neu und zählt die Pause nicht mit).
- **Neue Spieler** (kein gespeicherter Eintrag in einem Bereich) starten mit dem vollen Limit dieses Bereichs.
- **Hinweis Singleplayer:** Mit erlaubten Cheats gilt der Spieler als OP und ist mit `ops_exempt = true` ausgenommen. Für Singleplayer-Welten mit Cheats `ops_exempt = false` setzen. Gehört in Mod-Beschreibung und Wiki.

## 4. Refill

- **Zeitplan im Cron-Format** (5 Felder: Minute, Stunde, Tag des Monats, Monat, Wochentag), Auswertung identisch zu [crontab.guru](https://crontab.guru), das in Config-Kommentaren, Mod-Beschreibung und Wiki verlinkt wird.
- **Modus:** `SET` setzt die Restzeit auf das Limit, `ADD` addiert das Limit; `refill_max` deckelt die Restzeit nach einem `ADD` (leer = keine Obergrenze). Bei `SET` gilt `refill_max` nicht.
- **Welche Regel gilt:** Welt bzw. Dimension mit `use_custom_refill = true` nutzt ihre eigenen `refill_cron`, `refill_mode`, `refill_max`; sonst gelten die Werte aus `[general]`. Leerer `refill_cron` = kein automatischer Refill für diesen Bereich (nur manuell per Befehl).
- **Zeitzone:** `time_zone` (z. B. `Europe/Berlin`), leer = Systemzeitzone des Servers. Sommer-/Winterzeit: nicht existierende Zeitpunkte (Zeitumstellung vorwärts) werden übersprungen, doppelt vorkommende (rückwärts) zählen einmal.
- **Verarbeitung:** einmal pro Minute für **alle bekannten Spieler** (auch offline) und einmal beim Serverstart. Pro Spieler und Bereich wird der Zeitpunkt des **letzten verarbeiteten Refills** gespeichert; alle Cron-Zeitpunkte zwischen diesem und jetzt werden nachgeholt:
  - `SET`: nur einmal anwenden (Ergebnis ist unabhängig von der Anzahl).
  - `ADD`: für jeden verpassten Zeitpunkt einzeln, jeweils gedeckelt durch `refill_max`; die Anzahl nachgeholter Zeitpunkte ist auf 10 000 begrenzt (Schutz bei z. B. `* * * * *` nach langer Abwesenheit).
  - Für neue Spieler wird der letzte Refill-Zeitpunkt beim Anlegen auf „jetzt“ gesetzt.
- **Änderungen:** Ein geänderter Zeitplan gilt ab der Änderung; Zeitpunkte nach altem Schema werden nicht nachgeholt (der gespeicherte „letzte Refill“ wird beim Wechsel des Zeitplans auf „jetzt“ gesetzt). Ein geändertes Limit wirkt erst beim nächsten Refill; sofort mehr Zeit gibt es per Befehl.
- **Warnungen** (Abschnitt 8) werden nach einem Refill oder einer Erhöhung per Befehl für diesen Bereich zurückgesetzt.

### Cron-Parser

- Eigene Implementierung ohne externe Bibliothek (keine JarInJar-Abhängigkeit über die vielen Minecraft-Versionen), reines Java ohne Minecraft-Klassen, vollständig unit-getestet.
- Syntax: `*`, Zahlen, Listen (`1,15`), Bereiche (`1-5`), Schritte (`*/6`, `0-30/10`; ein Schritt nur nach `*` oder einem Bereich, wie Standard-Cron), Monats- und Wochentagsnamen (`JAN`-`DEC`, `SUN`-`SAT`, Groß-/Kleinschreibung egal). Wochentag `0` und `7` = Sonntag.
- **Standard-Sonderregel:** Sind Tag des Monats **und** Wochentag eingeschränkt (beide nicht `*`), passt ein Zeitpunkt, wenn **einer** von beiden passt.
- Ungültige Ausdrücke (falsche Feldanzahl, Werte außerhalb des Bereichs, unbekannte Namen) werden mit einer verständlichen Fehlermeldung abgelehnt.
- Funktionen: nächster Zeitpunkt nach X (für Anzeigen und Meldungen); alle Zeitpunkte im Intervall (X, Y] mit Höchstzahl (für das Nachholen).

## 5. Datenhaltung

- **Level-Attachments** (NeoForge Data Attachments), gespeichert mit dem normalen World Save und mit NBT-Editoren (z. B. NBTExplorer) bearbeitbar:
  - **An jeder Dimension**, die in `[[dimensions]]` eingetragen ist: `Map<UUID, Eintrag>`.
  - **An der Overworld zusätzlich** die Welt-Map (Minecraft speichert weltweite Daten ebenfalls an der Overworld).
  - **Eintrag:** Restzeit (Millisekunden, `long`), Zeitpunkt des letzten verarbeiteten Refills (Epoch-Millisekunden), Zeitplan-Kennung des letzten Refills (zum Erkennen eines geänderten Zeitplans).
- **Serialisierung per Codec** von Anfang an (stabiles Format über alle Versionen, kein `INBTSerializable`, das ab NeoForge 21.6 nicht mehr gelesen wird).
- **Ab 26.1** legt NeoForge Level-Attachments in `dimensions/<ns>/<path>/data/neoforge/data_attachments.dat` ab; das Welt-Upgrade lässt die alte Datei liegen. Der 26.1-Jar übernimmt die Daten aus der alten Datei einmalig (Migrations-Handler nach dem Vorbild von DimensionAccessManager, `LevelEvent.Load` + `DimensionDataStorage.readTagFromDisk`).
- Jede Änderung markiert das Attachment als geändert, damit der nächste Autosave sie schreibt.
- Daten einer Dimension, deren Eintrag entfernt wird, bleiben am Level liegen und werden wieder genutzt, wenn der Eintrag zurückkommt.

## 6. Ablauf, Teleport, Sperren

### Dimensionszeit bei 0

- **Teleport** zum Zielpunkt der Dimension (Abschnitt 7) mit Meldung: „Deine Zeit in <Dimension> ist aufgebraucht. Nächster Refill: <Datum Uhrzeit>“ bzw. „… bis ein Admin dir Zeit gibt“ ohne automatischen Refill.
- **Reittiere:**
  - **Ab 1.21.2:** Sitzt der Spieler auf einem Reittier/Fahrzeug, wird das gesamte Fahrzeug mit allen Passagieren teleportiert (`Entity.teleport(TeleportTransition)` auf das Root-Fahrzeug, wie Vanilla-Portale ab 1.21.2). Sitzen **weitere Spieler** im selben Fahrzeug, steigt der betroffene Spieler vorher ab und wird allein teleportiert.
  - **1.21.1:** Spieler steigt ab und wird allein teleportiert (auch Vanilla-Portale nehmen dort keine Reiter mit; im Plan in den 1.21.1-Quellen bestätigen).
  - Angeleinte Tiere bleiben zurück.
- **Betreten gesperrt**, solange die Restzeit 0 ist: `EntityTravelToDimensionEvent` wird für den Spieler abgebrochen (Portale, `/tp`, `/execute in`, andere Mods). Meldung wie oben, höchstens alle 5 Sekunden pro Spieler (im Portal feuert das Event jeden Tick). Andere Entities als Spieler sind nicht betroffen.
- **Login in einer Dimension ohne Restzeit:** sofort Teleport zum Zielpunkt.
- **Respawn in einer Dimension ohne Restzeit** (z. B. Seelenanker): der Respawn wird vorab per `PlayerRespawnPositionEvent` auf den Zielpunkt umgeleitet (kein kurzer Umweg über die gesperrte Dimension; der Spawnpunkt des Spielers bleibt erhalten), danach Meldung wie oben. Teleport direkt nach dem Respawn bleibt als Absicherung (entschieden 2026-10-18 nach Ingame-Test).

### Weltzeit bei 0

- **Kick** mit Meldung „Deine Spielzeit ist aufgebraucht. Nächster Refill: <Datum Uhrzeit>“ bzw. „… bis ein Admin dir Zeit gibt“.
- **Login gesperrt:** Spieler mit Welt-Restzeit 0 werden direkt nach dem Login mit derselben Meldung getrennt (`PlayerLoggedInEvent`; einen sauberen früheren Ablehnungspunkt gibt es nicht).
- Singleplayer: Der Spieler landet im Hauptmenü und kommt erst nach dem Refill wieder in die Welt.

## 7. Zielpunkt

- Pro Dimension `target_dimension` (Standard `minecraft:overworld`) und `target_position` (`"x y z"` oder `"x y z yaw pitch"`, Dezimalzahlen erlaubt).
- **Leere Position:** Overworld = Welt-Spawn; End = Obsidian-Plattform (Vanilla-Position 100 / 49 / 0, Plattform wird wie beim Betreten des Ends erzeugt); **jede andere Dimension braucht eine Position**.
- **Setzen per Befehl** wie `/setworldspawn`: `target here` übernimmt Dimension, Position und Blickrichtung des ausführenden Spielers.
- **Ausweg:** Ist der Zielpunkt ungültig (fehlende Position, unbekannte Dimension, unlesbarer Positions-String) oder liegt er in einer Dimension, für die der Spieler selbst keine Restzeit hat, geht der Teleport zum **Welt-Spawn der Overworld**. Ein unsicherer, aber gültiger fester Punkt (Lava, Wand) wird nicht korrigiert - der Admin hat ihn bewusst gesetzt.
- **Die Overworld kann nicht als `[[dimensions]]`-Eintrag limitiert werden** (sie ist der sichere letzte Ausweg; für sie gilt nur das Welt-Limit). Ein solcher Eintrag wird beim Laden mit Warnung ignoriert, der Befehl lehnt ihn ab.

## 8. Anzeige, Warnungen, Texte

- **`display_mode`:**
  - `NONE`: keine Daueranzeige, nur `/playtime` und Chat-Warnungen.
  - `ACTIONBAR`: jede Sekunde Text über der Hotbar, z. B. „Welt: 1h 02m | Nether: 12m 34s“.
  - `BOSSBARS` (Standard): eine Bossbar pro laufendem Limit (Welt immer während online, Dimension nur in der Dimension).
  - `BOSSBAR_SHORTEST`: eine Bossbar mit dem Limit, das zuerst abläuft.
- **Bossbar:** Titel „<Bereich>: <Restzeit>“, Füllstand Restzeit/Limit (bei Restzeit über dem Limit voll), Farbe grün, gelb unter `bossbar_yellow_below_percent` (Standard 50), rot unter `bossbar_red_below_percent` (Standard 10). Bei pausierter Zeit zeigt die Bossbar „pausiert“; ausgenommene Spieler (OP-Ausnahme, Listen) sehen keine Anzeige.
- **Chat-Warnungen:** beim Unterschreiten jedes Werts aus `warning_times` (Standard `10m`, `5m`, `1m`, `10s`) einmal pro Bereich, z. B. „Noch 5 Minuten im Nether“, mit Ton bei `warning_sound = true`. Leere Liste = keine Warnungen.
- **Texte:** alle übersetzbar mit englischem Fallback (`Component.translatableWithFallback`); mitgeliefert `en_us` und `de_de`. Vanilla-Clients sehen Englisch, Clients mit installiertem Mod und Singleplayer die Übersetzung.
- **Formate** (entschieden 2026-10-18 nach Ingame-Test):
  - Bossbar und Actionbar (Live-Anzeigen) im Uhr-Format: `4:05`, `29:59`, `1:30:00`, `2d 03:00:00`.
  - Chat, `/playtime`, Warnungen, Kick-Meldung und Befehle: Dauern als `1h 23m 45s` (Nullen-Einheiten weglassen), wie die Eingabe-Schreibweise.
  - Zeitpunkte fest als `dd.MM.yyyy HH:mm` (`19.10.2026 06:00`) in der eingestellten Zeitzone, ohne Config-Option.

## 9. Befehle

Rechtestufe aller Admin-Befehle: `command_permission_level` (0 - 4, Standard 3, wie bei DimensionAccessManager; ab 1.21.11 über das neue Rechtesystem). Spieler-Argumente wie bei `/whitelist` (`GameProfileArgument`): Namen auch von Offline-Spielern, die schon auf dem Server waren, sowie Selektoren. Dauern im Format `2h`, `30m`, `1h30m`, `45s`.

| Befehl | Rechte | Wirkung |
|---|---|---|
| `/playtime` | keine | eigene Restzeiten aller laufenden Limits und jeweils nächster Refill |
| `/playtime time get <spieler> [world\|dimension <dim>]` | Admin | Restzeiten anzeigen |
| `/playtime time add\|remove\|set <spieler> world\|dimension <dim>\|all <dauer>` | Admin | Restzeit ändern (nicht unter 0) |
| `/playtime time refill <spieler> world\|dimension <dim>\|all` | Admin | Refill sofort nach der geltenden Regel |
| `/playtime time overview [world\|dimension <dim>]` | Admin | alle bekannten Spieler (auch offline, mit nachgeholten Refills), aufsteigend nach Restzeit |
| `/playtime config general refill_cron\|refill_mode\|refill_max\|ops_exempt [wert]` | Admin | globalen Wert anzeigen/setzen |
| `/playtime config world enabled\|limit\|use_custom_refill\|refill_cron\|refill_mode\|refill_max [wert]` | Admin | Welt-Wert anzeigen/setzen |
| `/playtime config dimension <dim> add\|remove` | Admin | Dimension in `[[dimensions]]` aufnehmen/entfernen |
| `/playtime config dimension <dim> enabled\|limit\|use_custom_refill\|refill_cron\|refill_mode\|refill_max [wert]` | Admin | Dimensions-Wert anzeigen/setzen |
| `/playtime config dimension <dim> target here \| <ziel-dim> <x y z> [yaw pitch] \| default` | Admin | Zielpunkt setzen (`default` = Ziel Overworld, Position leer) |
| `/playtime players limited\|exempt list\|add\|remove <spieler>` | Admin | Spielerlisten pflegen |

- Ohne `[wert]` wird der aktuelle Wert angezeigt. Der Cron-Ausdruck steht am Ende und wird ohne Anführungszeichen angegeben (`… refill_cron 0 8 * * 6,0`).
- Befehle prüfen Werte sofort und lehnen ungültige mit Fehlermeldung ab (Datei bleibt unverändert). Gültige Werte werden per `set(..)` **und `save()`** in die TOML geschrieben (`ConfigValue.set` speichert sonst nicht) und wirken sofort.
- Nur in der TOML (kein Befehl): `command_permission_level`, `time_zone`, `pause_in_creative`, `pause_in_spectator`, `display_mode`, Bossbar-Prozente, `warning_times`, `warning_sound`.
- Kein Reload-Befehl: NeoForge übernimmt Änderungen an der TOML-Datei im laufenden Betrieb selbst.

## 10. Config

Datei `config/player_time_manager-server.toml` im Server-Ordner (NeoForge 21.1 legt neue Server-Configs dort an, nicht mehr unter `world/serverconfig/`; eine vorhandene Datei in `world/serverconfig/` hat Vorrang - im Ingame-Test 2026-10-18 festgestellt; im Singleplayer `config/` der Client-Instanz, gilt dann für alle Welten). Umsetzung mit der Listen-Technik aus DiscordIntegration (`api/config/`: `AbstractConfig`, `AbstractSubConfig`, `AbstractListEntryConfig`, `SubConfigListFactory`, `ListEntryConfigData`); der Plan nutzt eine eigene kleine Umwandlung statt einer Kopie dieser Klassen (Begründung dort). **Jeder Wert hat einen englischen Kommentar** mit Bedeutung, Format und erlaubten Werten.

```toml
[general]
	#Permission level (0 - 4) required for all admin commands
	command_permission_level = 3
	#Operators are not affected by any time limit (set to false for singleplayer worlds with cheats)
	ops_exempt = true
	#Time does not count down while a player is in creative mode
	pause_in_creative = true
	#Time does not count down while a player is in spectator mode
	pause_in_spectator = true
	#If not empty, ONLY these players are affected by the time limits. Entries: { name = "...", uuid = "..." }
	limited_players = []
	#These players are never affected by the time limits (has priority over limited_players). Entries: { name = "...", uuid = "..." }
	exempt_players = []
	#Time zone of the refill schedules, e.g. "Europe/Berlin" ("" = system time zone of the server)
	time_zone = ""
	#Default refill schedule in cron format (minute hour day month weekday), e.g. "0 6 * * *" = daily at 06:00.
	#Generator and explanation: https://crontab.guru ("" = no automatic refill)
	refill_cron = "0 6 * * *"
	#Default refill mode: SET = set the remaining time to the limit, ADD = add the limit to the remaining time
	refill_mode = "SET"
	#Default maximum remaining time after an ADD refill, e.g. "4h" ("" = no maximum)
	refill_max = ""
	#How players see their remaining time: NONE = only via the command, ACTIONBAR = text above the hotbar,
	#BOSSBARS = one bossbar per running limit, BOSSBAR_SHORTEST = one bossbar with the limit that runs out first
	display_mode = "BOSSBARS"
	#The bossbar turns yellow below this percentage of the limit (0 - 100)
	bossbar_yellow_below_percent = 50
	#The bossbar turns red below this percentage of the limit (0 - 100, lower than the yellow value)
	bossbar_red_below_percent = 10
	#Remaining times that trigger a chat warning, e.g. ["10m", "1m", "10s"] (empty list = no chat warnings)
	warning_times = ["10m", "5m", "1m", "10s"]
	#Play a sound with each chat warning
	warning_sound = true

[world]
	#Is the time limit for the whole world (all dimensions together) active?
	enabled = false
	#Time per refill, e.g. "2h", "1h30m", "45m"
	limit = "2h"
	#true = use the refill settings below, false = use the refill settings from [general]
	use_custom_refill = false
	#Refill schedule in cron format (minute hour day month weekday), see https://crontab.guru ("" = no automatic refill)
	refill_cron = "0 6 * * *"
	#SET = set the remaining time to the limit, ADD = add the limit to the remaining time
	refill_mode = "SET"
	#Maximum remaining time after an ADD refill, e.g. "4h" ("" = no maximum)
	refill_max = ""

#Time limit of one dimension (the Overworld can't be limited here, use [world] instead)
[[dimensions]]
	#Name of the dimension, e.g. "minecraft:the_nether" or "modid:dimension"
	dimension = "minecraft:the_nether"
	#Is the time limit for this dimension active?
	enabled = false
	#Time per refill, e.g. "2h", "1h30m", "45m"
	limit = "1h"
	#true = use the refill settings below, false = use the refill settings from [general]
	use_custom_refill = false
	#Refill schedule in cron format (minute hour day month weekday), see https://crontab.guru ("" = no automatic refill)
	refill_cron = "0 6 * * *"
	#SET = set the remaining time to the limit, ADD = add the limit to the remaining time
	refill_mode = "SET"
	#Maximum remaining time after an ADD refill, e.g. "4h" ("" = no maximum)
	refill_max = ""
	#Dimension players are sent to when their time in this dimension is used up
	target_dimension = "minecraft:overworld"
	#Target position "x y z" or "x y z yaw pitch" ("" = world spawn in the Overworld, obsidian platform in the End;
	#required for all other dimensions)
	target_position = ""
```

- **Auslieferung:** alles deaktiviert (`enabled = false`); die Standard-Config enthält je einen deaktivierten Eintrag für `minecraft:the_nether` und `minecraft:the_end` als Vorlage. Ohne Admin-Eingriff verändert der Mod nichts.
- **Spielerlisten:** Einträge mit Name und UUID, geprüft wird die UUID. Steht von Hand nur ein Name drin, ergänzt der Mod die UUID beim ersten Login dieses Spielers und speichert sie.
- **Prüfung beim Laden** (Serverstart und Dateiänderung im Betrieb). Ungültige Werte verhindern **nie** den Start:
  - WARN-Zeile im Log mit Ursache und Lösung (z. B. passender Befehl),
  - Hinweis im Chat an OPs beim Login, solange Fehler bestehen,
  - sicherer Ersatzwert: ungültiger Cron → kein automatischer Refill für den Bereich; ungültige Dauer → Bereich deaktiviert; ungültiger/fehlender Zielpunkt → Welt-Spawn der Overworld; ungültige Bossbar-Prozente → Standardwerte; doppelter Dimensions-Eintrag → der erste gilt; Overworld-Eintrag → ignoriert; unbekannte Dimension → Eintrag inaktiv.
- Der Status ungültiger Einträge ist zusätzlich über `/playtime config dimension <dim>` sichtbar.

## 11. Versionen und Projektaufbau

- **Entwicklung** zuerst vollständig auf `develop_1.21.1`, danach Port Branch für Branch nach vorn (Skill `migrate-mod-version`, `Docs/migrations/BREAKING_CHANGES.md`). Ein Jar pro API-Bruch, kompiliert gegen die niedrigste Version seines Bereichs; Bytecode-Prüfung (`Docs/testing/tools/bincheck.ps1`) vor jedem Deploy.
- **Erwartete Jar-Schnitte** (genaue Grenzen im Plan per Kompilieren und bincheck bestätigen): 1.21.2 (`TeleportTransition`, Reittiere), 1.21.6 (`@EventBusSubscriber(bus = …)` entfällt), 1.21.9 (`NameAndId`/`GameProfile` in Spielerlisten und Spieler-Argument), 1.21.11 (`Identifier`, Rechtesystem), 26.1 (Java 25, Attachment-Datei-Migration). Voraussichtlich 5 - 7 Jars.
- **Packages** (`de.geheimagentnr1.player_time_manager`):
  - `config/` - Config-Klassen und -Prüfung (Listen-Technik wie DiscordIntegration, eigene Umwandlung statt Kopie von `api/config/`, siehe Plan)
  - `cron/` - Cron-Parser (reines Java)
  - `time/` - Zeitzählung, Refill-Berechnung, Dauer-Parser/-Formatierer (Logik reines Java, testbar mit fester Uhr)
  - `data/` - Level-Attachments mit Codecs, ab 26.1 Migrations-Handler
  - `commands/` - Befehle (nur Vanilla-Argumenttypen, siehe Plan)
  - `handlers/` - Tick, Login, Respawn, Dimensionswechsel, Config-Ladeereignis
  - `display/` - Bossbars, Actionbar, Warnungen
  - `teleport/` - Zielpunkt bestimmen, Teleport inkl. Reittier
- **Repo und Infrastruktur:** GitHub-Repo `GeheimagentNr1/PlayerTimeManager` **existiert (vom User angelegt 2026-10-18)**, lokal `C:\MinecraftModding\ActiveMods\PlayerTimeManager`, Branch `master` mit README, LICENSE, `.gitignore`, `.github/` (Issue-Vorlagen, `stale.yml`) wie bei den anderen Mods. `develop_1.21.1` zweigt von `master` ab (gleiche Historie wie bei den anderen Mods) und bekommt das Gradle-Grundgerüst nach Vorlage DimensionAccessManager `develop_1.21.1`, den CI-Workflow aus `Docs/ci/`, Einträge in `settings.gradle` (Composite Build), `0__env.cmd`, `Docs/VERSIONEN.md`, `MOD_KOMPATIBILITAET.md`, Mod-`CLAUDE.md` unter `Docs/mod-claude/`. Die README auf `master` stammt aus ManyIdeas Doors (Titel und CurseForge-Link falsch) und wird korrigiert.
- **Projekte (angelegt vom User 2026-10-18):** CurseForge `1752337`, Modrinth `zb9AY963`, Slug jeweils `player-time-manager`; Kurzbeschreibung „Adds play time limits per world and per dimension, with automatic refills.“ Auf Modrinth sind Icon, Kurzbeschreibung, Issues-/Source-/Wiki-Link und Lizenz (All Rights Reserved) gesetzt; es fehlen Beschreibung, Kategorien `management`, `utility` und die Umgebung `client_side=unsupported`, `server_side=required` (wie WorldPreGenerator; Modrinth zeigt das als „server only, works in singleplayer“).
- **Icon:** `Dimensions-Stoppuhr.png` (1024×1024, liegt untracked im Repo-Wurzelordner) wird wie bei den anderen Mods zu `images/PlayerTimeManager.png` und `src/main/resources/player_time_manager.png` (`logoFile=player_time_manager.png` in `gradle.properties`).
- **Beschreibung, gleicher Inhalt an drei Stellen** (wie bei den anderen Mods, Aufbau nach DimensionAccessManager `4.X.X.md`): Wiki-Versionsseite `1.X.X.md` = Modrinth-Beschreibung = CurseForge-Beschreibung. Inhalt: Kopf „Version 1.X.X“ mit Wiki-Link, Funktionsübersicht, „NeoForge, nur auf dem Server nötig, läuft auch im Singleplayer“, BisectHosting-Standardblock **mit verlinktem Banner** (Affiliate-Link `https://bisecthosting.com/agent`, Pflicht auf Modrinth und CurseForge, im Wiki ebenso), Support-Bild, Zeitlimits (Welt/Dimension), Refill mit Cron-Abschnitt (Beispiele `0 6 * * *`, `0 14 * * 1-5`, `0 8 * * 6,0`, `0 6 * * 1`, `0 */6 * * *`, Link crontab.guru), Zielpunkt, Anzeige/Warnungen, Ausnahmen inkl. Singleplayer-Hinweis zu `ops_exempt`, Config-Referenz, Befehle, Fußzeile wie bei den anderen Mods (Logo-Dank an Muse31, Modpack-Erlaubnis, weitere Mods, Mod-Loader-Banner `mod_loader_banner.png` - der Forge-Teil trifft nicht zu, der User tauscht das Banner ggf. später aus; entschieden 2026-10-18). `credits=Thanks for the logo goes to Muse31` in `gradle.properties`.
- **GitHub-Wiki** wie bei den anderen Mods: `Home.md` (Begrüßung, Links CurseForge/Modrinth, Versions-Liste mit `[[1.X.X]] (Current)`, Support, Changelogs), `_Sidebar.md` (Home, Versions, Changelogs, Download-Link), `1.X.X.md`, `Changelog-1.0.0.md` (Inhalt aus `CHANGELOG.md`, Markdown-Liste). Das Wiki-Git-Repo existiert erst, wenn die erste Seite im Browser angelegt wurde.
- **Reihenfolge:** Beschreibungen werden nach den Ingame-Tests (Config-Schlüssel und Befehle stehen dann fest) und vor dem ersten Release veröffentlicht: Wiki und Modrinth per Git/API, **CurseForge zuletzt im Chrome-Modus** (Editor nur im eingeloggten Browser, Save nur nach OK des Users). Nach dem ersten Upload reicht der User das Modrinth-Projekt zur Prüfung ein (Entwurf → Review).

## 12. Tests

- **Unit-Tests (JUnit 5):**
  - Cron: alle Syntaxformen, Sonntag `0`/`7`, Tag-/Wochentag-ODER-Regel, Monatsenden und Schaltjahr, Sommer-/Winterzeit (`Europe/Berlin`), nächster Zeitpunkt, Intervall mit Höchstzahl, Fehlermeldungen für ungültige Ausdrücke; Abgleich ausgewählter Ausdrücke mit den Ergebnissen von crontab.guru.
  - Dauern parsen/formatieren (`2h`, `1h30m`, `45s`, ungültige Eingaben).
  - Refill-Berechnung: `SET`, `ADD`, `refill_max`, Nachholen mehrerer Zeitpunkte, Zeitplanwechsel, neue Spieler - mit fester Test-Uhr.
  - Config-Prüfung: Positions-Strings, Overworld-Eintrag, doppelte Einträge, Bossbar-Prozente.
- **Server-Tests per RCON** je Jar auf der niedrigsten und höchsten Version: Ablauf Dimensionszeit → Teleport (Standardziel, gesetztes Ziel, ungültiges Ziel → Ausweg), Portal-Sperre, Ablauf Weltzeit → Kick und Login-Sperre, Befehle (inkl. Offline-Spieler), Refill (Cron „jede Minute“), Neustart (Restzeiten bleiben), Config per Befehl landet in der Datei, Config-Fehler → Warnung statt Absturz.
- **Client-Tests mit dem User:** Bossbars (beide Modi, Farben), Actionbar, Warnungen mit Ton, Teleport mit Reittier (ab 1.21.2) bzw. ohne (1.21.1), Singleplayer inkl. `ops_exempt`, Vanilla-Client (englische Fallback-Texte).
- **Welt-Upgrade-Kette** 1.21.1 → 1.21.2 → … → 26.1 → 26.3: Restzeiten bleiben erhalten (Codec-Format, Migration bei 26.1).
- Allgemeine Testregeln: `Docs/testing/README.md`.

## 13. Folgeaufgaben

- **Skill „neuen Mod anlegen“** (`~/.claude/skills/`) nach dem Anlegen dieses Mods schreiben - aus den tatsächlich ausgeführten Schritten (Repo, Gradle-Grundgerüst, CI, Composite Build, Root-Skripte, Docs-Einträge, CurseForge/Modrinth-Projekt, Icon, Wiki-Erstanlage, Beschreibungen, Testpacks). **Nach dieser Spec bzw. nach dem Implementierungsplan dem User vorschlagen.**
- Antwort auf DimensionAccessManager GH#7 (Entwurf, der User postet), sobald der Mod veröffentlicht ist.
