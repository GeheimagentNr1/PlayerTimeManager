# Player Time Manager 1.21.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Den neuen NeoForge-Mod „Player Time Manager“ (Welt- und Dimensions-Zeitlimits mit Cron-Refill) vollständig auf Minecraft 1.21.1 (`develop_1.21.1`) bauen, unit- und ingame-getestet, mit Repo, CI, Docs-Einträgen und den Projektseiten (Wiki, Modrinth, CurseForge) als Voraussetzung für den ersten Release.

**Architecture:** Reine Java-Logik (Dauern, Cron-Parser, Refill-Berechnung, Config-Auflösung, Ausnahmen, Warnschwellen) ohne Minecraft-Abhängigkeit und vollständig per JUnit getestet; darum eine dünne NeoForge-Schicht: TOML-Config (`ModConfigSpec`, Dimensionsliste als TOML-Tabellen), Level-Attachments mit Codec für die Restzeiten, ein `TimeService`, der jede Sekunde zählt und jede Minute auffüllt, Event-Handler (Tick, Login, Respawn, Dimensionswechsel), Bossbar/Actionbar-Anzeige und ein Brigadier-Befehl `/playtime` nur mit Vanilla-Argumenttypen (kein Client-Mod nötig).

**Tech Stack:** Java 21, NeoForge 21.1.216 (Minecraft 1.21.1), ModDevGradle 2.0.x, Lombok 1.18.34, JUnit 5.10.2, Mojang DataFixerUpper-Codecs, NightConfig (über NeoForge).

**Spec:** `C:\MinecraftModding\ActiveMods\Docs\specs\2026-10-13-player-time-manager-design.md` (freigegeben 2026-10-14). **Folgeplan:** Ports 1.21.2 - 26.3 (Spec Abschnitt 11) als eigener Plan nach Abschluss dieses Plans.

## Global Constraints

- Mod-Name `Player Time Manager`, Mod-ID `player_time_manager`, Package `de.geheimagentnr1.player_time_manager`, Befehl `/playtime`, Projekt-Slug `player-time-manager`, Repo `GeheimagentNr1/PlayerTimeManager`, Branch `develop_1.21.1`, Mod-Version `1.0.0`.
- Minecraft `1.21.1`, `minecraft_version_range=[1.21.1,1.21.2)`, `minecraft_versions=["1.21.1"]`, NeoForge `21.1.216`, Java 21 (`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot`).
- Rein serverseitig, **keine Dist-Beschränkung** (läuft auch im Singleplayer), `displayTest=IGNORE_SERVER_VERSION`. **Keine eigenen Befehls-Argumenttypen und keine eigenen Netzwerkpakete** (sonst bräuchte der Client den Mod).
- Code-Stil: Tabs, Leerzeichen innerhalb von Klammern bei Aufrufen und Deklarationen (`method( arg )`), `@NotNull`/`@Nullable` aus `org.jetbrains.annotations`, keine Wildcard-Imports, Logging per `LogUtils.getLogger()`.
- Alle Texte übersetzbar mit englischem Fallback (`Component.translatableWithFallback`); `en_us.json` und `de_de.json`. Englischer Fallback-Text im Code = Text in `en_us.json`.
- Config-Datei `config/player_time_manager-server.toml` (NeoForge 21.1 legt neue Server-Configs dort an, Ingame-Test 2026-10-18), **jeder Wert mit englischem Kommentar**. Per Befehl gesetzte Werte immer mit `set(..)` **und** `save()`.
- Ungültige Config-Werte verhindern nie den Start: WARN im Log, Hinweis an OPs beim Login, sicherer Ersatzwert (Spec Abschnitt 10).
- Restzeiten als Level-Attachments mit **Codec**-Serialisierung (kein `INBTSerializable`).
- Cron: 5 Felder, Auswertung wie crontab.guru (Vixie-Cron-Regel für Tag/Wochentag), höchstens 10 000 nachgeholte Zeitpunkte.
- Repo existiert (User, 2026-10-18): `C:\MinecraftModding\ActiveMods\PlayerTimeManager`, Remote `origin` = `https://github.com/GeheimagentNr1/PlayerTimeManager.git`, Branch `master` mit README/LICENSE/`.gitignore`/`.github`. Projekt-IDs: `curseforge_project_id=1752337`, `modrinth_project_id=zb9AY963`. Icon: `Dimensions-Stoppuhr.png` im Repo-Wurzelordner (untracked).
- Python auf dieser Maschine nur als `python3`. Nichts pushen, nichts veröffentlichen (Wiki, Modrinth, CurseForge, Release) ohne ausdrückliche Freigabe des Users.
- Commits mit Trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

### Begründete Abweichungen von der Spec

- **Listen-Config (Spec 10):** gleiche TOML-Technik wie DiscordIntegration (`defineListAllowEmpty` mit `CommentedConfig`-Tabellen, Kommentar pro Schlüssel), aber **ohne** die kopierten `api/config`-Klassen: deren `convertToNightConfig()` schreibt beim Zurückschreiben die **Standardwerte** der Einträge, nicht die geänderten (DiscordIntegration schreibt Listen nie per Befehl). Eigene kleine Umwandlung in `DimensionEntries`/`PlayerEntries`.
- **Element-Validator der Listen** prüft nur „ist eine Tabelle“: Würde er Inhalte prüfen, entfernt NeoForge ungültige Einträge stillschweigend aus der Datei. Inhalte prüft `SettingsResolver` (Warnung + Ersatzwert).
- **Spielerlisten** werden als TOML-Tabellen-Arrays (`[[general.exempt_players]]` mit `name`/`uuid`) geschrieben statt als Inline-Tabellen - so schreibt NightConfig Listen von Tabellen; Inhalt wie in der Spec.
- **Leeren per Befehl:** `none` als Wert setzt `refill_cron`/`refill_max` auf `""` (Spec: leer = kein Refill / keine Obergrenze).
- **Pausierte Spieler** (Kreativ/Zuschauer) werden beim Betreten einer Dimension ohne Restzeit nicht gesperrt; gesperrt wird nur, wer gerade zählt (Spec 6 legt das nicht fest).

## Review Focus

1. **Singleplayer-Pause / Server-Hänger:** Nach einer Pause oder einem langen Lag darf nicht schlagartig viel Zeit abgezogen werden - Zählintervalle über 10 s werden verworfen (`TimeAccounting.elapsedToCount`, Test in Task 8).
2. **Tippfehler in einem `[[dimensions]]`-Eintrag** (z. B. `limit = "1x"`) darf den Eintrag nicht aus der Datei löschen - Validator akzeptiert jede Tabelle, `SettingsResolver` warnt (Tests in Task 5 und Task 6).
3. **Admin setzt Restzeit über `refill_max`, danach `ADD`-Refill** - die Restzeit darf nicht gekürzt werden (Test in Task 4).
4. **Teleport wird von einem anderen Mod abgebrochen** (z. B. DimensionAccessManager sperrt das Ziel) - Ausweg Overworld-Spawn, keine Endlosschleife und kein Nachrichten-Spam (Ingame-Test in Task 14, Schritt 14).
5. **Zeitumstellung (Europe/Berlin)** - nicht existierende Zeitpunkte übersprungen, doppelte einmal gezählt (Tests in Task 3).

## Dateistruktur

```
PlayerTimeManager/                                   (eigenes Git-Repo, Branch develop_1.21.1)
├── build.gradle, settings.gradle, custom.gradle, gradle.properties, gradlew(.bat), gradle/wrapper/  (aus DimensionAccessManager)
├── .gitattributes, .gitignore, LICENSE, README.md, CHANGELOG.md, CLAUDE.md
├── .github/ISSUE_TEMPLATE/*, .github/stale.yml (schon auf master), .github/workflows/build-and-test.yml
├── images/PlayerTimeManager.png                     Icon (aus Dimensions-Stoppuhr.png)
└── src/
    ├── main/java/de/geheimagentnr1/player_time_manager/
    │   ├── PlayerTimeManager.java                  Mod-Klasse, verdrahtet alles
    │   ├── cron/CronExpression.java                Cron-Parser + next/between (reines Java)
    │   ├── cron/CronParseException.java
    │   ├── time/DurationParser.java                "1h30m" <-> Millisekunden (reines Java)
    │   ├── time/RefillMode.java, RefillRule.java, TimeEntry.java, RefillCalculator.java   Refill-Logik (reines Java + Codec)
    │   ├── time/Scope.java                         Welt oder Dimension
    │   ├── time/TimeState.java, PlayerFacts.java, ExemptionChecker.java                   Ausnahmen
    │   ├── time/TimeAccounting.java                Zählintervall-Regel
    │   ├── time/TimeService.java                   Zählen, Auffüllen, Ablauf (Minecraft)
    │   ├── config/ServerConfig.java                TOML-Spezifikation, Lesen/Schreiben
    │   ├── config/DimensionEntries.java, PlayerEntries.java   TOML-Tabellen <-> Raw-Records
    │   ├── config/raw/*.java                       Rohwerte aus der TOML (Records)
    │   ├── config/resolved/*.java                  geprüfte, aufgelöste Einstellungen (Records)
    │   ├── config/SettingsResolver.java            Prüfung + Ersatzwerte (reines Java)
    │   ├── config/TargetPosition.java              "x y z [yaw pitch]"
    │   ├── config/SettingsHolder.java              aktuelle Einstellungen zur Laufzeit
    │   ├── data/TimeData.java, ModAttachmentTypes.java, TimeStore.java   Level-Attachments
    │   ├── display/DisplayManager.java, ScopeStatus.java, BarColor.java, WarningTracker.java   Bossbar/Actionbar/Warnungen
    │   ├── teleport/Destination.java, TargetResolver.java, PlayerTeleporter.java
    │   ├── text/Messages.java                      alle übersetzbaren Texte
    │   ├── handlers/GameEventHandler.java, ConfigReloadHandler.java
    │   └── commands/PlaytimeCommand.java, ConfigEdits.java
    ├── main/resources/META-INF/neoforge.mods.toml, pack.mcmeta, player_time_manager.png (Logo), assets/player_time_manager/lang/en_us.json, de_de.json
    └── test/java/de/geheimagentnr1/player_time_manager/...   (JUnit 5, nur reine Logik)
```

---

### Task 1: Projekt-Grundgerüst

**Files:**
- Modify: `C:\MinecraftModding\ActiveMods\PlayerTimeManager\README.md` (auf `master`, stammt aus ManyIdeas Doors)
- Create (Branch `develop_1.21.1` von `master`): Build-Dateien und `.github/workflows/` aus DimensionAccessManager `develop_1.21.1`, `images/PlayerTimeManager.png`, `src/main/resources/player_time_manager.png`
- Create: `gradle.properties`, `src/main/java/de/geheimagentnr1/player_time_manager/PlayerTimeManager.java`, `src/main/resources/assets/player_time_manager/lang/en_us.json`, `de_de.json`, `CHANGELOG.md`
- Modify: `C:\MinecraftModding\ActiveMods\settings.gradle` (Composite Build)

**Interfaces:**
- Produces: `PlayerTimeManager.MODID = "player_time_manager"`; Konstruktor `PlayerTimeManager( IEventBus modEventBus, ModContainer modContainer )`, in den spätere Tasks ihre Registrierungen eintragen.

- [ ] **Step 1: README auf `master` korrigieren**

Das vom User angelegte Repo enthält auf `master` README, LICENSE, `.gitignore` und `.github/` (Issue-Vorlagen, `stale.yml`). Die README ist eine Kopie von ManyIdeas Doors. Den Kopf ersetzen, Rest (Used libraries, Branches) unverändert lassen:

```markdown
# Player Time Manager

This is a Minecraft NeoForge mod.  
A mod description can be found on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/player-time-manager) and [Modrinth](https://modrinth.com/mod/player-time-manager).  
This is a combined work only using and not modifying used libraries.
```
Unter „### Minecraft Forge“ steht bereits der NeoForge-Text, die Überschrift in `### NeoForge` ändern.

```bash
cd /c/MinecraftModding/ActiveMods/PlayerTimeManager
git status --short          # nur "?? Dimensions-Stoppuhr.png" erwartet
git diff README.md          # nur Kopf + Überschrift
git add README.md
git commit -m "Fix README

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 2: Branch anlegen, Build-Dateien und Icon übernehmen**

```bash
cd /c/MinecraftModding/ActiveMods
git -C PlayerTimeManager switch -c develop_1.21.1
git -C DimensionAccessManager archive develop_1.21.1 build.gradle settings.gradle custom.gradle gradlew gradlew.bat gradle .gitattributes .github/workflows src/main/resources/META-INF/neoforge.mods.toml src/main/resources/pack.mcmeta | tar -x -C PlayerTimeManager
diff <(git -C DimensionAccessManager show develop_1.21.1:.gitignore) PlayerTimeManager/.gitignore
mkdir -p PlayerTimeManager/images
mv PlayerTimeManager/Dimensions-Stoppuhr.png PlayerTimeManager/images/PlayerTimeManager.png
cp PlayerTimeManager/images/PlayerTimeManager.png PlayerTimeManager/src/main/resources/player_time_manager.png
ls -a PlayerTimeManager PlayerTimeManager/.github/workflows PlayerTimeManager/images
```
Expected: `build.gradle`, `gradlew`, `gradle/wrapper/gradle-wrapper.jar`, `.github/workflows/build-and-test.yml`, `images/PlayerTimeManager.png` vorhanden; `.gitignore`, LICENSE und Issue-Vorlagen von `master` bleiben (weicht die `.gitignore` von der DAM-Fassung ab und fehlen dort `build/`, `run/` o. ä., die DAM-Zeilen ergänzen).

- [ ] **Step 3: `gradle.properties` schreiben** (komplett ersetzen bzw. neu anlegen)

```properties
# suppress inspection "LongLine" for whole file
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false

## Environment Properties

minecraft_version=1.21.1
minecraft_version_range=[1.21.1,1.21.2)
minecraft_versions=["1.21.1"]
neoforge_version=21.1.216
neoforge_version_range=[21.1,)
loader_version_range=[4,)
mapping_channel=official
mapping_version=1.21.1

## Mod Properties

mod_id=player_time_manager
mod_name=Player Time Manager
mod_license=All Rights Reserved
mod_version=1.0.0
mod_group_id=de.geheimagentnr1
mod_authors=GeheimagentNr1
mod_description=Limits how long players can play in the world and in single dimensions, with automatic refills by cron schedule.
issueTrackerURL=https://github.com/GeheimagentNr1/PlayerTimeManager/issues
displayURL=https://www.curseforge.com/minecraft/mc-mods/player-time-manager
# A file name (in the root of the mod JAR) containing a logo for display
logoFile=player_time_manager.png
credits=Thanks for the logo goes to Muse31
# Server-side mod: clients don't need it, works in singleplayer, too
displayTest=IGNORE_SERVER_VERSION

modDependencies=

## Dependency Properties

local_repository_path=../../ModdingMavenRepositories

lombok_version=1.18.34

## Deployment Properties

project_mod_name=PlayerTimeManager
curseforge_project_id=1752337
modrinth_project_id=zb9AY963
usableOnClientSide=true
usableOnServerSide=true
deployment_dependencies=[]
#For debugging: gradlew curseforge modrinth -PuploadDebug=true
uploadDebug=false
```

- [ ] **Step 4: Mod-Klasse anlegen**

`src/main/java/de/geheimagentnr1/player_time_manager/PlayerTimeManager.java`:
```java
package de.geheimagentnr1.player_time_manager;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.jetbrains.annotations.NotNull;


@Mod( PlayerTimeManager.MODID )
public class PlayerTimeManager {
	
	
	@NotNull
	public static final String MODID = "player_time_manager";
	
	public PlayerTimeManager( @NotNull IEventBus modEventBus, @NotNull ModContainer modContainer ) {
		
		// Registrations are added by the following tasks
	}
}
```

- [ ] **Step 5: Sprachdateien und Changelog anlegen**

`src/main/resources/assets/player_time_manager/lang/en_us.json` und `de_de.json` jeweils mit Inhalt `{}` (werden in Task 9 gefüllt).

`CHANGELOG.md` (Markdown-Liste wie bei den anderen Mods; wird beim Release die Wiki-Seite `Changelog-1.0.0.md`, Task 16):
```markdown
- Time limits for the whole world and for single dimensions
- Automatic refills by cron schedule (SET or ADD, optional maximum), globally or per world/dimension
- Players are teleported to a configurable target point when the dimension time runs out and can't enter the dimension again until the next refill
- Players are kicked when the world time runs out and can't join again until the next refill
- Display of the remaining time as action bar or boss bars, chat warnings with sound
- Operators, players in creative or spectator mode and listed players can be exempted
- /playtime command to show and change remaining times, limits, refills and target points
```

- [ ] **Step 6: Composite Build eintragen**

In `C:\MinecraftModding\ActiveMods\settings.gradle` nach `includeBuild "MumbleIntegration"` die Zeile `includeBuild "PlayerTimeManager"` einfügen (alphabetisch).

- [ ] **Step 7: Bauen**

```powershell
cd C:\MinecraftModding\ActiveMods\PlayerTimeManager
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"
.\gradlew.bat build
```
Expected: `BUILD SUCCESSFUL`, Datei `build/libs/PlayerTimeManager-1.21.1-1.0.0.jar` existiert und enthält `player_time_manager.png` im Wurzelverzeichnis (`unzip -l`). Danach `git status` - von `licenseReport` erzeugte Dateien unter `lib_licences/` werden mit committet (wie bei allen Mods).

- [ ] **Step 8: Commit**

```bash
git -C /c/MinecraftModding/ActiveMods/PlayerTimeManager add -A
git -C /c/MinecraftModding/ActiveMods/PlayerTimeManager commit -m "Initial project setup

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Dauern parsen und formatieren

**Files:**
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/time/DurationParser.java`
- Test: `src/test/java/de/geheimagentnr1/player_time_manager/time/DurationParserTest.java`

**Interfaces:**
- Produces: `static long DurationParser.parse( String text )` (Millisekunden, wirft `IllegalArgumentException` mit verständlicher Meldung); `static String DurationParser.format( long millis )` (z. B. `1h 23m 45s`, rundet Sekunden auf, negative Werte = `0s`).

- [ ] **Step 1: Failing Test schreiben**

```java
package de.geheimagentnr1.player_time_manager.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


class DurationParserTest {
	
	
	@Test
	void parsesSingleUnits() {
		
		assertEquals( 7_200_000L, DurationParser.parse( "2h" ) );
		assertEquals( 1_800_000L, DurationParser.parse( "30m" ) );
		assertEquals( 45_000L, DurationParser.parse( "45s" ) );
		assertEquals( 0L, DurationParser.parse( "0m" ) );
	}
	
	@Test
	void parsesCombinationsSpacesAndUpperCase() {
		
		assertEquals( 5_400_000L, DurationParser.parse( "1h30m" ) );
		assertEquals( 5_400_000L, DurationParser.parse( "1h 30m" ) );
		assertEquals( 5_025_000L, DurationParser.parse( "1H23M45S" ) );
	}
	
	@Test
	void rejectsInvalidInput() {
		
		for( String invalid : new String[] { "", " ", "2x", "h", "1.5h", "30m1h", "-5m", "99999999999999999999h" } ) {
			assertThrows( IllegalArgumentException.class, () -> DurationParser.parse( invalid ), invalid );
		}
	}
	
	@Test
	void formatsWithoutZeroUnits() {
		
		assertEquals( "0s", DurationParser.format( 0 ) );
		assertEquals( "0s", DurationParser.format( -5000 ) );
		assertEquals( "1s", DurationParser.format( 1 ) );
		assertEquals( "1h", DurationParser.format( 3_600_000 ) );
		assertEquals( "1h 5s", DurationParser.format( 3_605_000 ) );
		assertEquals( "1h 23m 45s", DurationParser.format( 5_025_000 ) );
		assertEquals( "30m", DurationParser.format( 1_800_000 ) );
	}
}
```

- [ ] **Step 2: Test ausführen, er muss fehlschlagen**

Run: `.\gradlew.bat test --tests "*DurationParserTest"`
Expected: FAIL beim Kompilieren (`cannot find symbol: class DurationParser`).

- [ ] **Step 3: Implementierung**

```java
package de.geheimagentnr1.player_time_manager.time;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class DurationParser {
	
	
	@NotNull
	private static final Pattern PATTERN = Pattern.compile( "^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$" );
	
	private DurationParser() {
	
	}
	
	public static long parse( @NotNull String text ) {
		
		String compact = text.replace( " ", "" ).toLowerCase( Locale.ROOT );
		Matcher matcher = PATTERN.matcher( compact );
		if( compact.isEmpty() || !matcher.matches() ) {
			throw invalid( text );
		}
		try {
			long hours = group( matcher, 1 );
			long minutes = group( matcher, 2 );
			long seconds = group( matcher, 3 );
			return Math.addExact(
				Math.addExact( Math.multiplyExact( hours, 3_600_000L ), Math.multiplyExact( minutes, 60_000L ) ),
				Math.multiplyExact( seconds, 1_000L )
			);
		} catch( NumberFormatException | ArithmeticException exception ) {
			throw invalid( text );
		}
	}
	
	@NotNull
	public static String format( long millis ) {
		
		long totalSeconds = millis <= 0 ? 0 : ( millis + 999 ) / 1000;
		long hours = totalSeconds / 3600;
		long minutes = totalSeconds % 3600 / 60;
		long seconds = totalSeconds % 60;
		StringBuilder builder = new StringBuilder();
		if( hours > 0 ) {
			builder.append( hours ).append( 'h' );
		}
		if( minutes > 0 ) {
			append( builder, minutes, 'm' );
		}
		if( seconds > 0 || builder.isEmpty() ) {
			append( builder, seconds, 's' );
		}
		return builder.toString();
	}
	
	private static void append( @NotNull StringBuilder builder, long value, char unit ) {
		
		if( !builder.isEmpty() ) {
			builder.append( ' ' );
		}
		builder.append( value ).append( unit );
	}
	
	private static long group( @NotNull Matcher matcher, int index ) {
		
		String group = matcher.group( index );
		return group == null ? 0 : Long.parseLong( group );
	}
	
	@NotNull
	private static IllegalArgumentException invalid( @NotNull String text ) {
		
		return new IllegalArgumentException(
			"Invalid duration \"" + text + "\", expected e.g. \"2h\", \"1h30m\", \"45m\" or \"30s\""
		);
	}
}
```

- [ ] **Step 4: Test ausführen, er muss bestehen**

Run: `.\gradlew.bat test --tests "*DurationParserTest"`
Expected: PASS (4 Tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/geheimagentnr1/player_time_manager/time/DurationParser.java src/test/java/de/geheimagentnr1/player_time_manager/time/DurationParserTest.java
git commit -m "Add duration parser and formatter

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Cron-Parser

**Files:**
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/cron/CronParseException.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/cron/CronExpression.java`
- Test: `src/test/java/de/geheimagentnr1/player_time_manager/cron/CronExpressionTest.java`

**Interfaces:**
- Produces: `static CronExpression CronExpression.parse( String expression )` (wirft `CronParseException extends IllegalArgumentException`); `String expression()` (normalisiert, Felder mit einem Leerzeichen getrennt); `Optional<ZonedDateTime> next( ZonedDateTime after )` (erster Zeitpunkt **nach** `after`, in der Zeitzone von `after`); `List<ZonedDateTime> between( ZonedDateTime fromExclusive, ZonedDateTime toInclusive, int limit )`.
- Regeln (wie crontab.guru / Vixie-Cron): 5 Felder; `*`, Zahlen, Listen, Bereiche, Schritte nur nach `*` oder Bereich; Monats-/Wochentagsnamen (`JAN`-`DEC`, `SUN`-`SAT`, beliebige Groß-/Kleinschreibung); Wochentag `0` und `7` = Sonntag; beginnt das Tag- **oder** das Wochentagsfeld mit `*`, müssen beide passen, sonst reicht eines; nicht existierende Uhrzeiten (Zeitumstellung vor) werden übersprungen, doppelte (Zeitumstellung zurück) zählen einmal (frühere); Ausdrücke, die in 5 Jahren nie passen (z. B. 30. Februar), sind ungültig.

- [ ] **Step 1: Failing Test schreiben**

```java
package de.geheimagentnr1.player_time_manager.cron;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


class CronExpressionTest {
	
	
	private static final ZoneId BERLIN = ZoneId.of( "Europe/Berlin" );
	
	private static ZonedDateTime at( int year, int month, int day, int hour, int minute ) {
		
		return ZonedDateTime.of( year, month, day, hour, minute, 0, 0, BERLIN );
	}
	
	private static ZonedDateTime next( String expression, ZonedDateTime after ) {
		
		return CronExpression.parse( expression ).next( after ).orElseThrow();
	}
	
	@Test
	void dailyAtSix() {
		
		assertEquals( at( 2026, 10, 14, 6, 0 ), next( "0 6 * * *", at( 2026, 10, 14, 5, 59 ) ) );
		assertEquals( at( 2026, 10, 15, 6, 0 ), next( "0 6 * * *", at( 2026, 10, 14, 6, 0 ) ) );
	}
	
	@Test
	void workdaysAndWeekendsIncludingNamesAndSeven() {
		
		// 2026-10-16 is a Friday, 2026-10-17 a Saturday
		assertEquals( at( 2026, 10, 19, 14, 0 ), next( "0 14 * * 1-5", at( 2026, 10, 16, 15, 0 ) ) );
		assertEquals( at( 2026, 10, 17, 8, 0 ), next( "0 8 * * 6,0", at( 2026, 10, 14, 12, 0 ) ) );
		assertEquals( at( 2026, 10, 17, 8, 0 ), next( "0 8 * * SAT,SUN", at( 2026, 10, 14, 12, 0 ) ) );
		assertEquals( at( 2026, 10, 18, 8, 0 ), next( "0 8 * * 7", at( 2026, 10, 14, 12, 0 ) ) );
		assertEquals( at( 2026, 10, 19, 6, 0 ), next( "0 6 * oct mon", at( 2026, 10, 14, 12, 0 ) ) );
	}
	
	@Test
	void dayOfMonthOrDayOfWeekWhenBothRestricted() {
		
		// 2026-12-13 is a Sunday: matches "13th OR Friday"
		assertEquals( at( 2026, 12, 13, 0, 0 ), next( "0 0 13 * 5", at( 2026, 12, 12, 0, 0 ) ) );
		// 2026-10-16 is a Friday
		assertEquals( at( 2026, 10, 16, 0, 0 ), next( "0 0 13 * 5", at( 2026, 10, 14, 0, 0 ) ) );
	}
	
	@Test
	void starPrefixedDayFieldCombinesWithAnd() {
		
		assertEquals( at( 2026, 10, 21, 0, 0 ), next( "0 0 */10 * *", at( 2026, 10, 14, 0, 0 ) ) );
	}
	
	@Test
	void stepsRangesAndLeapDay() {
		
		assertEquals( at( 2026, 10, 14, 10, 15 ), next( "*/15 * * * *", at( 2026, 10, 14, 10, 7 ) ) );
		assertEquals( at( 2026, 10, 14, 10, 20 ), next( "0-30/10 * * * *", at( 2026, 10, 14, 10, 15 ) ) );
		assertEquals( at( 2028, 2, 29, 0, 0 ), next( "0 0 29 2 *", at( 2026, 10, 14, 0, 0 ) ) );
	}
	
	@Test
	void skipsNonExistingTimeAtSpringForward() {
		
		// 2027-03-28 02:30 doesn't exist in Berlin
		assertEquals( at( 2027, 3, 29, 2, 30 ), next( "30 2 * * *", at( 2027, 3, 27, 3, 0 ) ) );
	}
	
	@Test
	void countsDoubledTimeOnceAtFallBack() {
		
		// 2026-10-25 02:30 exists twice in Berlin
		ZonedDateTime first = next( "30 2 * * *", at( 2026, 10, 25, 0, 0 ) );
		assertEquals( 2, first.getHour() );
		assertEquals( 25, first.getDayOfMonth() );
		assertEquals( at( 2026, 10, 26, 2, 30 ), next( "30 2 * * *", first ) );
	}
	
	@Test
	void betweenRespectsBoundsAndLimit() {
		
		CronExpression hourly = CronExpression.parse( "0 * * * *" );
		List<ZonedDateTime> times = hourly.between( at( 2026, 10, 14, 10, 0 ), at( 2026, 10, 14, 13, 0 ), 100 );
		assertEquals( List.of( at( 2026, 10, 14, 11, 0 ), at( 2026, 10, 14, 12, 0 ), at( 2026, 10, 14, 13, 0 ) ), times );
		assertEquals( 5, hourly.between( at( 2026, 10, 14, 0, 0 ), at( 2026, 10, 15, 0, 0 ), 5 ).size() );
	}
	
	@Test
	void normalizesExpression() {
		
		assertEquals( "0 6 * * 1-5", CronExpression.parse( "  0  6 * *   1-5 " ).expression() );
	}
	
	@Test
	void rejectsInvalidExpressions() {
		
		for( String invalid : new String[] {
			"", "* * * *", "* * * * * *", "60 * * * *", "* 24 * * *", "* * 0 * *", "* * * 13 *", "* * * * 8",
			"5/15 * * * *", "* * * * FOO", "a * * * *", "10-5 * * * *", "*/0 * * * *", "1,,2 * * * *", "0 0 30 2 *"
		} ) {
			assertThrows( CronParseException.class, () -> CronExpression.parse( invalid ), invalid );
		}
	}
}
```

- [ ] **Step 2: Test ausführen, er muss fehlschlagen**

Run: `.\gradlew.bat test --tests "*CronExpressionTest"`
Expected: FAIL beim Kompilieren (`cannot find symbol: class CronExpression`).

- [ ] **Step 3: Implementierung**

`CronParseException.java`:
```java
package de.geheimagentnr1.player_time_manager.cron;

import org.jetbrains.annotations.NotNull;


public class CronParseException extends IllegalArgumentException {
	
	
	public CronParseException( @NotNull String message ) {
		
		super( message );
	}
}
```

