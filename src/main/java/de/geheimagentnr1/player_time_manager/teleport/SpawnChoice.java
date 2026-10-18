package de.geheimagentnr1.player_time_manager.teleport;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;


// From Minecraft 1.21.9 the world spawn can be in any dimension
public final class SpawnChoice {
	
	
	@NotNull
	private static final String OVERWORLD = "minecraft:overworld";
	
	private SpawnChoice() {
	
	}
	
	// A world spawn in a dimension without remaining time would send the player back again and again
	public static boolean useWorldSpawnDimension( @NotNull String worldSpawnDimension, @NotNull Predicate<String> blockedDimension ) {
		
		return worldSpawnDimension.equals( OVERWORLD ) || !blockedDimension.test( worldSpawnDimension );
	}
}
