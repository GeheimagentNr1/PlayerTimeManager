package de.geheimagentnr1.player_time_manager.display;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;


public final class WarningTracker {
	
	
	private WarningTracker() {
	
	}
	
	@NotNull
	public static Optional<Long> crossedThreshold( long before, long after, @NotNull List<Long> thresholdsDescending ) {
		
		Optional<Long> crossed = Optional.empty();
		for( long threshold : thresholdsDescending ) {
			if( before > threshold && after <= threshold ) {
				crossed = Optional.of( threshold );
			}
		}
		return crossed;
	}
}