`CronExpression.java`:
```java
package de.geheimagentnr1.player_time_manager.cron;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;


public final class CronExpression {
	
	
	private static final int SEARCH_DAYS = 366 * 5;
	
	@NotNull
	private static final String[] MONTH_NAMES =
		{ "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC" };
	
	@NotNull
	private static final String[] DAY_NAMES = { "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT" };
	
	@NotNull
	private final String expression;
	
	@NotNull
	private final BitSet minutes;
	
	@NotNull
	private final BitSet hours;
	
	@NotNull
	private final BitSet daysOfMonth;
	
	@NotNull
	private final BitSet months;
	
	@NotNull
	private final BitSet daysOfWeek;
	
	private final boolean dayOfMonthStar;
	
	private final boolean dayOfWeekStar;
	
	private CronExpression(
		@NotNull String _expression,
		@NotNull BitSet _minutes,
		@NotNull BitSet _hours,
		@NotNull BitSet _daysOfMonth,
		@NotNull BitSet _months,
		@NotNull BitSet _daysOfWeek,
		boolean _dayOfMonthStar,
		boolean _dayOfWeekStar ) {
		
		expression = _expression;
		minutes = _minutes;
		hours = _hours;
		daysOfMonth = _daysOfMonth;
		months = _months;
		daysOfWeek = _daysOfWeek;
		dayOfMonthStar = _dayOfMonthStar;
		dayOfWeekStar = _dayOfWeekStar;
	}
	
	@NotNull
	public static CronExpression parse( @NotNull String expression ) {
		
		String trimmed = expression.trim();
		String[] fields = trimmed.isEmpty() ? new String[0] : trimmed.split( "\\s+" );
		if( fields.length != 5 ) {
			throw new CronParseException( String.format(
				"Cron expression \"%s\" needs 5 fields (minute hour day month weekday), found %d",
				expression,
				fields.length
			) );
		}
		BitSet daysOfWeek = parseField( fields[4], 0, 7, DAY_NAMES, 0, "weekday" );
		if( daysOfWeek.get( 7 ) ) {
			daysOfWeek.set( 0 );
			daysOfWeek.clear( 7 );
		}
		CronExpression cron = new CronExpression(
			String.join( " ", fields ),
			parseField( fields[0], 0, 59, null, 0, "minute" ),
			parseField( fields[1], 0, 23, null, 0, "hour" ),
			parseField( fields[2], 1, 31, null, 0, "day" ),
			parseField( fields[3], 1, 12, MONTH_NAMES, 1, "month" ),
			daysOfWeek,
			fields[2].startsWith( "*" ),
			fields[4].startsWith( "*" )
		);
		if( cron.next( ZonedDateTime.of( 2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC ) ).isEmpty() ) {
			throw new CronParseException( String.format( "Cron expression \"%s\" never matches", expression ) );
		}
		return cron;
	}
	
	@NotNull
	private static BitSet parseField(
		@NotNull String field,
		int min,
		int max,
		@Nullable String[] names,
		int nameOffset,
		@NotNull String fieldName ) {
		
		BitSet bits = new BitSet( max + 1 );
		for( String part : field.split( ",", -1 ) ) {
			if( part.isEmpty() ) {
				throw new CronParseException( String.format( "Empty list element in %s field \"%s\"", fieldName, field ) );
			}
			String rangePart = part;
			int step = 1;
			int slash = part.indexOf( '/' );
			if( slash >= 0 ) {
				rangePart = part.substring( 0, slash );
				step = parseNumber( part.substring( slash + 1 ), fieldName, field );
				if( step <= 0 ) {
					throw new CronParseException( String.format( "Step must be positive in %s field \"%s\"", fieldName, field ) );
				}
				if( !rangePart.equals( "*" ) && !rangePart.contains( "-" ) ) {
					throw new CronParseException( String.format(
						"A step is only allowed after \"*\" or a range in %s field \"%s\"",
						fieldName,
						field
					) );
				}
			}
			int start;
			int end;
			if( rangePart.equals( "*" ) ) {
				start = min;
				end = max;
			} else {
				int dash = rangePart.indexOf( '-' );
				if( dash >= 0 ) {
					start = parseValue( rangePart.substring( 0, dash ), names, nameOffset, fieldName, field );
					end = parseValue( rangePart.substring( dash + 1 ), names, nameOffset, fieldName, field );
				} else {
					start = parseValue( rangePart, names, nameOffset, fieldName, field );
					end = start;
				}
			}
			if( start < min || end > max || start > end ) {
				throw new CronParseException( String.format(
					"Value out of range %d-%d in %s field \"%s\"",
					min,
					max,
					fieldName,
					field
				) );
			}
			for( int value = start; value <= end; value += step ) {
				bits.set( value );
			}
		}
		return bits;
	}
	
	private static int parseValue(
		@NotNull String text,
		@Nullable String[] names,
		int nameOffset,
		@NotNull String fieldName,
		@NotNull String field ) {
		
		if( names != null ) {
			String upper = text.toUpperCase( Locale.ROOT );
			for( int i = 0; i < names.length; i++ ) {
				if( names[i].equals( upper ) ) {
					return i + nameOffset;
				}
			}
		}
		return parseNumber( text, fieldName, field );
	}
	
	private static int parseNumber( @NotNull String text, @NotNull String fieldName, @NotNull String field ) {
		
		try {
			return Integer.parseInt( text );
		} catch( NumberFormatException exception ) {
			throw new CronParseException( String.format( "Invalid value \"%s\" in %s field \"%s\"", text, fieldName, field ) );
		}
	}
	
	@NotNull
	public String expression() {
		
		return expression;
	}
	
	@NotNull
	public Optional<ZonedDateTime> next( @NotNull ZonedDateTime after ) {
		
		ZoneId zone = after.getZone();
		LocalDate date = after.toLocalDate();
		for( int day = 0; day < SEARCH_DAYS; day++, date = date.plusDays( 1 ) ) {
			if( !months.get( date.getMonthValue() ) || !dayMatches( date ) ) {
				continue;
			}
			for( int hour = hours.nextSetBit( 0 ); hour >= 0; hour = hours.nextSetBit( hour + 1 ) ) {
				for( int minute = minutes.nextSetBit( 0 ); minute >= 0; minute = minutes.nextSetBit( minute + 1 ) ) {
					LocalDateTime local = date.atTime( hour, minute );
					ZonedDateTime candidate = ZonedDateTime.ofLocal( local, zone, null );
					if( candidate.toLocalDateTime().equals( local ) && candidate.isAfter( after ) ) {
						return Optional.of( candidate );
					}
				}
			}
		}
		return Optional.empty();
	}
	
	@NotNull
	public List<ZonedDateTime> between(
		@NotNull ZonedDateTime fromExclusive,
		@NotNull ZonedDateTime toInclusive,
		int limit ) {
		
		List<ZonedDateTime> times = new ArrayList<>();
		ZonedDateTime current = fromExclusive;
		while( times.size() < limit ) {
			Optional<ZonedDateTime> next = next( current );
			if( next.isEmpty() || next.get().isAfter( toInclusive ) ) {
				break;
			}
			times.add( next.get() );
			current = next.get();
		}
		return times;
	}
	
	private boolean dayMatches( @NotNull LocalDate date ) {
		
		boolean dayOfMonthMatch = daysOfMonth.get( date.getDayOfMonth() );
		boolean dayOfWeekMatch = daysOfWeek.get( date.getDayOfWeek().getValue() % 7 );
		if( dayOfMonthStar || dayOfWeekStar ) {
			return dayOfMonthMatch && dayOfWeekMatch;
		}
		return dayOfMonthMatch || dayOfWeekMatch;
	}
}
```

- [ ] **Step 4: Test ausführen, er muss bestehen**

Run: `.\gradlew.bat test --tests "*CronExpressionTest"`
Expected: PASS (10 Tests).

- [ ] **Step 5: Abgleich mit crontab.guru**

Die Ausdrücke `0 0 13 * 5`, `0 0 */10 * *`, `0 8 * * 6,0` und `0-30/10 * * * *` auf https://crontab.guru eingeben und prüfen, dass die dort angezeigten „next“-Zeitpunkte zu den Testerwartungen passen. Abweichungen: Test und Implementierung an crontab.guru anpassen, nicht umgekehrt.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/de/geheimagentnr1/player_time_manager/cron src/test/java/de/geheimagentnr1/player_time_manager/cron
git commit -m "Add cron expression parser

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Refill-Berechnung

**Files:**
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/time/RefillMode.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/time/RefillRule.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/time/TimeEntry.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/time/RefillCalculator.java`
- Test: `src/test/java/de/geheimagentnr1/player_time_manager/time/RefillCalculatorTest.java`

**Interfaces:**
- Consumes: `CronExpression` (Task 3).
- Produces:
  - `enum RefillMode { SET, ADD }`
  - `record RefillRule( @Nullable CronExpression cron, RefillMode mode, long limitMillis, @Nullable Long maxMillis )` mit `String scheduleId()` (`""` ohne Cron, sonst `cron.expression()`)
  - `record TimeEntry( long remainingMillis, long lastRefillMillis, String scheduleId )` mit `TimeEntry withRemaining( long )` (Restzeit nie unter 0)
  - `RefillCalculator.MAX_CATCH_UP = 10_000`, `static TimeEntry create( RefillRule rule, long nowMillis )`, `static TimeEntry apply( TimeEntry entry, RefillRule rule, long nowMillis, ZoneId zone )`, `static TimeEntry refillNow( TimeEntry entry, RefillRule rule )`

- [ ] **Step 1: Failing Test schreiben**

```java
package de.geheimagentnr1.player_time_manager.time;

import de.geheimagentnr1.player_time_manager.cron.CronExpression;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;


class RefillCalculatorTest {
	
	
	private static final ZoneId BERLIN = ZoneId.of( "Europe/Berlin" );
	
	private static final long MINUTE = 60_000L;
	
	private static long at( int day, int hour, int minute ) {
		
		return ZonedDateTime.of( 2026, 10, day, hour, minute, 0, 0, BERLIN ).toInstant().toEpochMilli();
	}
	
	private static RefillRule rule( String cron, RefillMode mode, long limit, Long max ) {
		
		return new RefillRule( cron == null ? null : CronExpression.parse( cron ), mode, limit, max );
	}
	
	@Test
	void createStartsWithFullLimit() {
		
		RefillRule rule = rule( "0 6 * * *", RefillMode.SET, 60 * MINUTE, null );
		assertEquals( new TimeEntry( 60 * MINUTE, at( 14, 12, 0 ), "0 6 * * *" ), RefillCalculator.create( rule, at( 14, 12, 0 ) ) );
	}
	
	@Test
	void setRefillsOnceToLimit() {
		
		RefillRule rule = rule( "0 6 * * *", RefillMode.SET, 60 * MINUTE, null );
		TimeEntry entry = new TimeEntry( 5 * MINUTE, at( 14, 12, 0 ), "0 6 * * *" );
		TimeEntry result = RefillCalculator.apply( entry, rule, at( 17, 7, 0 ), BERLIN );
		assertEquals( 60 * MINUTE, result.remainingMillis() );
		assertEquals( at( 17, 6, 0 ), result.lastRefillMillis() );
	}
	
	@Test
	void addRefillsEveryMissedOccurrence() {
		
		RefillRule rule = rule( "0 * * * *", RefillMode.ADD, 10 * MINUTE, null );
		TimeEntry entry = new TimeEntry( 0, at( 14, 10, 0 ), "0 * * * *" );
		TimeEntry result = RefillCalculator.apply( entry, rule, at( 14, 13, 30 ), BERLIN );
		assertEquals( 30 * MINUTE, result.remainingMillis() );
		assertEquals( at( 14, 13, 0 ), result.lastRefillMillis() );
	}
	
	@Test
	void addIsCappedByMax() {
		
		RefillRule rule = rule( "0 * * * *", RefillMode.ADD, 10 * MINUTE, 25 * MINUTE );
		TimeEntry entry = new TimeEntry( 0, at( 14, 10, 0 ), "0 * * * *" );
		assertEquals( 25 * MINUTE, RefillCalculator.apply( entry, rule, at( 14, 13, 30 ), BERLIN ).remainingMillis() );
	}
	
	@Test
	void addNeverCutsTimeAboveMax() {
		
		RefillRule rule = rule( "0 * * * *", RefillMode.ADD, 10 * MINUTE, 25 * MINUTE );
		TimeEntry entry = new TimeEntry( 90 * MINUTE, at( 14, 10, 0 ), "0 * * * *" );
		assertEquals( 90 * MINUTE, RefillCalculator.apply( entry, rule, at( 14, 11, 30 ), BERLIN ).remainingMillis() );
	}
	
	@Test
	void noOccurrenceKeepsEntry() {
		
		RefillRule rule = rule( "0 6 * * *", RefillMode.SET, 60 * MINUTE, null );
		TimeEntry entry = new TimeEntry( 5 * MINUTE, at( 14, 7, 0 ), "0 6 * * *" );
		assertEquals( entry, RefillCalculator.apply( entry, rule, at( 14, 23, 0 ), BERLIN ) );
	}
	
	@Test
	void changedScheduleResetsWithoutRefill() {
		
		RefillRule rule = rule( "0 8 * * *", RefillMode.SET, 60 * MINUTE, null );
		TimeEntry entry = new TimeEntry( 5 * MINUTE, at( 10, 7, 0 ), "0 6 * * *" );
		assertEquals( new TimeEntry( 5 * MINUTE, at( 14, 12, 0 ), "0 8 * * *" ), RefillCalculator.apply( entry, rule, at( 14, 12, 0 ), BERLIN ) );
	}
	
	@Test
	void noCronKeepsEntry() {
		
		RefillRule rule = rule( null, RefillMode.SET, 60 * MINUTE, null );
		TimeEntry entry = new TimeEntry( 5 * MINUTE, at( 10, 7, 0 ), "" );
		assertEquals( entry, RefillCalculator.apply( entry, rule, at( 14, 12, 0 ), BERLIN ) );
	}
	
	@Test
	void catchUpIsLimited() {
		
		RefillRule rule = rule( "* * * * *", RefillMode.ADD, 1_000L, null );
		TimeEntry entry = new TimeEntry( 0, at( 1, 0, 0 ), "* * * * *" );
		TimeEntry result = RefillCalculator.apply( entry, rule, at( 31, 0, 0 ), BERLIN );
		assertEquals( RefillCalculator.MAX_CATCH_UP * 1_000L, result.remainingMillis() );
		assertEquals( at( 1, 0, 0 ) + RefillCalculator.MAX_CATCH_UP * MINUTE, result.lastRefillMillis() );
	}
	
	@Test
	void refillNowKeepsSchedulePosition() {
		
		RefillRule set = rule( "0 6 * * *", RefillMode.SET, 60 * MINUTE, null );
		RefillRule add = rule( "0 6 * * *", RefillMode.ADD, 60 * MINUTE, 90 * MINUTE );
		TimeEntry entry = new TimeEntry( 45 * MINUTE, at( 14, 6, 0 ), "0 6 * * *" );
		assertEquals( new TimeEntry( 60 * MINUTE, at( 14, 6, 0 ), "0 6 * * *" ), RefillCalculator.refillNow( entry, set ) );
		assertEquals( new TimeEntry( 90 * MINUTE, at( 14, 6, 0 ), "0 6 * * *" ), RefillCalculator.refillNow( entry, add ) );
	}
}
```

- [ ] **Step 2: Test ausführen, er muss fehlschlagen**

Run: `.\gradlew.bat test --tests "*RefillCalculatorTest"`
Expected: FAIL beim Kompilieren (`cannot find symbol: class RefillRule`).

- [ ] **Step 3: Implementierung**

`RefillMode.java`:
```java
package de.geheimagentnr1.player_time_manager.time;


public enum RefillMode {
	SET,
	ADD
}
```

`RefillRule.java`:
```java
package de.geheimagentnr1.player_time_manager.time;

import de.geheimagentnr1.player_time_manager.cron.CronExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public record RefillRule(
	@Nullable CronExpression cron,
	@NotNull RefillMode mode,
	long limitMillis,
	@Nullable Long maxMillis ) {
	
	
	@NotNull
	public String scheduleId() {
		
		return cron == null ? "" : cron.expression();
	}
}
```

`TimeEntry.java` (bewusst ohne Minecraft-/DFU-Abhängigkeit, damit die Unit-Tests ohne Minecraft-Klassen laufen; der Codec liegt in `data/TimeData`, Task 7):
```java
package de.geheimagentnr1.player_time_manager.time;

import org.jetbrains.annotations.NotNull;


public record TimeEntry( long remainingMillis, long lastRefillMillis, @NotNull String scheduleId ) {
	
	
	@NotNull
	public TimeEntry withRemaining( long newRemainingMillis ) {
		
		return new TimeEntry( Math.max( 0, newRemainingMillis ), lastRefillMillis, scheduleId );
	}
}
```

`RefillCalculator.java`:
```java
package de.geheimagentnr1.player_time_manager.time;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;


public final class RefillCalculator {
	
	
	public static final int MAX_CATCH_UP = 10_000;
	
	private RefillCalculator() {
	
	}
	
	@NotNull
	public static TimeEntry create( @NotNull RefillRule rule, long nowMillis ) {
		
		return new TimeEntry( rule.limitMillis(), nowMillis, rule.scheduleId() );
	}
	
	@NotNull
	public static TimeEntry apply( @NotNull TimeEntry entry, @NotNull RefillRule rule, long nowMillis, @NotNull ZoneId zone ) {
		
		if( !entry.scheduleId().equals( rule.scheduleId() ) ) {
			return new TimeEntry( entry.remainingMillis(), nowMillis, rule.scheduleId() );
		}
		if( rule.cron() == null ) {
			return entry;
		}
		List<ZonedDateTime> due = rule.cron().between(
			Instant.ofEpochMilli( entry.lastRefillMillis() ).atZone( zone ),
			Instant.ofEpochMilli( nowMillis ).atZone( zone ),
			MAX_CATCH_UP
		);
		if( due.isEmpty() ) {
			return entry;
		}
		long remaining = entry.remainingMillis();
		if( rule.mode() == RefillMode.SET ) {
			remaining = rule.limitMillis();
		} else {
			for( int i = 0; i < due.size(); i++ ) {
				remaining = add( remaining, rule );
			}
		}
		return new TimeEntry( remaining, due.getLast().toInstant().toEpochMilli(), entry.scheduleId() );
	}
	
	@NotNull
	public static TimeEntry refillNow( @NotNull TimeEntry entry, @NotNull RefillRule rule ) {
		
		long remaining = rule.mode() == RefillMode.SET ? rule.limitMillis() : add( entry.remainingMillis(), rule );
		return new TimeEntry( remaining, entry.lastRefillMillis(), entry.scheduleId() );
	}
	
	private static long add( long remaining, @NotNull RefillRule rule ) {
		
		long current = Math.max( 0, remaining );
		long sum = current > Long.MAX_VALUE - rule.limitMillis() ? Long.MAX_VALUE : current + rule.limitMillis();
		if( rule.maxMillis() == null ) {
			return sum;
		}
		// Never cut time that is already above the maximum (e.g. set by an admin)
		return Math.max( current, Math.min( sum, rule.maxMillis() ) );
	}
}
```

- [ ] **Step 4: Test ausführen, er muss bestehen**

Run: `.\gradlew.bat test --tests "*RefillCalculatorTest"`
Expected: PASS (10 Tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/geheimagentnr1/player_time_manager/time src/test/java/de/geheimagentnr1/player_time_manager/time/RefillCalculatorTest.java
git commit -m "Add refill calculation

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Einstellungsmodell, Zielposition und Prüfung

**Files:**
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/config/raw/PlayerRef.java`, `ScopeRaw.java`, `DimensionRaw.java`, `GeneralRaw.java`, `RawSettings.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/config/resolved/DisplayMode.java`, `DisplaySettings.java`, `PlayerRules.java`, `ResolvedScope.java`, `TargetSpec.java`, `ResolvedDimension.java`, `ResolvedSettings.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/config/TargetPosition.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/config/SettingsResolver.java`
- Test: `src/test/java/de/geheimagentnr1/player_time_manager/config/TargetPositionTest.java`, `SettingsResolverTest.java`, `RawSettingsTestData.java`

**Interfaces:**
- Consumes: `DurationParser` (Task 2), `CronExpression`/`CronParseException` (Task 3), `RefillMode`/`RefillRule` (Task 4).
- Produces (alle Records in `config.raw` bzw. `config.resolved`):
  - `PlayerRef( String name, String uuid )`
  - `ScopeRaw( boolean enabled, String limit, boolean useCustomRefill, String refillCron, String refillMode, String refillMax )`
  - `DimensionRaw( String dimension, ScopeRaw scope, String targetDimension, String targetPosition )`
  - `GeneralRaw( int commandPermissionLevel, boolean opsExempt, boolean pauseInCreative, boolean pauseInSpectator, List<PlayerRef> limitedPlayers, List<PlayerRef> exemptPlayers, String timeZone, String refillCron, String refillMode, String refillMax, String displayMode, int bossbarYellowBelowPercent, int bossbarRedBelowPercent, List<String> warningTimes, boolean warningSound )`
  - `RawSettings( GeneralRaw general, ScopeRaw world, List<DimensionRaw> dimensions )`
  - `enum DisplayMode { NONE, ACTIONBAR, BOSSBARS, BOSSBAR_SHORTEST }`
  - `DisplaySettings( DisplayMode mode, int yellowBelowPercent, int redBelowPercent, List<Long> warningTimesMillis /* absteigend */, boolean warningSound )`
  - `PlayerRules( boolean opsExempt, boolean pauseInCreative, boolean pauseInSpectator, Set<UUID> limitedIds, Set<String> limitedNames, Set<UUID> exemptIds, Set<String> exemptNames )` mit `boolean hasLimitedList()`; Namen klein geschrieben
  - `ResolvedScope( boolean active, long limitMillis, @Nullable RefillRule refillRule )` mit `static final ResolvedScope INACTIVE`
  - `TargetSpec( String dimension, @Nullable TargetPosition position )`
  - `ResolvedDimension( String dimension, ResolvedScope scope, @Nullable TargetSpec target )` - `target == null` = Ausweg Overworld-Spawn
  - `ResolvedSettings( int commandPermissionLevel, PlayerRules playerRules, ZoneId zone, DisplaySettings display, ResolvedScope world, Map<String, ResolvedDimension> dimensions, List<String> problems )`
  - `TargetPosition( double x, double y, double z, @Nullable Float yaw, @Nullable Float pitch )` mit `static TargetPosition parse( String text )` (wirft `IllegalArgumentException`) und `String format()`
  - `SettingsResolver.OVERWORLD = "minecraft:overworld"`, `SettingsResolver.THE_END = "minecraft:the_end"`, `static ResolvedSettings resolve( RawSettings raw, Set<String> knownDimensions, ZoneId systemZone )`

- [ ] **Step 1: Failing Tests schreiben**

`RawSettingsTestData.java` (Testhilfe):
```java
package de.geheimagentnr1.player_time_manager.config;

import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.GeneralRaw;
import de.geheimagentnr1.player_time_manager.config.raw.PlayerRef;
import de.geheimagentnr1.player_time_manager.config.raw.RawSettings;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;

import java.util.List;


final class RawSettingsTestData {
	
	
	static GeneralRaw general() {
		
		return general( "0 6 * * *", List.of(), List.of(), "", 50, 10 );
	}
	
	static GeneralRaw general(
		String cron,
		List<PlayerRef> limited,
		List<PlayerRef> exempt,
		String timeZone,
		int yellow,
		int red ) {
		
		return new GeneralRaw( 3, true, true, true, limited, exempt, timeZone, cron, "SET", "", "BOSSBARS", yellow, red,
			List.of( "1m", "10m", "10s", "5m" ), true );
	}
	
	static ScopeRaw scope( boolean enabled, String limit ) {
		
		return new ScopeRaw( enabled, limit, false, "0 6 * * *", "SET", "" );
	}
	
	static DimensionRaw dimension( String id, String limit, String targetDimension, String targetPosition ) {
		
		return new DimensionRaw( id, scope( true, limit ), targetDimension, targetPosition );
	}
	
	static RawSettings settings( ScopeRaw world, DimensionRaw... dimensions ) {
		
		return new RawSettings( general(), world, List.of( dimensions ) );
	}
}
```

`TargetPositionTest.java`:
```java
package de.geheimagentnr1.player_time_manager.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


class TargetPositionTest {
	
	
	@Test
	void parsesPositionWithAndWithoutRotation() {
		
		TargetPosition plain = TargetPosition.parse( "120 64 -35" );
		assertEquals( new TargetPosition( 120, 64, -35, null, null ), plain );
		assertNull( plain.yaw() );
		assertEquals( new TargetPosition( 120.5, 64, -35.25, 90f, -10f ), TargetPosition.parse( " 120.5  64 -35.25 90 -10 " ) );
	}
	
	@Test
	void rejectsInvalidPositions() {
		
		for( String invalid : new String[] { "", "1 2", "1 2 3 4", "1 2 3 4 5 6", "a 2 3", "1 2 NaN", "1 2 3 x 5" } ) {
			assertThrows( IllegalArgumentException.class, () -> TargetPosition.parse( invalid ), invalid );
		}
	}
	
	@Test
	void formatsShortAndRoundTrips() {
		
		assertEquals( "120 64 -35", new TargetPosition( 120, 64, -35, null, null ).format() );
		assertEquals( "120.5 64 -35.25 90 -10", new TargetPosition( 120.5, 64, -35.25, 90f, -10f ).format() );
		assertEquals( "1.23 2 3", new TargetPosition( 1.234, 2, 3, null, null ).format() );
		TargetPosition position = new TargetPosition( 10.75, 70, -3.5, 180f, 0f );
		assertEquals( position, TargetPosition.parse( position.format() ) );
	}
}
```

`SettingsResolverTest.java`:
```java
package de.geheimagentnr1.player_time_manager.config;

import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.PlayerRef;
import de.geheimagentnr1.player_time_manager.config.raw.RawSettings;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedDimension;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedSettings;
import de.geheimagentnr1.player_time_manager.time.RefillMode;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static de.geheimagentnr1.player_time_manager.config.RawSettingsTestData.dimension;
import static de.geheimagentnr1.player_time_manager.config.RawSettingsTestData.general;
import static de.geheimagentnr1.player_time_manager.config.RawSettingsTestData.scope;
import static de.geheimagentnr1.player_time_manager.config.RawSettingsTestData.settings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


class SettingsResolverTest {
	
	
	private static final Set<String> KNOWN =
		Set.of( "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "mymod:mining" );
	
	private static final ZoneId SYSTEM = ZoneId.of( "Europe/Berlin" );
	
	private static ResolvedSettings resolve( RawSettings raw ) {
		
		return SettingsResolver.resolve( raw, KNOWN, SYSTEM );
	}
	
	private static boolean hasProblem( ResolvedSettings settings, String part ) {
		
		return settings.problems().stream().anyMatch( problem -> problem.contains( part ) );
	}
	
	@Test
	void defaultsAreInactiveWithoutProblems() {
		
		ResolvedSettings settings = resolve( settings(
			scope( false, "2h" ),
			new DimensionRaw( "minecraft:the_nether", scope( false, "1h" ), "minecraft:overworld", "" )
		) );
		assertFalse( settings.world().active() );
		assertFalse( settings.dimensions().get( "minecraft:the_nether" ).scope().active() );
		assertEquals( List.of(), settings.problems() );
		assertEquals( SYSTEM, settings.zone() );
		assertEquals( List.of( 600_000L, 300_000L, 60_000L, 10_000L ), settings.display().warningTimesMillis() );
	}
	
	@Test
	void activeWorldUsesGeneralRefill() {
		
		ResolvedSettings settings = resolve( settings( scope( true, "2h" ) ) );
		assertTrue( settings.world().active() );
		assertEquals( 7_200_000L, settings.world().limitMillis() );
		assertEquals( "0 6 * * *", settings.world().refillRule().scheduleId() );
		assertEquals( RefillMode.SET, settings.world().refillRule().mode() );
	}
	
	@Test
	void customRefillWithFallbacks() {
		
		ScopeRaw custom = new ScopeRaw( true, "30m", true, "0 8 * * 6,0", "ADD", "1h" );
		ResolvedSettings ok = resolve( settings( custom ) );
		assertEquals( "0 8 * * 6,0", ok.world().refillRule().scheduleId() );
		assertEquals( RefillMode.ADD, ok.world().refillRule().mode() );
		assertEquals( 3_600_000L, ok.world().refillRule().maxMillis() );
		
		ScopeRaw broken = new ScopeRaw( true, "30m", true, "0 25 * * *", "MULTIPLY", "1x" );
		ResolvedSettings fallback = resolve( settings( broken ) );
		assertNull( fallback.world().refillRule().cron() );
		assertEquals( RefillMode.SET, fallback.world().refillRule().mode() );
		assertNull( fallback.world().refillRule().maxMillis() );
		assertTrue( hasProblem( fallback, "world.refill_cron" ) );
		assertTrue( hasProblem( fallback, "world.refill_mode" ) );
		assertTrue( hasProblem( fallback, "world.refill_max" ) );
	}
	
	@Test
	void invalidLimitDisablesScope() {
		
		ResolvedSettings settings = resolve( settings( scope( true, "1x" ), dimension( "minecraft:the_nether", "0m", "minecraft:overworld", "" ) ) );
		assertFalse( settings.world().active() );
		assertFalse( settings.dimensions().get( "minecraft:the_nether" ).scope().active() );
		assertTrue( hasProblem( settings, "world.limit" ) );
		assertTrue( hasProblem( settings, "minecraft:the_nether" ) );
	}
	
	@Test
	void dimensionTargets() {
		
		ResolvedSettings settings = resolve( settings(
			scope( false, "2h" ),
			dimension( "minecraft:the_nether", "1h", "minecraft:overworld", "" ),
			dimension( "minecraft:the_end", "1h", "minecraft:the_end", "" ),
			dimension( "mymod:mining", "1h", "minecraft:the_nether", "" )
		) );
		ResolvedDimension nether = settings.dimensions().get( "minecraft:the_nether" );
		assertEquals( "minecraft:overworld", nether.target().dimension() );
		assertNull( nether.target().position() );
		assertNotNull( settings.dimensions().get( "minecraft:the_end" ).target() );
		assertNull( settings.dimensions().get( "mymod:mining" ).target() );
		assertTrue( hasProblem( settings, "needs a target_position" ) );
		assertTrue( hasProblem( settings, "/playtime config dimension mymod:mining target here" ) );
	}
	
	@Test
	void invalidTargetsFallBack() {
		
		ResolvedSettings settings = resolve( settings(
			scope( false, "2h" ),
			dimension( "minecraft:the_nether", "1h", "unknown:dim", "" ),
			dimension( "minecraft:the_end", "1h", "minecraft:overworld", "1 2" ),
			dimension( "mymod:mining", "1h", "", "" )
		) );
		assertNull( settings.dimensions().get( "minecraft:the_nether" ).target() );
		assertNull( settings.dimensions().get( "minecraft:the_end" ).target() );
		assertEquals( "minecraft:overworld", settings.dimensions().get( "mymod:mining" ).target().dimension() );
		assertTrue( hasProblem( settings, "unknown:dim" ) );
		assertTrue( hasProblem( settings, "target_position" ) );
	}
	
	@Test
	void overworldDuplicatesAndUnknownDimensionsAreIgnored() {
		
		ResolvedSettings settings = resolve( settings(
			scope( false, "2h" ),
			dimension( "minecraft:overworld", "1h", "minecraft:overworld", "" ),
			dimension( "minecraft:the_nether", "1h", "minecraft:overworld", "" ),
			dimension( "minecraft:the_nether", "2h", "minecraft:overworld", "" ),
			dimension( "missing:dim", "1h", "minecraft:overworld", "" ),
			dimension( "", "1h", "minecraft:overworld", "" )
		) );
		assertEquals( Set.of( "minecraft:the_nether" ), settings.dimensions().keySet() );
		assertEquals( 3_600_000L, settings.dimensions().get( "minecraft:the_nether" ).scope().limitMillis() );
		assertTrue( hasProblem( settings, "can't be limited" ) );
		assertTrue( hasProblem( settings, "duplicate" ) );
		assertTrue( hasProblem( settings, "missing:dim" ) );
		assertEquals( 4, settings.problems().size() );
	}
	
	@Test
	void generalValuesWithFallbacks() {
		
		UUID id = UUID.fromString( "8667ba71-b85a-4004-af54-457a9734eed7" );
		RawSettings raw = new RawSettings(
			general(
				"not a cron",
				List.of( new PlayerRef( "Alex", "" ) ),
				List.of( new PlayerRef( "Steve", id.toString() ), new PlayerRef( "Bad", "no-uuid" ), new PlayerRef( "", "" ) ),
				"Mars/Olympus",
				10,
				50
			),
			scope( true, "2h" ),
			List.of()
		);
		ResolvedSettings settings = resolve( raw );
		assertNull( settings.world().refillRule().cron() );
		assertEquals( SYSTEM, settings.zone() );
		assertEquals( 50, settings.display().yellowBelowPercent() );
		assertEquals( 10, settings.display().redBelowPercent() );
		assertEquals( Set.of( "alex" ), settings.playerRules().limitedNames() );
		assertTrue( settings.playerRules().hasLimitedList() );
		assertEquals( Set.of( id ), settings.playerRules().exemptIds() );
		assertEquals( Set.of( "bad" ), settings.playerRules().exemptNames() );
		assertTrue( hasProblem( settings, "general.refill_cron" ) );
		assertTrue( hasProblem( settings, "general.time_zone" ) );
		assertTrue( hasProblem( settings, "bossbar" ) );
		assertTrue( hasProblem( settings, "no-uuid" ) );
		assertTrue( hasProblem( settings, "exempt_players" ) );
	}
	
	@Test
	void validTimeZoneIsUsed() {
		
		RawSettings raw = new RawSettings( general( "0 6 * * *", List.of(), List.of(), "America/New_York", 50, 10 ), scope( false, "2h" ), List.of() );
		assertEquals( ZoneId.of( "America/New_York" ), resolve( raw ).zone() );
	}
}
```

