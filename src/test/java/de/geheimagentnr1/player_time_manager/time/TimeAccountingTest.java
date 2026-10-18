package de.geheimagentnr1.player_time_manager.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


class TimeAccountingTest {
	
	
	@Test
	void countsNormalIntervalsAndDropsGaps() {
		
		assertEquals( 0, TimeAccounting.elapsedToCount( -1, 5_000 ) );
		assertEquals( 1_000, TimeAccounting.elapsedToCount( 4_000, 5_000 ) );
		assertEquals( 10_000, TimeAccounting.elapsedToCount( 0, 10_000 ) );
		assertEquals( 0, TimeAccounting.elapsedToCount( 0, 10_001 ) );
		assertEquals( 0, TimeAccounting.elapsedToCount( 5_000, 4_000 ) );
	}
}
