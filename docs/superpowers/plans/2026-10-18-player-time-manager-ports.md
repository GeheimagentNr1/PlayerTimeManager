# Player Time Manager - Ports 1.21.2 bis 26.3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Player Time Manager 1.0.0 läuft mit möglichst wenigen Jars auf allen Minecraft-Versionen 1.21.2 - 26.3 mit demselben Verhalten wie `develop_1.21.1`, getestet und bereit für einen gebündelten Release.

**Architecture:** Port Branch für Branch nach vorn (Skill `migrate-mod-version`): jeder neue Branch zweigt vom vorherigen ab, wird gegen die niedrigste Version seines Bereichs kompiliert und per Probe-Kompilieren gegen die folgenden NeoForge-Versionen begrenzt. Reine Logik (`time/`, `cron/`, `config/` ohne NeoForge) bleibt unverändert und durch die 67 Unit-Tests abgesichert; geändert wird nur die Minecraft-Anbindung (`teleport/`, `handlers/`, `commands/`, `data/`, `time/TimeService`). Implementiert wird auf allen Branches zuerst (lokale Commits), getestet wird danach gebündelt.

**Tech Stack:** NeoForge 21.2 - 26.3, ModDevGradle, Java 21 (1.21.x) bzw. 25 (26.x), JUnit 5, `Docs/testing/tools/` (pack.sh, rcon.py, bincheck.ps1).

**Spec:** `PlayerTimeManager/docs/superpowers/specs/2026-10-13-player-time-manager-design.md` (Abschnitte 6, 7, 11, 12). Vorgängerplan: `PlayerTimeManager/docs/superpowers/plans/2026-10-14-player-time-manager-1.21.1.md`.

## Global Constraints

