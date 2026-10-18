package de.geheimagentnr1.player_time_manager.commands;

import de.geheimagentnr1.player_time_manager.config.SettingsResolver;
import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;
import de.geheimagentnr1.player_time_manager.cron.CronExpression;
import de.geheimagentnr1.player_time_manager.time.DurationParser;
import de.geheimagentnr1.player_time_manager.time.RefillMode;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.function.UnaryOperator;


public final class ConfigEdits {
	
	
	private ConfigEdits() {
	
	}
	
	@NotNull
	public static DimensionRaw withScope( @NotNull DimensionRaw dimension, @NotNull UnaryOperator<ScopeRaw> change ) {
		
		return new DimensionRaw( dimension.dimension(), change.apply( dimension.scope() ), dimension.targetDimension(), dimension.targetPosition() );
	}
	
	@NotNull
	public static ScopeRaw withEnabled( @NotNull ScopeRaw scope, boolean value ) {
		
		return new ScopeRaw( value, scope.limit(), scope.useCustomRefill(), scope.refillCron(), scope.refillMode(), scope.refillMax() );
	}
	
	@NotNull
	public static ScopeRaw withLimit( @NotNull ScopeRaw scope, @NotNull String value ) {
		
		return new ScopeRaw( scope.enabled(), value, scope.useCustomRefill(), scope.refillCron(), scope.refillMode(), scope.refillMax() );
	}
	
	@NotNull
	public static ScopeRaw withUseCustomRefill( @NotNull ScopeRaw scope, boolean value ) {
		
		return new ScopeRaw( scope.enabled(), scope.limit(), value, scope.refillCron(), scope.refillMode(), scope.refillMax() );
	}
	
	@NotNull
	public static ScopeRaw withRefillCron( @NotNull ScopeRaw scope, @NotNull String value ) {
		
		return new ScopeRaw( scope.enabled(), scope.limit(), scope.useCustomRefill(), value, scope.refillMode(), scope.refillMax() );
	}
	
	@NotNull
	public static ScopeRaw withRefillMode( @NotNull ScopeRaw scope, @NotNull String value ) {
		
		return new ScopeRaw( scope.enabled(), scope.limit(), scope.useCustomRefill(), scope.refillCron(), value, scope.refillMax() );
	}
	
	@NotNull
	public static ScopeRaw withRefillMax( @NotNull ScopeRaw scope, @NotNull String value ) {
		
		return new ScopeRaw( scope.enabled(), scope.limit(), scope.useCustomRefill(), scope.refillCron(), scope.refillMode(), value );
	}
	
	@NotNull
	public static DimensionRaw withTarget( @NotNull DimensionRaw dimension, @NotNull String targetDimension, @NotNull String targetPosition ) {
		
		return new DimensionRaw( dimension.dimension(), dimension.scope(), targetDimension, targetPosition );
	}
	
	@NotNull
	public static DimensionRaw newDimension( @NotNull String id ) {
		
		return new DimensionRaw( id, new ScopeRaw( false, "1h", false, "0 6 * * *", "SET", "" ), SettingsResolver.OVERWORLD, "" );
	}
	
	@NotNull
	public static String cronValue( @NotNull String input ) {
		
		return input.trim().equalsIgnoreCase( "none" ) ? "" : CronExpression.parse( input ).expression();
	}
	
	@NotNull
	public static String durationValue( @NotNull String input, boolean clearable ) {
		
		String trimmed = input.trim();
		if( trimmed.equalsIgnoreCase( "none" ) ) {
			if( clearable ) {
				return "";
			}
			throw new IllegalArgumentException( "This value can't be empty, use a duration like \"2h\"" );
		}
		if( DurationParser.parse( trimmed ) <= 0 ) {
			throw new IllegalArgumentException( "The duration must be greater than 0" );
		}
		return trimmed;
	}
	
	@NotNull
	public static String modeValue( @NotNull String input ) {
		
		try {
			return RefillMode.valueOf( input.trim().toUpperCase( Locale.ROOT ) ).name();
		} catch( IllegalArgumentException exception ) {
			throw new IllegalArgumentException( "Unknown refill mode \"" + input + "\", use SET or ADD" );
		}
	}
}
