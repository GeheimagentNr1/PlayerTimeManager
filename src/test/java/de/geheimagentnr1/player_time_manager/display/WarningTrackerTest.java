package de.geheimagentnr1.player_time_manager.display;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;


class WarningTrackerTest {
	
	
	private static final List<Long> THRESHOLDS = List.of( 600_000L, 300_000L, 60_000L, 10_000L );
	
	@Test
	void warnsOnceWhenCrossing() {
		
		assertEquals( Optional.of( 300_000L ), WarningTracker.crossedThreshold( 300_500, 299_500, THRESHOLDS ) );
		assertEquals( Optional.of( 300_000L ), WarningTracker.crossedThreshold( 301_000, 300_000, THRESHOLDS ) );
		assertEquals( Optional.empty(), WarningTracker.crossedThreshold( 299_500, 298_500, THRESHOLDS ) );
	}
	
	@Test
	void warnsLowestThresholdWhenSeveralAreCrossed() {
		
		assertEquals( Optional.of( 60_000L ), WarningTracker.crossedThreshold( 700_000, 30_000, THRESHOLDS ) );
	}
	
	@Test
	void increasesNeverWarn() {
		
		assertEquals( Optional.empty(), WarningTracker.crossedThreshold( 30_000, 700_000, THRESHOLDS ) );
		assertEquals( Optional.empty(), WarningTracker.crossedThreshold( 30_000, 30_000, List.of() ) );
	}
}
