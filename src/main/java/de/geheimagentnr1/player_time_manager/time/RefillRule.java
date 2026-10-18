package de.geheimagentnr1.player_time_manager.time;

import de.geheimagentnr1.player_time_manager.cron.CronExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public record RefillRule(
	@Nullable CronExpression cron,
	@NotNull RefillMode mode,
	long limitMillis,
	@Nullable Long maxMillis ) {
	
	
	@NotNull
	public String scheduleId() {
		
		return cron == null ? "" : cron.expression();
	}
}