- **Kein Release, bevor alle Ports fertig und getestet sind** (User 2026-10-18). Release danach gebündelt nur auf Anweisung (Skill `release-mod`, CurseForge vor Modrinth).
- **Pushen nur nach ausdrücklicher Freigabe des Users**, auch einzelne Branches.
- Ein Branch pro Jar (`develop_<niedrigste mc>`), Range `[niedrigste,erste brechende)`; kompiliert gegen die **niedrigste** Version, getestet niedrigste und höchste.
- **Lieber ein Jar mehr als ein Cross-fix, der das Verhalten ändert.** Cross-fixes ohne Verhaltensänderung sind erlaubt.
- Bytecode-Prüfung (`Docs/testing/tools/bincheck.ps1`) für jedes Jar mit mehr als einer Version, vor jedem Deploy.
- Version bleibt `mod_version=1.0.0` auf allen Branches, `CHANGELOG.md` identisch zu `develop_1.21.1`.
- Verhalten identisch zu 1.21.1, außer den Spec-Unterschieden: **ab 1.21.2 wird das ganze Fahrzeug mit allen Passagieren teleportiert** (`Entity.teleport(TeleportTransition)` auf das Root-Fahrzeug); sitzen weitere Spieler darin, steigt der betroffene Spieler ab und wird allein teleportiert. Angeleinte Tiere bleiben zurück.
- **Ab 26.1** übernimmt der Jar die Restzeiten einmalig aus der alten Attachment-Datei (Spec Abschnitt 5).
- Code-Stil wie im Workspace: `method( a, b )`, `@NotNull` aus `org.jetbrains.annotations`, keine Wildcard-Imports, Tabs.
- Commits mit `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Java/JDK: `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot` (1.21.x), `jdk-25.0.4.7-hotspot` (26.x); Python nur `python3`.
- Bekannte Brüche immer zuerst in `Docs/migrations/BREAKING_CHANGES.md` nachschlagen; Referenz-Diffs in DimensionAccessManager (gleiche Branch-Schnitte, Befehle mit Spieler-Argument, Level-Attachments, Rechtestufe aus der Config) und MagicalTorches (`develop_26.1`, `handlers/LegacyAttachmentMigrationHandler.java`).

## Review Focus

1. **Fahrzeug-Teleport (ab 1.21.2):** Spieler auf Pferd/Boot mit Tier → ganzes Fahrzeug landet am Zielpunkt, Spieler sitzt weiter; zweiter Spieler im Boot → nur der betroffene Spieler wird abgesetzt und teleportiert, der andere bleibt (Test in Task 1 per `TeleportPlan`, ingame Task 8).
2. **Welt-Upgrade-Kette:** Restzeiten aus einer 1.21.1-Welt überstehen jeden Jar-Wechsel bis 26.3, auch den Attachment-Dateiwechsel in 26.1, ohne doppelte Übernahme beim zweiten Start (Task 9).
3. **Welt-Spawn ab 1.21.9:** Minecraft kann den Welt-Spawn ab 1.21.9 in eine andere Dimension legen (`/setworldspawn` in einer Dimension). Ziel „Welt-Spawn“ darf nie in eine Dimension führen, für die der Spieler keine Restzeit hat (sonst Endlos-Teleport) - Ausweg Overworld (Task 4).
4. **Rechtestufe ab 1.21.11:** `command_permission_level` aus der Config muss weiter greifen (0 = alle, 3 = Admins) - ein Fehler hier öffnet Admin-Befehle für alle (Task 5).
5. **Anker-Ladung:** Die Rückgabe der Seelenanker-Ladung beim umgeleiteten Respawn bleibt korrekt, wenn Minecraft die Respawn-Logik ändert (1.21.2 `TeleportTransition`, 1.21.5 `RespawnConfig`) - nie eine Ladung zurückgeben, die nicht abgezogen wurde (Tasks 1 und 2, ingame Task 8).

---

## Branch-Schnitte (vorläufig, Task für Task bestätigt)

Erwartung nach DimensionAccessManager und den genutzten APIs; bestätigt bzw. korrigiert durch das Probe-Kompilieren in jedem Task:

| Branch | Range | NeoForge (Kompilieren) | Getaggte Versionen | Grund für den Schnitt |
|---|---|---|---|---|
| `develop_1.21.2` | `[1.21.2,1.21.5)` | `21.2.1-beta` | 1.21.2, 1.21.3, 1.21.4 | `TeleportTransition`, `teleportTo` ohne 6-Parameter-Variante |
| `develop_1.21.5` | `[1.21.5,1.21.6)` (ggf. bis 1.21.9) | `21.5.98` | 1.21.5 | vermutlich `RespawnConfig` (`ServerPlayer.getRespawnPosition()` …) |
| `develop_1.21.6` | `[1.21.6,1.21.9)` | `21.6.20-beta` | 1.21.6 - 1.21.8 | nur falls Attachment-Codec oder Events brechen - sonst entfällt der Branch |
| `develop_1.21.9` | `[1.21.9,1.21.11)` | `21.9.16-beta` | 1.21.9, 1.21.10 | `GameProfile`-Record, `NameAndId`, Welt-Spawn (`RespawnData`) |
| `develop_1.21.11` | `[1.21.11,1.21.12)` | `21.11.45` | 1.21.11 | `Identifier`, Rechtesystem |
| `develop_26.1` | `[26.1,27)` | `26.1.0.19-beta` | 26.1, 26.1.1, 26.1.2, 26.2, 26.3 | Java 25, Attachment-Datei-Migration |

Es gibt Testpacks für 1.21.2, 1.21.4, 1.21.5, 1.21.6, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.2, 26.3 (`C:\MinecraftModding\Testpacks\TestPack <mc>`).

**Vorgehen pro Port-Task (gilt für Tasks 1 - 6, dort nicht wiederholt):**
1. `git checkout -b develop_<mc> <vorheriger develop-Branch>` im Mod-Repo.
2. `gradle.properties`: `minecraft_version`, `minecraft_version_range`, `minecraft_versions`, `neoforge_version`, `neoforge_version_range` wie in der Tabelle (bei DimensionAccessManager auf demselben Branch gegenprüfen: `git -C ../DimensionAccessManager show develop_<mc>:gradle.properties`).
3. `./gradlew build` (JDK passend), Fehler beheben wie im Task beschrieben, Quellen der Zielversion unter `build/moddev/artifacts/neoforge-<nf>-sources.jar` (26.x: `minecraft-patched-<nf>-sources.jar`) lesen statt raten.
4. **Probe-Kompilieren nach vorn:** `neoforge_version` testweise auf die letzte NeoForge-Version jeder folgenden MC-Version im Bereich setzen und `./gradlew compileJava` laufen lassen; die erste Version, die bricht, ist das Ende der Range. Danach `neoforge_version` zurück auf die niedrigste.
5. `bincheck.ps1` gegen niedrigste und höchste Version der Range: keine unterschiedlichen externen Referenzen.
6. Unit-Tests grün (`./gradlew build`, 67 + neue Tests), Commit.
7. Neue verifizierte Brüche als Zeile in `Docs/migrations/BREAKING_CHANGES.md`, Details in `Docs/migrations/<von>-to-<nach>.md`.

---

### Task 1: develop_1.21.2 - TeleportTransition und Fahrzeug-Teleport

**Files:**
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/teleport/TeleportPlan.java`
- Create: `src/test/java/de/geheimagentnr1/player_time_manager/teleport/TeleportPlanTest.java`
- Modify: `teleport/PlayerTeleporter.java`, `teleport/TargetResolver.java` (falls `EndPlatformFeature`/`adjustSpawnLocation` brechen), `time/TimeService.java` (`redirectRespawn`, `refundAnchorCharge`), `handlers/GameEventHandler.java` (`PlayerRespawnPositionEvent`), `gradle.properties`

