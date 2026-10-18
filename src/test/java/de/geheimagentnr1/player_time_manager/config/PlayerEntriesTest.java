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
		
		assertTrue( ConfigTables.isTable( com.electronwill.nightconfig.core.Config.inMemory() ) );
		assertFalse( ConfigTables.isTable( "minecraft:the_nether" ) );
	}
	
	@Test
	void matchesByUuidOrByNameWithoutUuid() {
		
		assertTrue( PlayerEntries.matches( new PlayerRef( "Alex", "u1" ), "Steve", "u1" ) );
		assertTrue( PlayerEntries.matches( new PlayerRef( "Steve", "" ), "steve", "u1" ) );
		assertFalse( PlayerEntries.matches( new PlayerRef( "Steve", "u2" ), "Steve", "u1" ) );
	}
}
