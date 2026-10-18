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
