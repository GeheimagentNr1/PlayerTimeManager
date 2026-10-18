package de.geheimagentnr1.player_time_manager.config;

import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class DimensionEntries {
	
	
	@NotNull
	public static final Map<String, String> COMMENTS;
	
	static {
		Map<String, String> comments = new LinkedHashMap<>();
		comments.put( "dimension", "Name of the dimension, e.g. \"minecraft:the_nether\" or \"modid:dimension\"" );
		comments.put( "enabled", "Is the time limit for this dimension active?" );
		comments.put( "limit", "Time per refill, e.g. \"2h\", \"1h30m\", \"45m\"" );
		comments.put( "use_custom_refill", "true = use the refill settings below, false = use the refill settings from [general]" );
		comments.put( "refill_cron", "Refill schedule in cron format (minute hour day month weekday), see https://crontab.guru (\"\" = no automatic refill)" );
		comments.put( "refill_mode", "SET = set the remaining time to the limit, ADD = add the limit to the remaining time" );
		comments.put( "refill_max", "Maximum remaining time after an ADD refill, e.g. \"4h\" (\"\" = no maximum)" );
		comments.put( "target_dimension", "Dimension players are sent to when their time in this dimension is used up" );
		comments.put( "target_position", "Target position \"x y z\" or \"x y z yaw pitch\" (\"\" = world spawn in the Overworld, obsidian platform in the End; required for all other dimensions)" );
		COMMENTS = Collections.unmodifiableMap( comments );
	}
	
	private DimensionEntries() {
	
	}
	
	@NotNull
	public static DimensionRaw fromValues( @NotNull Map<String, ?> values ) {
		
		return new DimensionRaw(
			string( values, "dimension", "" ),
			new ScopeRaw(
				bool( values, "enabled", false ),
				string( values, "limit", "1h" ),
				bool( values, "use_custom_refill", false ),
				string( values, "refill_cron", "0 6 * * *" ),
				string( values, "refill_mode", "SET" ),
				string( values, "refill_max", "" )
			),
			string( values, "target_dimension", SettingsResolver.OVERWORLD ),
			string( values, "target_position", "" )
		);
	}
	
	@NotNull
	public static Map<String, Object> toValues( @NotNull DimensionRaw dimension ) {
		
		Map<String, Object> values = new LinkedHashMap<>();
		values.put( "dimension", dimension.dimension() );
		values.put( "enabled", dimension.scope().enabled() );
		values.put( "limit", dimension.scope().limit() );
		values.put( "use_custom_refill", dimension.scope().useCustomRefill() );
		values.put( "refill_cron", dimension.scope().refillCron() );
		values.put( "refill_mode", dimension.scope().refillMode() );
		values.put( "refill_max", dimension.scope().refillMax() );
		values.put( "target_dimension", dimension.targetDimension() );
		values.put( "target_position", dimension.targetPosition() );
		return values;
	}
	
	@NotNull
	public static List<DimensionRaw> defaults() {
		
		return List.of(
			new DimensionRaw( "minecraft:the_nether", new ScopeRaw( false, "1h", false, "0 6 * * *", "SET", "" ), SettingsResolver.OVERWORLD, "" ),
			new DimensionRaw( SettingsResolver.THE_END, new ScopeRaw( false, "1h", false, "0 6 * * *", "SET", "" ), SettingsResolver.OVERWORLD, "" )
		);
	}
	
	@NotNull
	static String string( @NotNull Map<String, ?> values, @NotNull String key, @NotNull String defaultValue ) {
		
		Object value = values.get( key );
		return value == null ? defaultValue : String.valueOf( value );
	}
	
	static boolean bool( @NotNull Map<String, ?> values, @NotNull String key, boolean defaultValue ) {
		
		Object value = values.get( key );
		if( value instanceof Boolean booleanValue ) {
			return booleanValue;
		}
		if( value instanceof String text && ( text.equalsIgnoreCase( "true" ) || text.equalsIgnoreCase( "false" ) ) ) {
			return Boolean.parseBoolean( text );
		}
		return defaultValue;
	}
}
