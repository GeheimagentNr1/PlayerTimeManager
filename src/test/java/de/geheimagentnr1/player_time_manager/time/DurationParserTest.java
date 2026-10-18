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
}
