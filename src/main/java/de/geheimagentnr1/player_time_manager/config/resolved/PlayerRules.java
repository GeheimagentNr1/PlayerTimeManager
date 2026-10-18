package de.geheimagentnr1.player_time_manager.config.resolved;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;


public record PlayerRules(
	boolean opsExempt,
	boolean pauseInCreative,
	boolean pauseInSpectator,
	@NotNull Set<UUID> limitedIds,
	@NotNull Set<String> limitedNames,
	@NotNull Set<UUID> exemptIds,
	@NotNull Set<String> exemptNames ) {
	
	
	public boolean hasLimitedList() {
		
		return !limitedIds.isEmpty() || !limitedNames.isEmpty();
	}
}
