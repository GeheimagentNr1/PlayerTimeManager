package de.geheimagentnr1.player_time_manager.time;


public final class TimeAccounting {
	
	
	// Longer gaps come from a paused singleplayer game or a hanging server and are not counted
	public static final long MAX_COUNTED_GAP_MILLIS = 10_000L;
	
	private TimeAccounting() {
	
	}
	
	public static long elapsedToCount( long lastMillis, long nowMillis ) {
		
		if( lastMillis < 0 ) {
			return 0;
		}
		long elapsed = nowMillis - lastMillis;
		return elapsed < 0 || elapsed > MAX_COUNTED_GAP_MILLIS ? 0 : elapsed;
	}
}
