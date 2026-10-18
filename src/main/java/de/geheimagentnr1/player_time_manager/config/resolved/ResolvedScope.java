package de.geheimagentnr1.player_time_manager.config.resolved;

import de.geheimagentnr1.player_time_manager.time.RefillRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public record ResolvedScope( boolean active, long limitMillis, @Nullable RefillRule refillRule ) {
	
	
	@NotNull
	public static final ResolvedScope INACTIVE = new ResolvedScope( false, 0, null );
}
