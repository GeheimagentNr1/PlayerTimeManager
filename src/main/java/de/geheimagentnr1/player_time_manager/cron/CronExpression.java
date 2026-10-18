package de.geheimagentnr1.player_time_manager.cron;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;


public final class CronExpression {
	
	
	private static final int SEARCH_DAYS = 366 * 5;
	
	@NotNull
	private static final String[] MONTH_NAMES =
		{ "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC" };
	
	@NotNull
	private static final String[] DAY_NAMES = { "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT" };
	
	@NotNull
	private final String expression;
	
	@NotNull
	private final BitSet minutes;
	
	@NotNull
	private final BitSet hours;
	
	@NotNull
	private final BitSet daysOfMonth;
	
	@NotNull
	private final BitSet months;
	
	@NotNull
	private final BitSet daysOfWeek;
	
	private final boolean dayOfMonthStar;
	
	private final boolean dayOfWeekStar;
	
	private CronExpression(
		@NotNull String _expression,
		@NotNull BitSet _minutes,
		@NotNull BitSet _hours,
		@NotNull BitSet _daysOfMonth,
		@NotNull BitSet _months,
		@NotNull BitSet _daysOfWeek,
		boolean _dayOfMonthStar,
		boolean _dayOfWeekStar ) {
		
		expression = _expression;
		minutes = _minutes;
		hours = _hours;
		daysOfMonth = _daysOfMonth;
		months = _months;
		daysOfWeek = _daysOfWeek;
		dayOfMonthStar = _dayOfMonthStar;
		dayOfWeekStar = _dayOfWeekStar;
	}
	
	@NotNull
	public static CronExpression parse( @NotNull String expression ) {
		
		String trimmed = expression.trim();
		String[] fields = trimmed.isEmpty() ? new String[0] : trimmed.split( "\\s+" );
		if( fields.length != 5 ) {
			throw new CronParseException( String.format(
				"Cron expression \"%s\" needs 5 fields (minute hour day month weekday), found %d",
				expression,
				fields.length
			) );
		}
		BitSet daysOfWeek = parseField( fields[4], 0, 7, DAY_NAMES, 0, "weekday" );
		if( daysOfWeek.get( 7 ) ) {
			daysOfWeek.set( 0 );
			daysOfWeek.clear( 7 );
		}
		CronExpression cron = new CronExpression(
			String.join( " ", fields ),
			parseField( fields[0], 0, 59, null, 0, "minute" ),
			parseField( fields[1], 0, 23, null, 0, "hour" ),
			parseField( fields[2], 1, 31, null, 0, "day" ),
			parseField( fields[3], 1, 12, MONTH_NAMES, 1, "month" ),
			daysOfWeek,
			fields[2].startsWith( "*" ),
			fields[4].startsWith( "*" )
		);
		if( cron.next( ZonedDateTime.of( 2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC ) ).isEmpty() ) {
			throw new CronParseException( String.format( "Cron expression \"%s\" never matches", expression ) );
		}
		return cron;
	}
	
