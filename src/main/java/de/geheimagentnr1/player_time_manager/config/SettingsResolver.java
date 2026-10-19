package de.geheimagentnr1.player_time_manager.config;

import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.GeneralRaw;
import de.geheimagentnr1.player_time_manager.config.raw.PlayerRef;
import de.geheimagentnr1.player_time_manager.config.raw.RawSettings;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;
import de.geheimagentnr1.player_time_manager.config.resolved.DisplayMode;
import de.geheimagentnr1.player_time_manager.config.resolved.DisplaySettings;
import de.geheimagentnr1.player_time_manager.config.resolved.PlayerRules;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedDimension;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedScope;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedSettings;
import de.geheimagentnr1.player_time_manager.config.resolved.TargetSpec;
import de.geheimagentnr1.player_time_manager.cron.CronExpression;
import de.geheimagentnr1.player_time_manager.cron.CronParseException;
import de.geheimagentnr1.player_time_manager.time.DurationParser;
import de.geheimagentnr1.player_time_manager.time.RefillMode;
import de.geheimagentnr1.player_time_manager.time.RefillRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;


public final class SettingsResolver {
	
	
	@NotNull
	public static final String OVERWORLD = "minecraft:overworld";
	
	@NotNull
	public static final String THE_END = "minecraft:the_end";
	
	private SettingsResolver() {
	
	}
	
	@NotNull
	public static ResolvedSettings resolve(
		@NotNull RawSettings raw,
		@NotNull Set<String> knownDimensions,
		@NotNull ZoneId systemZone ) {
		
		List<String> problems = new ArrayList<>();
		GeneralRaw general = raw.general();
		ZoneId zone = resolveZone( general.timeZone(), systemZone, problems );
		RefillMode generalMode = parseMode( general.refillMode(), RefillMode.SET, "general.refill_mode", problems );
		CronExpression generalCron = parseCron( general.refillCron(), "general.refill_cron", problems );
		Long generalMax = parseMax( general.refillMax(), "general.refill_max", problems );
		RefillDefaults defaults = new RefillDefaults( generalCron, generalMode, generalMax );
		
		ResolvedScope world = resolveScope( "world", raw.world(), defaults, problems );
		Map<String, ResolvedDimension> dimensions = new LinkedHashMap<>();
		List<DimensionRaw> entries = raw.dimensions();
		for( int i = 0; i < entries.size(); i++ ) {
			DimensionRaw entry = entries.get( i );
			String id = entry.dimension().trim();
			String prefix = String.format( "dimensions[%d] (\"%s\")", i, id );
			for( String invalidValue : entry.invalidValues() ) {
				problems.add( prefix + "." + invalidValue + ": expected true or false - false is used" );
			}
			if( id.isEmpty() ) {
				problems.add( prefix + ": dimension is empty - entry ignored" );
			} else if( id.equals( OVERWORLD ) ) {
				problems.add( prefix + ": the Overworld can't be limited here, use [world] - entry ignored" );
			} else if( dimensions.containsKey( id ) ) {
				problems.add( prefix + ": duplicate entry for " + id + ", the first one is used - entry ignored" );
			} else if( !knownDimensions.contains( id ) ) {
				problems.add( prefix + ": unknown dimension " + id + " - entry inactive" );
			} else {
				dimensions.put(
					id,
					new ResolvedDimension(
						id,
						resolveScope( prefix, entry.scope(), defaults, problems ),
						resolveTarget( prefix, id, entry, knownDimensions, problems )
					)
				);
			}
		}
		return new ResolvedSettings(
			general.commandPermissionLevel(),
			resolvePlayerRules( general, problems ),
			zone,
			resolveDisplay( general, problems ),
			world,
			Collections.unmodifiableMap( dimensions ),
			List.copyOf( problems )
		);
	}
	
	private record RefillDefaults( @Nullable CronExpression cron, @NotNull RefillMode mode, @Nullable Long max ) {
	
	}
	
	@NotNull
	private static ResolvedScope resolveScope(
		@NotNull String prefix,
		@NotNull ScopeRaw scope,
		@NotNull RefillDefaults defaults,
		@NotNull List<String> problems ) {
		
		if( !scope.enabled() ) {
			return ResolvedScope.INACTIVE;
		}
		long limit;
		try {
			limit = DurationParser.parse( scope.limit() );
		} catch( IllegalArgumentException exception ) {
			problems.add( prefix + ".limit: " + exception.getMessage() + " - time limit disabled" );
			return ResolvedScope.INACTIVE;
		}
		if( limit <= 0 ) {
			problems.add( prefix + ".limit: \"" + scope.limit() + "\" must be greater than 0 - time limit disabled" );
			return ResolvedScope.INACTIVE;
		}
		CronExpression cron = defaults.cron();
		RefillMode mode = defaults.mode();
		Long max = defaults.max();
		if( scope.useCustomRefill() ) {
			cron = parseCron( scope.refillCron(), prefix + ".refill_cron", problems );
			mode = parseMode( scope.refillMode(), defaults.mode(), prefix + ".refill_mode", problems );
			max = parseMax( scope.refillMax(), prefix + ".refill_max", problems );
		}
		return new ResolvedScope( true, limit, new RefillRule( cron, mode, limit, max ) );
	}
	
