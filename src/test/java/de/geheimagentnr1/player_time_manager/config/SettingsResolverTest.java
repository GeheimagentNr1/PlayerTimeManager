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
	
	@Test
	void reportsNonBooleanValuesOfADimension() {
		
		DimensionRaw nether = DimensionEntries.fromValues( java.util.Map.of( "dimension", "minecraft:the_nether", "enabled", 1 ) );
		ResolvedSettings settings = resolve( settings( scope( false, "2h" ), nether ) );
		assertTrue( hasProblem( settings, "dimensions[0] (\"minecraft:the_nether\").enabled = 1" ) );
	}
}
