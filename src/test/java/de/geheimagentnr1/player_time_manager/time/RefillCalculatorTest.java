package de.geheimagentnr1.player_time_manager.time;

import de.geheimagentnr1.player_time_manager.cron.CronExpression;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;


class RefillCalculatorTest {
	
	
	private static final ZoneId BERLIN = ZoneId.of( "Europe/Berlin" );
	
	private static final long MINUTE = 60_000L;
	
	private static long at( int day, int hour, int minute ) {
		
		return ZonedDateTime.of( 2026, 10, day, hour, minute, 0, 0, BERLIN ).toInstant().toEpochMilli();
	}
	
	private static RefillRule rule( String cron, RefillMode mode, long limit, Long max ) {
		
		return new RefillRule( cron == null ? null : CronExpression.parse( cron ), mode, limit, max );
	}
	
	@Test
	void createStartsWithFullLimit() {
		
		RefillRule rule = rule( "0 6 * * *", RefillMode.SET, 60 * MINUTE, null );
		assertEquals( new TimeEntry( 60 * MINUTE, at( 14, 12, 0 ), "0 6 * * *" ), RefillCalculator.create( rule, at( 14, 12, 0 ) ) );
	}
	
	@Test
	void setRefillsOnceToLimit() {
		
		RefillRule rule = rule( "0 6 * * *", RefillMode.SET, 60 * MINUTE, null );
		TimeEntry entry = new TimeEntry( 5 * MINUTE, at( 14, 12, 0 ), "0 6 * * *" );
		TimeEntry result = RefillCalculator.apply( entry, rule, at( 17, 7, 0 ), BERLIN );
		assertEquals( 60 * MINUTE, result.remainingMillis() );
		assertEquals( at( 17, 6, 0 ), result.lastRefillMillis() );
	}
	
	@Test
	void addRefillsEveryMissedOccurrence() {
		
		RefillRule rule = rule( "0 * * * *", RefillMode.ADD, 10 * MINUTE, null );
		TimeEntry entry = new TimeEntry( 0, at( 14, 10, 0 ), "0 * * * *" );
		TimeEntry result = RefillCalculator.apply( entry, rule, at( 14, 13, 30 ), BERLIN );
		assertEquals( 30 * MINUTE, result.remainingMillis() );
		assertEquals( at( 14, 13, 0 ), result.lastRefillMillis() );
	}
	
	@Test
	void addIsCappedByMax() {
		
		RefillRule rule = rule( "0 * * * *", RefillMode.ADD, 10 * MINUTE, 25 * MINUTE );
		TimeEntry entry = new TimeEntry( 0, at( 14, 10, 0 ), "0 * * * *" );
		assertEquals( 25 * MINUTE, RefillCalculator.apply( entry, rule, at( 14, 13, 30 ), BERLIN ).remainingMillis() );
	}
	
	@Test
	void addNeverCutsTimeAboveMax() {
		
		RefillRule rule = rule( "0 * * * *", RefillMode.ADD, 10 * MINUTE, 25 * MINUTE );
		TimeEntry entry = new TimeEntry( 90 * MINUTE, at( 14, 10, 0 ), "0 * * * *" );
		assertEquals( 90 * MINUTE, RefillCalculator.apply( entry, rule, at( 14, 11, 30 ), BERLIN ).remainingMillis() );
	}
	
	@Test
	void noOccurrenceKeepsEntry() {
		
		RefillRule rule = rule( "0 6 * * *", RefillMode.SET, 60 * MINUTE, null );
		TimeEntry entry = new TimeEntry( 5 * MINUTE, at( 14, 7, 0 ), "0 6 * * *" );
		assertEquals( entry, RefillCalculator.apply( entry, rule, at( 14, 23, 0 ), BERLIN ) );
	}
	
	@Test
	void changedScheduleResetsWithoutRefill() {
		
		RefillRule rule = rule( "0 8 * * *", RefillMode.SET, 60 * MINUTE, null );
		TimeEntry entry = new TimeEntry( 5 * MINUTE, at( 10, 7, 0 ), "0 6 * * *" );
		assertEquals( new TimeEntry( 5 * MINUTE, at( 14, 12, 0 ), "0 8 * * *" ), RefillCalculator.apply( entry, rule, at( 14, 12, 0 ), BERLIN ) );
	}
	
	@Test
	void noCronKeepsEntry() {
		
		RefillRule rule = rule( null, RefillMode.SET, 60 * MINUTE, null );
		TimeEntry entry = new TimeEntry( 5 * MINUTE, at( 10, 7, 0 ), "" );
		assertEquals( entry, RefillCalculator.apply( entry, rule, at( 14, 12, 0 ), BERLIN ) );
	}
	
	@Test
	void catchUpIsLimited() {
		
		RefillRule rule = rule( "* * * * *", RefillMode.ADD, 1_000L, null );
		TimeEntry entry = new TimeEntry( 0, at( 1, 0, 0 ), "* * * * *" );
		TimeEntry result = RefillCalculator.apply( entry, rule, at( 31, 0, 0 ), BERLIN );
		assertEquals( RefillCalculator.MAX_CATCH_UP * 1_000L, result.remainingMillis() );
		// The rest is dropped: the next call must not catch up another batch
		assertEquals( at( 31, 0, 0 ), result.lastRefillMillis() );
		assertEquals( result, RefillCalculator.apply( result, rule, at( 31, 0, 0 ), BERLIN ) );
	}
	
	@Test
	void refillNowKeepsSchedulePosition() {
		
		RefillRule set = rule( "0 6 * * *", RefillMode.SET, 60 * MINUTE, null );
		RefillRule add = rule( "0 6 * * *", RefillMode.ADD, 60 * MINUTE, 90 * MINUTE );
		TimeEntry entry = new TimeEntry( 45 * MINUTE, at( 14, 6, 0 ), "0 6 * * *" );
		assertEquals( new TimeEntry( 60 * MINUTE, at( 14, 6, 0 ), "0 6 * * *" ), RefillCalculator.refillNow( entry, set ) );
		assertEquals( new TimeEntry( 90 * MINUTE, at( 14, 6, 0 ), "0 6 * * *" ), RefillCalculator.refillNow( entry, add ) );
	}
}