	@Nullable
	private static TargetSpec resolveTarget(
		@NotNull String prefix,
		@NotNull String id,
		@NotNull DimensionRaw entry,
		@NotNull Set<String> knownDimensions,
		@NotNull List<String> problems ) {
		
		String targetDimension = entry.targetDimension().isBlank() ? OVERWORLD : entry.targetDimension().trim();
		String fix = " Players are sent to the world spawn instead. Fix: /playtime config dimension " + id + " target here";
		if( !knownDimensions.contains( targetDimension ) ) {
			problems.add( prefix + ".target_dimension: unknown dimension " + targetDimension + "." + fix );
			return null;
		}
		if( entry.targetPosition().isBlank() ) {
			if( targetDimension.equals( OVERWORLD ) || targetDimension.equals( THE_END ) ) {
				return new TargetSpec( targetDimension, null );
			}
			problems.add( prefix + ": target_dimension " + targetDimension + " needs a target_position." + fix );
			return null;
		}
		try {
			return new TargetSpec( targetDimension, TargetPosition.parse( entry.targetPosition() ) );
		} catch( IllegalArgumentException exception ) {
			problems.add( prefix + ".target_position: " + exception.getMessage() + "." + fix );
			return null;
		}
	}
	
	@NotNull
	private static PlayerRules resolvePlayerRules( @NotNull GeneralRaw general, @NotNull List<String> problems ) {
		
		Set<UUID> limitedIds = new HashSet<>();
		Set<String> limitedNames = new HashSet<>();
		Set<UUID> exemptIds = new HashSet<>();
		Set<String> exemptNames = new HashSet<>();
		readPlayers( general.limitedPlayers(), "general.limited_players", limitedIds, limitedNames, problems );
		readPlayers( general.exemptPlayers(), "general.exempt_players", exemptIds, exemptNames, problems );
		return new PlayerRules(
			general.opsExempt(),
			general.pauseInCreative(),
			general.pauseInSpectator(),
			Set.copyOf( limitedIds ),
			Set.copyOf( limitedNames ),
			Set.copyOf( exemptIds ),
			Set.copyOf( exemptNames )
		);
	}
	
	private static void readPlayers(
		@NotNull List<PlayerRef> players,
		@NotNull String key,
		@NotNull Set<UUID> ids,
		@NotNull Set<String> names,
		@NotNull List<String> problems ) {
		
		for( PlayerRef player : players ) {
			String name = player.name().trim();
			String uuid = player.uuid().trim();
			if( !uuid.isEmpty() ) {
				try {
					ids.add( UUID.fromString( uuid ) );
					continue;
				} catch( IllegalArgumentException exception ) {
					problems.add( key + ": invalid uuid \"" + uuid + "\" for \"" + name + "\" - matching by name" );
				}
			}
			if( name.isEmpty() ) {
				problems.add( key + ": entry without name and uuid - entry ignored" );
			} else {
				names.add( name.toLowerCase( Locale.ROOT ) );
			}
		}
	}
	
	@NotNull
	private static DisplaySettings resolveDisplay( @NotNull GeneralRaw general, @NotNull List<String> problems ) {
		
		DisplayMode mode;
		try {
			mode = DisplayMode.valueOf( general.displayMode().trim().toUpperCase( Locale.ROOT ) );
		} catch( IllegalArgumentException exception ) {
			problems.add( "general.display_mode: unknown value \"" + general.displayMode() + "\" - using BOSSBARS" );
			mode = DisplayMode.BOSSBARS;
		}
		int yellow = general.bossbarYellowBelowPercent();
		int red = general.bossbarRedBelowPercent();
		if( red < 0 || yellow > 100 || red >= yellow ) {
			problems.add( "general.bossbar_yellow_below_percent/bossbar_red_below_percent: need 0 <= red < yellow <= 100 - using 50 and 10" );
			yellow = 50;
			red = 10;
		}
		TreeSet<Long> warningTimes = new TreeSet<>( Comparator.reverseOrder() );
		for( String warningTime : general.warningTimes() ) {
			try {
				long millis = DurationParser.parse( warningTime );
				if( millis > 0 ) {
					warningTimes.add( millis );
				}
			} catch( IllegalArgumentException exception ) {
				problems.add( "general.warning_times: " + exception.getMessage() + " - value ignored" );
			}
		}
		return new DisplaySettings( mode, yellow, red, List.copyOf( warningTimes ), general.warningSound() );
	}
	
	@NotNull
	private static ZoneId resolveZone( @NotNull String timeZone, @NotNull ZoneId systemZone, @NotNull List<String> problems ) {
		
		if( timeZone.isBlank() ) {
			return systemZone;
		}
		try {
			return ZoneId.of( timeZone.trim() );
		} catch( DateTimeException exception ) {
			problems.add( "general.time_zone: \"" + timeZone + "\" is not a valid time zone - using the time zone of the server" );
			return systemZone;
		}
	}
	
	@Nullable
	private static CronExpression parseCron( @NotNull String text, @NotNull String key, @NotNull List<String> problems ) {
		
		if( text.isBlank() ) {
			return null;
		}
		try {
			return CronExpression.parse( text );
		} catch( CronParseException exception ) {
			problems.add( key + ": " + exception.getMessage() + " - no automatic refill" );
			return null;
		}
	}
	
	@NotNull
	private static RefillMode parseMode(
		@NotNull String text,
		@NotNull RefillMode fallback,
		@NotNull String key,
		@NotNull List<String> problems ) {
		
		try {
			return RefillMode.valueOf( text.trim().toUpperCase( Locale.ROOT ) );
		} catch( IllegalArgumentException exception ) {
			problems.add( key + ": unknown value \"" + text + "\" (SET or ADD) - using " + fallback );
			return fallback;
		}
	}
	
	@Nullable
	private static Long parseMax( @NotNull String text, @NotNull String key, @NotNull List<String> problems ) {
		
		if( text.isBlank() ) {
			return null;
		}
		try {
			return DurationParser.parse( text );
		} catch( IllegalArgumentException exception ) {
			problems.add( key + ": " + exception.getMessage() + " - no maximum" );
			return null;
		}
	}
}
