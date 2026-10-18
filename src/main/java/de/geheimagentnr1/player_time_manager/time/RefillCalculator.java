package de.geheimagentnr1.player_time_manager.time;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;


public final class RefillCalculator {
	
	
	public static final int MAX_CATCH_UP = 10_000;
	
	private RefillCalculator() {
	
	}
	
	@NotNull
	public static TimeEntry create( @NotNull RefillRule rule, long nowMillis ) {
		
		return new TimeEntry( rule.limitMillis(), nowMillis, rule.scheduleId() );
	}
	
	@NotNull
	public static TimeEntry apply( @NotNull TimeEntry entry, @NotNull RefillRule rule, long nowMillis, @NotNull ZoneId zone ) {
		
		if( !entry.scheduleId().equals( rule.scheduleId() ) ) {
			return new TimeEntry( entry.remainingMillis(), nowMillis, rule.scheduleId() );
		}
		if( rule.cron() == null ) {
			return entry;
		}
		List<ZonedDateTime> due = rule.cron().between(
			Instant.ofEpochMilli( entry.lastRefillMillis() ).atZone( zone ),
			Instant.ofEpochMilli( nowMillis ).atZone( zone ),
			MAX_CATCH_UP
		);
		if( due.isEmpty() ) {
			return entry;
		}
		long remaining = entry.remainingMillis();
		if( rule.mode() == RefillMode.SET ) {
			remaining = rule.limitMillis();
		} else {
			for( int i = 0; i < due.size(); i++ ) {
				remaining = add( remaining, rule );
			}
		}
		return new TimeEntry( remaining, due.getLast().toInstant().toEpochMilli(), entry.scheduleId() );
	}
	
	@NotNull
	public static TimeEntry refillNow( @NotNull TimeEntry entry, @NotNull RefillRule rule ) {
		
		long remaining = rule.mode() == RefillMode.SET ? rule.limitMillis() : add( entry.remainingMillis(), rule );
		return new TimeEntry( remaining, entry.lastRefillMillis(), entry.scheduleId() );
	}
	
	private static long add( long remaining, @NotNull RefillRule rule ) {
		
		long current = Math.max( 0, remaining );
		long sum = current > Long.MAX_VALUE - rule.limitMillis() ? Long.MAX_VALUE : current + rule.limitMillis();
		if( rule.maxMillis() == null ) {
			return sum;
		}
		// Never cut time that is already above the maximum (e.g. set by an admin)
		return Math.max( current, Math.min( sum, rule.maxMillis() ) );
	}
}