**Interfaces:**
- Produces: `TeleportPlan.Mode decide( boolean riding, int otherPlayerPassengers )` mit `enum Mode { PLAYER_ONLY, WHOLE_VEHICLE }`; `TimeService.redirectRespawn( ServerPlayer, TeleportTransition, boolean fromEndFight, long now ) : Optional<TeleportTransition>`; `PlayerTeleporter.teleport( ServerPlayer, Destination ) : boolean` (Rückgabe unverändert: Spieler ist danach im Ziel-Level).

- [ ] **Step 1: Failing test `TeleportPlanTest`**

```java
@Test
void decidesWhoIsTeleported() {
	assertEquals( TeleportPlan.Mode.PLAYER_ONLY, TeleportPlan.decide( false, 0 ) );
	assertEquals( TeleportPlan.Mode.WHOLE_VEHICLE, TeleportPlan.decide( true, 0 ) );
	// Other players in the same vehicle stay where they are
	assertEquals( TeleportPlan.Mode.PLAYER_ONLY, TeleportPlan.decide( true, 1 ) );
}
```
Run: `./gradlew test --tests "*TeleportPlanTest"` - Expected: Kompilierfehler (`TeleportPlan` fehlt).

- [ ] **Step 2: `TeleportPlan` (reines Java) umsetzen**, Test grün.

- [ ] **Step 3: Minecraft-Anbindung portieren** (gradle.properties wie Tabelle, dann Build):
  - `PlayerTeleporter.teleport`: Schlafen beenden wie bisher; `otherPlayerPassengers` = Spieler unter `player.getRootVehicle().getIndirectPassengers()` außer dem Spieler selbst. `WHOLE_VEHICLE` → `player.getRootVehicle().teleport( new TeleportTransition( level, position, Vec3.ZERO, yaw, pitch, TeleportTransition.DO_NOTHING ) )` (wie Vanilla-Portal, Passagiere bleiben sitzen); `PLAYER_ONLY` → `player.stopRiding()` und `player.teleportTo( level, x, y, z, Set.of(), yaw, pitch, true )` (wie Vanilla-`/tp`, siehe DimensionTeleport `develop_1.21.2`).
  - `DimensionTransition` → `TeleportTransition` in `TimeService.redirectRespawn` und `GameEventHandler.handleRespawnPosition` (`event.getTeleportTransition()` / `setTeleportTransition(..)` - Namen in der 21.2-Quelle von `PlayerRespawnPositionEvent` prüfen).
  - `refundAnchorCharge`: In 1.21.2 die Stelle in `PlayerList.respawn`/`ServerPlayer.findRespawnPositionAndUseSpawnBlock` lesen. Zieht Minecraft die Ladung weiterhin **vor** dem Event ab und nur bei `!forced && !keepInventory`, bleibt `AnchorRefund` unverändert; sonst `AnchorRefund.isNeeded` an die neue Regel anpassen (Test in `AnchorRefundTest` zuerst ändern, rot sehen).
