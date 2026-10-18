# CLAUDE.md - Player Time Manager

Player Time Manager begrenzt, wie lange Spieler **insgesamt in der Welt** und **in einzelnen Dimensionen** spielen dürfen, füllt die Zeit nach Cron-Zeitplan automatisch auf und lässt Admins sie per `/playtime` anpassen. Mod-ID `player_time_manager`, Package `de.geheimagentnr1.player_time_manager`. **Serverseitig**: Clients brauchen den Mod nicht (keine Registrierungen außer Attachments, kein Netzwerk, `displayTest = IGNORE_SERVER_VERSION`); keine Dist-Beschränkung (`@Mod( MODID )`), im Singleplayer läuft er im integrierten Server.

> Diese Datei ist auf allen `develop_*`-Branches identisch; Quelle: `../Docs/mod-claude/PlayerTimeManager.md` (Änderungen dort, dann `Docs/tools/sync_mod_claude_md.sh`). Versionsstand: [`../Docs/VERSIONEN.md`](../Docs/VERSIONEN.md).

## Versionen

- Branches, Ranges und Tags: Zeile „PlayerTimeManager“ in [`../Docs/VERSIONEN.md`](../Docs/VERSIONEN.md).
- NeoForge: `develop_1.21.1` (Range `[1.21.1,1.21.2)`, Version 1.0.0). Ports 1.21.2 - 26.3 folgen (Folgeplan, Spec Abschnitt 11). Keine Forge-Branches.
- Spec und Plan: [`docs/superpowers/specs/2026-10-13-player-time-manager-design.md`](docs/superpowers/specs/2026-10-13-player-time-manager-design.md), [`docs/superpowers/plans/2026-10-14-player-time-manager-1.21.1.md`](docs/superpowers/plans/2026-10-14-player-time-manager-1.21.1.md).

## Abhängigkeiten

- Keine Mod-Abhängigkeiten, keine gebündelten Bibliotheken. Unit-Tests brauchen `testImplementation 'com.electronwill.night-config:core:3.8.3'` (MDG legt Minecraft/NeoForge nur auf den Main-Klassenpfad).

## Architektur

- `PlayerTimeManager` - Hauptklasse: registriert `ServerConfig` (Typ SERVER), `ModAttachmentTypes`, `ConfigReloadHandler` (Mod-Bus) und `GameEventHandler` (Game-Bus).
- `config/` - `ServerConfig` (ModConfigSpec, Listen `[[dimensions]]` als Tabellen), `ConfigTables` (NeoForge-frei: Tabellen-Prüfung und Tabellen mit fester Schlüsselreihenfolge), `raw/` (Werte wie in der Datei), `SettingsResolver` → `resolved/` (geprüfte Werte mit sicheren Ersatzwerten und Problem-Liste), `SettingsHolder` (aktueller Stand, neu bei jedem Config-Reload), `TargetPosition` (Positions-String parsen/formatieren/beschreiben).
- `cron/CronExpression` - 5-Feld-Cron wie crontab.guru (Tag/Wochentag ODER-verknüpft, wenn keins mit `*` beginnt).
- `time/` - reine Logik, mit fester Uhr testbar: `TimeAccounting` (Echtzeit-Differenz, lange Lücken zählen nicht), `RefillCalculator`/`RefillRule`/`RefillMode` (`SET`/`ADD` mit `refill_max`, Nachholen mit Höchstzahl), `ExemptionChecker` (OP, Listen, Kreativ/Zuschauer → `TimeState`), `DurationParser` (Dauern parsen, `format` für Text, `formatClock` für Live-Anzeigen, `formatDateTime` = `dd.MM.yyyy HH:mm`). `TimeService` verbindet alles mit dem Server (Zählung jede Sekunde, Ablauf, Sperren, Respawn, Login).
- `data/` - Restzeiten als Level-Attachment (`TimeData`, **Codec**, auf Overworld bzw. pro Dimension), Zugriff über `TimeStore`.
- `display/` - `DisplayManager` (Bossbars/Actionbar je `display_mode`), `BarColor`, `WarningTracker` (Warnungen mit Ton bei `warning_times`).
- `teleport/` - `TargetResolver` (Zielpunkt → `Destination`; leere Position: Overworld = Welt-Spawn, End = Obsidian-Plattform; sonst/ungültig/gesperrt → Welt-Spawn), `PlayerTeleporter`.
- `commands/PlaytimeCommand` (nur Vanilla-Argumenttypen), `ConfigEdits` (Befehle schreiben dieselben Werte in die Datei).
- `text/Messages` - alle Texte als Übersetzungsschlüssel **mit englischem Fallback** (`Component.translatableWithFallback`), Sprachdateien `en_us`/`de_de` nur für Clients mit dem Mod.

## Funktionen, Befehle, Konfiguration

