package de.geheimagentnr1.player_time_manager.time;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public record Scope( @Nullable String dimension ) {
	
	
	@NotNull
	public static final Scope WORLD = new Scope( null );
	
	@NotNull
	public static Scope dimension( @NotNull String id ) {
		
		return new Scope( id );
	}
	
	public boolean isWorld() {
		
		return dimension == null;
	}
}
