package de.geheimagentnr1.player_time_manager.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.GeneralRaw;
import de.geheimagentnr1.player_time_manager.config.raw.PlayerRef;
import de.geheimagentnr1.player_time_manager.config.raw.RawSettings;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;
import de.geheimagentnr1.player_time_manager.config.resolved.DisplayMode;
import de.geheimagentnr1.player_time_manager.time.RefillMode;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.Function;


public class ServerConfig {
	
	
	private static final String CRON_COMMENT =
		"Refill schedule in cron format (minute hour day month weekday), see https://crontab.guru (\"\" = no automatic refill)";
	
	private static final String MODE_COMMENT =
		"SET = set the remaining time to the limit, ADD = add the limit to the remaining time";
	
	private static final String MAX_COMMENT = "Maximum remaining time after an ADD refill, e.g. \"4h\" (\"\" = no maximum)";
	
	@NotNull
	private final ModConfigSpec spec;
	
	private final ModConfigSpec.IntValue commandPermissionLevel;
	
	private final ModConfigSpec.BooleanValue opsExempt;
	
	private final ModConfigSpec.BooleanValue pauseInCreative;
	
	private final ModConfigSpec.BooleanValue pauseInSpectator;
	
	private final ModConfigSpec.ConfigValue<List<? extends UnmodifiableConfig>> limitedPlayers;
	
	private final ModConfigSpec.ConfigValue<List<? extends UnmodifiableConfig>> exemptPlayers;
	
	private final ModConfigSpec.ConfigValue<String> timeZone;
	
	private final ModConfigSpec.ConfigValue<String> refillCron;
	
	private final ModConfigSpec.EnumValue<RefillMode> refillMode;
	
	private final ModConfigSpec.ConfigValue<String> refillMax;
	
	private final ModConfigSpec.EnumValue<DisplayMode> displayMode;
	
	private final ModConfigSpec.IntValue bossbarYellowBelowPercent;
	
	private final ModConfigSpec.IntValue bossbarRedBelowPercent;
	
	private final ModConfigSpec.ConfigValue<List<? extends String>> warningTimes;
	
	private final ModConfigSpec.BooleanValue warningSound;
	
	private final ModConfigSpec.BooleanValue worldEnabled;
	
	private final ModConfigSpec.ConfigValue<String> worldLimit;
	
	private final ModConfigSpec.BooleanValue worldUseCustomRefill;
	
	private final ModConfigSpec.ConfigValue<String> worldRefillCron;
	
	private final ModConfigSpec.EnumValue<RefillMode> worldRefillMode;
	
	private final ModConfigSpec.ConfigValue<String> worldRefillMax;
	
	private final ModConfigSpec.ConfigValue<List<? extends UnmodifiableConfig>> dimensions;
	
	public ServerConfig() {
		
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		
		builder.push( "general" );
		commandPermissionLevel = builder.comment( "Permission level (0 - 4) required for all admin commands" )
			.defineInRange( "command_permission_level", 3, 0, 4 );
		opsExempt = builder.comment( "Operators are not affected by any time limit (set to false for singleplayer worlds with cheats)" )
			.define( "ops_exempt", true );
		pauseInCreative = builder.comment( "Time does not count down while a player is in creative mode" )
			.define( "pause_in_creative", true );
		pauseInSpectator = builder.comment( "Time does not count down while a player is in spectator mode" )
			.define( "pause_in_spectator", true );
		limitedPlayers = builder.comment( "If not empty, ONLY these players are affected by the time limits. Entries: name and uuid" )
			.defineListAllowEmpty( "limited_players", List::of, ConfigTables::isTable );
		exemptPlayers = builder.comment( "These players are never affected by the time limits (has priority over limited_players). Entries: name and uuid" )
			.defineListAllowEmpty( "exempt_players", List::of, ConfigTables::isTable );
		timeZone = builder.comment( "Time zone of the refill schedules, e.g. \"Europe/Berlin\" (\"\" = system time zone of the server)" )
			.define( "time_zone", "" );
		refillCron = builder.comment(
				"Default refill schedule in cron format (minute hour day month weekday), e.g. \"0 6 * * *\" = daily at 06:00.",
				"Generator and explanation: https://crontab.guru (\"\" = no automatic refill)"
			)
			.define( "refill_cron", "0 6 * * *" );
		refillMode = builder.comment( "Default refill mode: " + MODE_COMMENT ).defineEnum( "refill_mode", RefillMode.SET );
		refillMax = builder.comment( "Default maximum remaining time after an ADD refill, e.g. \"4h\" (\"\" = no maximum)" )
			.define( "refill_max", "" );
		displayMode = builder.comment(
				"How players see their remaining time: NONE = only via the command, ACTIONBAR = text above the hotbar,",
				"BOSSBARS = one bossbar per running limit, BOSSBAR_SHORTEST = one bossbar with the limit that runs out first"
			)
			.defineEnum( "display_mode", DisplayMode.BOSSBARS );
		bossbarYellowBelowPercent = builder.comment( "The bossbar turns yellow below this percentage of the limit (0 - 100)" )
			.defineInRange( "bossbar_yellow_below_percent", 50, 0, 100 );
		bossbarRedBelowPercent = builder.comment( "The bossbar turns red below this percentage of the limit (0 - 100, lower than the yellow value)" )
			.defineInRange( "bossbar_red_below_percent", 10, 0, 100 );
		warningTimes = builder.comment( "Remaining times that trigger a chat warning, e.g. [\"10m\", \"1m\", \"10s\"] (empty list = no chat warnings)" )
			.defineListAllowEmpty( "warning_times", () -> List.of( "10m", "5m", "1m", "10s" ), value -> value instanceof String );
		warningSound = builder.comment( "Play a sound with each chat warning" ).define( "warning_sound", true );
		builder.pop();
		
		builder.push( "world" );
		worldEnabled = builder.comment( "Is the time limit for the whole world (all dimensions together) active?" )
			.define( "enabled", false );
		worldLimit = builder.comment( "Time per refill, e.g. \"2h\", \"1h30m\", \"45m\"" ).define( "limit", "2h" );
		worldUseCustomRefill = builder.comment( "true = use the refill settings below, false = use the refill settings from [general]" )
			.define( "use_custom_refill", false );
		worldRefillCron = builder.comment( CRON_COMMENT ).define( "refill_cron", "0 6 * * *" );
		worldRefillMode = builder.comment( MODE_COMMENT ).defineEnum( "refill_mode", RefillMode.SET );
		worldRefillMax = builder.comment( MAX_COMMENT ).define( "refill_max", "" );
		builder.pop();
		
		dimensions = builder.comment( "Time limits of single dimensions (the Overworld can't be limited here, use [world] instead)" )
			.defineListAllowEmpty(
				"dimensions",
				() -> DimensionEntries.defaults().stream().map( entry -> toConfig( DimensionEntries.toValues( entry ), DimensionEntries.COMMENTS ) ).toList(),
				ConfigTables::isTable
			);
		
		spec = builder.build();
	}
	