- Welt-Limit (zählt in allen Dimensionen) und Dimensions-Limits laufen parallel. Welt bei 0: Kick + Login-Sperre. Dimension bei 0: Teleport zum Zielpunkt, Betreten gesperrt (`EntityTravelToDimensionEvent`), Respawn dorthin vorab umgeleitet (`PlayerRespawnPositionEvent`). Die Overworld kann keinen Dimensions-Eintrag haben.
- Pause: OP mit `ops_exempt` (Standard true), `exempt_players`, nicht in nicht-leerer `limited_players`, Kreativ/Zuschauer (abschaltbar).
- `/playtime` (alle: eigene Restzeit), Admin (`command_permission_level`, Standard 3): `time get|set|add|remove|refill|overview`, `config general|world|dimension <dim> ...` (inkl. `add|remove`, `target here|<dim> <x y z> [yaw pitch]|default`), `players limited|exempt list|add|remove`.
- Config-Datei: **`config/player_time_manager-server.toml`** im Server-Ordner (NeoForge 21.1 legt neue Server-Configs dort an; eine vorhandene Datei in `world/serverconfig/` hätte Vorrang). Jeder Wert mit englischem Kommentar; nur in der Datei: `command_permission_level`, `time_zone`, Pausen, `display_mode`, Bossbar-Prozente, `warning_times`, `warning_sound`.
- Formate: Bossbar/Actionbar `1:30:05` / `2d 03:00:00`, Text `1h 30m 5s`, Datum `19.10.2026 06:00` (in `time_zone`).

## Fallstricke

- **Listen-Validator nur „ist eine Tabelle“**: ein strengerer Validator lässt NeoForge Einträge mit Tippfehlern beim Laden **löschen**. Inhalte prüft der `SettingsResolver` (WARN im Log, OP-Hinweis beim Login, sicherer Ersatzwert, Eintrag bleibt in der Datei).
- **Tabellen mit `LinkedHashMap`** (`ConfigTables.toTable`) bauen, sonst stehen die Schlüssel in `[[dimensions]]` in Hash-Reihenfolge.
- **Config speichern**: nach `set(..)` immer `save()` (siehe Workspace-Bug „Config save“).
- **Keine eigenen Argumenttypen** (Server-only: Vanilla-Clients kennen sie nicht).
- **Attachments per Codec**, kein `INBTSerializable` (wird ab NeoForge 21.6 nicht mehr gelesen).
- **Teleport 1.21.1**: Spieler steigt vom Reittier ab und wird allein teleportiert; ab 1.21.2 Fahrzeug mit `TeleportTransition` (Port-Plan). Bricht ein anderer Mod den Teleport ab (z. B. DimensionAccessManager), Ausweichen zum Welt-Spawn, Log-WARN und Meldung höchstens alle 5 s.
- `PlayerRespawnPositionEvent` nutzt in 1.21.1 `DimensionTransition`, ab 1.21.2 `TeleportTransition` - beim Port anpassen. Minecraft zieht die Seelenanker-Ladung **vor** dem Event ab; beim Umleiten gibt `TimeService.refundAnchorCharge` sie zurück (`AnchorRefund`) - beim Port prüfen, ob das noch so ist.
- Bekannte kleine Punkte aus dem Final Review (zurückgestellt): Refill-Nachholen bei Cron unter einer Stunde nach langer Pause langsam, Pausen bis 10 s zählen, `enabled = 1` still `false`, `time get` legt Einträge an, `players add` kann Doppeleinträge erzeugen, Überlauf bei `Long.MAX_VALUE`.

## Testen

- Unit-Tests (JUnit 5, 63): Cron, Dauern/Formate, Refill, Zählung, Ausnahmen, Config-Auflösung, Tabellen, Zielpositionen, Bossbar-Farben, Warnungen, Anker-Rückgabe. CI: Build + JUnit.
- Ingame-Testplan: [`../Docs/testing/TESTPLAN_2026-10-player-time-manager.md`](../Docs/testing/TESTPLAN_2026-10-player-time-manager.md) (Server per RCON, Client mit dem User). Server-Teile (Config, Befehle, Refill, Offline-Spieler, Persistenz) per RCON; Bossbar, Meldungen, Ton, Kick, Respawn, Singleplayer und Vanilla-Client brauchen den User.
- Im TestPack 1.21.1: AutoRestart startet den leeren Server nach 2 min neu, DimensionAccessManager sperrt das End (Blacklist) - siehe „Stolperfallen“ in [`../Docs/testing/README.md`](../Docs/testing/README.md).

## Links

- Wiki: https://github.com/GeheimagentNr1/PlayerTimeManager/wiki
- CurseForge: https://www.curseforge.com/minecraft/mc-mods/player-time-manager (Projekt-ID 1752337)
- Modrinth: https://modrinth.com/mod/zb9AY963
- [`../Docs/migrations/BREAKING_CHANGES.md`](../Docs/migrations/BREAKING_CHANGES.md), [`../Docs/releasing/release-process.md`](../Docs/releasing/release-process.md)
