package de.geheimagentnr1.player_time_manager.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


class DurationParserTest {


	@Test
	void parsesSingleUnits() {

		assertEquals( 7_200_000L, DurationParser.parse( "2h" ) );
		assertEquals( 1_800_000L, DurationParser.parse( "30m" ) );
		assertEquals( 45_000L, DurationParser.parse( "45s" ) );
		assertEquals( 0L, DurationParser.parse( "0m" ) );
	}

	@Test
	void parsesCombinationsSpacesAndUpperCase() {

		assertEquals( 5_400_000L, DurationParser.parse( "1h30m" ) );
		assertEquals( 5_400_000L, DurationParser.parse( "1h 30m" ) );
		assertEquals( 5_025_000L, DurationParser.parse( "1H23M45S" ) );
	}

	@Test
	void rejectsInvalidInput() {

		for( String invalid : new String[] { "", " ", "2x", "h", "1.5h", "30m1h", "-5m", "99999999999999999999h" } ) {
			assertThrows( IllegalArgumentException.class, () -> DurationParser.parse( invalid ), invalid );
		}
	}

	@Test
	void formatsWithoutZeroUnits() {

		assertEquals( "0s", DurationParser.format( 0 ) );
		assertEquals( "0s", DurationParser.format( -5000 ) );
		assertEquals( "1s", DurationParser.format( 1 ) );
		assertEquals( "1h", DurationParser.format( 3_600_000 ) );
		assertEquals( "1h 5s", DurationParser.format( 3_605_000 ) );
		assertEquals( "1h 23m 45s", DurationParser.format( 5_025_000 ) );
		assertEquals( "30m", DurationParser.format( 1_800_000 ) );
	}

	@Test
	void formatsAsClock() {

		assertEquals( "0:00", DurationParser.formatClock( 0 ) );
		assertEquals( "0:00", DurationParser.formatClock( -5000 ) );
		assertEquals( "0:01", DurationParser.formatClock( 1 ) );
		assertEquals( "0:09", DurationParser.formatClock( 9_000 ) );
		assertEquals( "4:05", DurationParser.formatClock( 245_000 ) );
		assertEquals( "29:59", DurationParser.formatClock( 1_799_000 ) );
		assertEquals( "30:00", DurationParser.formatClock( 1_800_000 ) );
		assertEquals( "1:00:00", DurationParser.formatClock( 3_600_000 ) );
		assertEquals( "1:30:05", DurationParser.formatClock( 5_405_000 ) );
		assertEquals( "23:59:59", DurationParser.formatClock( 86_399_000 ) );
		assertEquals( "1d 00:00:00", DurationParser.formatClock( 86_400_000 ) );
		assertEquals( "2d 03:00:00", DurationParser.formatClock( 183_600_000 ) );
	}

	@Test
	void formatsDateTimeDayFirst() {

		java.time.ZonedDateTime time = java.time.ZonedDateTime.of( 2026, 10, 19, 6, 0, 0, 0, java.time.ZoneId.of( "Europe/Berlin" ) );
		assertEquals( "19.10.2026 06:00", DurationParser.formatDateTime( time ) );
	}
}