	@NotNull
	private static CommentedConfig toConfig( @NotNull Map<String, Object> values, @NotNull Map<String, String> comments ) {
		
		CommentedConfig config = CommentedConfig.inMemory();
		values.forEach( ( key, value ) -> {
			config.set( key, value );
			String comment = comments.get( key );
			if( comment != null ) {
				config.setComment( key, comment );
			}
		} );
		return config;
	}
	
	@NotNull
	private static <T> List<T> fromConfigs(
		@NotNull List<? extends UnmodifiableConfig> configs,
		@NotNull Function<Map<String, ?>, T> converter ) {
		
		return configs.stream().map( config -> converter.apply( config.valueMap() ) ).toList();
	}
	
	@NotNull
	public ModConfigSpec getSpec() {
		
		return spec;
	}
	
	@NotNull
	public RawSettings read() {
		
		return new RawSettings(
			new GeneralRaw(
				commandPermissionLevel.get(),
				opsExempt.get(),
				pauseInCreative.get(),
				pauseInSpectator.get(),
				fromConfigs( limitedPlayers.get(), PlayerEntries::fromValues ),
				fromConfigs( exemptPlayers.get(), PlayerEntries::fromValues ),
				timeZone.get(),
				refillCron.get(),
				refillMode.get().name(),
				refillMax.get(),
				displayMode.get().name(),
				bossbarYellowBelowPercent.get(),
				bossbarRedBelowPercent.get(),
				List.copyOf( warningTimes.get() ),
				warningSound.get()
			),
			new ScopeRaw(
				worldEnabled.get(),
				worldLimit.get(),
				worldUseCustomRefill.get(),
				worldRefillCron.get(),
				worldRefillMode.get().name(),
				worldRefillMax.get()
			),
			fromConfigs( dimensions.get(), DimensionEntries::fromValues )
		);
	}
	
	private static <T> void setAndSave( @NotNull ModConfigSpec.ConfigValue<T> configValue, @NotNull T value ) {
		
		configValue.set( value );
		configValue.save();
	}
	
	public void setGeneralRefillCron( @NotNull String value ) {
		
		setAndSave( refillCron, value );
	}
	
	public void setGeneralRefillMode( @NotNull RefillMode value ) {
		
		setAndSave( refillMode, value );
	}
	
	public void setGeneralRefillMax( @NotNull String value ) {
		
		setAndSave( refillMax, value );
	}
	
	public void setOpsExempt( boolean value ) {
		
		setAndSave( opsExempt, value );
	}
	
	public void setWorldEnabled( boolean value ) {
		
		setAndSave( worldEnabled, value );
	}
	
	public void setWorldLimit( @NotNull String value ) {
		
		setAndSave( worldLimit, value );
	}
	
	public void setWorldUseCustomRefill( boolean value ) {
		
		setAndSave( worldUseCustomRefill, value );
	}
	
	public void setWorldRefillCron( @NotNull String value ) {
		
		setAndSave( worldRefillCron, value );
	}
	
	public void setWorldRefillMode( @NotNull RefillMode value ) {
		
		setAndSave( worldRefillMode, value );
	}
	
	public void setWorldRefillMax( @NotNull String value ) {
		
		setAndSave( worldRefillMax, value );
	}
	
	public void setDimensions( @NotNull List<DimensionRaw> value ) {
		
		setAndSave( dimensions, value.stream().map( entry -> toConfig( DimensionEntries.toValues( entry ), DimensionEntries.COMMENTS ) ).toList() );
	}
	
	public void setLimitedPlayers( @NotNull List<PlayerRef> value ) {
		
		setAndSave( limitedPlayers, value.stream().map( entry -> toConfig( PlayerEntries.toValues( entry ), PlayerEntries.COMMENTS ) ).toList() );
	}
	
	public void setExemptPlayers( @NotNull List<PlayerRef> value ) {
		
		setAndSave( exemptPlayers, value.stream().map( entry -> toConfig( PlayerEntries.toValues( entry ), PlayerEntries.COMMENTS ) ).toList() );
	}
}
