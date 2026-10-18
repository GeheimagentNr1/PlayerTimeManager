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
