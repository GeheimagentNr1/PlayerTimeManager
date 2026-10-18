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
