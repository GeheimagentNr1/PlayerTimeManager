package de.geheimagentnr1.player_time_manager.cron;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


class CronExpressionTest {
	
	
	private static final ZoneId BERLIN = ZoneId.of( "Europe/Berlin" );
	
	private static ZonedDateTime at( int year, int month, int day, int hour, int minute ) {
		
		return ZonedDateTime.of( year, month, day, hour, minute, 0, 0, BERLIN );
	}
	
	private static ZonedDateTime next( String expression, ZonedDateTime after ) {
		
		return CronExpression.parse( expression ).next( after ).orElseThrow();
	}
	
	@Test
	void dailyAtSix() {
		
		assertEquals( at( 2026, 10, 14, 6, 0 ), next( "0 6 * * *", at( 2026, 10, 14, 5, 59 ) ) );
		assertEquals( at( 2026, 10, 15, 6, 0 ), next( "0 6 * * *", at( 2026, 10, 14, 6, 0 ) ) );
	}
	
	@Test
	void workdaysAndWeekendsIncludingNamesAndSeven() {
		
		// 2026-10-16 is a Friday, 2026-10-17 a Saturday
		assertEquals( at( 2026, 10, 19, 14, 0 ), next( "0 14 * * 1-5", at( 2026, 10, 16, 15, 0 ) ) );
		assertEquals( at( 2026, 10, 17, 8, 0 ), next( "0 8 * * 6,0", at( 2026, 10, 14, 12, 0 ) ) );
		assertEquals( at( 2026, 10, 17, 8, 0 ), next( "0 8 * * SAT,SUN", at( 2026, 10, 14, 12, 0 ) ) );
		assertEquals( at( 2026, 10, 18, 8, 0 ), next( "0 8 * * 7", at( 2026, 10, 14, 12, 0 ) ) );
		assertEquals( at( 2026, 10, 19, 6, 0 ), next( "0 6 * oct mon", at( 2026, 10, 14, 12, 0 ) ) );
	}
	
	@Test
	void dayOfMonthOrDayOfWeekWhenBothRestricted() {
		
		// 2026-12-13 is a Sunday: matches "13th OR Friday"
		assertEquals( at( 2026, 12, 13, 0, 0 ), next( "0 0 13 * 5", at( 2026, 12, 12, 0, 0 ) ) );
		// 2026-10-16 is a Friday
		assertEquals( at( 2026, 10, 16, 0, 0 ), next( "0 0 13 * 5", at( 2026, 10, 14, 0, 0 ) ) );
	}
	
	@Test
	void starPrefixedDayFieldCombinesWithAnd() {
		
		assertEquals( at( 2026, 10, 21, 0, 0 ), next( "0 0 */10 * *", at( 2026, 10, 14, 0, 0 ) ) );
	}
	
	@Test
	void stepsRangesAndLeapDay() {
		
		assertEquals( at( 2026, 10, 14, 10, 15 ), next( "*/15 * * * *", at( 2026, 10, 14, 10, 7 ) ) );
		assertEquals( at( 2026, 10, 14, 10, 20 ), next( "0-30/10 * * * *", at( 2026, 10, 14, 10, 15 ) ) );
		assertEquals( at( 2028, 2, 29, 0, 0 ), next( "0 0 29 2 *", at( 2026, 10, 14, 0, 0 ) ) );
	}
	
	@Test
	void skipsNonExistingTimeAtSpringForward() {
		
		// 2027-03-28 02:30 doesn't exist in Berlin
		assertEquals( at( 2027, 3, 29, 2, 30 ), next( "30 2 * * *", at( 2027, 3, 27, 3, 0 ) ) );
	}
	
	@Test
	void countsDoubledTimeOnceAtFallBack() {
		
		// 2026-10-25 02:30 exists twice in Berlin
		ZonedDateTime first = next( "30 2 * * *", at( 2026, 10, 25, 0, 0 ) );
		assertEquals( 2, first.getHour() );
		assertEquals( 25, first.getDayOfMonth() );
		assertEquals( at( 2026, 10, 26, 2, 30 ), next( "30 2 * * *", first ) );
	}
	
	@Test
	void betweenRespectsBoundsAndLimit() {
		
		CronExpression hourly = CronExpression.parse( "0 * * * *" );
		List<ZonedDateTime> times = hourly.between( at( 2026, 10, 14, 10, 0 ), at( 2026, 10, 14, 13, 0 ), 100 );
		assertEquals( List.of( at( 2026, 10, 14, 11, 0 ), at( 2026, 10, 14, 12, 0 ), at( 2026, 10, 14, 13, 0 ) ), times );
		assertEquals( 5, hourly.between( at( 2026, 10, 14, 0, 0 ), at( 2026, 10, 15, 0, 0 ), 5 ).size() );
	}
	
	@Test
	void normalizesExpression() {
		
		assertEquals( "0 6 * * 1-5", CronExpression.parse( "  0  6 * *   1-5 " ).expression() );
	}
	
	@Test
	void rejectsInvalidExpressions() {
		
		for( String invalid : new String[] {
			"", "* * * *", "* * * * * *", "60 * * * *", "* 24 * * *", "* * 0 * *", "* * * 13 *", "* * * * 8",
			"5/15 * * * *", "* * * * FOO", "a * * * *", "10-5 * * * *", "*/0 * * * *", "1,,2 * * * *", "0 0 30 2 *"
		} ) {
			assertThrows( CronParseException.class, () -> CronExpression.parse( invalid ), invalid );
		}
	}
}
