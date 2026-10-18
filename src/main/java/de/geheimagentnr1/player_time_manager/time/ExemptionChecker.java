package de.geheimagentnr1.player_time_manager.time;

import de.geheimagentnr1.player_time_manager.config.resolved.PlayerRules;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;


public final class ExemptionChecker {
	
	
	private ExemptionChecker() {
	
	}
	
	@NotNull
	public static TimeState state( @NotNull PlayerFacts facts, @NotNull PlayerRules rules ) {
		
		String name = facts.name().toLowerCase( Locale.ROOT );
		if( rules.opsExempt() && facts.op() ) {
			return TimeState.EXCLUDED;
		}
		if( rules.exemptIds().contains( facts.uuid() ) || rules.exemptNames().contains( name ) ) {
			return TimeState.EXCLUDED;
		}
		if( rules.hasLimitedList() && !rules.limitedIds().contains( facts.uuid() ) && !rules.limitedNames().contains( name ) ) {
			return TimeState.EXCLUDED;
		}
		if( facts.creative() && rules.pauseInCreative() || facts.spectator() && rules.pauseInSpectator() ) {
			return TimeState.PAUSED;
		}
		return TimeState.COUNTING;
	}
}
