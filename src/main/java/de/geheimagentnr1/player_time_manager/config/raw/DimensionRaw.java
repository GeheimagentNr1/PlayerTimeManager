package de.geheimagentnr1.player_time_manager.config.raw;

import org.jetbrains.annotations.NotNull;

import java.util.List;


public record DimensionRaw(
	@NotNull String dimension,
	@NotNull ScopeRaw scope,
	@NotNull String targetDimension,
	@NotNull String targetPosition,
	@NotNull List<String> invalidValues ) {
	
	
	// invalidValues: values that are not a boolean, e.g. "enabled = 1" (read as false)
	public DimensionRaw( @NotNull String dimension, @NotNull ScopeRaw scope, @NotNull String targetDimension, @NotNull String targetPosition ) {
		
		this( dimension, scope, targetDimension, targetPosition, List.of() );
	}
}
