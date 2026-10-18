package de.geheimagentnr1.player_time_manager.teleport;

import de.geheimagentnr1.player_time_manager.config.TargetPosition;
import de.geheimagentnr1.player_time_manager.config.resolved.TargetSpec;
import de.geheimagentnr1.player_time_manager.data.TimeStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
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
			return worldSpawn( server, player, blockedDimension );
		}
		ServerLevel level = TimeStore.level( server, target.dimension() );
		if( level == null ) {
			return worldSpawn( server, player, blockedDimension );
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
		return worldSpawn( server, player, blockedDimension );
	}
	
	@NotNull
	public static Destination worldSpawn(
		@NotNull MinecraftServer server,
		@NotNull ServerPlayer player,
		@NotNull Predicate<String> blockedDimension ) {
		
		LevelData.RespawnData respawn = server.getRespawnData();
		ServerLevel level = server.findRespawnDimension();
		BlockPos position = respawn.pos();
		if( !SpawnChoice.useWorldSpawnDimension( level.dimension().identifier().toString(), blockedDimension ) ) {
			// Same x/z on the surface of the Overworld, which can't be limited
			level = server.overworld();
			position = level.getHeightmapPos( Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, position );
		}
		// Like vanilla: a free place within the spawn radius, not inside blocks
		Vec3 target = Vec3.atBottomCenterOf( player.adjustSpawnLocation( level, position ) );
		return new Destination( level, target, respawn.yaw(), respawn.pitch() );
	}
}
