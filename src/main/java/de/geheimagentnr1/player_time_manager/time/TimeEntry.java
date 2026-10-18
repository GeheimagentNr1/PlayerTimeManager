package de.geheimagentnr1.player_time_manager.time;

import org.jetbrains.annotations.NotNull;


public record TimeEntry( long remainingMillis, long lastRefillMillis, @NotNull String scheduleId ) {
	
	
	@NotNull
	public TimeEntry withRemaining( long newRemainingMillis ) {
		
		return new TimeEntry( Math.max( 0, newRemainingMillis ), lastRefillMillis, scheduleId );
	}
}
