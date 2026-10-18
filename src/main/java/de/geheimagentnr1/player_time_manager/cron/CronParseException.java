package de.geheimagentnr1.player_time_manager.cron;

import org.jetbrains.annotations.NotNull;


public class CronParseException extends IllegalArgumentException {
	
	
	public CronParseException( @NotNull String message ) {
		
		super( message );
	}
}
