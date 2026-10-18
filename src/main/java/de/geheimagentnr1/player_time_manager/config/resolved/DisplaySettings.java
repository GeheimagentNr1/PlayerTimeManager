package de.geheimagentnr1.player_time_manager.config.resolved;

import org.jetbrains.annotations.NotNull;

import java.util.List;


public record DisplaySettings(
	@NotNull DisplayMode mode,
	int yellowBelowPercent,
	int redBelowPercent,
	@NotNull List<Long> warningTimesMillis,
	boolean warningSound ) {

}
