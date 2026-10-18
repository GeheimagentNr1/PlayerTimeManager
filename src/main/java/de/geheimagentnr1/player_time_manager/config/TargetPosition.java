package de.geheimagentnr1.player_time_manager.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;


public record TargetPosition( double x, double y, double z, @Nullable Float yaw, @Nullable Float pitch ) {
	
	
	@NotNull
	public static TargetPosition parse( @NotNull String text ) {
		
		String trimmed = text.trim();
		String[] parts = trimmed.isEmpty() ? new String[0] : trimmed.split( "\\s+" );
		if( parts.length != 3 && parts.length != 5 ) {
			throw invalid( text );
		}
		try {
			double x = finite( Double.parseDouble( parts[0] ), text );
			double y = finite( Double.parseDouble( parts[1] ), text );
			double z = finite( Double.parseDouble( parts[2] ), text );
			if( parts.length == 3 ) {
				return new TargetPosition( x, y, z, null, null );
			}
			return new TargetPosition(
				x,
				y,
				z,
				(float)finite( Float.parseFloat( parts[3] ), text ),
				(float)finite( Float.parseFloat( parts[4] ), text )
			);
		} catch( NumberFormatException exception ) {
			throw invalid( text );
		}
	}
	
	// For command output: an empty position means world spawn (Overworld) or the End platform
	@NotNull
	public static String describe( @NotNull String dimension, @NotNull String position ) {
		
		if( !position.isEmpty() ) {
			return dimension + " " + position;
		}
		return switch( dimension ) {
			case "minecraft:overworld" -> dimension + " (world spawn)";
			case "minecraft:the_end" -> dimension + " (End platform)";
			default -> dimension + " (no position, world spawn is used)";
		};
	}
	
	private static double finite( double value, @NotNull String text ) {
		
		if( !Double.isFinite( value ) ) {
			throw invalid( text );
		}
		return value;
	}
	
	@NotNull
	private static IllegalArgumentException invalid( @NotNull String text ) {
		
		return new IllegalArgumentException(
			"Invalid position \"" + text + "\", expected \"x y z\" or \"x y z yaw pitch\""
		);
	}
	
	@NotNull
	public String format() {
		
		String position = number( x ) + " " + number( y ) + " " + number( z );
		if( yaw == null || pitch == null ) {
			return position;
		}
		return position + " " + number( yaw ) + " " + number( pitch );
	}
	
	@NotNull
	private static String number( double value ) {
		
		return BigDecimal.valueOf( value ).setScale( 2, RoundingMode.HALF_UP ).stripTrailingZeros().toPlainString();
	}
}
