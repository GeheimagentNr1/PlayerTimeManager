package de.geheimagentnr1.player_time_manager.time;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class DurationParser {


	@NotNull
	private static final Pattern PATTERN = Pattern.compile( "^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$" );

	private DurationParser() {

	}

	public static long parse( @NotNull String text ) {

		String compact = text.replace( " ", "" ).toLowerCase( Locale.ROOT );
		Matcher matcher = PATTERN.matcher( compact );
		if( compact.isEmpty() || !matcher.matches() ) {
			throw invalid( text );
		}
		try {
			long hours = group( matcher, 1 );
			long minutes = group( matcher, 2 );
			long seconds = group( matcher, 3 );
			return Math.addExact(
				Math.addExact( Math.multiplyExact( hours, 3_600_000L ), Math.multiplyExact( minutes, 60_000L ) ),
				Math.multiplyExact( seconds, 1_000L )
			);
		} catch( NumberFormatException | ArithmeticException exception ) {
			throw invalid( text );
		}
	}

	@NotNull
	public static String format( long millis ) {

		long totalSeconds = millis <= 0 ? 0 : ( millis + 999 ) / 1000;
		long hours = totalSeconds / 3600;
		long minutes = totalSeconds % 3600 / 60;
		long seconds = totalSeconds % 60;
		StringBuilder builder = new StringBuilder();
		if( hours > 0 ) {
			builder.append( hours ).append( 'h' );
		}
		if( minutes > 0 ) {
			append( builder, minutes, 'm' );
		}
		if( seconds > 0 || builder.isEmpty() ) {
			append( builder, seconds, 's' );
		}
		return builder.toString();
	}

	private static void append( @NotNull StringBuilder builder, long value, char unit ) {

		if( !builder.isEmpty() ) {
			builder.append( ' ' );
		}
		builder.append( value ).append( unit );
	}

	private static long group( @NotNull Matcher matcher, int index ) {

		String group = matcher.group( index );
		return group == null ? 0 : Long.parseLong( group );
	}

	@NotNull
	private static IllegalArgumentException invalid( @NotNull String text ) {

		return new IllegalArgumentException(
			"Invalid duration \"" + text + "\", expected e.g. \"2h\", \"1h30m\", \"45m\" or \"30s\""
		);
	}
}
