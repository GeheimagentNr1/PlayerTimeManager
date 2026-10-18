package de.geheimagentnr1.player_time_manager.display;

import org.jetbrains.annotations.NotNull;


public enum BarColor {
	GREEN,
	YELLOW,
	RED;
	
	@NotNull
	public static BarColor of( long remaining, long limit, int yellowBelowPercent, int redBelowPercent ) {
		
		if( limit <= 0 ) {
			return RED;
		}
		double percent = remaining * 100.0 / limit;
		if( percent < redBelowPercent ) {
			return RED;
		}
		return percent < yellowBelowPercent ? YELLOW : GREEN;
	}
	
	public static float progress( long remaining, long limit ) {
		
		if( limit <= 0 ) {
			return 0f;
		}
		return (float)Math.max( 0.0, Math.min( 1.0, remaining / (double)limit ) );
	}
}