- [ ] **Step 2: Tests ausführen, sie müssen fehlschlagen**

Run: `.\gradlew.bat test --tests "*TargetPositionTest" --tests "*SettingsResolverTest"`
Expected: FAIL beim Kompilieren (`cannot find symbol`).

- [ ] **Step 3: Records anlegen**

`config/raw/PlayerRef.java`:
```java
package de.geheimagentnr1.player_time_manager.config.raw;

import org.jetbrains.annotations.NotNull;


public record PlayerRef( @NotNull String name, @NotNull String uuid ) {

}
```

`config/raw/ScopeRaw.java`:
```java
package de.geheimagentnr1.player_time_manager.config.raw;

import org.jetbrains.annotations.NotNull;


public record ScopeRaw(
	boolean enabled,
	@NotNull String limit,
	boolean useCustomRefill,
	@NotNull String refillCron,
	@NotNull String refillMode,
	@NotNull String refillMax ) {

}
```

`config/raw/DimensionRaw.java`:
```java
package de.geheimagentnr1.player_time_manager.config.raw;

import org.jetbrains.annotations.NotNull;


public record DimensionRaw(
	@NotNull String dimension,
	@NotNull ScopeRaw scope,
	@NotNull String targetDimension,
	@NotNull String targetPosition ) {

}
```

`config/raw/GeneralRaw.java`:
```java
package de.geheimagentnr1.player_time_manager.config.raw;

import org.jetbrains.annotations.NotNull;

import java.util.List;


public record GeneralRaw(
	int commandPermissionLevel,
	boolean opsExempt,
	boolean pauseInCreative,
	boolean pauseInSpectator,
	@NotNull List<PlayerRef> limitedPlayers,
	@NotNull List<PlayerRef> exemptPlayers,
	@NotNull String timeZone,
	@NotNull String refillCron,
	@NotNull String refillMode,
	@NotNull String refillMax,
	@NotNull String displayMode,
	int bossbarYellowBelowPercent,
	int bossbarRedBelowPercent,
	@NotNull List<String> warningTimes,
	boolean warningSound ) {

}
```

`config/raw/RawSettings.java`:
```java
package de.geheimagentnr1.player_time_manager.config.raw;

import org.jetbrains.annotations.NotNull;

import java.util.List;


public record RawSettings( @NotNull GeneralRaw general, @NotNull ScopeRaw world, @NotNull List<DimensionRaw> dimensions ) {

}
```

`config/resolved/DisplayMode.java`:
```java
package de.geheimagentnr1.player_time_manager.config.resolved;


public enum DisplayMode {
	NONE,
	ACTIONBAR,
	BOSSBARS,
	BOSSBAR_SHORTEST
}
```

`config/resolved/DisplaySettings.java`:
```java
package de.geheimagentnr1.player_time_manager.config.resolved;

import org.jetbrains.annotations.NotNull;

import java.util.List;


public record DisplaySettings(
	@NotNull DisplayMode mode,
	int yellowBelowPercent,
	int redBelowPercent,
	@NotNull List<Long> warningTimesMillis,
	boolean warningSound ) {

}
```

`config/resolved/PlayerRules.java`:
```java
package de.geheimagentnr1.player_time_manager.config.resolved;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;


public record PlayerRules(
	boolean opsExempt,
	boolean pauseInCreative,
	boolean pauseInSpectator,
	@NotNull Set<UUID> limitedIds,
	@NotNull Set<String> limitedNames,
	@NotNull Set<UUID> exemptIds,
	@NotNull Set<String> exemptNames ) {
	
	
	public boolean hasLimitedList() {
		
		return !limitedIds.isEmpty() || !limitedNames.isEmpty();
	}
}
```

`config/resolved/ResolvedScope.java`:
```java
package de.geheimagentnr1.player_time_manager.config.resolved;

import de.geheimagentnr1.player_time_manager.time.RefillRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public record ResolvedScope( boolean active, long limitMillis, @Nullable RefillRule refillRule ) {
	
	
	@NotNull
	public static final ResolvedScope INACTIVE = new ResolvedScope( false, 0, null );
}
```

`config/resolved/TargetSpec.java`:
```java
package de.geheimagentnr1.player_time_manager.config.resolved;

import de.geheimagentnr1.player_time_manager.config.TargetPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public record TargetSpec( @NotNull String dimension, @Nullable TargetPosition position ) {

}
```

`config/resolved/ResolvedDimension.java`:
```java
package de.geheimagentnr1.player_time_manager.config.resolved;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public record ResolvedDimension( @NotNull String dimension, @NotNull ResolvedScope scope, @Nullable TargetSpec target ) {

}
```

`config/resolved/ResolvedSettings.java`:
```java
package de.geheimagentnr1.player_time_manager.config.resolved;

import org.jetbrains.annotations.NotNull;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;


public record ResolvedSettings(
	int commandPermissionLevel,
	@NotNull PlayerRules playerRules,
	@NotNull ZoneId zone,
	@NotNull DisplaySettings display,
	@NotNull ResolvedScope world,
	@NotNull Map<String, ResolvedDimension> dimensions,
	@NotNull List<String> problems ) {

}
```

- [ ] **Step 4: `TargetPosition` implementieren**

```java
package de.geheimagentnr1.player_time_manager.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;


public record TargetPosition( double x, double y, double z, @Nullable Float yaw, @Nullable Float pitch ) {
	
	
	@NotNull
	public static TargetPosition parse( @NotNull String text ) {
		
		String trimmed = text.trim();
		String[] parts = trimmed.isEmpty() ? new String[0] : trimmed.split( "\\s+" );
		if( parts.length != 3 && parts.length != 5 ) {
			throw invalid( text );
		}
		try {
			double x = finite( Double.parseDouble( parts[0] ), text );
			double y = finite( Double.parseDouble( parts[1] ), text );
			double z = finite( Double.parseDouble( parts[2] ), text );
			if( parts.length == 3 ) {
				return new TargetPosition( x, y, z, null, null );
			}
			return new TargetPosition(
				x,
				y,
				z,
				(float)finite( Float.parseFloat( parts[3] ), text ),
				(float)finite( Float.parseFloat( parts[4] ), text )
			);
		} catch( NumberFormatException exception ) {
			throw invalid( text );
		}
	}
	
	private static double finite( double value, @NotNull String text ) {
		
		if( !Double.isFinite( value ) ) {
			throw invalid( text );
		}
		return value;
	}
	
	@NotNull
	private static IllegalArgumentException invalid( @NotNull String text ) {
		
		return new IllegalArgumentException(
			"Invalid position \"" + text + "\", expected \"x y z\" or \"x y z yaw pitch\""
		);
	}
	
	@NotNull
	public String format() {
		
		String position = number( x ) + " " + number( y ) + " " + number( z );
		if( yaw == null || pitch == null ) {
			return position;
		}
		return position + " " + number( yaw ) + " " + number( pitch );
	}
	
	@NotNull
	private static String number( double value ) {
		
		return BigDecimal.valueOf( value ).setScale( 2, RoundingMode.HALF_UP ).stripTrailingZeros().toPlainString();
	}
}
```

- [ ] **Step 5: `SettingsResolver` implementieren**

```java
package de.geheimagentnr1.player_time_manager.config;

import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.GeneralRaw;
import de.geheimagentnr1.player_time_manager.config.raw.PlayerRef;
import de.geheimagentnr1.player_time_manager.config.raw.RawSettings;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;
import de.geheimagentnr1.player_time_manager.config.resolved.DisplayMode;
import de.geheimagentnr1.player_time_manager.config.resolved.DisplaySettings;
import de.geheimagentnr1.player_time_manager.config.resolved.PlayerRules;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedDimension;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedScope;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedSettings;
import de.geheimagentnr1.player_time_manager.config.resolved.TargetSpec;
import de.geheimagentnr1.player_time_manager.cron.CronExpression;
import de.geheimagentnr1.player_time_manager.cron.CronParseException;
import de.geheimagentnr1.player_time_manager.time.DurationParser;
import de.geheimagentnr1.player_time_manager.time.RefillMode;
import de.geheimagentnr1.player_time_manager.time.RefillRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;


public final class SettingsResolver {
	
	
	@NotNull
	public static final String OVERWORLD = "minecraft:overworld";
	
	@NotNull
	public static final String THE_END = "minecraft:the_end";
	
	private SettingsResolver() {
	
	}
	
	@NotNull
	public static ResolvedSettings resolve(
		@NotNull RawSettings raw,
		@NotNull Set<String> knownDimensions,
		@NotNull ZoneId systemZone ) {
		
		List<String> problems = new ArrayList<>();
		GeneralRaw general = raw.general();
		ZoneId zone = resolveZone( general.timeZone(), systemZone, problems );
		RefillMode generalMode = parseMode( general.refillMode(), RefillMode.SET, "general.refill_mode", problems );
		CronExpression generalCron = parseCron( general.refillCron(), "general.refill_cron", problems );
		Long generalMax = parseMax( general.refillMax(), "general.refill_max", problems );
		RefillDefaults defaults = new RefillDefaults( generalCron, generalMode, generalMax );
		
		ResolvedScope world = resolveScope( "world", raw.world(), defaults, problems );
		Map<String, ResolvedDimension> dimensions = new LinkedHashMap<>();
		List<DimensionRaw> entries = raw.dimensions();
		for( int i = 0; i < entries.size(); i++ ) {
			DimensionRaw entry = entries.get( i );
			String id = entry.dimension().trim();
			String prefix = String.format( "dimensions[%d] (\"%s\")", i, id );
			if( id.isEmpty() ) {
				problems.add( prefix + ": dimension is empty - entry ignored" );
			} else if( id.equals( OVERWORLD ) ) {
				problems.add( prefix + ": the Overworld can't be limited here, use [world] - entry ignored" );
			} else if( dimensions.containsKey( id ) ) {
				problems.add( prefix + ": duplicate entry for " + id + ", the first one is used - entry ignored" );
			} else if( !knownDimensions.contains( id ) ) {
				problems.add( prefix + ": unknown dimension " + id + " - entry inactive" );
			} else {
				dimensions.put(
					id,
					new ResolvedDimension(
						id,
						resolveScope( prefix, entry.scope(), defaults, problems ),
						resolveTarget( prefix, id, entry, knownDimensions, problems )
					)
				);
			}
		}
		return new ResolvedSettings(
			general.commandPermissionLevel(),
			resolvePlayerRules( general, problems ),
			zone,
			resolveDisplay( general, problems ),
			world,
			Collections.unmodifiableMap( dimensions ),
			List.copyOf( problems )
		);
	}
	
	private record RefillDefaults( @Nullable CronExpression cron, @NotNull RefillMode mode, @Nullable Long max ) {
	
	}
	
	@NotNull
	private static ResolvedScope resolveScope(
		@NotNull String prefix,
		@NotNull ScopeRaw scope,
		@NotNull RefillDefaults defaults,
		@NotNull List<String> problems ) {
		
		if( !scope.enabled() ) {
			return ResolvedScope.INACTIVE;
		}
		long limit;
		try {
			limit = DurationParser.parse( scope.limit() );
		} catch( IllegalArgumentException exception ) {
			problems.add( prefix + ".limit: " + exception.getMessage() + " - time limit disabled" );
			return ResolvedScope.INACTIVE;
		}
		if( limit <= 0 ) {
			problems.add( prefix + ".limit: \"" + scope.limit() + "\" must be greater than 0 - time limit disabled" );
			return ResolvedScope.INACTIVE;
		}
		CronExpression cron = defaults.cron();
		RefillMode mode = defaults.mode();
		Long max = defaults.max();
		if( scope.useCustomRefill() ) {
			cron = parseCron( scope.refillCron(), prefix + ".refill_cron", problems );
			mode = parseMode( scope.refillMode(), defaults.mode(), prefix + ".refill_mode", problems );
			max = parseMax( scope.refillMax(), prefix + ".refill_max", problems );
		}
		return new ResolvedScope( true, limit, new RefillRule( cron, mode, limit, max ) );
	}
	
	@Nullable
	private static TargetSpec resolveTarget(
		@NotNull String prefix,
		@NotNull String id,
		@NotNull DimensionRaw entry,
		@NotNull Set<String> knownDimensions,
		@NotNull List<String> problems ) {
		
		String targetDimension = entry.targetDimension().isBlank() ? OVERWORLD : entry.targetDimension().trim();
		String fix = " Players are sent to the Overworld world spawn instead. Fix: /playtime config dimension " + id + " target here";
		if( !knownDimensions.contains( targetDimension ) ) {
			problems.add( prefix + ".target_dimension: unknown dimension " + targetDimension + "." + fix );
			return null;
		}
		if( entry.targetPosition().isBlank() ) {
			if( targetDimension.equals( OVERWORLD ) || targetDimension.equals( THE_END ) ) {
				return new TargetSpec( targetDimension, null );
			}
			problems.add( prefix + ": target_dimension " + targetDimension + " needs a target_position." + fix );
			return null;
		}
		try {
			return new TargetSpec( targetDimension, TargetPosition.parse( entry.targetPosition() ) );
		} catch( IllegalArgumentException exception ) {
			problems.add( prefix + ".target_position: " + exception.getMessage() + "." + fix );
			return null;
		}
	}
	
	@NotNull
	private static PlayerRules resolvePlayerRules( @NotNull GeneralRaw general, @NotNull List<String> problems ) {
		
		Set<UUID> limitedIds = new HashSet<>();
		Set<String> limitedNames = new HashSet<>();
		Set<UUID> exemptIds = new HashSet<>();
		Set<String> exemptNames = new HashSet<>();
		readPlayers( general.limitedPlayers(), "general.limited_players", limitedIds, limitedNames, problems );
		readPlayers( general.exemptPlayers(), "general.exempt_players", exemptIds, exemptNames, problems );
		return new PlayerRules(
			general.opsExempt(),
			general.pauseInCreative(),
			general.pauseInSpectator(),
			Set.copyOf( limitedIds ),
			Set.copyOf( limitedNames ),
			Set.copyOf( exemptIds ),
			Set.copyOf( exemptNames )
		);
	}
	
	private static void readPlayers(
		@NotNull List<PlayerRef> players,
		@NotNull String key,
		@NotNull Set<UUID> ids,
		@NotNull Set<String> names,
		@NotNull List<String> problems ) {
		
		for( PlayerRef player : players ) {
			String name = player.name().trim();
			String uuid = player.uuid().trim();
			if( !uuid.isEmpty() ) {
				try {
					ids.add( UUID.fromString( uuid ) );
					continue;
				} catch( IllegalArgumentException exception ) {
					problems.add( key + ": invalid uuid \"" + uuid + "\" for \"" + name + "\" - matching by name" );
				}
			}
			if( name.isEmpty() ) {
				problems.add( key + ": entry without name and uuid - entry ignored" );
			} else {
				names.add( name.toLowerCase( Locale.ROOT ) );
			}
		}
	}
	
	@NotNull
	private static DisplaySettings resolveDisplay( @NotNull GeneralRaw general, @NotNull List<String> problems ) {
		
		DisplayMode mode;
		try {
			mode = DisplayMode.valueOf( general.displayMode().trim().toUpperCase( Locale.ROOT ) );
		} catch( IllegalArgumentException exception ) {
			problems.add( "general.display_mode: unknown value \"" + general.displayMode() + "\" - using BOSSBARS" );
			mode = DisplayMode.BOSSBARS;
		}
		int yellow = general.bossbarYellowBelowPercent();
		int red = general.bossbarRedBelowPercent();
		if( red < 0 || yellow > 100 || red >= yellow ) {
			problems.add( "general.bossbar_yellow_below_percent/bossbar_red_below_percent: need 0 <= red < yellow <= 100 - using 50 and 10" );
			yellow = 50;
			red = 10;
		}
		TreeSet<Long> warningTimes = new TreeSet<>( Comparator.reverseOrder() );
		for( String warningTime : general.warningTimes() ) {
			try {
				long millis = DurationParser.parse( warningTime );
				if( millis > 0 ) {
					warningTimes.add( millis );
				}
			} catch( IllegalArgumentException exception ) {
				problems.add( "general.warning_times: " + exception.getMessage() + " - value ignored" );
			}
		}
		return new DisplaySettings( mode, yellow, red, List.copyOf( warningTimes ), general.warningSound() );
	}
	
	@NotNull
	private static ZoneId resolveZone( @NotNull String timeZone, @NotNull ZoneId systemZone, @NotNull List<String> problems ) {
		
		if( timeZone.isBlank() ) {
			return systemZone;
		}
		try {
			return ZoneId.of( timeZone.trim() );
		} catch( DateTimeException exception ) {
			problems.add( "general.time_zone: \"" + timeZone + "\" is not a valid time zone - using the time zone of the server" );
			return systemZone;
		}
	}
	
	@Nullable
	private static CronExpression parseCron( @NotNull String text, @NotNull String key, @NotNull List<String> problems ) {
		
		if( text.isBlank() ) {
			return null;
		}
		try {
			return CronExpression.parse( text );
		} catch( CronParseException exception ) {
			problems.add( key + ": " + exception.getMessage() + " - no automatic refill" );
			return null;
		}
	}
	
	@NotNull
	private static RefillMode parseMode(
		@NotNull String text,
		@NotNull RefillMode fallback,
		@NotNull String key,
		@NotNull List<String> problems ) {
		
		try {
			return RefillMode.valueOf( text.trim().toUpperCase( Locale.ROOT ) );
		} catch( IllegalArgumentException exception ) {
			problems.add( key + ": unknown value \"" + text + "\" (SET or ADD) - using " + fallback );
			return fallback;
		}
	}
	
	@Nullable
	private static Long parseMax( @NotNull String text, @NotNull String key, @NotNull List<String> problems ) {
		
		if( text.isBlank() ) {
			return null;
		}
		try {
			return DurationParser.parse( text );
		} catch( IllegalArgumentException exception ) {
			problems.add( key + ": " + exception.getMessage() + " - no maximum" );
			return null;
		}
	}
}
```

- [ ] **Step 6: Tests ausführen, sie müssen bestehen**