- [ ] **Step 4: Probe-Kompilieren** gegen `21.3.100`, `21.4.161`, `21.5.101`. Expected: 21.3 und 21.4 grün, 21.5 rot (sonst Range und Tabelle anpassen, Abweichung ins Ledger).
- [ ] **Step 5: bincheck** `-Versions @(@('1.21.2','21.2.1-beta'),@('1.21.4','21.4.161'))` - Expected: keine Unterschiede.
- [ ] **Step 6: Commit** `Port to Minecraft 1.21.2 - 1.21.4 (teleport whole vehicle)`.

### Task 2: develop_1.21.5 - RespawnConfig

**Files:** Modify: `time/TimeService.java` (`refundAnchorCharge`), ggf. `handlers/GameEventHandler.java`, `gradle.properties`

**Interfaces:**
- Consumes: alles aus Task 1 unverändert.
- Produces: keine neuen Signaturen.

- [ ] **Step 1: Build gegen `21.5.98`.** Erwartete Brüche: `ServerPlayer.getRespawnPosition()/getRespawnDimension()/isRespawnForced()` → `getRespawnConfig()` (Felder Dimension, Position, Winkel, forced). `refundAnchorCharge` darauf umstellen; `null`-Config = kein Spawnpunkt → keine Rückgabe.
- [ ] **Step 2: Anker-Logik in den 1.21.5-Quellen nachlesen** (wie Task 1 Step 3) und `AnchorRefund` nur bei geänderter Regel per Test anpassen.
- [ ] **Step 3: Probe-Kompilieren** gegen `21.6.20-beta`, `21.8.57`, `21.9.16-beta`. Ende der Range = erste rote Version. Bricht 1.21.6 nicht, entfällt `develop_1.21.6` (Task 3 wird übersprungen, Range dieses Branches wird `[1.21.5,1.21.9)`).
- [ ] **Step 4: bincheck** niedrigste/höchste, Unit-Tests, Commit `Port to Minecraft 1.21.5`.

### Task 3: develop_1.21.6 (nur falls Task 2 bei 1.21.6 bricht)

**Files:** Modify: `data/ModAttachmentTypes.java`, `data/TimeData.java` (falls `AttachmentType.Builder.serialize(Codec)` in 21.6 auf `MapCodec` umgestellt ist), `gradle.properties`

**Interfaces:**
- Produces: `TimeData.CODEC` bleibt `Codec<TimeData>`; falls 21.6 eine `MapCodec` verlangt: `TimeData.MAP_CODEC = TimeData.CODEC.fieldOf( "<feldname>" )` - **gespeichertes Format darf sich dabei nicht ändern** (Welt aus 1.21.5 muss laden).

- [ ] **Step 1: Build gegen `21.6.20-beta`, Fehler beheben.**
- [ ] **Step 2: Formatprüfung:** Welt aus dem 1.21.5-Pack mit gesetzten Restzeiten (`playtime time set <spieler> world 7m` per RCON) in das 1.21.6-Pack kopieren, starten, `playtime time get` - Expected: 7m (abzüglich Spielzeit). Bei Formatwechsel Migration wie DimensionAccessManager `develop_1.21.6` (Attachment in `LevelEvent.Load` aus dem alten Format lesen), Ledger-Eintrag.
- [ ] **Step 3: Probe-Kompilieren** gegen `21.8.57`, `21.9.16-beta`; bincheck 1.21.6/1.21.8; Commit `Port to Minecraft 1.21.6 - 1.21.8`.

### Task 4: develop_1.21.9 - GameProfile, NameAndId, Welt-Spawn

