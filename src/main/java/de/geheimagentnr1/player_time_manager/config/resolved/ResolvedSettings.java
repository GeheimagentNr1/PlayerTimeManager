package de.geheimagentnr1.player_time_manager.config.resolved;

import org.jetbrains.annotations.NotNull;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;


public record ResolvedSettings(
	int commandPermissionLevel,
	@NotNull PlayerRules playerRules,
	@NotNull ZoneId zone,
	@NotNull DisplaySettings display,
	@NotNull ResolvedScope world,
	@NotNull Map<String, ResolvedDimension> dimensions,
	@NotNull List<String> problems ) {

}
