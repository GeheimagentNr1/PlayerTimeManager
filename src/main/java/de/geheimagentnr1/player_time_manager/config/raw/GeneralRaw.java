package de.geheimagentnr1.player_time_manager.config.raw;

import org.jetbrains.annotations.NotNull;

import java.util.List;


public record GeneralRaw(
	int commandPermissionLevel,
	boolean opsExempt,
	boolean pauseInCreative,
	boolean pauseInSpectator,
	@NotNull List<PlayerRef> limitedPlayers,
	@NotNull List<PlayerRef> exemptPlayers,
	@NotNull String timeZone,
	@NotNull String refillCron,
	@NotNull String refillMode,
	@NotNull String refillMax,
	@NotNull String displayMode,
	int bossbarYellowBelowPercent,
	int bossbarRedBelowPercent,
	@NotNull List<String> warningTimes,
	boolean warningSound ) {

}