**Files:**
- Modify: `commands/PlaytimeCommand.java` (Spieler-Argument), `time/TimeService.java` (`fillMissingUuids`, OP-Prüfung), `time/PlayerFacts`-Erzeugung, `teleport/TargetResolver.java` (`worldSpawn`), `gradle.properties`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/teleport/SpawnChoice.java`, `src/test/java/de/geheimagentnr1/player_time_manager/teleport/SpawnChoiceTest.java`

**Interfaces:**
- Produces: `SpawnChoice.useWorldSpawnDimension( String worldSpawnDimension, Predicate<String> blockedDimension ) : boolean`; `TargetResolver.worldSpawn( MinecraftServer, ServerPlayer, Predicate<String> blockedDimension ) : Destination` (zusätzlicher Parameter; alle Aufrufer übergeben `target -> isBlocked( player, target, now )`).

- [ ] **Step 1: Failing test `SpawnChoiceTest`**

```java
@Test
void worldSpawnInABlockedDimensionFallsBackToTheOverworld() {
	assertTrue( SpawnChoice.useWorldSpawnDimension( "minecraft:overworld", dimension -> false ) );
	assertTrue( SpawnChoice.useWorldSpawnDimension( "minecraft:the_nether", dimension -> false ) );
	assertFalse( SpawnChoice.useWorldSpawnDimension( "minecraft:the_nether", dimension -> dimension.equals( "minecraft:the_nether" ) ) );
}
```
Run - Expected: Kompilierfehler.

- [ ] **Step 2: `SpawnChoice` umsetzen** (Overworld immer erlaubt, sonst `!blockedDimension.test( dimension )`), Test grün.
- [ ] **Step 3: Build gegen `21.9.16-beta`**, Brüche beheben:
  - `GameProfile` ist ein Record (`id()`, `name()`), `GameProfileArgument.getGameProfiles(..)` liefert `NameAndId` (`id()`, `name()`) - wie DimensionAccessManager `develop_1.21.9`; OP-Prüfung mit `server.getPlayerList().isOp( new NameAndId( player.getGameProfile() ) )`.
  - Welt-Spawn: `getSharedSpawnPos()`/`getSharedSpawnAngle()` → die Respawn-Daten des Servers ab 1.21.9 (Dimension, Position, Winkel; Namen aus der Quelle). `worldSpawn(..)`: Lässt `SpawnChoice` die Dimension des Welt-Spawns zu, dort `adjustSpawnLocation( level, pos )`. Sonst Overworld: x/z aus den Respawn-Daten, y aus `overworld.getHeight( Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z )`, danach `adjustSpawnLocation( overworld, pos )`. Ledger-Eintrag mit dem tatsächlichen API-Namen.
- [ ] **Step 4: Probe-Kompilieren** gegen `21.10.64`, `21.11.45`; bincheck 1.21.9/1.21.10; Commit `Port to Minecraft 1.21.9 - 1.21.10`.

### Task 5: develop_1.21.11 - Identifier und Rechtesystem

**Files:**
- Modify: alle Dateien mit `ResourceLocation`/`ResourceKey.location()`, `commands/PlaytimeCommand.java` (`isAdmin`), `gradle.properties`

**Interfaces:**
- Produces: `PlaytimeCommand.isAdmin( CommandSourceStack ) : boolean` prüft die Stufe aus `command_permission_level` über das neue Rechtesystem.

- [ ] **Step 1: Build gegen `21.11.45`**, Brüche beheben: `ResourceLocation` → `Identifier`, `location()` → `identifier()`; `source.hasPermission( level )` → wie DimensionAccessManager `develop_1.21.11`: `PermissionLevel.byId( level )`, `Commands.hasPermission( new PermissionCheck.Require( new Permission.HasCommandLevel( permissionLevel ) ) ).test( source )` bzw. `Commands.LEVEL_ALL` für 0.
- [ ] **Step 2: Probe-Kompilieren** gegen `26.1.0.19-beta` (erwartet rot wegen Java 25/Gradle); bincheck entfällt (ein Version); Commit `Port to Minecraft 1.21.11`.

### Task 6: develop_26.1 - Java 25 und Attachment-Datei-Migration

**Files:**
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/data/LegacyAttachmentMigration.java` (Event-Handler, Vorbild MagicalTorches `develop_26.1` `handlers/LegacyAttachmentMigrationHandler.java`)
- Modify: `gradle/wrapper/gradle-wrapper.properties`, `build.gradle`, `settings.gradle`, `gradle.properties` (Werte wie AutoRestart/DimensionAccessManager `develop_26.1`: Gradle 9.2.1, moddev 2.0.147, foojay 1.0.0, Lombok 1.18.48, Toolchain 25, `file( local_repository_path ).toURI()`), `PlayerTimeManager.java` (Handler registrieren)

