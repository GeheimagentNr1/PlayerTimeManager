package de.geheimagentnr1.player_time_manager.config.raw;

import org.jetbrains.annotations.NotNull;


public record ScopeRaw(
	boolean enabled,
	@NotNull String limit,
	boolean useCustomRefill,
	@NotNull String refillCron,
	@NotNull String refillMode,
	@NotNull String refillMax ) {

}
