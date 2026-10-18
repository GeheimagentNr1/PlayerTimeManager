package de.geheimagentnr1.player_time_manager.teleport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class SpawnChoiceTest {
	
	
	@Test
	void worldSpawnInABlockedDimensionFallsBackToTheOverworld() {
		
		assertTrue( SpawnChoice.useWorldSpawnDimension( "minecraft:overworld", dimension -> false ) );
		assertTrue( SpawnChoice.useWorldSpawnDimension( "minecraft:the_nether", dimension -> false ) );
		assertFalse( SpawnChoice.useWorldSpawnDimension( "minecraft:the_nether", dimension -> dimension.equals( "minecraft:the_nether" ) ) );
	}
}