**Interfaces:**
- Consumes: `ModAttachmentTypes` (Attachment-Typ der Restzeiten), `TimeData.CODEC`.
- Produces: `LegacyAttachmentMigration.handleLevelLoad( LevelEvent.Load )`: liest für jede geladene Dimension die alte Datei (`<welt>/data`, `DIM-1/data`, `DIM1/data`, sonst `dimensions/<ns>/<path>/data`-Vorgänger wie bei MagicalTorches) `neoforge_data_attachments.dat`, nimmt aus `data` den Schlüssel `player_time_manager:<attachment-name>` und setzt ihn **nur, wenn das Level das Attachment noch nicht hat**.

- [ ] **Step 1: Build-Umstellung auf Java 25**, `./gradlew build` grün (Unit-Tests laufen unverändert).
- [ ] **Step 2: Übrige 26.x-Brüche** aus dem Build beheben (Kandidaten: `Player.displayClientMessage(c, true)` für die Actionbar → `sendOverlayMessage`; `EventHooks`-Signaturen) - nur was der Compiler meldet.
- [ ] **Step 3: Migration umsetzen**; Schlüsselname aus `ModAttachmentTypes` ableiten, Dekodierung per `TimeData.CODEC.parse( NbtOps.INSTANCE, tag )`, Fehler → WARN, kein Absturz.
- [ ] **Step 4: Probe-Kompilieren** gegen `26.2.0.93`, `26.3.0.92`. Bricht eine Version, zusätzlicher Branch (`develop_26.2`/`develop_26.3`) nach demselben Muster.
- [ ] **Step 5: bincheck** 26.1/26.3, Commit `Port to Minecraft 26.1 - 26.3 and migrate old attachment files`.

### Task 7: Server-Smoke aller Jars (RCON)

**Files:**
- Create: `C:\MinecraftModding\ActiveMods\Docs\testing\tools\ptm_smoke.sh` (Aufruf `ptm_smoke.sh <mc>`; nutzt `pack.sh` und `rcon.py`)
- Modify: `Docs/testing/TESTPLAN_2026-10-player-time-manager.md` (Abschnitt „Ports“)

**Interfaces:**
- Produces: `ptm_smoke.sh <mc>` → Exit 0 und Zeile `PTM SMOKE <mc> OK` oder Exit 1 mit dem ersten fehlgeschlagenen Schritt.

- [ ] **Step 1: Skript schreiben.** Ablauf (Server gestoppt → Jar aus dem passenden Branch-Build ins Pack, Config-Datei löschen → Start):
  1. Log enthält `Player Time Manager settings loaded (0 problem(s))`, kein `ERROR` mit `player_time_manager`.
  2. `playtime config world enabled true`, `playtime config world limit 3m`, `playtime config dimension minecraft:the_nether enabled true` → Datei `config/player_time_manager-server.toml` enthält `limit = "3m"`; Stop/Start; `playtime config world limit` → `3m`.
  3. `playtime config world limit 1x` → `Invalid value`.
  4. Stop, im Nether-Eintrag `enabled = 1` setzen, Start → Log `enabled = 1: expected true or false`.
  5. `playtime time overview` antwortet ohne Fehler.
  6. Am Ende Config-Datei löschen (Standard wiederherstellen).
- [ ] **Step 2: Lauf auf allen Packs** (niedrigste und höchste Version jeder Range: 1.21.2, 1.21.4, 1.21.5, 1.21.6, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.3). Expected: überall `PTM SMOKE <mc> OK`. Fehler → Fix-Commit auf dem betroffenen Branch (mit Test, wo möglich), Lauf wiederholen.
- [ ] **Step 3: Rechtestufe (1.21.11, 26.1, 26.3):** `command_permission_level = 0` in der Datei → `playtime config world limit` per RCON klappt weiterhin; Nicht-OP-Probe gehört zu Task 8.

### Task 8: Client-Testrunde mit dem User (gebündelt)

**Files:** Modify: `Docs/testing/TESTPLAN_2026-10-player-time-manager.md`