Run: `.\gradlew.bat test --tests "*TargetPositionTest" --tests "*SettingsResolverTest"`
Expected: PASS (3 + 9 Tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/de/geheimagentnr1/player_time_manager/config src/test/java/de/geheimagentnr1/player_time_manager/config
git commit -m "Add settings model and validation

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: TOML-Config und Laufzeit-Einstellungen

**Files:**
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/config/DimensionEntries.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/config/PlayerEntries.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/config/ServerConfig.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/config/SettingsHolder.java`
- Modify: `src/main/java/de/geheimagentnr1/player_time_manager/PlayerTimeManager.java`
- Test: `src/test/java/de/geheimagentnr1/player_time_manager/config/DimensionEntriesTest.java`, `PlayerEntriesTest.java`

**Interfaces:**
- Consumes: Records aus `config.raw` / `config.resolved`, `SettingsResolver` (Task 5), `RefillMode` (Task 4).
- Produces:
  - `DimensionEntries.COMMENTS` (`Map<String, String>`, Schlüssel in Ausgabereihenfolge), `static DimensionRaw fromValues( Map<String, ?> values )`, `static Map<String, Object> toValues( DimensionRaw dimension )`, `static List<DimensionRaw> defaults()`
  - `PlayerEntries.COMMENTS`, `static PlayerRef fromValues( Map<String, ?> values )`, `static Map<String, Object> toValues( PlayerRef player )`
  - `ServerConfig`: `ModConfigSpec getSpec()`, `RawSettings read()`, Setter (jeweils `set` + `save`): `setGeneralRefillCron( String )`, `setGeneralRefillMode( RefillMode )`, `setGeneralRefillMax( String )`, `setOpsExempt( boolean )`, `setWorldEnabled( boolean )`, `setWorldLimit( String )`, `setWorldUseCustomRefill( boolean )`, `setWorldRefillCron( String )`, `setWorldRefillMode( RefillMode )`, `setWorldRefillMax( String )`, `setDimensions( List<DimensionRaw> )`, `setLimitedPlayers( List<PlayerRef> )`, `setExemptPlayers( List<PlayerRef> )`; `static boolean isTable( Object )`
  - `SettingsHolder( ServerConfig config )`: `ServerConfig config()`, `boolean isLoaded()`, `ResolvedSettings current()`, `List<String> reload( MinecraftServer server )`

- [ ] **Step 1: Failing Tests schreiben**

`DimensionEntriesTest.java`:
```java
package de.geheimagentnr1.player_time_manager.config;

import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class DimensionEntriesTest {
	
	
	@Test
	void roundTripsAllValues() {
		
		DimensionRaw nether = new DimensionRaw(
			"minecraft:the_nether",
			new ScopeRaw( true, "30m", true, "0 8 * * 6,0", "ADD", "2h" ),
			"minecraft:overworld",
			"10 64 -5 90 0"
		);
		assertEquals( nether, DimensionEntries.fromValues( DimensionEntries.toValues( nether ) ) );
	}
	
	@Test
	void everyKeyHasAComment() {
		
		Map<String, Object> values = DimensionEntries.toValues( DimensionEntries.defaults().getFirst() );
		assertEquals( List.copyOf( DimensionEntries.COMMENTS.keySet() ), List.copyOf( values.keySet() ) );
		DimensionEntries.COMMENTS.values().forEach( comment -> assertFalse( comment.isBlank() ) );
	}
	
	@Test
	void missingAndWrongTypedValuesFallBackWithoutLosingTheEntry() {
		
		DimensionRaw raw = DimensionEntries.fromValues( Map.of( "dimension", "minecraft:the_end", "limit", 30, "enabled", "true" ) );
		assertEquals( "minecraft:the_end", raw.dimension() );
		assertEquals( "30", raw.scope().limit() );
		assertTrue( raw.scope().enabled() );
		assertEquals( "minecraft:overworld", raw.targetDimension() );
		assertEquals( "", raw.targetPosition() );
		assertEquals( "SET", raw.scope().refillMode() );
	}
	
	@Test
	void defaultsAreDisabledNetherAndEnd() {
		
		List<DimensionRaw> defaults = DimensionEntries.defaults();
		assertEquals( List.of( "minecraft:the_nether", "minecraft:the_end" ), defaults.stream().map( DimensionRaw::dimension ).toList() );
		defaults.forEach( dimension -> assertFalse( dimension.scope().enabled() ) );
	}
}
```

`PlayerEntriesTest.java`:
```java
package de.geheimagentnr1.player_time_manager.config;

import de.geheimagentnr1.player_time_manager.config.raw.PlayerRef;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class PlayerEntriesTest {
	
	
	@Test
	void roundTripsAndReadsNameOnlyEntries() {
		
		PlayerRef steve = new PlayerRef( "Steve", "8667ba71-b85a-4004-af54-457a9734eed7" );
		assertEquals( steve, PlayerEntries.fromValues( PlayerEntries.toValues( steve ) ) );
		assertEquals( new PlayerRef( "Alex", "" ), PlayerEntries.fromValues( Map.of( "name", "Alex" ) ) );
	}
	
	@Test
	void tablesAreAcceptedRegardlessOfContent() {
		
		assertTrue( ServerConfig.isTable( com.electronwill.nightconfig.core.Config.inMemory() ) );
		assertFalse( ServerConfig.isTable( "minecraft:the_nether" ) );
	}
}
```

> Hinweis: `tablesAreAcceptedRegardlessOfContent` braucht NightConfig zur Testlaufzeit. Schlägt der Test mit `NoClassDefFoundError: com/electronwill/nightconfig/core/Config` fehl, diesen einen Test entfernen und den Punkt im Ingame-Test (Task 14, Schritt „Tippfehler in der Config“) prüfen - alle anderen Tests bleiben ohne Minecraft-/NightConfig-Klassen.

- [ ] **Step 2: Tests ausführen, sie müssen fehlschlagen**

Run: `.\gradlew.bat test --tests "*DimensionEntriesTest" --tests "*PlayerEntriesTest"`
Expected: FAIL beim Kompilieren (`cannot find symbol: class DimensionEntries`).

- [ ] **Step 3: `DimensionEntries` und `PlayerEntries` implementieren**

```java
package de.geheimagentnr1.player_time_manager.config;

import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class DimensionEntries {
	
	
	@NotNull
	public static final Map<String, String> COMMENTS;
	
	static {
		Map<String, String> comments = new LinkedHashMap<>();
		comments.put( "dimension", "Name of the dimension, e.g. \"minecraft:the_nether\" or \"modid:dimension\"" );
		comments.put( "enabled", "Is the time limit for this dimension active?" );
		comments.put( "limit", "Time per refill, e.g. \"2h\", \"1h30m\", \"45m\"" );
		comments.put( "use_custom_refill", "true = use the refill settings below, false = use the refill settings from [general]" );
		comments.put( "refill_cron", "Refill schedule in cron format (minute hour day month weekday), see https://crontab.guru (\"\" = no automatic refill)" );
		comments.put( "refill_mode", "SET = set the remaining time to the limit, ADD = add the limit to the remaining time" );
		comments.put( "refill_max", "Maximum remaining time after an ADD refill, e.g. \"4h\" (\"\" = no maximum)" );
		comments.put( "target_dimension", "Dimension players are sent to when their time in this dimension is used up" );
		comments.put( "target_position", "Target position \"x y z\" or \"x y z yaw pitch\" (\"\" = world spawn in the Overworld, obsidian platform in the End; required for all other dimensions)" );
		COMMENTS = Collections.unmodifiableMap( comments );
	}
	
	private DimensionEntries() {
	
	}
	
	@NotNull
	public static DimensionRaw fromValues( @NotNull Map<String, ?> values ) {
		
		return new DimensionRaw(
			string( values, "dimension", "" ),
			new ScopeRaw(
				bool( values, "enabled", false ),
				string( values, "limit", "1h" ),
				bool( values, "use_custom_refill", false ),
				string( values, "refill_cron", "0 6 * * *" ),
				string( values, "refill_mode", "SET" ),
				string( values, "refill_max", "" )
			),
			string( values, "target_dimension", SettingsResolver.OVERWORLD ),
			string( values, "target_position", "" )
		);
	}
	
	@NotNull
	public static Map<String, Object> toValues( @NotNull DimensionRaw dimension ) {
		
		Map<String, Object> values = new LinkedHashMap<>();
		values.put( "dimension", dimension.dimension() );
		values.put( "enabled", dimension.scope().enabled() );
		values.put( "limit", dimension.scope().limit() );
		values.put( "use_custom_refill", dimension.scope().useCustomRefill() );
		values.put( "refill_cron", dimension.scope().refillCron() );
		values.put( "refill_mode", dimension.scope().refillMode() );
		values.put( "refill_max", dimension.scope().refillMax() );
		values.put( "target_dimension", dimension.targetDimension() );
		values.put( "target_position", dimension.targetPosition() );
		return values;
	}
	
	@NotNull
	public static List<DimensionRaw> defaults() {
		
		return List.of(
			new DimensionRaw( "minecraft:the_nether", new ScopeRaw( false, "1h", false, "0 6 * * *", "SET", "" ), SettingsResolver.OVERWORLD, "" ),
			new DimensionRaw( SettingsResolver.THE_END, new ScopeRaw( false, "1h", false, "0 6 * * *", "SET", "" ), SettingsResolver.OVERWORLD, "" )
		);
	}
	
	@NotNull
	static String string( @NotNull Map<String, ?> values, @NotNull String key, @NotNull String defaultValue ) {
		
		Object value = values.get( key );
		return value == null ? defaultValue : String.valueOf( value );
	}
	
	static boolean bool( @NotNull Map<String, ?> values, @NotNull String key, boolean defaultValue ) {
		
		Object value = values.get( key );
		if( value instanceof Boolean booleanValue ) {
			return booleanValue;
		}
		if( value instanceof String text && ( text.equalsIgnoreCase( "true" ) || text.equalsIgnoreCase( "false" ) ) ) {
			return Boolean.parseBoolean( text );
		}
		return defaultValue;
	}
}
```

```java
package de.geheimagentnr1.player_time_manager.config;

import de.geheimagentnr1.player_time_manager.config.raw.PlayerRef;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;


public final class PlayerEntries {
	
	
	@NotNull
	public static final Map<String, String> COMMENTS = Map.of(
		"name", "Player name (only used to show the entry and to fill in the uuid on the next login)",
		"uuid", "Player uuid (\"\" = filled in automatically on the next login of a player with this name)"
	);
	
	private PlayerEntries() {
	
	}
	
	@NotNull
	public static PlayerRef fromValues( @NotNull Map<String, ?> values ) {
		
		return new PlayerRef( DimensionEntries.string( values, "name", "" ), DimensionEntries.string( values, "uuid", "" ) );
	}
	
	@NotNull
	public static Map<String, Object> toValues( @NotNull PlayerRef player ) {
		
		Map<String, Object> values = new LinkedHashMap<>();
		values.put( "name", player.name() );
		values.put( "uuid", player.uuid() );
		return values;
	}
}
```

- [ ] **Step 4: `ServerConfig` implementieren**

```java
package de.geheimagentnr1.player_time_manager.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.GeneralRaw;
import de.geheimagentnr1.player_time_manager.config.raw.PlayerRef;
import de.geheimagentnr1.player_time_manager.config.raw.RawSettings;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;
import de.geheimagentnr1.player_time_manager.config.resolved.DisplayMode;
import de.geheimagentnr1.player_time_manager.time.RefillMode;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.Function;


public class ServerConfig {
	
	
	private static final String CRON_COMMENT =
		"Refill schedule in cron format (minute hour day month weekday), see https://crontab.guru (\"\" = no automatic refill)";
	
	private static final String MODE_COMMENT =
		"SET = set the remaining time to the limit, ADD = add the limit to the remaining time";
	
	private static final String MAX_COMMENT = "Maximum remaining time after an ADD refill, e.g. \"4h\" (\"\" = no maximum)";
	
	@NotNull
	private final ModConfigSpec spec;
	
	private final ModConfigSpec.IntValue commandPermissionLevel;
	
	private final ModConfigSpec.BooleanValue opsExempt;
	
	private final ModConfigSpec.BooleanValue pauseInCreative;
	
	private final ModConfigSpec.BooleanValue pauseInSpectator;
	
	private final ModConfigSpec.ConfigValue<List<? extends UnmodifiableConfig>> limitedPlayers;
	
	private final ModConfigSpec.ConfigValue<List<? extends UnmodifiableConfig>> exemptPlayers;
	
	private final ModConfigSpec.ConfigValue<String> timeZone;
	
	private final ModConfigSpec.ConfigValue<String> refillCron;
	
	private final ModConfigSpec.EnumValue<RefillMode> refillMode;
	
	private final ModConfigSpec.ConfigValue<String> refillMax;
	
	private final ModConfigSpec.EnumValue<DisplayMode> displayMode;
	
	private final ModConfigSpec.IntValue bossbarYellowBelowPercent;
	
	private final ModConfigSpec.IntValue bossbarRedBelowPercent;
	
	private final ModConfigSpec.ConfigValue<List<? extends String>> warningTimes;
	
	private final ModConfigSpec.BooleanValue warningSound;
	
	private final ModConfigSpec.BooleanValue worldEnabled;
	
	private final ModConfigSpec.ConfigValue<String> worldLimit;
	
	private final ModConfigSpec.BooleanValue worldUseCustomRefill;
	
	private final ModConfigSpec.ConfigValue<String> worldRefillCron;
	
	private final ModConfigSpec.EnumValue<RefillMode> worldRefillMode;
	
	private final ModConfigSpec.ConfigValue<String> worldRefillMax;
	
	private final ModConfigSpec.ConfigValue<List<? extends UnmodifiableConfig>> dimensions;
	
	public ServerConfig() {
		
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		
		builder.push( "general" );
		commandPermissionLevel = builder.comment( "Permission level (0 - 4) required for all admin commands" )
			.defineInRange( "command_permission_level", 3, 0, 4 );
		opsExempt = builder.comment( "Operators are not affected by any time limit (set to false for singleplayer worlds with cheats)" )
			.define( "ops_exempt", true );
		pauseInCreative = builder.comment( "Time does not count down while a player is in creative mode" )
			.define( "pause_in_creative", true );
		pauseInSpectator = builder.comment( "Time does not count down while a player is in spectator mode" )
			.define( "pause_in_spectator", true );
		limitedPlayers = builder.comment( "If not empty, ONLY these players are affected by the time limits. Entries: name and uuid" )
			.defineListAllowEmpty( "limited_players", List::of, ServerConfig::isTable );
		exemptPlayers = builder.comment( "These players are never affected by the time limits (has priority over limited_players). Entries: name and uuid" )
			.defineListAllowEmpty( "exempt_players", List::of, ServerConfig::isTable );
		timeZone = builder.comment( "Time zone of the refill schedules, e.g. \"Europe/Berlin\" (\"\" = system time zone of the server)" )
			.define( "time_zone", "" );
		refillCron = builder.comment(
				"Default refill schedule in cron format (minute hour day month weekday), e.g. \"0 6 * * *\" = daily at 06:00.",
				"Generator and explanation: https://crontab.guru (\"\" = no automatic refill)"
			)
			.define( "refill_cron", "0 6 * * *" );
		refillMode = builder.comment( "Default refill mode: " + MODE_COMMENT ).defineEnum( "refill_mode", RefillMode.SET );
		refillMax = builder.comment( "Default maximum remaining time after an ADD refill, e.g. \"4h\" (\"\" = no maximum)" )
			.define( "refill_max", "" );
		displayMode = builder.comment(
				"How players see their remaining time: NONE = only via the command, ACTIONBAR = text above the hotbar,",
				"BOSSBARS = one bossbar per running limit, BOSSBAR_SHORTEST = one bossbar with the limit that runs out first"
			)
			.defineEnum( "display_mode", DisplayMode.BOSSBARS );
		bossbarYellowBelowPercent = builder.comment( "The bossbar turns yellow below this percentage of the limit (0 - 100)" )
			.defineInRange( "bossbar_yellow_below_percent", 50, 0, 100 );
		bossbarRedBelowPercent = builder.comment( "The bossbar turns red below this percentage of the limit (0 - 100, lower than the yellow value)" )
			.defineInRange( "bossbar_red_below_percent", 10, 0, 100 );
		warningTimes = builder.comment( "Remaining times that trigger a chat warning, e.g. [\"10m\", \"1m\", \"10s\"] (empty list = no chat warnings)" )
			.defineListAllowEmpty( "warning_times", () -> List.of( "10m", "5m", "1m", "10s" ), value -> value instanceof String );
		warningSound = builder.comment( "Play a sound with each chat warning" ).define( "warning_sound", true );
		builder.pop();
		
		builder.push( "world" );
		worldEnabled = builder.comment( "Is the time limit for the whole world (all dimensions together) active?" )
			.define( "enabled", false );
		worldLimit = builder.comment( "Time per refill, e.g. \"2h\", \"1h30m\", \"45m\"" ).define( "limit", "2h" );
		worldUseCustomRefill = builder.comment( "true = use the refill settings below, false = use the refill settings from [general]" )
			.define( "use_custom_refill", false );
		worldRefillCron = builder.comment( CRON_COMMENT ).define( "refill_cron", "0 6 * * *" );
		worldRefillMode = builder.comment( MODE_COMMENT ).defineEnum( "refill_mode", RefillMode.SET );
		worldRefillMax = builder.comment( MAX_COMMENT ).define( "refill_max", "" );
		builder.pop();
		
		dimensions = builder.comment( "Time limits of single dimensions (the Overworld can't be limited here, use [world] instead)" )
			.defineListAllowEmpty(
				"dimensions",
				() -> DimensionEntries.defaults().stream().map( entry -> toConfig( DimensionEntries.toValues( entry ), DimensionEntries.COMMENTS ) ).toList(),
				ServerConfig::isTable
			);
		
		spec = builder.build();
	}
	
	// Only checks that the element is a table: a stricter validator would make NeoForge delete entries with typos
	public static boolean isTable( Object value ) {
		
		return value instanceof UnmodifiableConfig;
	}
	
	@NotNull
	private static CommentedConfig toConfig( @NotNull Map<String, Object> values, @NotNull Map<String, String> comments ) {
		
		CommentedConfig config = CommentedConfig.inMemory();
		values.forEach( ( key, value ) -> {
			config.set( key, value );
			String comment = comments.get( key );
			if( comment != null ) {
				config.setComment( key, comment );
			}
		} );
		return config;
	}
	
	@NotNull
	private static <T> List<T> fromConfigs(
		@NotNull List<? extends UnmodifiableConfig> configs,
		@NotNull Function<Map<String, ?>, T> converter ) {
		
		return configs.stream().map( config -> converter.apply( config.valueMap() ) ).toList();
	}
	
	@NotNull
	public ModConfigSpec getSpec() {
		
		return spec;
	}
	
	@NotNull
	public RawSettings read() {
		
		return new RawSettings(
			new GeneralRaw(
				commandPermissionLevel.get(),
				opsExempt.get(),
				pauseInCreative.get(),
				pauseInSpectator.get(),
				fromConfigs( limitedPlayers.get(), PlayerEntries::fromValues ),
				fromConfigs( exemptPlayers.get(), PlayerEntries::fromValues ),
				timeZone.get(),
				refillCron.get(),
				refillMode.get().name(),
				refillMax.get(),
				displayMode.get().name(),
				bossbarYellowBelowPercent.get(),
				bossbarRedBelowPercent.get(),
				List.copyOf( warningTimes.get() ),
				warningSound.get()
			),
			new ScopeRaw(
				worldEnabled.get(),
				worldLimit.get(),
				worldUseCustomRefill.get(),
				worldRefillCron.get(),
				worldRefillMode.get().name(),
				worldRefillMax.get()
			),
			fromConfigs( dimensions.get(), DimensionEntries::fromValues )
		);
	}
	
	private static <T> void setAndSave( @NotNull ModConfigSpec.ConfigValue<T> configValue, @NotNull T value ) {
		
		configValue.set( value );
		configValue.save();
	}
	
	public void setGeneralRefillCron( @NotNull String value ) {
		
		setAndSave( refillCron, value );
	}
	
	public void setGeneralRefillMode( @NotNull RefillMode value ) {
		
		setAndSave( refillMode, value );
	}
	
	public void setGeneralRefillMax( @NotNull String value ) {
		
		setAndSave( refillMax, value );
	}
	
	public void setOpsExempt( boolean value ) {
		
		setAndSave( opsExempt, value );
	}
	
	public void setWorldEnabled( boolean value ) {
		
		setAndSave( worldEnabled, value );
	}
	
	public void setWorldLimit( @NotNull String value ) {
		
		setAndSave( worldLimit, value );
	}
	
	public void setWorldUseCustomRefill( boolean value ) {
		
		setAndSave( worldUseCustomRefill, value );
	}
	
	public void setWorldRefillCron( @NotNull String value ) {
		
		setAndSave( worldRefillCron, value );
	}
	
	public void setWorldRefillMode( @NotNull RefillMode value ) {
		
		setAndSave( worldRefillMode, value );
	}
	
	public void setWorldRefillMax( @NotNull String value ) {
		
		setAndSave( worldRefillMax, value );
	}
	
	public void setDimensions( @NotNull List<DimensionRaw> value ) {
		
		setAndSave( dimensions, value.stream().map( entry -> toConfig( DimensionEntries.toValues( entry ), DimensionEntries.COMMENTS ) ).toList() );
	}
	
	public void setLimitedPlayers( @NotNull List<PlayerRef> value ) {
		
		setAndSave( limitedPlayers, value.stream().map( entry -> toConfig( PlayerEntries.toValues( entry ), PlayerEntries.COMMENTS ) ).toList() );
	}
	
	public void setExemptPlayers( @NotNull List<PlayerRef> value ) {
		
		setAndSave( exemptPlayers, value.stream().map( entry -> toConfig( PlayerEntries.toValues( entry ), PlayerEntries.COMMENTS ) ).toList() );
	}
}
```

> Falls `setAndSave( dimensions, … )` wegen der Wildcard-Typen nicht kompiliert: lokale Variable `List<UnmodifiableConfig> configs = …` anlegen und `dimensions.set( configs ); dimensions.save();` direkt aufrufen (gleiches Verhalten).

- [ ] **Step 5: `SettingsHolder` implementieren**

```java
package de.geheimagentnr1.player_time_manager.config;

import com.mojang.logging.LogUtils;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedSettings;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class SettingsHolder {
	
	
	@NotNull
	private static final Logger LOGGER = LogUtils.getLogger();
	
	@NotNull
	private final ServerConfig config;
	
	@Nullable
	private volatile ResolvedSettings current;
	
	public SettingsHolder( @NotNull ServerConfig _config ) {
		
		config = _config;
	}
	
	@NotNull
	public ServerConfig config() {
		
		return config;
	}
	
	public boolean isLoaded() {
		
		return current != null;
	}
	
	@NotNull
	public ResolvedSettings current() {
		
		ResolvedSettings settings = current;
		if( settings == null ) {
			throw new IllegalStateException( "Player Time Manager settings are not loaded yet" );
		}
		return settings;
	}
	
	@NotNull
	public List<String> reload( @NotNull MinecraftServer server ) {
		
		Set<String> knownDimensions = server.levelKeys().stream()
			.map( key -> key.location().toString() )
			.collect( Collectors.toSet() );
		ResolvedSettings settings = SettingsResolver.resolve( config.read(), knownDimensions, ZoneId.systemDefault() );
		current = settings;
		settings.problems().forEach( problem -> LOGGER.warn( "Player Time Manager config: {}", problem ) );
		LOGGER.info( "Player Time Manager settings loaded ({} problem(s))", settings.problems().size() );
		return settings.problems();
	}
}
```

- [ ] **Step 6: Config in der Mod-Klasse registrieren**

In `PlayerTimeManager.java` den Konstruktor ersetzen und Imports `net.neoforged.fml.config.ModConfig`, `de.geheimagentnr1.player_time_manager.config.ServerConfig`, `de.geheimagentnr1.player_time_manager.config.SettingsHolder` ergänzen:
```java
	public PlayerTimeManager( @NotNull IEventBus modEventBus, @NotNull ModContainer modContainer ) {
		
		ServerConfig serverConfig = new ServerConfig();
		modContainer.registerConfig( ModConfig.Type.SERVER, serverConfig.getSpec() );
		SettingsHolder settings = new SettingsHolder( serverConfig );
	}
```
(`settings` wird ab Task 12 an die Handler übergeben.)

- [ ] **Step 7: Tests und Build**

Run: `.\gradlew.bat test --tests "*DimensionEntriesTest" --tests "*PlayerEntriesTest"` und danach `.\gradlew.bat build`
Expected: PASS (4 + 2 Tests), `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add -A src
git commit -m "Add TOML config with dimension list

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Restzeiten als Level-Attachments

**Files:**
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/time/Scope.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/data/TimeData.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/data/ModAttachmentTypes.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/data/TimeStore.java`
- Modify: `src/main/java/de/geheimagentnr1/player_time_manager/PlayerTimeManager.java`
- Test: `src/test/java/de/geheimagentnr1/player_time_manager/time/ScopeTest.java`

**Interfaces:**
- Consumes: `TimeEntry` (Task 4).
- Produces:
  - `record Scope( @Nullable String dimension )` mit `static final Scope WORLD`, `static Scope dimension( String id )`, `boolean isWorld()`
  - `TimeData` (veränderlich): `Optional<TimeEntry> get( UUID )`, `void put( UUID, TimeEntry )`, `Map<UUID, TimeEntry> entries()` (unveränderliche Sicht), `static final Codec<TimeData> CODEC`, `static final Codec<TimeEntry> ENTRY_CODEC`
  - `ModAttachmentTypes.ATTACHMENT_TYPES` (`DeferredRegister`), `WORLD_TIME_DATA`, `DIMENSION_TIME_DATA` (`Supplier<AttachmentType<TimeData>>`)
  - `TimeStore.data( MinecraftServer server, Scope scope )` -> `@Nullable TimeData` (Welt: Attachment an der Overworld; Dimension: am Level, `null` wenn das Level fehlt)

- [ ] **Step 1: Failing Test schreiben**

```java
package de.geheimagentnr1.player_time_manager.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ScopeTest {
	
	
	@Test
	void worldAndDimensionScopes() {
		
		assertTrue( Scope.WORLD.isWorld() );
		assertFalse( Scope.dimension( "minecraft:the_nether" ).isWorld() );
		assertEquals( Scope.dimension( "minecraft:the_nether" ), Scope.dimension( "minecraft:the_nether" ) );
	}
}
```

- [ ] **Step 2: Test ausführen, er muss fehlschlagen**

Run: `.\gradlew.bat test --tests "*ScopeTest"`
Expected: FAIL beim Kompilieren (`cannot find symbol: class Scope`).

- [ ] **Step 3: Implementierung**

`time/Scope.java`:
```java
package de.geheimagentnr1.player_time_manager.time;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public record Scope( @Nullable String dimension ) {
	
	
	@NotNull
	public static final Scope WORLD = new Scope( null );
	
	@NotNull
	public static Scope dimension( @NotNull String id ) {
		
		return new Scope( id );
	}
	
	public boolean isWorld() {
		
		return dimension == null;
	}
}
```

`data/TimeData.java`:
```java
package de.geheimagentnr1.player_time_manager.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.geheimagentnr1.player_time_manager.time.TimeEntry;
import net.minecraft.core.UUIDUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


public class TimeData {
	
	
	@NotNull
	public static final Codec<TimeEntry> ENTRY_CODEC = RecordCodecBuilder.create( instance -> instance.group(
		Codec.LONG.fieldOf( "remaining" ).forGetter( TimeEntry::remainingMillis ),
		Codec.LONG.fieldOf( "last_refill" ).forGetter( TimeEntry::lastRefillMillis ),
		Codec.STRING.optionalFieldOf( "schedule", "" ).forGetter( TimeEntry::scheduleId )
	).apply( instance, TimeEntry::new ) );
	
	@NotNull
	public static final Codec<TimeData> CODEC = Codec.unboundedMap( UUIDUtil.STRING_CODEC, ENTRY_CODEC )
		.xmap( TimeData::new, TimeData::entries );
	
	@NotNull
	private final Map<UUID, TimeEntry> entries;
	
	public TimeData() {
		
		this( Map.of() );
	}
	
	private TimeData( @NotNull Map<UUID, TimeEntry> _entries ) {
		
		entries = new HashMap<>( _entries );
	}
	
	@NotNull
	public Optional<TimeEntry> get( @NotNull UUID player ) {
		
		return Optional.ofNullable( entries.get( player ) );
	}
	
	public void put( @NotNull UUID player, @NotNull TimeEntry entry ) {
		
		entries.put( player, entry );
	}
	
	@NotNull
	public Map<UUID, TimeEntry> entries() {
		
		return Collections.unmodifiableMap( entries );
	}
}
```

`data/ModAttachmentTypes.java`:
```java
package de.geheimagentnr1.player_time_manager.data;

import de.geheimagentnr1.player_time_manager.PlayerTimeManager;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;


public final class ModAttachmentTypes {
	
	
	@NotNull
	public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
		DeferredRegister.create( NeoForgeRegistries.ATTACHMENT_TYPES, PlayerTimeManager.MODID );
	
	// Remaining world time of all players, only attached to the Overworld
	@NotNull
	public static final Supplier<AttachmentType<TimeData>> WORLD_TIME_DATA = ATTACHMENT_TYPES.register(
		"world_time_data",
		() -> AttachmentType.builder( () -> new TimeData() ).serialize( TimeData.CODEC ).build()
	);
	
	// Remaining time of all players in the dimension the attachment belongs to
	@NotNull
	public static final Supplier<AttachmentType<TimeData>> DIMENSION_TIME_DATA = ATTACHMENT_TYPES.register(
		"dimension_time_data",
		() -> AttachmentType.builder( () -> new TimeData() ).serialize( TimeData.CODEC ).build()
	);
	
	private ModAttachmentTypes() {
	
	}
}
```

`data/TimeStore.java`:
```java
package de.geheimagentnr1.player_time_manager.data;

import de.geheimagentnr1.player_time_manager.time.Scope;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class TimeStore {
	
	
	private TimeStore() {
	
	}
	
	// NeoForge 21.1 saves level attachments on every level save, changing the TimeData object is enough
	@Nullable
	public static TimeData data( @NotNull MinecraftServer server, @NotNull Scope scope ) {
		
		if( scope.isWorld() ) {
			return server.overworld().getData( ModAttachmentTypes.WORLD_TIME_DATA );
		}
		ServerLevel level = level( server, scope.dimension() );
		return level == null ? null : level.getData( ModAttachmentTypes.DIMENSION_TIME_DATA );
	}
	
	@Nullable
	public static ServerLevel level( @NotNull MinecraftServer server, @NotNull String dimension ) {
		
		ResourceLocation location = ResourceLocation.tryParse( dimension );
		return location == null ? null : server.getLevel( ResourceKey.create( Registries.DIMENSION, location ) );
	}
}
```

- [ ] **Step 4: Registrierung in der Mod-Klasse**

Im Konstruktor von `PlayerTimeManager` nach `registerConfig` ergänzen (Import `de.geheimagentnr1.player_time_manager.data.ModAttachmentTypes`):
```java
		ModAttachmentTypes.ATTACHMENT_TYPES.register( modEventBus );
```

- [ ] **Step 5: Test und Build**

Run: `.\gradlew.bat test --tests "*ScopeTest"` und `.\gradlew.bat build`
Expected: PASS, `BUILD SUCCESSFUL`. (Speichern und Laden der Attachments wird in Task 14 ingame geprüft: Restzeit setzen, Neustart, Restzeit abfragen.)

- [ ] **Step 6: Commit**

```bash
git add -A src
git commit -m "Store remaining times as level attachments

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Ausnahmen, Warnschwellen und Zählintervall

**Files:**
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/time/TimeState.java`, `PlayerFacts.java`, `ExemptionChecker.java`, `TimeAccounting.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/display/WarningTracker.java`
- Test: `src/test/java/de/geheimagentnr1/player_time_manager/time/ExemptionCheckerTest.java`, `TimeAccountingTest.java`, `src/test/java/de/geheimagentnr1/player_time_manager/display/WarningTrackerTest.java`

**Interfaces:**
- Consumes: `PlayerRules` (Task 5).
- Produces:
  - `enum TimeState { EXCLUDED, PAUSED, COUNTING }` - `EXCLUDED`: Limits gelten nicht (keine Anzeige, keine Sperren); `PAUSED`: Anzeige „pausiert“, keine Zählung, keine Sperren; `COUNTING`: zählt und wird gesperrt
  - `record PlayerFacts( UUID uuid, String name, boolean op, boolean creative, boolean spectator )`
  - `static TimeState ExemptionChecker.state( PlayerFacts facts, PlayerRules rules )`
  - `TimeAccounting.MAX_COUNTED_GAP_MILLIS = 10_000`, `static long TimeAccounting.elapsedToCount( long lastMillis, long nowMillis )` (`lastMillis < 0` oder Lücke > 10 s oder negativ -> 0)
  - `static Optional<Long> WarningTracker.crossedThreshold( long before, long after, List<Long> thresholdsDescending )` - kleinste Schwelle `t` mit `before > t >= after`

- [ ] **Step 1: Failing Tests schreiben**

`ExemptionCheckerTest.java`:
```java
package de.geheimagentnr1.player_time_manager.time;

import de.geheimagentnr1.player_time_manager.config.resolved.PlayerRules;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;


class ExemptionCheckerTest {
	
	
	private static final UUID STEVE = UUID.fromString( "8667ba71-b85a-4004-af54-457a9734eed7" );
	
	private static final UUID ALEX = UUID.fromString( "ec561538-f3fd-461d-aff5-086b22154bce" );
	
	private static PlayerRules rules( boolean opsExempt, Set<UUID> limited, Set<String> limitedNames, Set<UUID> exempt, Set<String> exemptNames ) {
		
		return new PlayerRules( opsExempt, true, true, limited, limitedNames, exempt, exemptNames );
	}
	
	private static PlayerRules defaults() {
		
		return rules( true, Set.of(), Set.of(), Set.of(), Set.of() );
	}
	
	private static PlayerFacts steve( boolean op, boolean creative, boolean spectator ) {
		
		return new PlayerFacts( STEVE, "Steve", op, creative, spectator );
	}
	
	@Test
	void normalPlayersCount() {
		
		assertEquals( TimeState.COUNTING, ExemptionChecker.state( steve( false, false, false ), defaults() ) );
	}
	
	@Test
	void opsAreExcludedOnlyIfConfigured() {
		
		assertEquals( TimeState.EXCLUDED, ExemptionChecker.state( steve( true, false, false ), defaults() ) );
		assertEquals( TimeState.COUNTING, ExemptionChecker.state( steve( true, false, false ), rules( false, Set.of(), Set.of(), Set.of(), Set.of() ) ) );
	}
	
	@Test
	void exemptListByUuidOrNameWins() {
		
		assertEquals( TimeState.EXCLUDED, ExemptionChecker.state( steve( false, false, false ), rules( false, Set.of( STEVE ), Set.of(), Set.of( STEVE ), Set.of() ) ) );
		assertEquals( TimeState.EXCLUDED, ExemptionChecker.state( steve( false, false, false ), rules( false, Set.of(), Set.of(), Set.of(), Set.of( "steve" ) ) ) );
	}
	
	@Test
	void limitedListRestrictsToListedPlayers() {
		
		assertEquals( TimeState.EXCLUDED, ExemptionChecker.state( steve( false, false, false ), rules( false, Set.of( ALEX ), Set.of(), Set.of(), Set.of() ) ) );
		assertEquals( TimeState.COUNTING, ExemptionChecker.state( steve( false, false, false ), rules( false, Set.of(), Set.of( "steve" ), Set.of(), Set.of() ) ) );
	}
	
	@Test
	void creativeAndSpectatorPause() {
		
		assertEquals( TimeState.PAUSED, ExemptionChecker.state( steve( false, true, false ), defaults() ) );
		assertEquals( TimeState.PAUSED, ExemptionChecker.state( steve( false, false, true ), defaults() ) );
		PlayerRules noPause = new PlayerRules( true, false, false, Set.of(), Set.of(), Set.of(), Set.of() );
		assertEquals( TimeState.COUNTING, ExemptionChecker.state( steve( false, true, false ), noPause ) );
	}
}
```

`TimeAccountingTest.java`:
```java
package de.geheimagentnr1.player_time_manager.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


class TimeAccountingTest {
	
	
	@Test
	void countsNormalIntervalsAndDropsGaps() {
		
		assertEquals( 0, TimeAccounting.elapsedToCount( -1, 5_000 ) );
		assertEquals( 1_000, TimeAccounting.elapsedToCount( 4_000, 5_000 ) );
		assertEquals( 10_000, TimeAccounting.elapsedToCount( 0, 10_000 ) );
		assertEquals( 0, TimeAccounting.elapsedToCount( 0, 10_001 ) );
		assertEquals( 0, TimeAccounting.elapsedToCount( 5_000, 4_000 ) );
	}
}
```

`WarningTrackerTest.java`:
```java
package de.geheimagentnr1.player_time_manager.display;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;


class WarningTrackerTest {
	
	
	private static final List<Long> THRESHOLDS = List.of( 600_000L, 300_000L, 60_000L, 10_000L );
	
	@Test
	void warnsOnceWhenCrossing() {
		
		assertEquals( Optional.of( 300_000L ), WarningTracker.crossedThreshold( 300_500, 299_500, THRESHOLDS ) );
		assertEquals( Optional.of( 300_000L ), WarningTracker.crossedThreshold( 301_000, 300_000, THRESHOLDS ) );
		assertEquals( Optional.empty(), WarningTracker.crossedThreshold( 299_500, 298_500, THRESHOLDS ) );
	}
	
	@Test
	void warnsLowestThresholdWhenSeveralAreCrossed() {
		
		assertEquals( Optional.of( 60_000L ), WarningTracker.crossedThreshold( 700_000, 30_000, THRESHOLDS ) );
	}
	
	@Test
	void increasesNeverWarn() {
		
		assertEquals( Optional.empty(), WarningTracker.crossedThreshold( 30_000, 700_000, THRESHOLDS ) );
		assertEquals( Optional.empty(), WarningTracker.crossedThreshold( 30_000, 30_000, List.of() ) );
	}
}
```

- [ ] **Step 2: Tests ausführen, sie müssen fehlschlagen**

Run: `.\gradlew.bat test --tests "*ExemptionCheckerTest" --tests "*TimeAccountingTest" --tests "*WarningTrackerTest"`
Expected: FAIL beim Kompilieren.

- [ ] **Step 3: Implementierung**

`time/TimeState.java`:
```java
package de.geheimagentnr1.player_time_manager.time;


public enum TimeState {
	EXCLUDED,
	PAUSED,
	COUNTING
}
```

`time/PlayerFacts.java`:
```java
package de.geheimagentnr1.player_time_manager.time;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;


public record PlayerFacts( @NotNull UUID uuid, @NotNull String name, boolean op, boolean creative, boolean spectator ) {

}
```

`time/ExemptionChecker.java`:
```java
package de.geheimagentnr1.player_time_manager.time;

import de.geheimagentnr1.player_time_manager.config.resolved.PlayerRules;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;


public final class ExemptionChecker {
	
	
	private ExemptionChecker() {
	
	}
	
	@NotNull
	public static TimeState state( @NotNull PlayerFacts facts, @NotNull PlayerRules rules ) {
		
		String name = facts.name().toLowerCase( Locale.ROOT );
		if( rules.opsExempt() && facts.op() ) {
			return TimeState.EXCLUDED;
		}
		if( rules.exemptIds().contains( facts.uuid() ) || rules.exemptNames().contains( name ) ) {
			return TimeState.EXCLUDED;
		}
		if( rules.hasLimitedList() && !rules.limitedIds().contains( facts.uuid() ) && !rules.limitedNames().contains( name ) ) {
			return TimeState.EXCLUDED;
		}
		if( facts.creative() && rules.pauseInCreative() || facts.spectator() && rules.pauseInSpectator() ) {
			return TimeState.PAUSED;
		}
		return TimeState.COUNTING;
	}
}
```

`time/TimeAccounting.java`:
```java
package de.geheimagentnr1.player_time_manager.time;


public final class TimeAccounting {
	
	
	// Longer gaps come from a paused singleplayer game or a hanging server and are not counted
	public static final long MAX_COUNTED_GAP_MILLIS = 10_000L;
	
	private TimeAccounting() {
	
	}
	
	public static long elapsedToCount( long lastMillis, long nowMillis ) {
		
		if( lastMillis < 0 ) {
			return 0;
		}
		long elapsed = nowMillis - lastMillis;
		return elapsed < 0 || elapsed > MAX_COUNTED_GAP_MILLIS ? 0 : elapsed;
	}
}
```

`display/WarningTracker.java`:
```java
package de.geheimagentnr1.player_time_manager.display;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;


public final class WarningTracker {
	
	
	private WarningTracker() {
	
	}
	
	@NotNull
	public static Optional<Long> crossedThreshold( long before, long after, @NotNull List<Long> thresholdsDescending ) {
		
		Optional<Long> crossed = Optional.empty();
		for( long threshold : thresholdsDescending ) {
			if( before > threshold && after <= threshold ) {
				crossed = Optional.of( threshold );
			}
		}
		return crossed;
	}
}
```

- [ ] **Step 4: Tests ausführen, sie müssen bestehen**

Run: `.\gradlew.bat test --tests "*ExemptionCheckerTest" --tests "*TimeAccountingTest" --tests "*WarningTrackerTest"`
Expected: PASS (5 + 1 + 3 Tests).

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "Add exemption, warning threshold and counting rules

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Texte und Übersetzungen

**Files:**
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/text/Messages.java`
- Modify: `src/main/resources/assets/player_time_manager/lang/en_us.json`, `de_de.json`

**Interfaces:**
- Consumes: `Scope` (Task 7).
- Produces: alle Texte als `MutableComponent` (Fallback = Englisch). Methoden: `scopeName( Scope )`, `nextRefill( @Nullable ZonedDateTime )`, `dimensionExpired( Component scope, Component refill )`, `dimensionBlocked( Component scope, Component refill )`, `worldExpired( Component refill )`, `warning( String remaining, Component scope )`, `statusHeader()`, `statusLine( Component scope, String remaining, Component refill, boolean paused )`, `statusNone()`, `bossbar( Component scope, String remaining, boolean paused )`, `configProblems( int count )`, `timeGet( String player, Component scope, String remaining )`, `timeChanged( String player, Component scope, String remaining )`, `noLimit( Component scope )`, `overviewHeader( Component scope )`, `overviewLine( String player, String remaining )`, `overviewEmpty()`, `configValue( String key, String value )`, `configSet( String key, String value )`, `invalidValue( String message )`, `dimensionAdded( String id )`, `dimensionRemoved( String id )`, `dimensionUnknown( String id )`, `dimensionExists( String id )`, `overworldNotAllowed()`, `playersList( String list, String names )`, `playerAdded( String list, String name )`, `playerRemoved( String list, String name )`, `playerAlreadyListed( String list, String name )`, `playerNotListed( String list, String name )`, `onlyPlayers()`. Zeitpunkte werden als `yyyy-MM-dd HH:mm` formatiert.

- [ ] **Step 1: `Messages` implementieren**

```java
package de.geheimagentnr1.player_time_manager.text;

import de.geheimagentnr1.player_time_manager.time.Scope;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;


public final class Messages {
	
	
	@NotNull
	private static final String PREFIX = "player_time_manager.";
	
	@NotNull
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm" );
	
	@NotNull
	private static final Map<String, String> VANILLA_DIMENSIONS = Map.of(
		"minecraft:overworld", "Overworld",
		"minecraft:the_nether", "Nether",
		"minecraft:the_end", "The End"
	);
	
	private Messages() {
	
	}
	
	@NotNull
	private static MutableComponent tr( @NotNull String key, @NotNull String fallback, @NotNull Object... args ) {
		
		return Component.translatableWithFallback( PREFIX + key, fallback, args );
	}
	
	@NotNull
	public static MutableComponent scopeName( @NotNull Scope scope ) {
		
		if( scope.isWorld() ) {
			return tr( "scope.world", "World" );
		}
		String id = scope.dimension();
		return Component.translatableWithFallback(
			PREFIX + "dimension." + id.replace( ':', '.' ),
			VANILLA_DIMENSIONS.getOrDefault( id, id )
		);
	}
	
	@NotNull
	public static MutableComponent nextRefill( @Nullable ZonedDateTime next ) {
		
		return next == null ? tr( "refill.none", "only by an admin" ) : Component.literal( DATE_TIME.format( next ) );
	}
	
	@NotNull
	public static MutableComponent dimensionExpired( @NotNull Component scope, @NotNull Component refill ) {
		
		return tr( "expired.dimension", "Your time in %s is used up. Next refill: %s", scope, refill );
	}
	
	@NotNull
	public static MutableComponent dimensionBlocked( @NotNull Component scope, @NotNull Component refill ) {
		
		return tr( "blocked.dimension", "You have no time left in %s. Next refill: %s", scope, refill );
	}
	
	@NotNull
	public static MutableComponent worldExpired( @NotNull Component refill ) {
		
		return tr( "expired.world", "Your playtime is used up. Next refill: %s", refill );
	}
	
	@NotNull
	public static MutableComponent warning( @NotNull String remaining, @NotNull Component scope ) {
		
		return tr( "warning", "Only %s left in %s", remaining, scope );
	}
	
	@NotNull
	public static MutableComponent statusHeader() {
		
		return tr( "status.header", "Your remaining time:" );
	}
	
	@NotNull
	public static MutableComponent statusLine( @NotNull Component scope, @NotNull String remaining, @NotNull Component refill, boolean paused ) {
		
		return paused
			? tr( "status.line.paused", "%s: %s, paused (next refill: %s)", scope, remaining, refill )
			: tr( "status.line", "%s: %s (next refill: %s)", scope, remaining, refill );
	}
	
	@NotNull
	public static MutableComponent statusNone() {
		
		return tr( "status.none", "No time limit applies to you." );
	}
	
	@NotNull
	public static MutableComponent bossbar( @NotNull Component scope, @NotNull String remaining, boolean paused ) {
		
		return paused
			? tr( "bossbar.paused", "%s: %s (paused)", scope, remaining )
			: tr( "bossbar", "%s: %s", scope, remaining );
	}
	
	@NotNull
	public static MutableComponent configProblems( int count ) {
		
		return tr( "config.problems", "Player Time Manager: the config has %s problem(s), see the server log.", count );
	}
	
	@NotNull
	public static MutableComponent timeGet( @NotNull String player, @NotNull Component scope, @NotNull String remaining ) {
		
		return tr( "command.time.get", "%s - %s: %s", player, scope, remaining );
	}
	
	@NotNull
	public static MutableComponent timeChanged( @NotNull String player, @NotNull Component scope, @NotNull String remaining ) {
		
		return tr( "command.time.changed", "%s - %s: now %s", player, scope, remaining );
	}
	
	@NotNull
	public static MutableComponent noLimit( @NotNull Component scope ) {
		
		return tr( "command.time.no_limit", "%s has no active time limit", scope );
	}
	
	@NotNull
	public static MutableComponent overviewHeader( @NotNull Component scope ) {
		
		return tr( "command.overview.header", "Remaining time in %s:", scope );
	}
	
	@NotNull
	public static MutableComponent overviewLine( @NotNull String player, @NotNull String remaining ) {
		
		return tr( "command.overview.line", "%s: %s", player, remaining );
	}
	
	@NotNull
	public static MutableComponent overviewEmpty() {
		
		return tr( "command.overview.empty", "No players yet" );
	}
	
	@NotNull
	public static MutableComponent configValue( @NotNull String key, @NotNull String value ) {
		
		return tr( "command.config.value", "%s = %s", key, value );
	}
	
	@NotNull
	public static MutableComponent configSet( @NotNull String key, @NotNull String value ) {
		
		return tr( "command.config.set", "%s set to %s", key, value );
	}
	
	@NotNull
	public static MutableComponent invalidValue( @NotNull String message ) {
		
		return tr( "command.invalid", "Invalid value: %s", message );
	}
	
	@NotNull
	public static MutableComponent dimensionAdded( @NotNull String id ) {
		
		return tr( "command.dimension.added", "%s added (disabled, enable it with: /playtime config dimension %s enabled true)", id, id );
	}
	
	@NotNull
	public static MutableComponent dimensionRemoved( @NotNull String id ) {
		
		return tr( "command.dimension.removed", "%s removed", id );
	}
	
	@NotNull
	public static MutableComponent dimensionUnknown( @NotNull String id ) {
		
		return tr( "command.dimension.unknown", "%s is not in the config, add it with: /playtime config dimension %s add", id, id );
	}
	
	@NotNull
	public static MutableComponent dimensionExists( @NotNull String id ) {
		
		return tr( "command.dimension.exists", "%s is already in the config", id );
	}
	
	@NotNull
	public static MutableComponent overworldNotAllowed() {
		
		return tr( "command.dimension.overworld", "The Overworld can't be limited as a dimension, use /playtime config world" );
	}
	
	@NotNull
	public static MutableComponent playersList( @NotNull String list, @NotNull String names ) {
		
		return tr( "command.players.list", "%s players: %s", list, names );
	}
	
	@NotNull
	public static MutableComponent playerAdded( @NotNull String list, @NotNull String name ) {
		
		return tr( "command.players.added", "%s added to %s players", name, list );
	}
	
	@NotNull
	public static MutableComponent playerRemoved( @NotNull String list, @NotNull String name ) {
		
		return tr( "command.players.removed", "%s removed from %s players", name, list );
	}
	
	@NotNull
	public static MutableComponent playerAlreadyListed( @NotNull String list, @NotNull String name ) {
		
		return tr( "command.players.already", "%s is already in %s players", name, list );
	}
	
	@NotNull
	public static MutableComponent playerNotListed( @NotNull String list, @NotNull String name ) {
		
		return tr( "command.players.not_listed", "%s is not in %s players", name, list );
	}
	
	@NotNull
	public static MutableComponent onlyPlayers() {
		
		return tr( "command.only_players", "Only players can use this command" );
	}
}
```

- [ ] **Step 2: `en_us.json` schreiben** (Texte identisch mit den Fallbacks)

```json
{
  "player_time_manager.scope.world": "World",
  "player_time_manager.dimension.minecraft.overworld": "Overworld",
  "player_time_manager.dimension.minecraft.the_nether": "Nether",
  "player_time_manager.dimension.minecraft.the_end": "The End",
  "player_time_manager.refill.none": "only by an admin",
  "player_time_manager.expired.dimension": "Your time in %s is used up. Next refill: %s",
  "player_time_manager.blocked.dimension": "You have no time left in %s. Next refill: %s",
  "player_time_manager.expired.world": "Your playtime is used up. Next refill: %s",
  "player_time_manager.warning": "Only %s left in %s",
  "player_time_manager.status.header": "Your remaining time:",
  "player_time_manager.status.line": "%s: %s (next refill: %s)",
  "player_time_manager.status.line.paused": "%s: %s, paused (next refill: %s)",
  "player_time_manager.status.none": "No time limit applies to you.",
  "player_time_manager.bossbar": "%s: %s",
  "player_time_manager.bossbar.paused": "%s: %s (paused)",
  "player_time_manager.config.problems": "Player Time Manager: the config has %s problem(s), see the server log.",
  "player_time_manager.command.time.get": "%s - %s: %s",
  "player_time_manager.command.time.changed": "%s - %s: now %s",
  "player_time_manager.command.time.no_limit": "%s has no active time limit",
  "player_time_manager.command.overview.header": "Remaining time in %s:",
  "player_time_manager.command.overview.line": "%s: %s",
  "player_time_manager.command.overview.empty": "No players yet",
  "player_time_manager.command.config.value": "%s = %s",
  "player_time_manager.command.config.set": "%s set to %s",
  "player_time_manager.command.invalid": "Invalid value: %s",
  "player_time_manager.command.dimension.added": "%s added (disabled, enable it with: /playtime config dimension %s enabled true)",
  "player_time_manager.command.dimension.removed": "%s removed",
  "player_time_manager.command.dimension.unknown": "%s is not in the config, add it with: /playtime config dimension %s add",
  "player_time_manager.command.dimension.exists": "%s is already in the config",
  "player_time_manager.command.dimension.overworld": "The Overworld can't be limited as a dimension, use /playtime config world",
  "player_time_manager.command.players.list": "%s players: %s",
  "player_time_manager.command.players.added": "%s added to %s players",
  "player_time_manager.command.players.removed": "%s removed from %s players",
  "player_time_manager.command.players.already": "%s is already in %s players",
  "player_time_manager.command.players.not_listed": "%s is not in %s players",
  "player_time_manager.command.only_players": "Only players can use this command"
}
```

- [ ] **Step 3: `de_de.json` schreiben**

```json
{
  "player_time_manager.scope.world": "Welt",
  "player_time_manager.dimension.minecraft.overworld": "Oberwelt",
  "player_time_manager.dimension.minecraft.the_nether": "Nether",
  "player_time_manager.dimension.minecraft.the_end": "Ende",
  "player_time_manager.refill.none": "nur durch einen Admin",
  "player_time_manager.expired.dimension": "Deine Zeit in %s ist aufgebraucht. Nächstes Auffüllen: %s",
  "player_time_manager.blocked.dimension": "Du hast keine Zeit mehr für %s. Nächstes Auffüllen: %s",
  "player_time_manager.expired.world": "Deine Spielzeit ist aufgebraucht. Nächstes Auffüllen: %s",
  "player_time_manager.warning": "Nur noch %s in %s",
  "player_time_manager.status.header": "Deine Restzeit:",
  "player_time_manager.status.line": "%s: %s (nächstes Auffüllen: %s)",
  "player_time_manager.status.line.paused": "%s: %s, pausiert (nächstes Auffüllen: %s)",
  "player_time_manager.status.none": "Für dich gilt kein Zeitlimit.",
  "player_time_manager.bossbar": "%s: %s",
  "player_time_manager.bossbar.paused": "%s: %s (pausiert)",
  "player_time_manager.config.problems": "Player Time Manager: Die Config hat %s Problem(e), siehe Server-Log.",
  "player_time_manager.command.time.get": "%s - %s: %s",
  "player_time_manager.command.time.changed": "%s - %s: jetzt %s",
  "player_time_manager.command.time.no_limit": "%s hat kein aktives Zeitlimit",
  "player_time_manager.command.overview.header": "Restzeit in %s:",
  "player_time_manager.command.overview.line": "%s: %s",
  "player_time_manager.command.overview.empty": "Noch keine Spieler",
  "player_time_manager.command.config.value": "%s = %s",
  "player_time_manager.command.config.set": "%s gesetzt auf %s",
  "player_time_manager.command.invalid": "Ungültiger Wert: %s",
  "player_time_manager.command.dimension.added": "%s hinzugefügt (deaktiviert, einschalten mit: /playtime config dimension %s enabled true)",
  "player_time_manager.command.dimension.removed": "%s entfernt",
  "player_time_manager.command.dimension.unknown": "%s steht nicht in der Config, hinzufügen mit: /playtime config dimension %s add",
  "player_time_manager.command.dimension.exists": "%s steht bereits in der Config",
  "player_time_manager.command.dimension.overworld": "Die Oberwelt kann nicht als Dimension begrenzt werden, nutze /playtime config world",
  "player_time_manager.command.players.list": "%s-Spieler: %s",
  "player_time_manager.command.players.added": "%s zu %s-Spielern hinzugefügt",
  "player_time_manager.command.players.removed": "%s aus %s-Spielern entfernt",
  "player_time_manager.command.players.already": "%s steht bereits in %s-Spielern",
  "player_time_manager.command.players.not_listed": "%s steht nicht in %s-Spielern",
  "player_time_manager.command.only_players": "Nur Spieler können diesen Befehl nutzen"
}
```

- [ ] **Step 4: Fallbacks gegen `en_us.json` prüfen**

```bash
cd /c/MinecraftModding/ActiveMods/PlayerTimeManager
grep -o 'tr( "[a-z_.]*", "[^"]*"' src/main/java/de/geheimagentnr1/player_time_manager/text/Messages.java | sed 's/tr( "\(.*\)", "\(.*\)"/player_time_manager.\1=\2/' | sort > /tmp/ptm_fallbacks.txt
python3 -c "import json;d=json.load(open('src/main/resources/assets/player_time_manager/lang/en_us.json',encoding='utf-8'));[print(k+'='+v) for k,v in sorted(d.items()) if not k.startswith('player_time_manager.dimension.')]" > /tmp/ptm_lang.txt
diff /tmp/ptm_fallbacks.txt /tmp/ptm_lang.txt && echo "fallbacks match en_us.json"
python3 -c "import json;a=json.load(open('src/main/resources/assets/player_time_manager/lang/en_us.json',encoding='utf-8'));b=json.load(open('src/main/resources/assets/player_time_manager/lang/de_de.json',encoding='utf-8'));print(set(a)^set(b) or 'same keys')"
```
Expected: `fallbacks match en_us.json` und `same keys`. Abweichungen in `Messages.java` bzw. den JSON-Dateien beheben.

- [ ] **Step 5: Build und Commit**

Run: `.\gradlew.bat build` - Expected: `BUILD SUCCESSFUL`.
```bash
git add -A src
git commit -m "Add translatable messages (en_us, de_de)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Anzeige (Bossbar, Actionbar, Warnungen)

**Files:**
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/display/BarColor.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/display/ScopeStatus.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/display/DisplayManager.java`
- Test: `src/test/java/de/geheimagentnr1/player_time_manager/display/BarColorTest.java`

**Interfaces:**
- Consumes: `Scope` (Task 7), `DisplaySettings`/`DisplayMode` (Task 5), `DurationParser` (Task 2), `Messages` (Task 9).
- Produces:
  - `enum BarColor { GREEN, YELLOW, RED }` mit `static BarColor of( long remaining, long limit, int yellowBelowPercent, int redBelowPercent )` und `static float progress( long remaining, long limit )` (0..1)
  - `record ScopeStatus( Scope scope, long remainingMillis, long limitMillis, boolean paused )`
  - `DisplayManager`: `void update( ServerPlayer player, List<ScopeStatus> statuses, DisplaySettings settings )`, `void warn( ServerPlayer player, Scope scope, long thresholdMillis, DisplaySettings settings )`, `void remove( ServerPlayer player )`, `void removeAll()`

- [ ] **Step 1: Failing Test schreiben**

```java
package de.geheimagentnr1.player_time_manager.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


class BarColorTest {
	
	
	@Test
	void colorsByPercentage() {
		
		assertEquals( BarColor.GREEN, BarColor.of( 60, 100, 50, 10 ) );
		assertEquals( BarColor.GREEN, BarColor.of( 50, 100, 50, 10 ) );
		assertEquals( BarColor.YELLOW, BarColor.of( 49, 100, 50, 10 ) );
		assertEquals( BarColor.YELLOW, BarColor.of( 10, 100, 50, 10 ) );
		assertEquals( BarColor.RED, BarColor.of( 9, 100, 50, 10 ) );
		assertEquals( BarColor.GREEN, BarColor.of( 500, 100, 50, 10 ) );
		assertEquals( BarColor.RED, BarColor.of( 0, 0, 50, 10 ) );
	}
	
	@Test
	void progressIsClamped() {
		
		assertEquals( 0.25f, BarColor.progress( 25, 100 ) );
		assertEquals( 1f, BarColor.progress( 250, 100 ) );
		assertEquals( 0f, BarColor.progress( -5, 100 ) );
		assertEquals( 0f, BarColor.progress( 5, 0 ) );
	}
}
```

- [ ] **Step 2: Test ausführen, er muss fehlschlagen**

Run: `.\gradlew.bat test --tests "*BarColorTest"`
Expected: FAIL beim Kompilieren.

- [ ] **Step 3: Implementierung**

`display/BarColor.java`:
```java
package de.geheimagentnr1.player_time_manager.display;

import org.jetbrains.annotations.NotNull;


public enum BarColor {
	GREEN,
	YELLOW,
	RED;
	
	@NotNull
	public static BarColor of( long remaining, long limit, int yellowBelowPercent, int redBelowPercent ) {
		
		if( limit <= 0 ) {
			return RED;
		}
		double percent = remaining * 100.0 / limit;
		if( percent < redBelowPercent ) {
			return RED;
		}
		return percent < yellowBelowPercent ? YELLOW : GREEN;
	}
	
	public static float progress( long remaining, long limit ) {
		
		if( limit <= 0 ) {
			return 0f;
		}
		return (float)Math.max( 0.0, Math.min( 1.0, remaining / (double)limit ) );
	}
}
```

`display/ScopeStatus.java`:
```java
package de.geheimagentnr1.player_time_manager.display;

import de.geheimagentnr1.player_time_manager.time.Scope;
import org.jetbrains.annotations.NotNull;


public record ScopeStatus( @NotNull Scope scope, long remainingMillis, long limitMillis, boolean paused ) {

}
```

`display/DisplayManager.java`:
```java
package de.geheimagentnr1.player_time_manager.display;

import de.geheimagentnr1.player_time_manager.config.resolved.DisplaySettings;
import de.geheimagentnr1.player_time_manager.text.Messages;
import de.geheimagentnr1.player_time_manager.time.DurationParser;
import de.geheimagentnr1.player_time_manager.time.Scope;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


public class DisplayManager {
	
	
	@NotNull
	private final Map<UUID, Map<Scope, ServerBossEvent>> bossbars = new HashMap<>();
	
	public void update( @NotNull ServerPlayer player, @NotNull List<ScopeStatus> statuses, @NotNull DisplaySettings settings ) {
		
		switch( settings.mode() ) {
			case NONE -> remove( player );
			case ACTIONBAR -> {
				remove( player );
				if( !statuses.isEmpty() ) {
					player.displayClientMessage( actionbar( statuses ), true );
				}
			}
			case BOSSBARS -> showBossbars( player, statuses, settings );
			case BOSSBAR_SHORTEST -> showBossbars(
				player,
				statuses.stream().min( Comparator.comparingLong( ScopeStatus::remainingMillis ) ).map( List::of ).orElse( List.of() ),
				settings
			);
		}
	}
	
	@NotNull
	private static Component actionbar( @NotNull List<ScopeStatus> statuses ) {
		
		MutableComponent text = Component.empty();
		for( int i = 0; i < statuses.size(); i++ ) {
			ScopeStatus status = statuses.get( i );
			if( i > 0 ) {
				text.append( " | " );
			}
			text.append( Messages.bossbar( Messages.scopeName( status.scope() ), DurationParser.format( status.remainingMillis() ), status.paused() ) );
		}
		return text;
	}
	
	private void showBossbars( @NotNull ServerPlayer player, @NotNull List<ScopeStatus> statuses, @NotNull DisplaySettings settings ) {
		
		Map<Scope, ServerBossEvent> playerBars = bossbars.computeIfAbsent( player.getUUID(), uuid -> new HashMap<>() );
		Set<Scope> shown = statuses.stream().map( ScopeStatus::scope ).collect( Collectors.toSet() );
		playerBars.entrySet().removeIf( entry -> {
			if( shown.contains( entry.getKey() ) ) {
				return false;
			}
			entry.getValue().removeAllPlayers();
			return true;
		} );
		for( ScopeStatus status : statuses ) {
			ServerBossEvent bar = playerBars.computeIfAbsent(
				status.scope(),
				scope -> new ServerBossEvent( Component.empty(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS )
			);
			// After a respawn the player is a new object, the bossbar has to be sent to it again
			if( !bar.getPlayers().contains( player ) ) {
				bar.removeAllPlayers();
				bar.addPlayer( player );
			}
			bar.setName( Messages.bossbar(
				Messages.scopeName( status.scope() ),
				DurationParser.format( status.remainingMillis() ),
				status.paused()
			) );
			bar.setProgress( BarColor.progress( status.remainingMillis(), status.limitMillis() ) );
			bar.setColor( switch( BarColor.of(
				status.remainingMillis(),
				status.limitMillis(),
				settings.yellowBelowPercent(),
				settings.redBelowPercent()
			) ) {
				case GREEN -> BossEvent.BossBarColor.GREEN;
				case YELLOW -> BossEvent.BossBarColor.YELLOW;
				case RED -> BossEvent.BossBarColor.RED;
			} );
		}
	}
	
	public void warn( @NotNull ServerPlayer player, @NotNull Scope scope, long thresholdMillis, @NotNull DisplaySettings settings ) {
		
		player.sendSystemMessage( Messages.warning( DurationParser.format( thresholdMillis ), Messages.scopeName( scope ) ) );
		if( settings.warningSound() ) {
			player.playNotifySound( SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 1.0F, 1.0F );
		}
	}
	
	public void remove( @NotNull ServerPlayer player ) {
		
		Map<Scope, ServerBossEvent> playerBars = bossbars.remove( player.getUUID() );
		if( playerBars != null ) {
			playerBars.values().forEach( ServerBossEvent::removeAllPlayers );
		}
	}
	
	public void removeAll() {
		
		bossbars.values().forEach( playerBars -> playerBars.values().forEach( ServerBossEvent::removeAllPlayers ) );
		bossbars.clear();
	}
}
```

- [ ] **Step 4: Test und Build**

Run: `.\gradlew.bat test --tests "*BarColorTest"` und `.\gradlew.bat build`
Expected: PASS (2 Tests), `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "Add bossbar, actionbar and warning display

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Zielpunkt und Teleport

**Files:**
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/teleport/Destination.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/teleport/TargetResolver.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/teleport/PlayerTeleporter.java`

**Interfaces:**
- Consumes: `TargetSpec`, `TargetPosition` (Task 5), `TimeStore.level(..)` (Task 7).
- Produces:
  - `record Destination( ServerLevel level, Vec3 position, float yaw, float pitch )`
  - `static Destination TargetResolver.resolve( MinecraftServer server, ServerPlayer player, @Nullable TargetSpec target, Predicate<String> blockedDimension )` - Ausweg `worldSpawn( server )` bei `target == null`, unbekanntem Level, fehlender Position außerhalb Overworld/End oder gesperrter Ziel-Dimension
  - `static Destination TargetResolver.worldSpawn( MinecraftServer server )`
  - `static boolean PlayerTeleporter.teleport( ServerPlayer player, Destination destination )` - `true`, wenn der Spieler danach im Ziel-Level ist

- [ ] **Step 1: Implementierung**

`teleport/Destination.java`:
```java
package de.geheimagentnr1.player_time_manager.teleport;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;


public record Destination( @NotNull ServerLevel level, @NotNull Vec3 position, float yaw, float pitch ) {

}
```

`teleport/TargetResolver.java`:
```java
package de.geheimagentnr1.player_time_manager.teleport;

import de.geheimagentnr1.player_time_manager.config.TargetPosition;
import de.geheimagentnr1.player_time_manager.config.resolved.TargetSpec;
import de.geheimagentnr1.player_time_manager.data.TimeStore;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;


public final class TargetResolver {
	
	
	private TargetResolver() {
	
	}
	
	@NotNull
	public static Destination resolve(
		@NotNull MinecraftServer server,
		@NotNull ServerPlayer player,
		@Nullable TargetSpec target,
		@NotNull Predicate<String> blockedDimension ) {
		
		if( target == null || blockedDimension.test( target.dimension() ) ) {
			return worldSpawn( server );
		}
		ServerLevel level = TimeStore.level( server, target.dimension() );
		if( level == null ) {
			return worldSpawn( server );
		}
		TargetPosition position = target.position();
		if( position != null ) {
			return new Destination(
				level,
				new Vec3( position.x(), position.y(), position.z() ),
				position.yaw() == null ? player.getYRot() : position.yaw(),
				position.pitch() == null ? player.getXRot() : position.pitch()
			);
		}
		if( level.dimension() == Level.END ) {
			// Same place and platform as when entering the End through a portal
			EndPlatformFeature.createEndPlatform( level, ServerLevel.END_SPAWN_POINT.below(), true );
			return new Destination( level, Vec3.atBottomCenterOf( ServerLevel.END_SPAWN_POINT ), Direction.WEST.toYRot(), 0.0F );
		}
		return worldSpawn( server );
	}
	
	@NotNull
	public static Destination worldSpawn( @NotNull MinecraftServer server ) {
		
		ServerLevel overworld = server.overworld();
		return new Destination( overworld, Vec3.atBottomCenterOf( overworld.getSharedSpawnPos() ), overworld.getSharedSpawnAngle(), 0.0F );
	}
}
```

`teleport/PlayerTeleporter.java`:
```java
package de.geheimagentnr1.player_time_manager.teleport;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;


public final class PlayerTeleporter {
	
	
	private PlayerTeleporter() {
	
	}
	
	// Minecraft 1.21.1: a vehicle with a player can't change the dimension, so the player always dismounts
	// (from 1.21.2 the whole vehicle can be teleported, see the port plan)
	public static boolean teleport( @NotNull ServerPlayer player, @NotNull Destination destination ) {
		
		if( player.isSleeping() ) {
			player.stopSleepInBed( true, true );
		}
		player.stopRiding();
		player.teleportTo(
			destination.level(),
			destination.position().x(),
			destination.position().y(),
			destination.position().z(),
			destination.yaw(),
			destination.pitch()
		);
		return player.level() == destination.level();
	}
}
```

- [ ] **Step 2: Build**

Run: `.\gradlew.bat build`
Expected: `BUILD SUCCESSFUL`. (Verhalten wird in Task 14 ingame geprüft.)

- [ ] **Step 3: Commit**

```bash
git add -A src
git commit -m "Add target resolution and teleport

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Zeitdienst und Event-Handler

**Files:**
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/time/TimeService.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/handlers/GameEventHandler.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/handlers/ConfigReloadHandler.java`
- Modify: `src/main/java/de/geheimagentnr1/player_time_manager/PlayerTimeManager.java`

**Interfaces:**
- Consumes: `SettingsHolder` (Task 6), `TimeStore`/`TimeData` (Task 7), `ExemptionChecker`/`PlayerFacts`/`TimeState`/`TimeAccounting`/`WarningTracker` (Task 8), `Messages` (Task 9), `DisplayManager`/`ScopeStatus` (Task 10), `TargetResolver`/`PlayerTeleporter` (Task 11), `RefillCalculator`/`TimeEntry` (Task 4).
- Produces:
  - `TimeService( MinecraftServer server, SettingsHolder settings )` mit
    - `MinecraftServer server()`, `DisplayManager display()`
    - `ResolvedScope scopeSettings( Scope )`, `List<Scope> activeScopes()`
    - `Optional<TimeEntry> entry( Scope, UUID, long now, boolean create )` (wendet fällige Refills an und speichert)
    - `Optional<TimeEntry> setRemaining( Scope, UUID, long remainingMillis, long now )`
    - `Optional<TimeEntry> refillNow( Scope, UUID, long now )`
    - `Map<UUID, TimeEntry> entries( Scope, long now )` (alle bekannten Spieler, Refills nachgeholt)
    - `Optional<ZonedDateTime> nextRefill( Scope, long now )`
    - `TimeState state( ServerPlayer )`, `boolean isBlocked( ServerPlayer, String dimension, long now )`
    - `void countSecond( long now )`, `void refillAll( long now )`, `void onLogin( ServerPlayer, long now )`, `void onRespawn( ServerPlayer, long now )`, `void onLogout( ServerPlayer )`, `void notifyBlocked( ServerPlayer, String dimension, long now )`
  - `GameEventHandler( SettingsHolder settings )` mit `@Nullable TimeService service()`
  - `ConfigReloadHandler( SettingsHolder settings, ModConfigSpec spec )`

- [ ] **Step 1: `TimeService` implementieren**

```java
package de.geheimagentnr1.player_time_manager.time;

import com.mojang.logging.LogUtils;
import de.geheimagentnr1.player_time_manager.config.SettingsHolder;
import de.geheimagentnr1.player_time_manager.config.raw.PlayerRef;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedDimension;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedScope;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedSettings;
import de.geheimagentnr1.player_time_manager.data.TimeData;
import de.geheimagentnr1.player_time_manager.data.TimeStore;
import de.geheimagentnr1.player_time_manager.display.DisplayManager;
import de.geheimagentnr1.player_time_manager.display.ScopeStatus;
import de.geheimagentnr1.player_time_manager.display.WarningTracker;
import de.geheimagentnr1.player_time_manager.teleport.Destination;
import de.geheimagentnr1.player_time_manager.teleport.PlayerTeleporter;
import de.geheimagentnr1.player_time_manager.teleport.TargetResolver;
import de.geheimagentnr1.player_time_manager.text.Messages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;


public class TimeService {
	
	
	@NotNull
	private static final Logger LOGGER = LogUtils.getLogger();
	
	private static final long NOTICE_INTERVAL_MILLIS = 5_000L;
	
	@NotNull
	private final MinecraftServer server;
	
	@NotNull
	private final SettingsHolder settings;
	
	@NotNull
	private final DisplayManager display = new DisplayManager();
	
	@NotNull
	private final Map<UUID, Long> lastBlockedNotice = new HashMap<>();
	
	@NotNull
	private final Map<UUID, Long> lastExpiryAttempt = new HashMap<>();
	
	private long lastCountMillis = -1;
	
	public TimeService( @NotNull MinecraftServer _server, @NotNull SettingsHolder _settings ) {
		
		server = _server;
		settings = _settings;
	}
	
	@NotNull
	public MinecraftServer server() {
		
		return server;
	}
	
	@NotNull
	public DisplayManager display() {
		
		return display;
	}
	
	@NotNull
	public ResolvedScope scopeSettings( @NotNull Scope scope ) {
		
		ResolvedSettings current = settings.current();
		if( scope.isWorld() ) {
			return current.world();
		}
		ResolvedDimension dimension = current.dimensions().get( scope.dimension() );
		return dimension == null ? ResolvedScope.INACTIVE : dimension.scope();
	}
	
	@NotNull
	public List<Scope> activeScopes() {
		
		List<Scope> scopes = new ArrayList<>();
		ResolvedSettings current = settings.current();
		if( current.world().active() ) {
			scopes.add( Scope.WORLD );
		}
		current.dimensions().values().stream()
			.filter( dimension -> dimension.scope().active() )
			.forEach( dimension -> scopes.add( Scope.dimension( dimension.dimension() ) ) );
		return scopes;
	}
	
	@NotNull
	public Optional<TimeEntry> entry( @NotNull Scope scope, @NotNull UUID player, long now, boolean create ) {
		
		ResolvedScope scopeSettings = scopeSettings( scope );
		TimeData data = TimeStore.data( server, scope );
		if( !scopeSettings.active() || scopeSettings.refillRule() == null || data == null ) {
			return Optional.empty();
		}
		Optional<TimeEntry> existing = data.get( player );
		if( existing.isEmpty() && !create ) {
			return Optional.empty();
		}
		TimeEntry entry = existing
			.map( value -> RefillCalculator.apply( value, scopeSettings.refillRule(), now, settings.current().zone() ) )
			.orElseGet( () -> RefillCalculator.create( scopeSettings.refillRule(), now ) );
		data.put( player, entry );
		return Optional.of( entry );
	}
	
	@NotNull
	private Optional<TimeEntry> update( @NotNull Scope scope, @NotNull UUID player, long now, @NotNull UnaryOperator<TimeEntry> change ) {
		
		return entry( scope, player, now, true ).map( entry -> {
			TimeEntry changed = change.apply( entry );
			TimeData data = TimeStore.data( server, scope );
			if( data != null ) {
				data.put( player, changed );
			}
			return changed;
		} );
	}
	
	@NotNull
	public Optional<TimeEntry> setRemaining( @NotNull Scope scope, @NotNull UUID player, long remainingMillis, long now ) {
		
		return update( scope, player, now, entry -> entry.withRemaining( remainingMillis ) );
	}
	
	@NotNull
	public Optional<TimeEntry> refillNow( @NotNull Scope scope, @NotNull UUID player, long now ) {
		
		ResolvedScope scopeSettings = scopeSettings( scope );
		if( scopeSettings.refillRule() == null ) {
			return Optional.empty();
		}
		return update( scope, player, now, entry -> RefillCalculator.refillNow( entry, scopeSettings.refillRule() ) );
	}
	
	@NotNull
	public Map<UUID, TimeEntry> entries( @NotNull Scope scope, long now ) {
		
		TimeData data = TimeStore.data( server, scope );
		Map<UUID, TimeEntry> result = new HashMap<>();
		if( data != null ) {
			for( UUID player : List.copyOf( data.entries().keySet() ) ) {
				entry( scope, player, now, false ).ifPresent( entry -> result.put( player, entry ) );
			}
		}
		return result;
	}
	
	@NotNull
	public Optional<ZonedDateTime> nextRefill( @NotNull Scope scope, long now ) {
		
		ResolvedScope scopeSettings = scopeSettings( scope );
		if( scopeSettings.refillRule() == null || scopeSettings.refillRule().cron() == null ) {
			return Optional.empty();
		}
		return scopeSettings.refillRule().cron().next( Instant.ofEpochMilli( now ).atZone( settings.current().zone() ) );
	}
	
	@NotNull
	private Component nextRefillText( @NotNull Scope scope, long now ) {
		
		return Messages.nextRefill( nextRefill( scope, now ).orElse( null ) );
	}
	
	@NotNull
	public TimeState state( @NotNull ServerPlayer player ) {
		
		return ExemptionChecker.state(
			new PlayerFacts(
				player.getUUID(),
				player.getGameProfile().getName(),
				server.getPlayerList().isOp( player.getGameProfile() ),
				player.isCreative(),
				player.isSpectator()
			),
			settings.current().playerRules()
		);
	}
	
	public boolean isBlocked( @NotNull ServerPlayer player, @NotNull String dimension, long now ) {
		
		Scope scope = Scope.dimension( dimension );
		if( !scopeSettings( scope ).active() || state( player ) != TimeState.COUNTING ) {
			return false;
		}
		return entry( scope, player.getUUID(), now, true ).map( entry -> entry.remainingMillis() <= 0 ).orElse( false );
	}
	
	private boolean isWorldExpired( @NotNull ServerPlayer player, long now ) {
		
		if( !scopeSettings( Scope.WORLD ).active() || state( player ) != TimeState.COUNTING ) {
			return false;
		}
		return entry( Scope.WORLD, player.getUUID(), now, true ).map( entry -> entry.remainingMillis() <= 0 ).orElse( false );
	}
	
	public void countSecond( long now ) {
		
		long elapsed = TimeAccounting.elapsedToCount( lastCountMillis, now );
		lastCountMillis = now;
		if( !settings.isLoaded() ) {
			return;
		}
		ResolvedSettings current = settings.current();
		for( ServerPlayer player : List.copyOf( server.getPlayerList().getPlayers() ) ) {
			countPlayer( player, current, elapsed, now );
		}
	}
	
	private void countPlayer( @NotNull ServerPlayer player, @NotNull ResolvedSettings current, long elapsed, long now ) {
		
		TimeState state = state( player );
		if( state == TimeState.EXCLUDED ) {
			display.remove( player );
			return;
		}
		String dimension = player.level().dimension().location().toString();
		List<ScopeStatus> statuses = new ArrayList<>();
		boolean worldExpired = false;
		boolean dimensionExpired = false;
		for( Scope scope : List.of( Scope.WORLD, Scope.dimension( dimension ) ) ) {
			ResolvedScope scopeSettings = scopeSettings( scope );
			Optional<TimeEntry> found = scopeSettings.active() ? entry( scope, player.getUUID(), now, true ) : Optional.empty();
			if( found.isEmpty() ) {
				continue;
			}
			TimeEntry entry = found.get();
			if( state == TimeState.COUNTING && elapsed > 0 && entry.remainingMillis() > 0 ) {
				long before = entry.remainingMillis();
				entry = setRemaining( scope, player.getUUID(), before - elapsed, now ).orElse( entry );
				WarningTracker.crossedThreshold( before, entry.remainingMillis(), current.display().warningTimesMillis() )
					.ifPresent( threshold -> display.warn( player, scope, threshold, current.display() ) );
			}
			if( state == TimeState.COUNTING && entry.remainingMillis() <= 0 ) {
				if( scope.isWorld() ) {
					worldExpired = true;
				} else {
					dimensionExpired = true;
				}
			}
			statuses.add( new ScopeStatus( scope, entry.remainingMillis(), scopeSettings.limitMillis(), state == TimeState.PAUSED ) );
		}
		if( worldExpired ) {
			kick( player, now );
			return;
		}
		if( dimensionExpired ) {
			sendOutOfDimension( player, dimension, now, false );
		}
		display.update( player, statuses, current.display() );
	}
	
	private void kick( @NotNull ServerPlayer player, long now ) {
		
		display.remove( player );
		player.connection.disconnect( Messages.worldExpired( nextRefillText( Scope.WORLD, now ) ) );
	}
	
	private void sendOutOfDimension( @NotNull ServerPlayer player, @NotNull String dimension, long now, boolean force ) {
		
		Long lastAttempt = lastExpiryAttempt.get( player.getUUID() );
		if( !force && lastAttempt != null && now - lastAttempt < NOTICE_INTERVAL_MILLIS ) {
			return;
		}
		lastExpiryAttempt.put( player.getUUID(), now );
		ResolvedDimension settingsOfDimension = settings.current().dimensions().get( dimension );
		Destination destination = TargetResolver.resolve(
			server,
			player,
			settingsOfDimension == null ? null : settingsOfDimension.target(),
			target -> isBlocked( player, target, now )
		);
		boolean teleported = PlayerTeleporter.teleport( player, destination );
		if( !teleported ) {
			// e.g. another mod cancelled the teleport into the target dimension
			teleported = PlayerTeleporter.teleport( player, TargetResolver.worldSpawn( server ) );
		}
		if( !teleported ) {
			LOGGER.warn( "Player Time Manager: could not teleport {} out of {}", player.getGameProfile().getName(), dimension );
		}
		Scope scope = Scope.dimension( dimension );
		player.sendSystemMessage( Messages.dimensionExpired( Messages.scopeName( scope ), nextRefillText( scope, now ) ) );
	}
	
	public void notifyBlocked( @NotNull ServerPlayer player, @NotNull String dimension, long now ) {
		
		Long last = lastBlockedNotice.get( player.getUUID() );
		if( last != null && now - last < NOTICE_INTERVAL_MILLIS ) {
			return;
		}
		lastBlockedNotice.put( player.getUUID(), now );
		Scope scope = Scope.dimension( dimension );
		player.sendSystemMessage( Messages.dimensionBlocked( Messages.scopeName( scope ), nextRefillText( scope, now ) ) );
	}
	
	public void refillAll( long now ) {
		
		if( !settings.isLoaded() ) {
			return;
		}
		for( Scope scope : activeScopes() ) {
			entries( scope, now );
		}
	}
	
	public void onLogin( @NotNull ServerPlayer player, long now ) {
		
		fillMissingUuids( player );
		ResolvedSettings current = settings.current();
		if( server.getPlayerList().isOp( player.getGameProfile() ) && !current.problems().isEmpty() ) {
			player.sendSystemMessage( Messages.configProblems( current.problems().size() ) );
		}
		if( isWorldExpired( player, now ) ) {
			kick( player, now );
			return;
		}
		String dimension = player.level().dimension().location().toString();
		if( isBlocked( player, dimension, now ) ) {
			sendOutOfDimension( player, dimension, now, true );
		}
	}
	
	public void onRespawn( @NotNull ServerPlayer player, long now ) {
		
		String dimension = player.level().dimension().location().toString();
		if( isBlocked( player, dimension, now ) ) {
			sendOutOfDimension( player, dimension, now, true );
		}
	}
	
	public void onLogout( @NotNull ServerPlayer player ) {
		
		display.remove( player );
		lastBlockedNotice.remove( player.getUUID() );
		lastExpiryAttempt.remove( player.getUUID() );
	}
	
	// Entries added by hand with only a name get their uuid on the first login of that player
	private void fillMissingUuids( @NotNull ServerPlayer player ) {
		
		String name = player.getGameProfile().getName();
		String uuid = player.getUUID().toString();
		List<PlayerRef> limited = settings.config().read().general().limitedPlayers();
		List<PlayerRef> exempt = settings.config().read().general().exemptPlayers();
		List<PlayerRef> newLimited = withUuid( limited, name, uuid );
		List<PlayerRef> newExempt = withUuid( exempt, name, uuid );
		if( !newLimited.equals( limited ) ) {
			settings.config().setLimitedPlayers( newLimited );
		}
		if( !newExempt.equals( exempt ) ) {
			settings.config().setExemptPlayers( newExempt );
		}
		if( !newLimited.equals( limited ) || !newExempt.equals( exempt ) ) {
			settings.reload( server );
		}
	}
	
	@NotNull
	private static List<PlayerRef> withUuid( @NotNull List<PlayerRef> players, @NotNull String name, @NotNull String uuid ) {
		
		return players.stream()
			.map( player -> player.uuid().isBlank() && player.name().equalsIgnoreCase( name ) ? new PlayerRef( name, uuid ) : player )
			.toList();
	}
}
```

- [ ] **Step 2: `GameEventHandler` implementieren**

```java
package de.geheimagentnr1.player_time_manager.handlers;

import de.geheimagentnr1.player_time_manager.commands.PlaytimeCommand;
import de.geheimagentnr1.player_time_manager.config.SettingsHolder;
import de.geheimagentnr1.player_time_manager.time.TimeService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class GameEventHandler {
	
	
	@NotNull
	private final SettingsHolder settings;
	
	@Nullable
	private TimeService service;
	
	public GameEventHandler( @NotNull SettingsHolder _settings ) {
		
		settings = _settings;
	}
	
	@Nullable
	public TimeService service() {
		
		return service;
	}
	
	@SubscribeEvent
	public void handleServerStarted( @NotNull ServerStartedEvent event ) {
		
		settings.reload( event.getServer() );
		service = new TimeService( event.getServer(), settings );
		service.refillAll( System.currentTimeMillis() );
	}
	
	@SubscribeEvent
	public void handleServerStopping( @NotNull ServerStoppingEvent event ) {
		
		if( service != null ) {
			service.display().removeAll();
		}
		service = null;
	}
	
	@SubscribeEvent
	public void handleServerTick( @NotNull ServerTickEvent.Post event ) {
		
		TimeService current = service;
		if( current == null ) {
			return;
		}
		int tick = event.getServer().getTickCount();
		long now = System.currentTimeMillis();
		if( tick % 20 == 0 ) {
			current.countSecond( now );
		}
		if( tick % 1200 == 0 ) {
			current.refillAll( now );
		}
	}
	
	@SubscribeEvent
	public void handleTravel( @NotNull EntityTravelToDimensionEvent event ) {
		
		TimeService current = service;
		if( current != null && event.getEntity() instanceof ServerPlayer player ) {
			String dimension = event.getDimension().location().toString();
			long now = System.currentTimeMillis();
			if( current.isBlocked( player, dimension, now ) ) {
				event.setCanceled( true );
				current.notifyBlocked( player, dimension, now );
			}
		}
	}
	
	@SubscribeEvent
	public void handleLogin( @NotNull PlayerEvent.PlayerLoggedInEvent event ) {
		
		if( service != null && event.getEntity() instanceof ServerPlayer player ) {
			service.onLogin( player, System.currentTimeMillis() );
		}
	}
	
	@SubscribeEvent
	public void handleRespawn( @NotNull PlayerEvent.PlayerRespawnEvent event ) {
		
		if( service != null && event.getEntity() instanceof ServerPlayer player ) {
			service.onRespawn( player, System.currentTimeMillis() );
		}
	}
	
	@SubscribeEvent
	public void handleLogout( @NotNull PlayerEvent.PlayerLoggedOutEvent event ) {
		
		if( service != null && event.getEntity() instanceof ServerPlayer player ) {
			service.onLogout( player );
		}
	}
	
	@SubscribeEvent
	public void handleRegisterCommands( @NotNull RegisterCommandsEvent event ) {
		
		event.getDispatcher().register( new PlaytimeCommand( settings, this::service ).build() );
	}
}
```

> `PlaytimeCommand` entsteht in Task 13. Bis dahin die Methode `handleRegisterCommands` samt Import auskommentiert lassen, damit der Build in diesem Task grün ist; in Task 13 Schritt 3 wieder einkommentieren.

- [ ] **Step 3: `ConfigReloadHandler` implementieren**

```java
package de.geheimagentnr1.player_time_manager.handlers;

import de.geheimagentnr1.player_time_manager.config.SettingsHolder;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;


public class ConfigReloadHandler {
	
	
	@NotNull
	private final SettingsHolder settings;
	
	@NotNull
	private final ModConfigSpec spec;
	
	public ConfigReloadHandler( @NotNull SettingsHolder _settings, @NotNull ModConfigSpec _spec ) {
		
		settings = _settings;
		spec = _spec;
	}
	
	// Fired when the TOML file was changed while the server is running (also after a command saved it)
	@SubscribeEvent
	public void handleReloading( @NotNull ModConfigEvent.Reloading event ) {
		
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if( event.getConfig().getSpec() == spec && server != null && settings.isLoaded() ) {
			server.execute( () -> settings.reload( server ) );
		}
	}
}
```

- [ ] **Step 4: Handler in der Mod-Klasse registrieren**

`PlayerTimeManager.java` komplett:
```java
package de.geheimagentnr1.player_time_manager;

import de.geheimagentnr1.player_time_manager.config.ServerConfig;
import de.geheimagentnr1.player_time_manager.config.SettingsHolder;
import de.geheimagentnr1.player_time_manager.data.ModAttachmentTypes;
import de.geheimagentnr1.player_time_manager.handlers.ConfigReloadHandler;
import de.geheimagentnr1.player_time_manager.handlers.GameEventHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;


@Mod( PlayerTimeManager.MODID )
public class PlayerTimeManager {
	
	
	@NotNull
	public static final String MODID = "player_time_manager";
	
	public PlayerTimeManager( @NotNull IEventBus modEventBus, @NotNull ModContainer modContainer ) {
		
		ServerConfig serverConfig = new ServerConfig();
		modContainer.registerConfig( ModConfig.Type.SERVER, serverConfig.getSpec() );
		SettingsHolder settings = new SettingsHolder( serverConfig );
		
		ModAttachmentTypes.ATTACHMENT_TYPES.register( modEventBus );
		modEventBus.register( new ConfigReloadHandler( settings, serverConfig.getSpec() ) );
		NeoForge.EVENT_BUS.register( new GameEventHandler( settings ) );
	}
}
```

- [ ] **Step 5: Build und alle Tests**

Run: `.\gradlew.bat build`
Expected: `BUILD SUCCESSFUL`, alle bisherigen Unit-Tests grün.

- [ ] **Step 6: Commit**

```bash
git add -A src
git commit -m "Count and refill time, handle expiry, travel, login and respawn

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 13: Befehl `/playtime`

**Files:**
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/commands/ConfigEdits.java`
- Create: `src/main/java/de/geheimagentnr1/player_time_manager/commands/PlaytimeCommand.java`
- Modify: `src/main/java/de/geheimagentnr1/player_time_manager/handlers/GameEventHandler.java` (Befehlsregistrierung einkommentieren)
- Test: `src/test/java/de/geheimagentnr1/player_time_manager/commands/ConfigEditsTest.java`

**Interfaces:**
- Consumes: `SettingsHolder`/`ServerConfig` (Task 6), `TimeService` (Task 12), `Messages` (Task 9), `DurationParser` (Task 2), `CronExpression` (Task 3), `TargetPosition`/`SettingsResolver` (Task 5).
- Produces:
  - `ConfigEdits` (reine Hilfen): `static DimensionRaw withScope( DimensionRaw, UnaryOperator<ScopeRaw> )`, `static ScopeRaw withEnabled( ScopeRaw, boolean )`, `withLimit( ScopeRaw, String )`, `withUseCustomRefill( ScopeRaw, boolean )`, `withRefillCron( ScopeRaw, String )`, `withRefillMode( ScopeRaw, String )`, `withRefillMax( ScopeRaw, String )`, `static DimensionRaw withTarget( DimensionRaw, String dimension, String position )`, `static DimensionRaw newDimension( String id )`, `static String cronValue( String input )` (`none` -> `""`, sonst normalisierter Ausdruck), `static String durationValue( String input, boolean clearable )` (`none` -> `""` wenn erlaubt, sonst geprüfte Dauer > 0), `static String modeValue( String input )` (`SET`/`ADD`)
  - `PlaytimeCommand( SettingsHolder settings, Supplier<TimeService> service )` mit `LiteralArgumentBuilder<CommandSourceStack> build()`
- Befehlsbaum (nur Vanilla-Argumenttypen: `GameProfileArgument`, `DimensionArgument`, `StringArgumentType`, `BoolArgumentType`):
  - `/playtime`
  - `/playtime time get <players> [world | dimension <dim>]`
  - `/playtime time add|remove|set <players> world|dimension <dim>|all <duration>`
  - `/playtime time refill <players> world|dimension <dim>|all`
  - `/playtime time overview [world | dimension <dim>]`
  - `/playtime config general refill_cron|refill_mode|refill_max|ops_exempt [value]`
  - `/playtime config world enabled|limit|use_custom_refill|refill_cron|refill_mode|refill_max [value]`
  - `/playtime config dimension <dim>` (alle Werte des Eintrags + zugehörige Config-Probleme), `/playtime config dimension <dim> add|remove`, `... enabled|limit|use_custom_refill|refill_cron|refill_mode|refill_max [value]`, `... target here|default|<target_dim> <position>`
  - `/playtime players limited|exempt list|add <players>|remove <players>`

- [ ] **Step 1: Failing Test für `ConfigEdits` schreiben**

```java
package de.geheimagentnr1.player_time_manager.commands;

import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;


class ConfigEditsTest {
	
	
	@Test
	void editsKeepOtherValues() {
		
		DimensionRaw nether = new DimensionRaw( "minecraft:the_nether", new ScopeRaw( false, "1h", false, "0 6 * * *", "SET", "" ), "minecraft:overworld", "" );
		DimensionRaw enabled = ConfigEdits.withScope( nether, scope -> ConfigEdits.withEnabled( scope, true ) );
		assertEquals( new ScopeRaw( true, "1h", false, "0 6 * * *", "SET", "" ), enabled.scope() );
		assertEquals( nether.targetDimension(), enabled.targetDimension() );
		DimensionRaw target = ConfigEdits.withTarget( nether, "minecraft:the_end", "1 2 3" );
		assertEquals( "minecraft:the_end", target.targetDimension() );
		assertEquals( "1 2 3", target.targetPosition() );
		assertEquals( nether.scope(), target.scope() );
	}
	
	@Test
	void newDimensionIsDisabled() {
		
		DimensionRaw dimension = ConfigEdits.newDimension( "mymod:mining" );
		assertEquals( "mymod:mining", dimension.dimension() );
		assertFalse( dimension.scope().enabled() );
		assertEquals( "minecraft:overworld", dimension.targetDimension() );
	}
	
	@Test
	void valueParsing() {
		
		assertEquals( "", ConfigEdits.cronValue( "none" ) );
		assertEquals( "0 8 * * 6,0", ConfigEdits.cronValue( " 0 8  * * 6,0 " ) );
		assertThrows( IllegalArgumentException.class, () -> ConfigEdits.cronValue( "0 25 * * *" ) );
		assertEquals( "", ConfigEdits.durationValue( "NONE", true ) );
		assertEquals( "1h30m", ConfigEdits.durationValue( "1h30m", false ) );
		assertThrows( IllegalArgumentException.class, () -> ConfigEdits.durationValue( "none", false ) );
		assertThrows( IllegalArgumentException.class, () -> ConfigEdits.durationValue( "0m", false ) );
		assertEquals( "ADD", ConfigEdits.modeValue( "add" ) );
		assertThrows( IllegalArgumentException.class, () -> ConfigEdits.modeValue( "multiply" ) );
	}
}
```

- [ ] **Step 2: Test ausführen, er muss fehlschlagen**

Run: `.\gradlew.bat test --tests "*ConfigEditsTest"`
Expected: FAIL beim Kompilieren.

- [ ] **Step 3: `ConfigEdits` implementieren**

```java
package de.geheimagentnr1.player_time_manager.commands;

import de.geheimagentnr1.player_time_manager.config.SettingsResolver;
import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;
import de.geheimagentnr1.player_time_manager.cron.CronExpression;
import de.geheimagentnr1.player_time_manager.time.DurationParser;
import de.geheimagentnr1.player_time_manager.time.RefillMode;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.function.UnaryOperator;


public final class ConfigEdits {
	
	
	private ConfigEdits() {
	
	}
	
	@NotNull
	public static DimensionRaw withScope( @NotNull DimensionRaw dimension, @NotNull UnaryOperator<ScopeRaw> change ) {
		
		return new DimensionRaw( dimension.dimension(), change.apply( dimension.scope() ), dimension.targetDimension(), dimension.targetPosition() );
	}
	
	@NotNull
	public static ScopeRaw withEnabled( @NotNull ScopeRaw scope, boolean value ) {
		
		return new ScopeRaw( value, scope.limit(), scope.useCustomRefill(), scope.refillCron(), scope.refillMode(), scope.refillMax() );
	}
	
	@NotNull
	public static ScopeRaw withLimit( @NotNull ScopeRaw scope, @NotNull String value ) {
		
		return new ScopeRaw( scope.enabled(), value, scope.useCustomRefill(), scope.refillCron(), scope.refillMode(), scope.refillMax() );
	}
	
	@NotNull
	public static ScopeRaw withUseCustomRefill( @NotNull ScopeRaw scope, boolean value ) {
		
		return new ScopeRaw( scope.enabled(), scope.limit(), value, scope.refillCron(), scope.refillMode(), scope.refillMax() );
	}
	
	@NotNull
	public static ScopeRaw withRefillCron( @NotNull ScopeRaw scope, @NotNull String value ) {
		
		return new ScopeRaw( scope.enabled(), scope.limit(), scope.useCustomRefill(), value, scope.refillMode(), scope.refillMax() );
	}
	
	@NotNull
	public static ScopeRaw withRefillMode( @NotNull ScopeRaw scope, @NotNull String value ) {
		
		return new ScopeRaw( scope.enabled(), scope.limit(), scope.useCustomRefill(), scope.refillCron(), value, scope.refillMax() );
	}
	
	@NotNull
	public static ScopeRaw withRefillMax( @NotNull ScopeRaw scope, @NotNull String value ) {
		
		return new ScopeRaw( scope.enabled(), scope.limit(), scope.useCustomRefill(), scope.refillCron(), scope.refillMode(), value );
	}
	
	@NotNull
	public static DimensionRaw withTarget( @NotNull DimensionRaw dimension, @NotNull String targetDimension, @NotNull String targetPosition ) {
		
		return new DimensionRaw( dimension.dimension(), dimension.scope(), targetDimension, targetPosition );
	}
	
	@NotNull
	public static DimensionRaw newDimension( @NotNull String id ) {
		
		return new DimensionRaw( id, new ScopeRaw( false, "1h", false, "0 6 * * *", "SET", "" ), SettingsResolver.OVERWORLD, "" );
	}
	
	@NotNull
	public static String cronValue( @NotNull String input ) {
		
		return input.trim().equalsIgnoreCase( "none" ) ? "" : CronExpression.parse( input ).expression();
	}
	
	@NotNull
	public static String durationValue( @NotNull String input, boolean clearable ) {
		
		String trimmed = input.trim();
		if( trimmed.equalsIgnoreCase( "none" ) ) {
			if( clearable ) {
				return "";
			}
			throw new IllegalArgumentException( "This value can't be empty, use a duration like \"2h\"" );
		}
		if( DurationParser.parse( trimmed ) <= 0 ) {
			throw new IllegalArgumentException( "The duration must be greater than 0" );
		}
		return trimmed;
	}
	
	@NotNull
	public static String modeValue( @NotNull String input ) {
		
		try {
			return RefillMode.valueOf( input.trim().toUpperCase( Locale.ROOT ) ).name();
		} catch( IllegalArgumentException exception ) {
			throw new IllegalArgumentException( "Unknown refill mode \"" + input + "\", use SET or ADD" );
		}
	}
}
```

Run: `.\gradlew.bat test --tests "*ConfigEditsTest"` - Expected: PASS (3 Tests).

- [ ] **Step 4: `PlaytimeCommand` implementieren**

```java
package de.geheimagentnr1.player_time_manager.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.geheimagentnr1.player_time_manager.config.DimensionEntries;
import de.geheimagentnr1.player_time_manager.config.ServerConfig;
import de.geheimagentnr1.player_time_manager.config.SettingsHolder;
import de.geheimagentnr1.player_time_manager.config.SettingsResolver;
import de.geheimagentnr1.player_time_manager.config.TargetPosition;
import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.PlayerRef;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;
import de.geheimagentnr1.player_time_manager.text.Messages;
import de.geheimagentnr1.player_time_manager.time.DurationParser;
import de.geheimagentnr1.player_time_manager.time.RefillMode;
import de.geheimagentnr1.player_time_manager.time.Scope;
import de.geheimagentnr1.player_time_manager.time.TimeEntry;
import de.geheimagentnr1.player_time_manager.time.TimeService;
import de.geheimagentnr1.player_time_manager.time.TimeState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;


public class PlaytimeCommand {
	
	
	@NotNull
	private final SettingsHolder settings;
	
	@NotNull
	private final Supplier<TimeService> service;
	
	public PlaytimeCommand( @NotNull SettingsHolder _settings, @NotNull Supplier<TimeService> _service ) {
		
		settings = _settings;
		service = _service;
	}
	
	private enum Change {
		ADD,
		REMOVE,
		SET
	}
	
	@FunctionalInterface
	private interface ValueParser {
		
		@NotNull
		String parse( @NotNull String input );
	}
	
	@NotNull
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		
		return Commands.literal( "playtime" )
			.executes( this::showOwn )
			.then( Commands.literal( "time" ).requires( this::isAdmin )
				.then( Commands.literal( "get" ).then( players()
					.executes( context -> get( context, null ) )
					.then( Commands.literal( "world" ).executes( context -> get( context, Scope.WORLD ) ) )
					.then( Commands.literal( "dimension" ).then( dimension().executes( context -> get( context, dimensionScope( context ) ) ) ) ) ) )
				.then( change( "add", Change.ADD ) )
				.then( change( "remove", Change.REMOVE ) )
				.then( change( "set", Change.SET ) )
				.then( Commands.literal( "refill" ).then( players()
					.then( Commands.literal( "world" ).executes( context -> refill( context, List.of( Scope.WORLD ) ) ) )
					.then( Commands.literal( "dimension" ).then( dimension().executes( context -> refill( context, List.of( dimensionScope( context ) ) ) ) ) )
					.then( Commands.literal( "all" ).executes( context -> refill( context, service().activeScopes() ) ) ) ) )
				.then( Commands.literal( "overview" )
					.executes( context -> overview( context, null ) )
					.then( Commands.literal( "world" ).executes( context -> overview( context, Scope.WORLD ) ) )
					.then( Commands.literal( "dimension" ).then( dimension().executes( context -> overview( context, dimensionScope( context ) ) ) ) ) ) )
			.then( Commands.literal( "config" ).requires( this::isAdmin )
				.then( Commands.literal( "general" )
					.then( stringKey( "general.refill_cron", "refill_cron", () -> config().read().general().refillCron(), config()::setGeneralRefillCron, ConfigEdits::cronValue, true ) )
					.then( stringKey( "general.refill_mode", "refill_mode", () -> config().read().general().refillMode(), value -> config().setGeneralRefillMode( RefillMode.valueOf( value ) ), ConfigEdits::modeValue, false ) )
					.then( stringKey( "general.refill_max", "refill_max", () -> config().read().general().refillMax(), config()::setGeneralRefillMax, input -> ConfigEdits.durationValue( input, true ), false ) )
					.then( boolKey( "general.ops_exempt", "ops_exempt", () -> config().read().general().opsExempt(), config()::setOpsExempt ) ) )
				.then( Commands.literal( "world" )
					.then( boolKey( "world.enabled", "enabled", () -> config().read().world().enabled(), config()::setWorldEnabled ) )
					.then( stringKey( "world.limit", "limit", () -> config().read().world().limit(), config()::setWorldLimit, input -> ConfigEdits.durationValue( input, false ), false ) )
					.then( boolKey( "world.use_custom_refill", "use_custom_refill", () -> config().read().world().useCustomRefill(), config()::setWorldUseCustomRefill ) )
					.then( stringKey( "world.refill_cron", "refill_cron", () -> config().read().world().refillCron(), config()::setWorldRefillCron, ConfigEdits::cronValue, true ) )
					.then( stringKey( "world.refill_mode", "refill_mode", () -> config().read().world().refillMode(), value -> config().setWorldRefillMode( RefillMode.valueOf( value ) ), ConfigEdits::modeValue, false ) )
					.then( stringKey( "world.refill_max", "refill_max", () -> config().read().world().refillMax(), config()::setWorldRefillMax, input -> ConfigEdits.durationValue( input, true ), false ) ) )
				.then( Commands.literal( "dimension" ).then( dimension()
					.executes( this::showDimension )
					.then( Commands.literal( "add" ).executes( this::addDimension ) )
					.then( Commands.literal( "remove" ).executes( this::removeDimension ) )
					.then( dimensionBool( "enabled", ScopeRaw::enabled, ConfigEdits::withEnabled ) )
					.then( dimensionString( "limit", ScopeRaw::limit, ConfigEdits::withLimit, input -> ConfigEdits.durationValue( input, false ), false ) )
					.then( dimensionBool( "use_custom_refill", ScopeRaw::useCustomRefill, ConfigEdits::withUseCustomRefill ) )
					.then( dimensionString( "refill_cron", ScopeRaw::refillCron, ConfigEdits::withRefillCron, ConfigEdits::cronValue, true ) )
					.then( dimensionString( "refill_mode", ScopeRaw::refillMode, ConfigEdits::withRefillMode, ConfigEdits::modeValue, false ) )
					.then( dimensionString( "refill_max", ScopeRaw::refillMax, ConfigEdits::withRefillMax, input -> ConfigEdits.durationValue( input, true ), false ) )
					.then( Commands.literal( "target" )
						.executes( this::showTarget )
						.then( Commands.literal( "here" ).executes( this::targetHere ) )
						.then( Commands.literal( "default" ).executes( context -> setTarget( context, SettingsResolver.OVERWORLD, "" ) ) )
						.then( Commands.argument( "target_dimension", DimensionArgument.dimension() )
							.then( Commands.argument( "position", StringArgumentType.greedyString() ).executes( this::targetPosition ) ) ) ) ) ) )
			.then( Commands.literal( "players" ).requires( this::isAdmin )
				.then( playerList( "limited", () -> config().read().general().limitedPlayers(), config()::setLimitedPlayers ) )
				.then( playerList( "exempt", () -> config().read().general().exemptPlayers(), config()::setExemptPlayers ) ) );
	}
	
	// ------------------------------------------------------------------ helpers
	
	private boolean isAdmin( @NotNull CommandSourceStack source ) {
		
		return settings.isLoaded() && source.hasPermission( settings.current().commandPermissionLevel() );
	}
	
	@NotNull
	private ServerConfig config() {
		
		return settings.config();
	}
	
	@NotNull
	private TimeService service() {
		
		TimeService current = service.get();
		if( current == null ) {
			throw new IllegalStateException( "Player Time Manager is not running" );
		}
		return current;
	}
	
	@NotNull
	private static RequiredArgumentBuilder<CommandSourceStack, GameProfileArgument.Result> players() {
		
		return Commands.argument( "players", GameProfileArgument.gameProfile() );
	}
	
	@NotNull
	private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> dimension() {
		
		return Commands.argument( "dimension", DimensionArgument.dimension() );
	}
	
	@NotNull
	private static String dimensionId( @NotNull CommandContext<CommandSourceStack> context, @NotNull String name ) throws CommandSyntaxException {
		
		return DimensionArgument.getDimension( context, name ).dimension().location().toString();
	}
	
	@NotNull
	private static Scope dimensionScope( @NotNull CommandContext<CommandSourceStack> context ) throws CommandSyntaxException {
		
		return Scope.dimension( dimensionId( context, "dimension" ) );
	}
	
	private static int success( @NotNull CommandContext<CommandSourceStack> context, @NotNull Component message ) {
		
		context.getSource().sendSuccess( () -> message, true );
		return 1;
	}
	
	private static int failure( @NotNull CommandContext<CommandSourceStack> context, @NotNull Component message ) {
		
		context.getSource().sendFailure( message );
		return 0;
	}
	
	private void reloadSettings( @NotNull CommandContext<CommandSourceStack> context ) {
		
		settings.reload( context.getSource().getServer() );
	}
	
	@NotNull
	private static String shown( @NotNull String value ) {
		
		return value.isEmpty() ? "\"\"" : value;
	}
	
	// ------------------------------------------------------------------ /playtime
	
	private int showOwn( @NotNull CommandContext<CommandSourceStack> context ) {
		
		ServerPlayer player = context.getSource().getPlayer();
		if( player == null ) {
			return failure( context, Messages.onlyPlayers() );
		}
		TimeService current = service();
		TimeState state = current.state( player );
		List<Scope> scopes = current.activeScopes();
		if( state == TimeState.EXCLUDED || scopes.isEmpty() ) {
			context.getSource().sendSystemMessage( Messages.statusNone() );
			return 1;
		}
		long now = System.currentTimeMillis();
		context.getSource().sendSystemMessage( Messages.statusHeader() );
		for( Scope scope : scopes ) {
			current.entry( scope, player.getUUID(), now, true ).ifPresent( entry -> context.getSource().sendSystemMessage( Messages.statusLine(
				Messages.scopeName( scope ),
				DurationParser.format( entry.remainingMillis() ),
				Messages.nextRefill( current.nextRefill( scope, now ).orElse( null ) ),
				state == TimeState.PAUSED
			) ) );
		}
		return 1;
	}
	
	// ------------------------------------------------------------------ /playtime time
	
	private int get( @NotNull CommandContext<CommandSourceStack> context, @Nullable Scope scope ) throws CommandSyntaxException {
		
		Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles( context, "players" );
		TimeService current = service();
		List<Scope> scopes = scope == null ? current.activeScopes() : List.of( scope );
		long now = System.currentTimeMillis();
		int count = 0;
		for( Scope target : scopes ) {
			if( !current.scopeSettings( target ).active() ) {
				failure( context, Messages.noLimit( Messages.scopeName( target ) ) );
				continue;
			}
			for( GameProfile profile : profiles ) {
				Optional<TimeEntry> entry = current.entry( target, profile.getId(), now, true );
				if( entry.isPresent() ) {
					success( context, Messages.timeGet( profile.getName(), Messages.scopeName( target ), DurationParser.format( entry.get().remainingMillis() ) ) );
					count++;
				}
			}
		}
		return count;
	}
	
	@NotNull
	private LiteralArgumentBuilder<CommandSourceStack> change( @NotNull String name, @NotNull Change change ) {
		
		return Commands.literal( name ).then( players()
			.then( Commands.literal( "world" ).then( duration().executes( context -> change( context, change, List.of( Scope.WORLD ) ) ) ) )
			.then( Commands.literal( "dimension" ).then( dimension().then( duration().executes( context -> change( context, change, List.of( dimensionScope( context ) ) ) ) ) ) )
			.then( Commands.literal( "all" ).then( duration().executes( context -> change( context, change, service().activeScopes() ) ) ) ) );
	}
	
	@NotNull
	private static RequiredArgumentBuilder<CommandSourceStack, String> duration() {
		
		return Commands.argument( "duration", StringArgumentType.word() );
	}
	
	private int change( @NotNull CommandContext<CommandSourceStack> context, @NotNull Change change, @NotNull List<Scope> scopes ) throws CommandSyntaxException {
		
		long millis;
		try {
			millis = DurationParser.parse( StringArgumentType.getString( context, "duration" ) );
		} catch( IllegalArgumentException exception ) {
			return failure( context, Messages.invalidValue( exception.getMessage() ) );
		}
		Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles( context, "players" );
		TimeService current = service();
		long now = System.currentTimeMillis();
		int count = 0;
		for( Scope scope : scopes ) {
			if( !current.scopeSettings( scope ).active() ) {
				failure( context, Messages.noLimit( Messages.scopeName( scope ) ) );
				continue;
			}
			for( GameProfile profile : profiles ) {
				long remaining = current.entry( scope, profile.getId(), now, true ).map( TimeEntry::remainingMillis ).orElse( 0L );
				long newRemaining = switch( change ) {
					case ADD -> remaining > Long.MAX_VALUE - millis ? Long.MAX_VALUE : remaining + millis;
					case REMOVE -> Math.max( 0, remaining - millis );
					case SET -> millis;
				};
				Optional<TimeEntry> changed = current.setRemaining( scope, profile.getId(), newRemaining, now );
				if( changed.isPresent() ) {
					success( context, Messages.timeChanged( profile.getName(), Messages.scopeName( scope ), DurationParser.format( changed.get().remainingMillis() ) ) );
					count++;
				}
			}
		}
		return count;
	}
	
	private int refill( @NotNull CommandContext<CommandSourceStack> context, @NotNull List<Scope> scopes ) throws CommandSyntaxException {
		
		Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles( context, "players" );
		TimeService current = service();
		long now = System.currentTimeMillis();
		int count = 0;
		for( Scope scope : scopes ) {
			if( !current.scopeSettings( scope ).active() ) {
				failure( context, Messages.noLimit( Messages.scopeName( scope ) ) );
				continue;
			}
			for( GameProfile profile : profiles ) {
				Optional<TimeEntry> refilled = current.refillNow( scope, profile.getId(), now );
				if( refilled.isPresent() ) {
					success( context, Messages.timeChanged( profile.getName(), Messages.scopeName( scope ), DurationParser.format( refilled.get().remainingMillis() ) ) );
					count++;
				}
			}
		}
		return count;
	}
	
	private int overview( @NotNull CommandContext<CommandSourceStack> context, @Nullable Scope scope ) {
		
		TimeService current = service();
		List<Scope> scopes = scope == null ? current.activeScopes() : List.of( scope );
		long now = System.currentTimeMillis();
		for( Scope target : scopes ) {
			if( !current.scopeSettings( target ).active() ) {
				failure( context, Messages.noLimit( Messages.scopeName( target ) ) );
				continue;
			}
			context.getSource().sendSystemMessage( Messages.overviewHeader( Messages.scopeName( target ) ) );
			Map<UUID, TimeEntry> entries = current.entries( target, now );
			if( entries.isEmpty() ) {
				context.getSource().sendSystemMessage( Messages.overviewEmpty() );
				continue;
			}
			entries.entrySet().stream()
				.sorted( Comparator.comparingLong( entry -> entry.getValue().remainingMillis() ) )
				.forEach( entry -> context.getSource().sendSystemMessage( Messages.overviewLine(
					current.server().getProfileCache().get( entry.getKey() ).map( GameProfile::getName ).orElse( entry.getKey().toString() ),
					DurationParser.format( entry.getValue().remainingMillis() )
				) ) );
		}
		return 1;
	}
	
	// ------------------------------------------------------------------ /playtime config general|world
	
	@NotNull
	private LiteralArgumentBuilder<CommandSourceStack> stringKey(
		@NotNull String fullKey,
		@NotNull String key,
		@NotNull Supplier<String> getter,
		@NotNull Consumer<String> setter,
		@NotNull ValueParser parser,
		boolean greedy ) {
		
		return Commands.literal( key )
			.executes( context -> success( context, Messages.configValue( fullKey, shown( getter.get() ) ) ) )
			.then( Commands.argument( "value", greedy ? StringArgumentType.greedyString() : StringArgumentType.word() )
				.suggests( ( context, builder ) -> key.equals( "refill_mode" )
					? SharedSuggestionProvider.suggest( List.of( "SET", "ADD" ), builder )
					: builder.buildFuture() )
				.executes( context -> {
					String value;
					try {
						value = parser.parse( StringArgumentType.getString( context, "value" ) );
					} catch( IllegalArgumentException exception ) {
						return failure( context, Messages.invalidValue( exception.getMessage() ) );
					}
					setter.accept( value );
					reloadSettings( context );
					return success( context, Messages.configSet( fullKey, shown( value ) ) );
				} ) );
	}
	
	@NotNull
	private LiteralArgumentBuilder<CommandSourceStack> boolKey(
		@NotNull String fullKey,
		@NotNull String key,
		@NotNull Supplier<Boolean> getter,
		@NotNull Consumer<Boolean> setter ) {
		
		return Commands.literal( key )
			.executes( context -> success( context, Messages.configValue( fullKey, String.valueOf( getter.get() ) ) ) )
			.then( Commands.argument( "value", BoolArgumentType.bool() ).executes( context -> {
				boolean value = BoolArgumentType.getBool( context, "value" );
				setter.accept( value );
				reloadSettings( context );
				return success( context, Messages.configSet( fullKey, String.valueOf( value ) ) );
			} ) );
	}
	
	// ------------------------------------------------------------------ /playtime config dimension
	
	private int updateDimension(
		@NotNull CommandContext<CommandSourceStack> context,
		@NotNull String key,
		@NotNull String shownValue,
		@NotNull UnaryOperator<DimensionRaw> change ) throws CommandSyntaxException {
		
		String id = dimensionId( context, "dimension" );
		List<DimensionRaw> dimensions = new ArrayList<>( config().read().dimensions() );
		int index = indexOf( dimensions, id );
		if( index < 0 ) {
			return failure( context, Messages.dimensionUnknown( id ) );
		}
		dimensions.set( index, change.apply( dimensions.get( index ) ) );
		config().setDimensions( dimensions );
		reloadSettings( context );
		return success( context, Messages.configSet( id + "." + key, shownValue ) );
	}
	
	private static int indexOf( @NotNull List<DimensionRaw> dimensions, @NotNull String id ) {
		
		for( int i = 0; i < dimensions.size(); i++ ) {
			if( dimensions.get( i ).dimension().trim().equals( id ) ) {
				return i;
			}
		}
		return -1;
	}
	
	@NotNull
	private Optional<DimensionRaw> findDimension( @NotNull String id ) {
		
		return config().read().dimensions().stream().filter( dimension -> dimension.dimension().trim().equals( id ) ).findFirst();
	}
	
	private int showDimensionValue( @NotNull CommandContext<CommandSourceStack> context, @NotNull String key, @NotNull Function<DimensionRaw, String> getter ) throws CommandSyntaxException {
		
		String id = dimensionId( context, "dimension" );
		Optional<DimensionRaw> dimension = findDimension( id );
		if( dimension.isEmpty() ) {
			return failure( context, Messages.dimensionUnknown( id ) );
		}
		return success( context, Messages.configValue( id + "." + key, getter.apply( dimension.get() ) ) );
	}
	
	@NotNull
	private LiteralArgumentBuilder<CommandSourceStack> dimensionString(
		@NotNull String key,
		@NotNull Function<ScopeRaw, String> getter,
		@NotNull BiFunction<ScopeRaw, String, ScopeRaw> setter,
		@NotNull ValueParser parser,
		boolean greedy ) {
		
		return Commands.literal( key )
			.executes( context -> showDimensionValue( context, key, dimension -> shown( getter.apply( dimension.scope() ) ) ) )
			.then( Commands.argument( "value", greedy ? StringArgumentType.greedyString() : StringArgumentType.word() )
				.suggests( ( context, builder ) -> key.equals( "refill_mode" )
					? SharedSuggestionProvider.suggest( List.of( "SET", "ADD" ), builder )
					: builder.buildFuture() )
				.executes( context -> {
					String value;
					try {
						value = parser.parse( StringArgumentType.getString( context, "value" ) );
					} catch( IllegalArgumentException exception ) {
						return failure( context, Messages.invalidValue( exception.getMessage() ) );
					}
					return updateDimension( context, key, shown( value ), dimension -> ConfigEdits.withScope( dimension, scope -> setter.apply( scope, value ) ) );
				} ) );
	}
	
	@NotNull
	private LiteralArgumentBuilder<CommandSourceStack> dimensionBool(
		@NotNull String key,
		@NotNull Function<ScopeRaw, Boolean> getter,
		@NotNull BiFunction<ScopeRaw, Boolean, ScopeRaw> setter ) {
		
		return Commands.literal( key )
			.executes( context -> showDimensionValue( context, key, dimension -> String.valueOf( getter.apply( dimension.scope() ) ) ) )
			.then( Commands.argument( "value", BoolArgumentType.bool() ).executes( context -> {
				boolean value = BoolArgumentType.getBool( context, "value" );
				return updateDimension( context, key, String.valueOf( value ), dimension -> ConfigEdits.withScope( dimension, scope -> setter.apply( scope, value ) ) );
			} ) );
	}
	
	// Shows all values of the entry and the config problems that belong to it
	private int showDimension( @NotNull CommandContext<CommandSourceStack> context ) throws CommandSyntaxException {
		
		String id = dimensionId( context, "dimension" );
		Optional<DimensionRaw> dimension = findDimension( id );
		if( dimension.isEmpty() ) {
			return failure( context, Messages.dimensionUnknown( id ) );
		}
		DimensionEntries.toValues( dimension.get() ).forEach( ( key, value ) ->
			context.getSource().sendSystemMessage( Messages.configValue( id + "." + key, shown( String.valueOf( value ) ) ) ) );
		settings.current().problems().stream()
			.filter( problem -> problem.contains( "\"" + id + "\"" ) )
			.forEach( problem -> context.getSource().sendFailure( Component.literal( problem ) ) );
		return 1;
	}
	
	private int addDimension( @NotNull CommandContext<CommandSourceStack> context ) throws CommandSyntaxException {
		
		String id = dimensionId( context, "dimension" );
		if( id.equals( SettingsResolver.OVERWORLD ) ) {
			return failure( context, Messages.overworldNotAllowed() );
		}
		List<DimensionRaw> dimensions = new ArrayList<>( config().read().dimensions() );
		if( indexOf( dimensions, id ) >= 0 ) {
			return failure( context, Messages.dimensionExists( id ) );
		}
		dimensions.add( ConfigEdits.newDimension( id ) );
		config().setDimensions( dimensions );
		reloadSettings( context );
		return success( context, Messages.dimensionAdded( id ) );
	}
	
	private int removeDimension( @NotNull CommandContext<CommandSourceStack> context ) throws CommandSyntaxException {
		
		String id = dimensionId( context, "dimension" );
		List<DimensionRaw> dimensions = new ArrayList<>( config().read().dimensions() );
		if( !dimensions.removeIf( dimension -> dimension.dimension().trim().equals( id ) ) ) {
			return failure( context, Messages.dimensionUnknown( id ) );
		}
		config().setDimensions( dimensions );
		reloadSettings( context );
		return success( context, Messages.dimensionRemoved( id ) );
	}
	
	private int showTarget( @NotNull CommandContext<CommandSourceStack> context ) throws CommandSyntaxException {
		
		return showDimensionValue( context, "target", dimension -> dimension.targetDimension() + " " + shown( dimension.targetPosition() ) );
	}
	
	private int targetHere( @NotNull CommandContext<CommandSourceStack> context ) throws CommandSyntaxException {
		
		ServerPlayer player = context.getSource().getPlayerOrException();
		String targetDimension = player.level().dimension().location().toString();
		String position = new TargetPosition( player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot() ).format();
		return setTarget( context, targetDimension, position );
	}
	
	private int targetPosition( @NotNull CommandContext<CommandSourceStack> context ) throws CommandSyntaxException {
		
		String targetDimension = dimensionId( context, "target_dimension" );
		String position;
		try {
			position = TargetPosition.parse( StringArgumentType.getString( context, "position" ) ).format();
		} catch( IllegalArgumentException exception ) {
			return failure( context, Messages.invalidValue( exception.getMessage() ) );
		}
		return setTarget( context, targetDimension, position );
	}
	
	private int setTarget( @NotNull CommandContext<CommandSourceStack> context, @NotNull String targetDimension, @NotNull String position ) throws CommandSyntaxException {
		
		String id = dimensionId( context, "dimension" );
		if( targetDimension.equals( id ) ) {
			return failure( context, Messages.invalidValue( "The target can't be inside the limited dimension itself" ) );
		}
		return updateDimension( context, "target", targetDimension + " " + shown( position ), dimension -> ConfigEdits.withTarget( dimension, targetDimension, position ) );
	}
	
	// ------------------------------------------------------------------ /playtime players
	
	@NotNull
	private LiteralArgumentBuilder<CommandSourceStack> playerList(
		@NotNull String list,
		@NotNull Supplier<List<PlayerRef>> getter,
		@NotNull Consumer<List<PlayerRef>> setter ) {
		
		return Commands.literal( list )
			.then( Commands.literal( "list" ).executes( context -> success( context, Messages.playersList(
				list,
				getter.get().stream().map( PlayerRef::name ).reduce( ( a, b ) -> a + ", " + b ).orElse( "-" )
			) ) ) )
			.then( Commands.literal( "add" ).then( players().executes( context -> {
				List<PlayerRef> players = new ArrayList<>( getter.get() );
				for( GameProfile profile : GameProfileArgument.getGameProfiles( context, "players" ) ) {
					if( players.stream().anyMatch( player -> player.uuid().equals( profile.getId().toString() ) ) ) {
						failure( context, Messages.playerAlreadyListed( list, profile.getName() ) );
					} else {
						players.add( new PlayerRef( profile.getName(), profile.getId().toString() ) );
						success( context, Messages.playerAdded( list, profile.getName() ) );
					}
				}
				setter.accept( players );
				reloadSettings( context );
				return 1;
			} ) ) )
			.then( Commands.literal( "remove" ).then( players().executes( context -> {
				List<PlayerRef> players = new ArrayList<>( getter.get() );
				for( GameProfile profile : GameProfileArgument.getGameProfiles( context, "players" ) ) {
					boolean removed = players.removeIf( player -> player.uuid().equals( profile.getId().toString() )
						|| player.uuid().isBlank() && player.name().equalsIgnoreCase( profile.getName() ) );
					if( removed ) {
						success( context, Messages.playerRemoved( list, profile.getName() ) );
					} else {
						failure( context, Messages.playerNotListed( list, profile.getName() ) );
					}
				}
				setter.accept( players );
				reloadSettings( context );
				return 1;
			} ) ) );
	}
}
```

- [ ] **Step 5: Befehlsregistrierung einkommentieren und bauen**

In `GameEventHandler` die in Task 12 auskommentierte Methode `handleRegisterCommands` und den Import `PlaytimeCommand` wieder aktivieren.

Run: `.\gradlew.bat build`
Expected: `BUILD SUCCESSFUL`, alle Unit-Tests grün.

- [ ] **Step 6: Commit**

```bash
git add -A src
git commit -m "Add /playtime command

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 14: Ingame-Tests (Server per RCON, Client mit dem User)

**Files:**
- Create: `C:\MinecraftModding\ActiveMods\Docs\testing\TESTPLAN_2026-10-player-time-manager.md` (Ergebnisse abhaken)
- Uses: `C:\MinecraftModding\Testpacks\TestPack 1.21.1` (Server), Client-Instanz `J:\software\Overwolf\Minecraft\Instances\NeoForge Test`, `Docs/testing/tools/pack.sh`, `Docs/testing/tools/rcon.py`

**Interfaces:**
- Consumes: das fertige Jar `build/libs/PlayerTimeManager-1.21.1-1.0.0.jar` aus Task 13.

Regeln aus `Docs/testing/README.md`: Jar in Server **und** Client; nie Jars im laufenden Client tauschen; Server-Configs nur bei gestopptem Server von Hand ändern; Client-Log lesen. Abkürzung unten: `R "<befehl>"` = `python3 Docs/testing/tools/rcon.py "/c/MinecraftModding/Testpacks/TestPack 1.21.1/server.properties" "<befehl>"` (aus `C:\MinecraftModding\ActiveMods`). `<spieler>` = Name des Test-Accounts des Users.

- [ ] **Step 1: Testplan-Datei anlegen**

`Docs/testing/TESTPLAN_2026-10-player-time-manager.md` mit den Schritten 3 - 17 dieses Tasks als Checkliste (`- [ ]`), Kopfzeile „Player Time Manager 1.0.0, MC 1.21.1, NeoForge 21.1.216, TestPack 1.21.1“.

- [ ] **Step 2: Jar verteilen (Server gestoppt, Client geschlossen)**

```bash
cd /c/MinecraftModding/ActiveMods
cp PlayerTimeManager/build/libs/PlayerTimeManager-1.21.1-1.0.0.jar "/c/MinecraftModding/Testpacks/TestPack 1.21.1/mods/"
cp PlayerTimeManager/build/libs/PlayerTimeManager-1.21.1-1.0.0.jar "/j/software/Overwolf/Minecraft/Instances/NeoForge Test/mods/"
bash Docs/testing/tools/pack.sh start neoforge 1.21.1
grep -a "Player Time Manager settings loaded" "/c/MinecraftModding/Testpacks/TestPack 1.21.1/logs/latest.log"
```
Expected: Server startet ohne ERROR mit `player_time_manager`, Log enthält `Player Time Manager settings loaded (0 problem(s))`.

- [ ] **Step 3: Config-Datei prüfen**

`world/serverconfig/player_time_manager-server.toml` öffnen. Expected: Abschnitte `[general]`, `[world]`, zwei `[[dimensions]]`-Einträge (Nether, End, `enabled = false`), **jeder Wert mit `#`-Kommentar**, Kommentar zu `refill_cron` enthält `https://crontab.guru`.

- [ ] **Step 4: Befehle schreiben die Config und überleben einen Neustart**

```bash
R "playtime config general ops_exempt false" "playtime config world enabled true" "playtime config world limit 3m" \
  "playtime config dimension minecraft:the_nether enabled true" "playtime config dimension minecraft:the_nether limit 1m" \
  "playtime config world refill_cron none" "playtime config world use_custom_refill true"
grep -a -A8 "^\[world\]" "/c/MinecraftModding/Testpacks/TestPack 1.21.1/world/serverconfig/player_time_manager-server.toml"
bash Docs/testing/tools/pack.sh stop neoforge 1.21.1 && bash Docs/testing/tools/pack.sh start neoforge 1.21.1
R "playtime config world limit" "playtime config dimension minecraft:the_nether limit" "playtime config general ops_exempt"
```
Expected: Datei enthält `enabled = true`, `limit = "3m"`, `refill_cron = ""`, `use_custom_refill = true`; nach dem Neustart `world.limit = 3m`, `minecraft:the_nether.limit = 1m`, `general.ops_exempt = false`. Kommentare der `[[dimensions]]`-Einträge sind nach dem Zurückschreiben noch vorhanden.

- [ ] **Step 5: Ungültige Werte per Befehl werden abgelehnt**

```bash
R "playtime config world limit 1x" "playtime config world refill_cron 0 25 * * *" "playtime config dimension minecraft:overworld add" "playtime config world refill_mode MULTIPLY"
```
Expected: vier Fehlermeldungen (`Invalid value: …`, Overworld-Hinweis), Datei unverändert.

- [ ] **Step 6: Tippfehler in der Datei löschen keinen Eintrag (Review Focus 2)**

Server stoppen, in der Datei beim Nether-Eintrag `limit = "1x"` setzen, Server starten.
Expected: Server startet; Log enthält `WARN … Player Time Manager config: dimensions[0] ("minecraft:the_nether").limit: Invalid duration "1x" …`; der Nether-Eintrag steht **noch in der Datei** (mit `"1x"`). Danach per `R "playtime config dimension minecraft:the_nether limit 1m"` reparieren.

- [ ] **Step 7: Client - Anzeige (mit dem User)**

User startet den Client (Instanz `NeoForge Test`) und tritt bei. Expected: Bossbar „World: 3m …“ (grün), zählt sekündlich runter; `/playtime` zeigt „Your remaining time:“ mit Welt-Zeile und „next refill: only by an admin“. Client-Log ohne Fehler zu `player_time_manager`.

- [ ] **Step 8: Dimensionszeit läuft ab - Teleport, Meldung, Sperre**

```bash
R "playtime time set <spieler> world 30m" "execute in minecraft:the_nether run tp <spieler> 0 100 0"
```
Nether:„Nether: 1m“; Warnungen bei `1m` (sofort, da Startwert 1m nicht >1m - keine Warnung) bzw. `10s` mit Glockenton; bei 0 Teleport zum Welt-Spawn der Overworld + Meldung „Your time in Nether is used up. Next refill: …“. Danach Portal betreten bzw. `R "execute in minecraft:the_nether run tp <spieler> 0 100 0"`: Reise wird abgebrochen, Meldung „You have no time left in Nether …“ höchstens alle 5 s.

- [ ] **Step 9: Zeit per Befehl geben, Übersicht, Offline-Spieler**

```bash
R "playtime time add <spieler> dimension minecraft:the_nether 2m" "playtime time get <spieler>" "playtime time overview"
```
Expected: Nether betretbar; `get` zeigt Welt- und Nether-Zeit; `overview` listet den Spieler aufsteigend. User loggt aus, `R "playtime time overview"` listet ihn weiterhin (Offline-Spieler), `R "playtime time add <spieler> world 1m"` funktioniert offline.

- [ ] **Step 10: Weltzeit läuft ab - Kick und Login-Sperre**

```bash
R "playtime time set <spieler> world 15s"
```
Expected: nach 15 s Kick mit „Your playtime is used up. Next refill: only by an admin“; erneuter Login wird sofort mit derselben Meldung getrennt. `R "playtime time set <spieler> world 10m"` → Login klappt wieder.

- [ ] **Step 11: Automatischer Refill per Cron**

```bash
R "playtime config dimension minecraft:the_nether use_custom_refill true" "playtime config dimension minecraft:the_nether refill_cron * * * * *" "playtime config dimension minecraft:the_nether refill_mode SET" "playtime time set <spieler> dimension minecraft:the_nether 5s"
```
Expected: Nach dem Wechsel des Zeitplans erst ab der **nächsten** vollen Minute Refill (kein Nachholen nach altem Schema); spätestens 2 Minuten später steht die Nether-Zeit wieder auf `1m`. Danach `R "playtime config dimension minecraft:the_nether refill_mode ADD" "playtime config dimension minecraft:the_nether refill_max 2m"` - die Zeit steigt jede Minute um 1m bis höchstens 2m.

- [ ] **Step 12: Pausen und Ausnahmen**

`R "gamemode creative <spieler>"` → Bossbar „(paused)“, Zeit steht; `R "gamemode survival <spieler>"` → zählt weiter. `R "playtime players exempt add <spieler>"` → Bossbars verschwinden, `/playtime` zeigt „No time limit applies to you.“; `R "playtime players exempt remove <spieler>"`.

- [ ] **Step 13: Zielpunkte (End-Plattform, eigener Punkt, ungültiges Ziel)**

```bash
R "playtime config dimension minecraft:the_nether target minecraft:the_end 0 0 0" "playtime config dimension minecraft:the_nether target default"
```
User steht an einer Stelle in der Overworld und führt selbst `/playtime config dimension minecraft:the_nether target here` aus (als OP mit `ops_exempt false`). Nether-Zeit auf 5s setzen und in den Nether teleportieren → Ablauf landet am gesetzten Punkt mit Blickrichtung. Dann Ziel per Datei auf `target_dimension = "minecraft:the_end"`, `target_position = ""` (Server gestoppt) → Ablauf landet auf der End-Obsidian-Plattform. Ziel `target_dimension = "minecraft:the_nether"` bei einem Mining-/Mod-Eintrag ohne Position → WARN beim Laden, OP-Hinweis beim Login, Ablauf landet am Welt-Spawn.

- [ ] **Step 14: Teleport von anderem Mod abgebrochen (Review Focus 4)**

Mit DimensionAccessManager im Pack: `R "dimension minecraft:overworld access lock"` (Overworld für den Spieler gesperrt), Nether-Zeit ablaufen lassen. Expected: kein Absturz, Log-WARN `could not teleport … out of minecraft:the_nether` höchstens alle 5 s, Meldung höchstens alle 5 s, kein Dauer-Spam. Danach `R "dimension minecraft:overworld access grant"` → nächster Versuch bringt den Spieler raus.

- [ ] **Step 15: Respawn in gesperrter Dimension**

Seelenanker im Nether setzen/aufladen, Nether-Zeit auf 0, Spieler stirbt in der Overworld → Respawn wird direkt zum Zielpunkt umgeleitet (nicht im Nether).

- [ ] **Step 16: Vanilla-Client-Verhalten und Singleplayer**

Client schließen, Jar aus der **Client**-Instanz entfernen, beitreten: Beitritt klappt (Server-only-Mod), Texte auf Englisch (Fallback), Bossbars sichtbar. Jar zurück in die Client-Instanz. Singleplayer-Welt mit Cheats erstellen: ohne Config-Änderung keine Limits (OP-Ausnahme); `ops_exempt false` + `world enabled true` per Befehl → Bossbar erscheint, Ablauf schickt ins Hauptmenü.

- [ ] **Step 17: Persistenz und Neustart**

Restzeiten setzen (`R "playtime time set <spieler> world 7m"`, Nether `2m`), Server per `pack.sh stop` stoppen und starten, `R "playtime time get <spieler>"`. Expected: Werte erhalten (abzüglich gespielter Zeit). Die Restzeiten liegen in `world/data/neoforge_data_attachments.dat` (Overworld) bzw. `world/DIM-1/data/neoforge_data_attachments.dat` (Nether).

- [ ] **Step 18: Abschluss**

Testplan abhaken, Fehler als eigene Fix-Commits (mit Test, wo möglich) beheben und betroffene Schritte wiederholen. Test-Config im Pack auf Standard zurücksetzen (Datei löschen, wird neu erzeugt). Commit im Mod-Repo nur bei Code-Fixes.

---

### Task 15: Push, CI und Doku

**Files:**
- Create: `C:\MinecraftModding\ActiveMods\Docs\mod-claude\PlayerTimeManager.md`, `PlayerTimeManager/docs/superpowers/specs/2026-10-13-player-time-manager-design.md`, `PlayerTimeManager/docs/superpowers/plans/2026-10-14-player-time-manager-1.21.1.md`
- Modify: `C:\MinecraftModding\ActiveMods\CLAUDE.md` (Mods-Übersicht), `Docs/VERSIONEN.md`, `MOD_KOMPATIBILITAET.md`, `0__env.cmd`, `Docs/README.md` (Spec-Verweis)

- [ ] **Step 1: Mod-CLAUDE.md anlegen und verteilen**

`Docs/mod-claude/PlayerTimeManager.md` nach dem Schema der anderen Vorlagen (Abschnitte Versionen, Abhängigkeiten, Architektur, Funktionen/Befehle/Config, Fallstricke, Testen, Links) aus Spec und Code schreiben, inkl. der Fallstricke aus diesem Plan (Listen-Validator, keine eigenen Argumenttypen, Attachments per Codec, Teleport-Verhalten 1.21.1). Dann:
```bash
cd /c/MinecraftModding/ActiveMods && bash Docs/tools/sync_mod_claude_md.sh --apply PlayerTimeManager
```

- [ ] **Step 2: Spec und Plan ins Repo verschieben**

```bash
cd /c/MinecraftModding/ActiveMods
mkdir -p PlayerTimeManager/docs/superpowers/specs PlayerTimeManager/docs/superpowers/plans
mv Docs/specs/2026-10-13-player-time-manager-design.md PlayerTimeManager/docs/superpowers/specs/
mv Docs/plans/2026-10-14-player-time-manager-1.21.1.md PlayerTimeManager/docs/superpowers/plans/
git -C PlayerTimeManager add docs && git -C PlayerTimeManager commit -m "Add design spec and implementation plan

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
In `Docs/README.md` den Spec-Verweis unter `specs/` auf `PlayerTimeManager/docs/superpowers/specs/2026-10-13-player-time-manager-design.md` ändern. Die Namensvorschlags-Dateien `Docs/specs/2026-10-14-zeitlimit-mod-namensvorschlaege*.md` nach `Docs/specs/archiv/2026-10/` verschieben.

- [ ] **Step 3: Workspace-Doku**

- `CLAUDE.md`: Überblick „20 Minecraft-Mods“; Zeile in der Mods-Übersicht: `| [PlayerTimeManager](PlayerTimeManager/) | player_time_manager | Serverseitig (Client optional, keine Dist-Sperre) | Zeitlimits für Welt und Dimensionen mit Cron-Refill, Befehl /playtime |`.
- `Docs/VERSIONEN.md`: neue Zeile „Player Time Manager - `develop_1.21.1`, Range `[1.21.1,1.21.2)`, Version 1.0.0 (noch nicht released); Ports 1.21.2 - 26.3 folgen (Folgeplan)“.
- `MOD_KOMPATIBILITAET.md`: Zeile analog zu den anderen Mods (1.21.1, in Arbeit).
- `0__env.cmd`: nach `MODS[18]` einfügen:
```
SET MODS[19].MOD_ID=player_time_manager
SET MODS[19].MOD_NAME=PlayerTimeManager
SET MODS[19].DIRECTORY=PlayerTimeManager
SET MODS[19].VERSION=1.0.0
SET MODS[19].DEPENDENCY_LEVEL=1
```
und `SET MODS_LAST_INDEX=19`.

- [ ] **Step 4: Pushen - NUR nach ausdrücklicher Freigabe des Users**

Das Repo existiert schon (vom User angelegt). User fragen: „`master` (README-Fix) und `develop_1.21.1` nach `GeheimagentNr1/PlayerTimeManager` pushen?“ Erst nach „Ja“:
```bash
cd /c/MinecraftModding/ActiveMods/PlayerTimeManager
git push origin master
git push -u origin develop_1.21.1
bash ../Docs/ci/ci_status.sh --wait PlayerTimeManager
```
Expected: Workflow „Build and Test“ grün (keine privaten Abhängigkeiten, kein Secret nötig).

- [ ] **Step 5: Abschluss und Folgeschritte**

- Projektseiten (Task 16) vor dem ersten Release.
- Release von `1.21.1-1.0.0` nur auf Anweisung des Users über den Skill `release-mod` (CurseForge vor Modrinth), erst wenn Task 16 erledigt ist.
- Folgeplan für die Ports 1.21.2 - 26.3 (Spec Abschnitt 11) mit `superpowers:writing-plans` erstellen.
- Dem User den Skill „neuen Mod anlegen“ vorschlagen (Spec Abschnitt 13), aufgebaut aus den tatsächlich ausgeführten Schritten von Task 1, Task 15 und Task 16.
- Antwortentwurf für DimensionAccessManager GH#7 vorbereiten, sobald der Mod veröffentlicht ist (Skill `triage-feedback`).

---

### Task 16: Projektseiten (Wiki, Modrinth, CurseForge)

Vor dem ersten Release, nach Task 14 (Config-Schlüssel, Befehle und Texte stehen dann fest). Skill `update-mod-pages`, Regeln aus `Docs/releasing/project-pages.md`: alles erst als Entwurf zeigen, veröffentlichen nur nach OK des Users. Reihenfolge: Modrinth per API → **Chrome-Modus** → Wiki (Erstseite im Browser, Rest per Git) → **CurseForge zuletzt**.

**Files (Scratchpad, dann veröffentlicht):**
- Wiki `GeheimagentNr1/PlayerTimeManager.wiki`: `Home.md`, `_Sidebar.md`, `1.X.X.md`, `Changelog-1.0.0.md`
- Modrinth-Projekt `zb9AY963`: `body` (= `1.X.X.md`), Kategorien, Umgebung
- CurseForge-Projekt `1752337`: Beschreibung (= `1.X.X.md`)

**Vorlage:** Wiki von DimensionAccessManager (`Home.md`, `_Sidebar.md`, `4.X.X.md`). Formatierung wie dort: Zeilenumbruch durch zwei Leerzeichen am Zeilenende, LF, Changelog-Seite ohne abschließenden Zeilenumbruch.

Entschieden (User, 2026-10-18): Fußzeile wie bei den anderen Mods mit Logo-Dank an Muse31 und **demselben Mod-Loader-Banner** (`mod_loader_banner.png`), obwohl der Forge-Teil nicht zutrifft - der User tauscht es ggf. später aus.

- [ ] **Step 1: Entwürfe schreiben**

Den Text von `1.X.X.md` gegen den fertigen Code prüfen: Config-Schlüssel und Kommentare (`ServerConfig`, Task 6), Befehle (`PlaytimeCommand`, Task 13), Standardwerte. Abweichungen im Entwurf korrigieren, nicht im Code.

`Home.md`:
````markdown
Welcome to the Player Time Manager wiki!  

This is a Minecraft NeoForge mod.  
It can be found on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/player-time-manager) and [Modrinth](https://modrinth.com/mod/player-time-manager).  

## Versions
* [[1.X.X]] (Current)

## Support
![Supported Versions Image](https://raw.githubusercontent.com/GeheimagentNr1/Online_Mod_Data/master/Supported_Versions.png)  
Minecraft versions, for which the mod is not released, are not supported.  
You can find a text version [here](https://github.com/GeheimagentNr1/Online_Mod_Data/wiki/Supported-Versions).  

## Changelogs
* [[1.0.0|Changelog 1.0.0]]
````

`_Sidebar.md`:
````markdown
[[Home]] (Overview)  

## Versions
* [[1.X.X]] (Current)

## Changelogs
* [[1.0.0|Changelog 1.0.0]]

[Download](https://www.curseforge.com/minecraft/mc-mods/player-time-manager/files)
````

`Changelog-1.0.0.md`: Inhalt von `CHANGELOG.md` aus Task 1 (ohne BOM, ohne abschließenden Zeilenumbruch).

`1.X.X.md`:
````markdown
## Version 1.X.X  
For older versions of the mod, check the [wiki](https://github.com/GeheimagentNr1/PlayerTimeManager/wiki).  

This Mod limits how long players can play: in the whole world (e.g. 2 hours per day) and in single dimensions (e.g. 1 hour per day in the Nether).  
The remaining time is refilled automatically by a schedule in cron format and can be changed per player by command.  
If the time in a dimension is used up, the player is teleported to a target point and can't enter the dimension again until the next refill.  
If the world time is used up, the player is kicked and can't join again until the next refill.  
The Mod is only needed on the server side, but can also be used in singleplayer. Players with a vanilla client can join.  

---

You need a server to play with friends? You don't know how to set up one? Just rent an already configured server!  

[![BisectHosting Promotion Banner](https://raw.githubusercontent.com/GeheimagentNr1/Online_Mod_Data/master/images/bisect_hosting_banner.png)](https://bisecthosting.com/agent)  

Click on the picture above, select a plan (with recommended at least 6 GB),  
use my code **[agent](https://bisecthosting.com/agent)** to get **25%** off your first month and enjoy playing with your friends!  

---

## Support  
![Supported Versions Image](https://raw.githubusercontent.com/GeheimagentNr1/Online_Mod_Data/master/Supported_Versions.png)  
Minecraft versions, for which the mod is not released, are not supported.  
You can find a text version [here](https://github.com/GeheimagentNr1/Online_Mod_Data/wiki/Supported-Versions).  

## Time Limits  
There is one time limit for the whole world (all dimensions together) and one time limit for each configured dimension.  
Both run in parallel: The world time counts down in all dimensions, the time of a dimension additionally only in this dimension.  
All players have the same limits, only their remaining time differs. New players start with the full limit.  
The time counts down in real time while a player is online. Server lag does not extend the play time and the time does not count down while a singleplayer world is paused.  
After installation, all limits are disabled. Without changes to the config, the mod changes nothing.  

### Dimension time used up  
- The player is teleported to the target point of the dimension and gets a message with the date and time of the next refill.  
- The player can't enter the dimension again until the next refill (portals, /tp and other mods).  
- If the player logs in or respawns in the dimension, the player is teleported to the target point.  
- Minecraft 1.21.1: A player riding an animal or a vehicle dismounts before the teleport. Starting with Minecraft 1.21.2, the animal or vehicle is teleported together with the player, unless other players are riding it, too.  

### World time used up  
- The player is kicked with a message with the date and time of the next refill.  
- The player can't join again until the next refill. In singleplayer, the player returns to the main menu.  

## Refill  
The remaining time is refilled automatically by a refill rule:  
- **Schedule** in cron format (see below). An empty schedule means no automatic refill, the time can then only be given by command.  
- **Mode** SET: The remaining time is set to the limit.  
- **Mode** ADD: The limit is added to the remaining time. A maximum can limit the remaining time after an ADD refill.  

The refill rule in [general] applies to the world and all dimensions. The world or a single dimension can use an own rule with "use_custom_refill = true".  
Refills missed while the server was offline are made up on the next start (SET once, ADD once per missed refill).  
A changed schedule applies from the moment of the change, a changed limit applies with the next refill.  

### Cron format  
A schedule has 5 fields: minute hour day-of-month month weekday.  
The schedules work like on [crontab.guru](https://crontab.guru), where you can create and check them.  
The time zone is set with "time_zone" (default: time zone of the server).  
Examples:  
- `0 6 * * *` - every day at 06:00  
- `0 14 * * 1-5` - Monday to Friday at 14:00  
- `0 8 * * 6,0` - Saturday and Sunday at 08:00  
- `0 6 * * 1` - every Monday at 06:00 (e.g. a weekly limit)  
- `0 */6 * * *` - every 6 hours  

## Target Point  
Every dimension has a target point, where players are teleported to, if their time in this dimension is used up.  
It consists of a target dimension (default: Overworld) and a target position "x y z" or "x y z yaw pitch".  
Without a position, the target is the world spawn in the Overworld and the obsidian platform in the End. All other target dimensions need a position.  
The easiest way to set the target point is to stand at the position and run `/playtime config dimension <dimension> target here`.  
If the target point is invalid or the player has no time left in the target dimension, the player is teleported to the world spawn in the Overworld.  
The Overworld can't be limited as a dimension, use the world limit instead.  

## Display and Warnings  
The remaining time is shown depending on "display_mode":  
- **NONE**: no display, only the command /playtime and the chat warnings  
- **ACTIONBAR**: text above the hotbar  
- **BOSSBARS**: one boss bar per running limit (default)  
- **BOSSBAR_SHORTEST**: one boss bar with the limit that runs out first  

The boss bars turn yellow below 50 % and red below 10 % of the limit (configurable).  
Chat warnings with sound are sent at 10 minutes, 5 minutes, 1 minute and 10 seconds remaining time (configurable).  
All texts are translatable. Players with a vanilla client see English texts.  

## Exemptions  
The time does not count down for:  
- Operators, if "ops_exempt" is true (default).  
  **Singleplayer:** With cheats allowed, you are an operator. Set "ops_exempt" to false to limit yourself.  
- Players in creative mode or spectator mode (both configurable).  
- Players on the exempt players list.  
- Players not on the limited players list, if this list is not empty.  

## Server Config  
The config file can be found in the "config" folder of the server and is named "player\_time\_manager-server.toml". In singleplayer it is in the "config" folder of the game (".minecraft") and applies to all worlds. If a world already has the file in its "serverconfig" folder, that file is used.  
Every value is explained by a comment in the file. Changes to the file are applied while the server is running.  
Invalid values never stop the server: You get a warning in the log and as operator in the chat on login, and a safe value is used instead.  
Durations are written like "2h", "30m", "1h30m" or "45s".  

- **general**  
  - **command\_permission\_level**: Permission level (0 - 4) required for all admin commands. Default = 3  
  - **ops\_exempt**: Operators are not affected by any time limit. Default = true  
  - **pause\_in\_creative** / **pause\_in\_spectator**: The time does not count down in creative / spectator mode. Default = true  
  - **limited\_players**: If not empty, only these players are affected by the time limits.  
  - **exempt\_players**: These players are never affected by the time limits.  
  - **time\_zone**: Time zone of the schedules, e.g. "Europe/Berlin". Default = "" (time zone of the server)  
  - **refill\_cron**, **refill\_mode**, **refill\_max**: Refill rule for the world and all dimensions. Default = "0 6 * * *", "SET", ""  
  - **display\_mode**: NONE, ACTIONBAR, BOSSBARS or BOSSBAR\_SHORTEST. Default = "BOSSBARS"  
  - **bossbar\_yellow\_below\_percent** / **bossbar\_red\_below\_percent**: Colors of the boss bars. Default = 50 / 10  
  - **warning\_times**: Remaining times for chat warnings. Default = ["10m", "5m", "1m", "10s"]  
  - **warning\_sound**: Play a sound with each chat warning. Default = true  
- **world**  
  - **enabled**: Is the world time limit active? Default = false  
  - **limit**: Time per refill. Default = "2h"  
  - **use\_custom\_refill**, **refill\_cron**, **refill\_mode**, **refill\_max**: Own refill rule for the world.  
- **dimensions** (one entry per dimension, the config contains disabled entries for the Nether and the End)  
  - **dimension**: Name of the dimension, e.g. "minecraft:the\_nether"  
  - **enabled**, **limit**, **use\_custom\_refill**, **refill\_cron**, **refill\_mode**, **refill\_max**: like in world  
  - **target\_dimension**, **target\_position**: Target point (see above)  

## Commands  
Players are selected like for /whitelist: names (also of offline players, who have already been on the server) or selectors.  
`<scope>` is `world` or `dimension <dimension>`.  
Commands without a value show the current value. Values set by command are saved to the config file.  
- **/playtime**  
  Shows your own remaining times and the next refills. Available for all players.  
- **/playtime time get \<players\> [\<scope\>]**  
  Shows the remaining times of the players.  
- **/playtime time add|remove|set \<players\> \<scope\>|all \<duration\>**  
  Changes the remaining time of the players.  
- **/playtime time refill \<players\> \<scope\>|all**  
  Refills the remaining time of the players now.  
- **/playtime time overview [\<scope\>]**  
  Shows the remaining times of all known players, sorted ascending.  
- **/playtime config general refill\_cron|refill\_mode|refill\_max|ops\_exempt [\<value\>]**  
  Shows or sets a value in [general]. The schedule is written without quotes at the end, e.g. `/playtime config general refill_cron 0 8 * * 6,0`. "none" clears refill\_cron or refill\_max.  
- **/playtime config world enabled|limit|use\_custom\_refill|refill\_cron|refill\_mode|refill\_max [\<value\>]**  
  Shows or sets a value of the world limit.  
- **/playtime config dimension \<dimension\>**  
  Shows the settings of the dimension and problems with them.  
- **/playtime config dimension \<dimension\> add|remove**  
  Adds or removes the time limit entry of the dimension.  
- **/playtime config dimension \<dimension\> enabled|limit|use\_custom\_refill|refill\_cron|refill\_mode|refill\_max [\<value\>]**  
  Shows or sets a value of the dimension limit.  
- **/playtime config dimension \<dimension\> target here**  
  Sets the target point to your current position and viewing direction.  
- **/playtime config dimension \<dimension\> target \<target dimension\> \<x y z\> [\<yaw pitch\>]**  
  Sets the target point.  
- **/playtime config dimension \<dimension\> target default**  
  Resets the target point (Overworld, world spawn).  
- **/playtime players limited|exempt list|add|remove [\<players\>]**  
  Shows or changes the limited players list or the exempt players list.  

---

Thanks for the logo to Muse31.  
You can use this mod in non-commercial modpacks without asking.  
You can find more mods from me on [CurseForge](https://www.curseforge.com/members/geheimagentnr1/projects) and [Modrinth](https://modrinth.com/user/GeheimagentNr1).  

![This is a Forge mod only until 1.21.1. This is a NeoForge mod only starting 1.21.1. There will be no Fabric version. It will not be backported.](https://raw.githubusercontent.com/GeheimagentNr1/Online_Mod_Data/master/images/mod_loader_banner.png)  
````

User die vier Entwürfe zeigen (Pfade im Scratchpad) und Freigabe einholen.

- [ ] **Step 2: Modrinth setzen (per API, nach Freigabe)**

Token aus `~/.gradle/gradle.properties` (`modrinthToken`), nie ausgeben. Vorher den aktuellen Stand sichern (`GET /v2/project/zb9AY963` mit Token → Scratchpad `modrinth_before.json`).
```powershell
$t = (Select-String -Path $env:USERPROFILE\.gradle\gradle.properties -Pattern '^modrinthToken=(.*)$').Matches[0].Groups[1].Value
$body = [IO.File]::ReadAllText("<scratchpad>\wiki\1.X.X.md")
$json = @{ body = $body; categories = @("management", "utility"); client_side = "unsupported"; server_side = "required" } | ConvertTo-Json
Invoke-WebRequest -Method Patch "https://api.modrinth.com/v2/project/zb9AY963" -Headers @{ Authorization = $t } -ContentType "application/json; charset=utf-8" -Body ([Text.Encoding]::UTF8.GetBytes($json))
```
Expected: HTTP 204. Prüfen mit Token und Cache-Buster (`?x=<zufall>`): `body` gleich der Datei, Kategorien und Umgebung gesetzt, Icon/Links unverändert. Umgebung wie WorldPreGenerator (Modrinth zeigt „server only, works in singleplayer“).

- [ ] **Step 3: In den Chrome-Modus wechseln**

Dem User sagen: „Bitte jetzt in den Chrome-Modus umschalten (eingeloggt bei GitHub und CurseForge). Ich lege dort die erste Wiki-Seite an und bereite die CurseForge-Beschreibung vor.“ Weiter erst, wenn der User bestätigt.

- [ ] **Step 4: Wiki anlegen**

Das Wiki-Git-Repo existiert erst nach der ersten Seite (`git clone …PlayerTimeManager.wiki.git` liefert sonst „Repository not found“). Hat der User inzwischen selbst eine Seite angelegt, diesen Teil überspringen.
1. Im Browser `https://github.com/GeheimagentNr1/PlayerTimeManager/wiki` → „Create the first page“, Titel `Home`, Inhalt `Home.md`, „Save page“ (nach OK des Users).
2. Dann per Git:
```bash
cd <scratchpad>
git clone https://github.com/GeheimagentNr1/PlayerTimeManager.wiki.git ptm-wiki
cp wiki/Home.md wiki/_Sidebar.md wiki/1.X.X.md wiki/Changelog-1.0.0.md ptm-wiki/
cd ptm-wiki && git add Home.md _Sidebar.md 1.X.X.md Changelog-1.0.0.md && git status --short && git diff --cached --stat
git commit -m "Add wiki pages for 1.X.X and changelog 1.0.0" && git push origin master
```
Expected: Wiki zeigt Home mit Seitenmenü, `1.X.X` und `Changelog 1.0.0` sind verlinkt und öffnen sich.

- [ ] **Step 5: CurseForge-Beschreibung (zuletzt, im Chrome-Modus)**

1. `https://authors.curseforge.com/#/projects/1752337/description` öffnen, aktuellen Text sichern (Scratchpad), Editor im Markdown-Modus.
2. Inhalt von `1.X.X.md` per nativem Setter + `input`-Event einsetzen (Vorgehen in `Docs/releasing/project-pages.md`, Abschnitt „CurseForge (Browser)“).
3. User prüft Vorschau (Banner verlinkt, Listen, Cron-Beispiele); „Save“ nur nach OK.
4. Prüfen: Seite neu laden und Editor lesen bzw. `fetch('/_api/projects/description/1752337')`; das HTML muss `<a href="https://bisecthosting.com/agent" …><img …bisect_hosting_banner.png` enthalten.
5. Außerdem prüfen und dem User melden, was fehlt: Projekt-Icon (sonst lädt der User `images/PlayerTimeManager.png` hoch), Kurzbeschreibung, Kategorien, Links (Issues, Source, Wiki).

- [ ] **Step 6: Abschluss**

- `Docs/releasing/project-pages.md`: Abschnitt „Neue Mod: Erstanlage“ ergänzen (Wiki-Erstseite nur im Browser, Modrinth-Kategorien/Umgebung per API, CurseForge im Chrome-Modus zuletzt, Modrinth-Projekt nach dem ersten Upload zur Prüfung einreichen).
- Nach dem ersten Release (Skill `release-mod`) den User daran erinnern, das Modrinth-Projekt zur Prüfung einzureichen (Status `draft`, nur der User reicht ein).