	@NotNull
	private static BitSet parseField(
		@NotNull String field,
		int min,
		int max,
		@Nullable String[] names,
		int nameOffset,
		@NotNull String fieldName ) {
		
		BitSet bits = new BitSet( max + 1 );
		for( String part : field.split( ",", -1 ) ) {
			if( part.isEmpty() ) {
				throw new CronParseException( String.format( "Empty list element in %s field \"%s\"", fieldName, field ) );
			}
			String rangePart = part;
			int step = 1;
			int slash = part.indexOf( '/' );
			if( slash >= 0 ) {
				rangePart = part.substring( 0, slash );
				step = parseNumber( part.substring( slash + 1 ), fieldName, field );
				if( step <= 0 ) {
					throw new CronParseException( String.format( "Step must be positive in %s field \"%s\"", fieldName, field ) );
				}
				if( !rangePart.equals( "*" ) && !rangePart.contains( "-" ) ) {
					throw new CronParseException( String.format(
						"A step is only allowed after \"*\" or a range in %s field \"%s\"",
						fieldName,
						field
					) );
				}
			}
			int start;
			int end;
			if( rangePart.equals( "*" ) ) {
				start = min;
				end = max;
			} else {
				int dash = rangePart.indexOf( '-' );
				if( dash >= 0 ) {
					start = parseValue( rangePart.substring( 0, dash ), names, nameOffset, fieldName, field );
					end = parseValue( rangePart.substring( dash + 1 ), names, nameOffset, fieldName, field );
				} else {
					start = parseValue( rangePart, names, nameOffset, fieldName, field );
					end = start;
				}
			}
			if( start < min || end > max || start > end ) {
				throw new CronParseException( String.format(
					"Value out of range %d-%d in %s field \"%s\"",
					min,
					max,
					fieldName,
					field
				) );
			}
			for( int value = start; value <= end; value += step ) {
				bits.set( value );
			}
		}
		return bits;
	}
	
	private static int parseValue(
		@NotNull String text,
		@Nullable String[] names,
		int nameOffset,
		@NotNull String fieldName,
		@NotNull String field ) {
		
		if( names != null ) {
			String upper = text.toUpperCase( Locale.ROOT );
			for( int i = 0; i < names.length; i++ ) {
				if( names[i].equals( upper ) ) {
					return i + nameOffset;
				}
			}
		}
		return parseNumber( text, fieldName, field );
	}
	
	private static int parseNumber( @NotNull String text, @NotNull String fieldName, @NotNull String field ) {
		
		try {
			return Integer.parseInt( text );
		} catch( NumberFormatException exception ) {
			throw new CronParseException( String.format( "Invalid value \"%s\" in %s field \"%s\"", text, fieldName, field ) );
		}
	}
	
	@NotNull
	public String expression() {
		
		return expression;
	}
	
	@NotNull
	public Optional<ZonedDateTime> next( @NotNull ZonedDateTime after ) {
		
		ZoneId zone = after.getZone();
		LocalDate date = after.toLocalDate();
		for( int day = 0; day < SEARCH_DAYS; day++, date = date.plusDays( 1 ) ) {
			if( !months.get( date.getMonthValue() ) || !dayMatches( date ) ) {
				continue;
			}
			for( int hour = hours.nextSetBit( 0 ); hour >= 0; hour = hours.nextSetBit( hour + 1 ) ) {
				for( int minute = minutes.nextSetBit( 0 ); minute >= 0; minute = minutes.nextSetBit( minute + 1 ) ) {
					LocalDateTime local = date.atTime( hour, minute );
					ZonedDateTime candidate = ZonedDateTime.ofLocal( local, zone, null );
					if( candidate.toLocalDateTime().equals( local ) && candidate.isAfter( after ) ) {
						return Optional.of( candidate );
					}
				}
			}
		}
		return Optional.empty();
	}
	
	@NotNull
	public List<ZonedDateTime> between(
		@NotNull ZonedDateTime fromExclusive,
		@NotNull ZonedDateTime toInclusive,
		int limit ) {
		
		List<ZonedDateTime> times = new ArrayList<>();
		ZonedDateTime current = fromExclusive;
		while( times.size() < limit ) {
			Optional<ZonedDateTime> next = next( current );
			if( next.isEmpty() || next.get().isAfter( toInclusive ) ) {
				break;
			}
			times.add( next.get() );
			current = next.get();
		}
		return times;
	}
	
	private boolean dayMatches( @NotNull LocalDate date ) {
		
		boolean dayOfMonthMatch = daysOfMonth.get( date.getDayOfMonth() );
		boolean dayOfWeekMatch = daysOfWeek.get( date.getDayOfWeek().getValue() % 7 );
		if( dayOfMonthStar || dayOfWeekStar ) {
			return dayOfMonthMatch && dayOfWeekMatch;
		}
		return dayOfMonthMatch || dayOfWeekMatch;
	}
}
