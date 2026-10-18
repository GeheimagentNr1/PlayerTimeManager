package de.geheimagentnr1.player_time_manager.teleport;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Set;


public final class PlayerTeleporter {
	
	
	private PlayerTeleporter() {
	
	}
	
	// A ridden vehicle is teleported with all passengers like through a portal, unless other players ride it, too
	public static boolean teleport( @NotNull ServerPlayer player, @NotNull Destination destination ) {
		
		if( player.isSleeping() ) {
			player.stopSleepInBed( true, true );
		}
		Entity vehicle = player.getRootVehicle();
		boolean riding = vehicle != player;
		int otherPlayerPassengers = riding ? vehicle.countPlayerPassengers() - 1 : 0;
		if( TeleportPlan.decide( riding, otherPlayerPassengers ) == TeleportPlan.Mode.WHOLE_VEHICLE ) {
			vehicle.teleport( new TeleportTransition(
				destination.level(),
				destination.position(),
				Vec3.ZERO,
				destination.yaw(),
				destination.pitch(),
				TeleportTransition.DO_NOTHING
			) );
		} else {
			player.stopRiding();
			player.teleportTo(
				destination.level(),
				destination.position().x(),
				destination.position().y(),
				destination.position().z(),
				Set.of(),
				destination.yaw(),
				destination.pitch(),
				true
			);
		}
		return player.level() == destination.level();
	}
}