- [ ] **Step 1:** Pro Jar **eine** Version mit dem User (Client-Instanz `Testpack <mc>`, Jar in Server **und** Client): 1.21.4, 1.21.5, 1.21.8 (falls eigener Jar), 1.21.10, 1.21.11, 26.3. Pro Version, je ca. 5 Minuten:
  - Beitritt, Bossbar im Uhr-Format zählt.
  - Netherzeit 5 s → Teleport zum Welt-Spawn auf freier Stelle, Meldung mit Datum `dd.MM.yyyy HH:mm`.
  - Seelenanker im Nether, Netherzeit 0, zweimal sterben → Respawn am Welt-Spawn, Anker behält 4 Ladungen.
  - Nur 1.21.4 und 26.3: Spieler auf Pferd bzw. Boot mit Tier im Nether → ganzes Fahrzeug am Ziel, Spieler sitzt weiter.
  - Nur 1.21.11 und 26.3: Nicht-OP-Spieler kann `/playtime config …` nicht nutzen, `/playtime` schon.
  - Nur 1.21.10: `/setworldspawn` im Nether (falls in 1.21.9+ möglich) + Netherzeit 0 → Ausweg Overworld statt Endlos-Teleport.
- [ ] **Step 2:** Vanilla-Client einmal auf 26.3 (Jar nur im Server): Beitritt, englische Texte.

### Task 9: Welt-Upgrade-Kette

- [ ] **Step 1:** Im 1.21.1-Pack eine frische Test-Welt (eigener Ordnername, `level-name` vorübergehend umstellen) mit Welt- und Netherlimit, Restzeiten `world 7m`, `nether 2m` per RCON (Spieler muss einmal beigetreten sein), Server stoppen.
- [ ] **Step 2:** Welt-Ordner nacheinander in 1.21.2 → 1.21.5 → 1.21.6 → 1.21.9 → 1.21.11 → 26.1 → 26.3 kopieren, jeweils starten, `playtime time get <spieler>` (Expected: 7m und 2m unverändert, da offline), stoppen. In 26.1 zusätzlich: zweiter Start, Werte gleich (keine doppelte Übernahme), Log ohne WARN der Migration.
- [ ] **Step 3:** `level-name` zurückstellen, Test-Welt-Ordner löschen; Ergebnis in den Testplan.

### Task 10: Doku, Push nach Freigabe

**Files:** Modify: `Docs/VERSIONEN.md`, `MOD_KOMPATIBILITAET.md`, `Docs/mod-claude/PlayerTimeManager.md` (Abschnitt Versionen mit allen Branches und Code-Unterschieden), `Docs/migrations/BREAKING_CHANGES.md` (neue Funde)

- [ ] **Step 1:** Doku nachziehen, `bash Docs/tools/sync_mod_claude_md.sh --apply PlayerTimeManager` (verteilt die CLAUDE.md auf alle neuen `develop_*`-Branches).
- [ ] **Step 2:** User fragen: „Alle neuen `develop_*`-Branches nach GitHub pushen?“ Erst nach Ja: `git push -u origin develop_<mc>` je Branch, `bash ../Docs/ci/ci_status.sh --wait PlayerTimeManager` - Expected: alle Workflows grün.
- [ ] **Step 3:** Final Review des Port-Branches (fresh reviewer) über alle Branches gegenüber `develop_1.21.1`.

### Task 11: Gebündelter Release (nur auf Anweisung des Users)

- [ ] **Step 1:** Skill `release-mod` für alle Branches (`1.21.1-1.0.0` bis `26.1-1.0.0`), CurseForge vor Modrinth, Tags `<mc>-1.0.0`, Master-Merges, `ModdingMavenRepositories`.
- [ ] **Step 2:** Modrinth: `environment = server_only` an jeder Version (v3-API, wie WorldPreGenerator); User reicht das Modrinth-Projekt zur Prüfung ein.
- [ ] **Step 3:** Wiki: Changelog-Seite 1.0.0 gilt für alle Jars; `MOD_KOMPATIBILITAET.md` auf ✅.
- [ ] **Step 4:** Antwortentwurf für DimensionAccessManager GH#7 (Skill `triage-feedback`), Vorschlag Skill „neuen Mod anlegen“.
