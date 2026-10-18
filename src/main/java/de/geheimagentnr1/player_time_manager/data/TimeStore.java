package de.geheimagentnr1.player_time_manager.data;

import de.geheimagentnr1.player_time_manager.time.Scope;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
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
		
		Identifier location = Identifier.tryParse( dimension );
		return location == null ? null : server.getLevel( ResourceKey.create( Registries.DIMENSION, location ) );
	}
}
