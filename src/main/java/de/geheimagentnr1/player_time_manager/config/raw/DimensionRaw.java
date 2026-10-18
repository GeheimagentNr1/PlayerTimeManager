package de.geheimagentnr1.player_time_manager.config.raw;

import org.jetbrains.annotations.NotNull;


public record DimensionRaw(
	@NotNull String dimension,
	@NotNull ScopeRaw scope,
	@NotNull String targetDimension,
	@NotNull String targetPosition ) {

}
