package de.geheimagentnr1.player_time_manager.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.geheimagentnr1.player_time_manager.config.DimensionEntries;
import de.geheimagentnr1.player_time_manager.config.ServerConfig;
import de.geheimagentnr1.player_time_manager.config.SettingsHolder;
import de.geheimagentnr1.player_time_manager.config.SettingsResolver;
import de.geheimagentnr1.player_time_manager.config.TargetPosition;
import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.PlayerRef;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;
import de.geheimagentnr1.player_time_manager.text.Messages;
import de.geheimagentnr1.player_time_manager.time.DurationParser;
import de.geheimagentnr1.player_time_manager.time.RefillMode;
import de.geheimagentnr1.player_time_manager.time.Scope;
import de.geheimagentnr1.player_time_manager.time.TimeEntry;
import de.geheimagentnr1.player_time_manager.time.TimeService;
import de.geheimagentnr1.player_time_manager.time.TimeState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;


public class PlaytimeCommand {
	
	
	@NotNull
	private final SettingsHolder settings;
	
	@NotNull
	private final Supplier<TimeService> service;
	
	public PlaytimeCommand( @NotNull SettingsHolder _settings, @NotNull Supplier<TimeService> _service ) {
		
		settings = _settings;
		service = _service;
	}
	
	private enum Change {
		ADD,
		REMOVE,
		SET
	}
	
	@FunctionalInterface
	private interface ValueParser {
		
		@NotNull
		String parse( @NotNull String input );
	}
	
	@NotNull
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		
		return Commands.literal( "playtime" )
			.executes( this::showOwn )
			.then( Commands.literal( "time" ).requires( this::isAdmin )
				.then( Commands.literal( "get" ).then( players()
					.executes( context -> get( context, null ) )
					.then( Commands.literal( "world" ).executes( context -> get( context, Scope.WORLD ) ) )
					.then( Commands.literal( "dimension" ).then( dimension().executes( context -> get( context, dimensionScope( context ) ) ) ) ) ) )
				.then( change( "add", Change.ADD ) )
				.then( change( "remove", Change.REMOVE ) )
				.then( change( "set", Change.SET ) )
				.then( Commands.literal( "refill" ).then( players()
					.then( Commands.literal( "world" ).executes( context -> refill( context, List.of( Scope.WORLD ) ) ) )
					.then( Commands.literal( "dimension" ).then( dimension().executes( context -> refill( context, List.of( dimensionScope( context ) ) ) ) ) )
					.then( Commands.literal( "all" ).executes( context -> refill( context, service().activeScopes() ) ) ) ) )
				.then( Commands.literal( "overview" )
					.executes( context -> overview( context, null ) )
					.then( Commands.literal( "world" ).executes( context -> overview( context, Scope.WORLD ) ) )
					.then( Commands.literal( "dimension" ).then( dimension().executes( context -> overview( context, dimensionScope( context ) ) ) ) ) ) )
			.then( Commands.literal( "config" ).requires( this::isAdmin )
				.then( Commands.literal( "general" )
					.then( stringKey( "general.refill_cron", "refill_cron", () -> config().read().general().refillCron(), config()::setGeneralRefillCron, ConfigEdits::cronValue, true ) )
					.then( stringKey( "general.refill_mode", "refill_mode", () -> config().read().general().refillMode(), value -> config().setGeneralRefillMode( RefillMode.valueOf( value ) ), ConfigEdits::modeValue, false ) )
					.then( stringKey( "general.refill_max", "refill_max", () -> config().read().general().refillMax(), config()::setGeneralRefillMax, input -> ConfigEdits.durationValue( input, true ), false ) )
					.then( boolKey( "general.ops_exempt", "ops_exempt", () -> config().read().general().opsExempt(), config()::setOpsExempt ) ) )
				.then( Commands.literal( "world" )
					.then( boolKey( "world.enabled", "enabled", () -> config().read().world().enabled(), config()::setWorldEnabled ) )
					.then( stringKey( "world.limit", "limit", () -> config().read().world().limit(), config()::setWorldLimit, input -> ConfigEdits.durationValue( input, false ), false ) )
					.then( boolKey( "world.use_custom_refill", "use_custom_refill", () -> config().read().world().useCustomRefill(), config()::setWorldUseCustomRefill ) )
					.then( stringKey( "world.refill_cron", "refill_cron", () -> config().read().world().refillCron(), config()::setWorldRefillCron, ConfigEdits::cronValue, true ) )
					.then( stringKey( "world.refill_mode", "refill_mode", () -> config().read().world().refillMode(), value -> config().setWorldRefillMode( RefillMode.valueOf( value ) ), ConfigEdits::modeValue, false ) )
					.then( stringKey( "world.refill_max", "refill_max", () -> config().read().world().refillMax(), config()::setWorldRefillMax, input -> ConfigEdits.durationValue( input, true ), false ) ) )
				.then( Commands.literal( "dimension" ).then( dimension()
					.executes( this::showDimension )
					.then( Commands.literal( "add" ).executes( this::addDimension ) )
					.then( Commands.literal( "remove" ).executes( this::removeDimension ) )
					.then( dimensionBool( "enabled", ScopeRaw::enabled, ConfigEdits::withEnabled ) )
					.then( dimensionString( "limit", ScopeRaw::limit, ConfigEdits::withLimit, input -> ConfigEdits.durationValue( input, false ), false ) )
					.then( dimensionBool( "use_custom_refill", ScopeRaw::useCustomRefill, ConfigEdits::withUseCustomRefill ) )
					.then( dimensionString( "refill_cron", ScopeRaw::refillCron, ConfigEdits::withRefillCron, ConfigEdits::cronValue, true ) )
					.then( dimensionString( "refill_mode", ScopeRaw::refillMode, ConfigEdits::withRefillMode, ConfigEdits::modeValue, false ) )
					.then( dimensionString( "refill_max", ScopeRaw::refillMax, ConfigEdits::withRefillMax, input -> ConfigEdits.durationValue( input, true ), false ) )
					.then( Commands.literal( "target" )
						.executes( this::showTarget )
						.then( Commands.literal( "here" ).executes( this::targetHere ) )
						.then( Commands.literal( "default" ).executes( context -> setTarget( context, SettingsResolver.OVERWORLD, "" ) ) )
						.then( Commands.argument( "target_dimension", DimensionArgument.dimension() )
							.then( Commands.argument( "position", StringArgumentType.greedyString() ).executes( this::targetPosition ) ) ) ) ) ) )
			.then( Commands.literal( "players" ).requires( this::isAdmin )
				.then( playerList( "limited", () -> config().read().general().limitedPlayers(), config()::setLimitedPlayers ) )
				.then( playerList( "exempt", () -> config().read().general().exemptPlayers(), config()::setExemptPlayers ) ) );
	}
	
	// ------------------------------------------------------------------ helpers
	
	private boolean isAdmin( @NotNull CommandSourceStack source ) {
		
		return settings.isLoaded() && source.hasPermission( settings.current().commandPermissionLevel() );
	}
	
	@NotNull
	private ServerConfig config() {
		
		return settings.config();
	}
	
	@NotNull
	private TimeService service() {
		
		TimeService current = service.get();
		if( current == null ) {
			throw new IllegalStateException( "Player Time Manager is not running" );
		}
		return current;
	}
	
	@NotNull
	private static RequiredArgumentBuilder<CommandSourceStack, GameProfileArgument.Result> players() {
		
		return Commands.argument( "players", GameProfileArgument.gameProfile() );
	}
	
	@NotNull
	private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> dimension() {
		
		return Commands.argument( "dimension", DimensionArgument.dimension() );
	}
	
	@NotNull
	private static String dimensionId( @NotNull CommandContext<CommandSourceStack> context, @NotNull String name ) throws CommandSyntaxException {
		
		return DimensionArgument.getDimension( context, name ).dimension().location().toString();
	}
	
	@NotNull
	private static Scope dimensionScope( @NotNull CommandContext<CommandSourceStack> context ) throws CommandSyntaxException {
		
		return Scope.dimension( dimensionId( context, "dimension" ) );
	}
	
	private static int success( @NotNull CommandContext<CommandSourceStack> context, @NotNull Component message ) {
		
		context.getSource().sendSuccess( () -> message, true );
		return 1;
	}
	
	private static int failure( @NotNull CommandContext<CommandSourceStack> context, @NotNull Component message ) {
		
		context.getSource().sendFailure( message );
		return 0;
	}
	
	private void reloadSettings( @NotNull CommandContext<CommandSourceStack> context ) {
		
		settings.reload( context.getSource().getServer() );
	}
	
	@NotNull
	private static String shown( @NotNull String value ) {
		
		return value.isEmpty() ? "\"\"" : value;
	}
	
	// ------------------------------------------------------------------ /playtime
	
	private int showOwn( @NotNull CommandContext<CommandSourceStack> context ) {
		
		ServerPlayer player = context.getSource().getPlayer();
		if( player == null ) {
			return failure( context, Messages.onlyPlayers() );
		}
		TimeService current = service();
		TimeState state = current.state( player );
		List<Scope> scopes = current.activeScopes();
		if( state == TimeState.EXCLUDED || scopes.isEmpty() ) {
			context.getSource().sendSystemMessage( Messages.statusNone() );
			return 1;
		}
		long now = System.currentTimeMillis();
		context.getSource().sendSystemMessage( Messages.statusHeader() );
		for( Scope scope : scopes ) {
			current.entry( scope, player.getUUID(), now, true ).ifPresent( entry -> context.getSource().sendSystemMessage( Messages.statusLine(
				Messages.scopeName( scope ),
				DurationParser.format( entry.remainingMillis() ),
				Messages.nextRefill( current.nextRefill( scope, now ).orElse( null ) ),
				state == TimeState.PAUSED
			) ) );
		}
		return 1;
	}
	
	// ------------------------------------------------------------------ /playtime time
	
	private int get( @NotNull CommandContext<CommandSourceStack> context, @Nullable Scope scope ) throws CommandSyntaxException {
		
		Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles( context, "players" );
		TimeService current = service();
		List<Scope> scopes = scope == null ? current.activeScopes() : List.of( scope );
		long now = System.currentTimeMillis();
		int count = 0;
		for( Scope target : scopes ) {
			if( !current.scopeSettings( target ).active() ) {
				failure( context, Messages.noLimit( Messages.scopeName( target ) ) );
				continue;
			}
			for( GameProfile profile : profiles ) {
				Optional<TimeEntry> entry = current.entry( target, profile.getId(), now, true );
				if( entry.isPresent() ) {
					success( context, Messages.timeGet( profile.getName(), Messages.scopeName( target ), DurationParser.format( entry.get().remainingMillis() ) ) );
					count++;
				}
			}
		}
		return count;
	}
	
	@NotNull
	private LiteralArgumentBuilder<CommandSourceStack> change( @NotNull String name, @NotNull Change change ) {
		
		return Commands.literal( name ).then( players()
			.then( Commands.literal( "world" ).then( duration().executes( context -> change( context, change, List.of( Scope.WORLD ) ) ) ) )
			.then( Commands.literal( "dimension" ).then( dimension().then( duration().executes( context -> change( context, change, List.of( dimensionScope( context ) ) ) ) ) ) )
			.then( Commands.literal( "all" ).then( duration().executes( context -> change( context, change, service().activeScopes() ) ) ) ) );
	}
	
	@NotNull
	private static RequiredArgumentBuilder<CommandSourceStack, String> duration() {
		
		return Commands.argument( "duration", StringArgumentType.word() );
	}
	
	private int change( @NotNull CommandContext<CommandSourceStack> context, @NotNull Change change, @NotNull List<Scope> scopes ) throws CommandSyntaxException {
		
		long millis;
		try {
			millis = DurationParser.parse( StringArgumentType.getString( context, "duration" ) );
		} catch( IllegalArgumentException exception ) {
			return failure( context, Messages.invalidValue( exception.getMessage() ) );
		}
		Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles( context, "players" );
		TimeService current = service();
		long now = System.currentTimeMillis();
		int count = 0;
		for( Scope scope : scopes ) {
			if( !current.scopeSettings( scope ).active() ) {
				failure( context, Messages.noLimit( Messages.scopeName( scope ) ) );
				continue;
			}
			for( GameProfile profile : profiles ) {
				long remaining = current.entry( scope, profile.getId(), now, true ).map( TimeEntry::remainingMillis ).orElse( 0L );
				long newRemaining = switch( change ) {
					case ADD -> remaining > Long.MAX_VALUE - millis ? Long.MAX_VALUE : remaining + millis;
					case REMOVE -> Math.max( 0, remaining - millis );
					case SET -> millis;
				};
				Optional<TimeEntry> changed = current.setRemaining( scope, profile.getId(), newRemaining, now );
				if( changed.isPresent() ) {
					success( context, Messages.timeChanged( profile.getName(), Messages.scopeName( scope ), DurationParser.format( changed.get().remainingMillis() ) ) );
					count++;
				}
			}
		}
		return count;
	}
	
	private int refill( @NotNull CommandContext<CommandSourceStack> context, @NotNull List<Scope> scopes ) throws CommandSyntaxException {
		
		Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles( context, "players" );
		TimeService current = service();
		long now = System.currentTimeMillis();
		int count = 0;
		for( Scope scope : scopes ) {
			if( !current.scopeSettings( scope ).active() ) {
				failure( context, Messages.noLimit( Messages.scopeName( scope ) ) );
				continue;
			}
			for( GameProfile profile : profiles ) {
				Optional<TimeEntry> refilled = current.refillNow( scope, profile.getId(), now );
				if( refilled.isPresent() ) {
					success( context, Messages.timeChanged( profile.getName(), Messages.scopeName( scope ), DurationParser.format( refilled.get().remainingMillis() ) ) );
					count++;
				}
			}
		}
		return count;
	}
	
	private int overview( @NotNull CommandContext<CommandSourceStack> context, @Nullable Scope scope ) {
		
		TimeService current = service();
		List<Scope> scopes = scope == null ? current.activeScopes() : List.of( scope );
		long now = System.currentTimeMillis();
		for( Scope target : scopes ) {
			if( !current.scopeSettings( target ).active() ) {
				failure( context, Messages.noLimit( Messages.scopeName( target ) ) );
				continue;
			}
			context.getSource().sendSystemMessage( Messages.overviewHeader( Messages.scopeName( target ) ) );
			Map<UUID, TimeEntry> entries = current.entries( target, now );
			if( entries.isEmpty() ) {
				context.getSource().sendSystemMessage( Messages.overviewEmpty() );
				continue;
			}
			entries.entrySet().stream()
				.sorted( Comparator.comparingLong( entry -> entry.getValue().remainingMillis() ) )
				.forEach( entry -> context.getSource().sendSystemMessage( Messages.overviewLine(
					current.server().getProfileCache().get( entry.getKey() ).map( GameProfile::getName ).orElse( entry.getKey().toString() ),
					DurationParser.format( entry.getValue().remainingMillis() )
				) ) );
		}
		return 1;
	}
	
	// ------------------------------------------------------------------ /playtime config general|world
	
	@NotNull
	private LiteralArgumentBuilder<CommandSourceStack> stringKey(
		@NotNull String fullKey,
		@NotNull String key,
		@NotNull Supplier<String> getter,
		@NotNull Consumer<String> setter,
		@NotNull ValueParser parser,
		boolean greedy ) {
		
		return Commands.literal( key )
			.executes( context -> success( context, Messages.configValue( fullKey, shown( getter.get() ) ) ) )
			.then( Commands.argument( "value", greedy ? StringArgumentType.greedyString() : StringArgumentType.word() )
				.suggests( ( context, builder ) -> key.equals( "refill_mode" )
					? SharedSuggestionProvider.suggest( List.of( "SET", "ADD" ), builder )
					: builder.buildFuture() )
				.executes( context -> {
					String value;
					try {
						value = parser.parse( StringArgumentType.getString( context, "value" ) );
					} catch( IllegalArgumentException exception ) {
						return failure( context, Messages.invalidValue( exception.getMessage() ) );
					}
					setter.accept( value );
					reloadSettings( context );
					return success( context, Messages.configSet( fullKey, shown( value ) ) );
				} ) );
	}
	
	@NotNull
	private LiteralArgumentBuilder<CommandSourceStack> boolKey(
		@NotNull String fullKey,
		@NotNull String key,
		@NotNull Supplier<Boolean> getter,
		@NotNull Consumer<Boolean> setter ) {
		
		return Commands.literal( key )
			.executes( context -> success( context, Messages.configValue( fullKey, String.valueOf( getter.get() ) ) ) )
			.then( Commands.argument( "value", BoolArgumentType.bool() ).executes( context -> {
				boolean value = BoolArgumentType.getBool( context, "value" );
				setter.accept( value );
				reloadSettings( context );
				return success( context, Messages.configSet( fullKey, String.valueOf( value ) ) );
			} ) );
	}
	
	// ------------------------------------------------------------------ /playtime config dimension
	
	private int updateDimension(
		@NotNull CommandContext<CommandSourceStack> context,
		@NotNull String key,
		@NotNull String shownValue,
		@NotNull UnaryOperator<DimensionRaw> change ) throws CommandSyntaxException {
		
		String id = dimensionId( context, "dimension" );
		List<DimensionRaw> dimensions = new ArrayList<>( config().read().dimensions() );
		int index = indexOf( dimensions, id );
		if( index < 0 ) {
			return failure( context, Messages.dimensionUnknown( id ) );
		}
		dimensions.set( index, change.apply( dimensions.get( index ) ) );
		config().setDimensions( dimensions );
		reloadSettings( context );
		return success( context, Messages.configSet( id + "." + key, shownValue ) );
	}
	
	private static int indexOf( @NotNull List<DimensionRaw> dimensions, @NotNull String id ) {
		
		for( int i = 0; i < dimensions.size(); i++ ) {
			if( dimensions.get( i ).dimension().trim().equals( id ) ) {
				return i;
			}
		}
		return -1;
	}
	
	@NotNull
	private Optional<DimensionRaw> findDimension( @NotNull String id ) {
		
		return config().read().dimensions().stream().filter( dimension -> dimension.dimension().trim().equals( id ) ).findFirst();
	}
	
	private int showDimensionValue( @NotNull CommandContext<CommandSourceStack> context, @NotNull String key, @NotNull Function<DimensionRaw, String> getter ) throws CommandSyntaxException {
		
		String id = dimensionId( context, "dimension" );
		Optional<DimensionRaw> dimension = findDimension( id );
		if( dimension.isEmpty() ) {
			return failure( context, Messages.dimensionUnknown( id ) );
		}
		return success( context, Messages.configValue( id + "." + key, getter.apply( dimension.get() ) ) );
	}
	
	@NotNull
	private LiteralArgumentBuilder<CommandSourceStack> dimensionString(
		@NotNull String key,
		@NotNull Function<ScopeRaw, String> getter,
		@NotNull BiFunction<ScopeRaw, String, ScopeRaw> setter,
		@NotNull ValueParser parser,
		boolean greedy ) {
		
		return Commands.literal( key )
			.executes( context -> showDimensionValue( context, key, dimension -> shown( getter.apply( dimension.scope() ) ) ) )
			.then( Commands.argument( "value", greedy ? StringArgumentType.greedyString() : StringArgumentType.word() )
				.suggests( ( context, builder ) -> key.equals( "refill_mode" )
					? SharedSuggestionProvider.suggest( List.of( "SET", "ADD" ), builder )
					: builder.buildFuture() )
				.executes( context -> {
					String value;
					try {
						value = parser.parse( StringArgumentType.getString( context, "value" ) );
					} catch( IllegalArgumentException exception ) {
						return failure( context, Messages.invalidValue( exception.getMessage() ) );
					}
					return updateDimension( context, key, shown( value ), dimension -> ConfigEdits.withScope( dimension, scope -> setter.apply( scope, value ) ) );
				} ) );
	}
	
	@NotNull
	private LiteralArgumentBuilder<CommandSourceStack> dimensionBool(
		@NotNull String key,
		@NotNull Function<ScopeRaw, Boolean> getter,
		@NotNull BiFunction<ScopeRaw, Boolean, ScopeRaw> setter ) {
		
		return Commands.literal( key )
			.executes( context -> showDimensionValue( context, key, dimension -> String.valueOf( getter.apply( dimension.scope() ) ) ) )
			.then( Commands.argument( "value", BoolArgumentType.bool() ).executes( context -> {
				boolean value = BoolArgumentType.getBool( context, "value" );
				return updateDimension( context, key, String.valueOf( value ), dimension -> ConfigEdits.withScope( dimension, scope -> setter.apply( scope, value ) ) );
			} ) );
	}
	
	// Shows all values of the entry and the config problems that belong to it
	private int showDimension( @NotNull CommandContext<CommandSourceStack> context ) throws CommandSyntaxException {
		
		String id = dimensionId( context, "dimension" );
		Optional<DimensionRaw> dimension = findDimension( id );
		if( dimension.isEmpty() ) {
			return failure( context, Messages.dimensionUnknown( id ) );
		}
		DimensionEntries.toValues( dimension.get() ).forEach( ( key, value ) ->
			context.getSource().sendSystemMessage( Messages.configValue( id + "." + key, shown( String.valueOf( value ) ) ) ) );
		settings.current().problems().stream()
			.filter( problem -> problem.contains( "\"" + id + "\"" ) )
			.forEach( problem -> context.getSource().sendFailure( Component.literal( problem ) ) );
		return 1;
	}
	
	private int addDimension( @NotNull CommandContext<CommandSourceStack> context ) throws CommandSyntaxException {
		
		String id = dimensionId( context, "dimension" );
		if( id.equals( SettingsResolver.OVERWORLD ) ) {
			return failure( context, Messages.overworldNotAllowed() );
		}
		List<DimensionRaw> dimensions = new ArrayList<>( config().read().dimensions() );
		if( indexOf( dimensions, id ) >= 0 ) {
			return failure( context, Messages.dimensionExists( id ) );
		}
		dimensions.add( ConfigEdits.newDimension( id ) );
		config().setDimensions( dimensions );
		reloadSettings( context );
		return success( context, Messages.dimensionAdded( id ) );
	}
	
	private int removeDimension( @NotNull CommandContext<CommandSourceStack> context ) throws CommandSyntaxException {
		
		String id = dimensionId( context, "dimension" );
		List<DimensionRaw> dimensions = new ArrayList<>( config().read().dimensions() );
		if( !dimensions.removeIf( dimension -> dimension.dimension().trim().equals( id ) ) ) {
			return failure( context, Messages.dimensionUnknown( id ) );
		}
		config().setDimensions( dimensions );
		reloadSettings( context );
		return success( context, Messages.dimensionRemoved( id ) );
	}
	
	private int showTarget( @NotNull CommandContext<CommandSourceStack> context ) throws CommandSyntaxException {
		
		return showDimensionValue( context, "target", dimension -> dimension.targetDimension() + " " + shown( dimension.targetPosition() ) );
	}
	
	private int targetHere( @NotNull CommandContext<CommandSourceStack> context ) throws CommandSyntaxException {
		
		ServerPlayer player = context.getSource().getPlayerOrException();
		String targetDimension = player.level().dimension().location().toString();
		String position = new TargetPosition( player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot() ).format();
		return setTarget( context, targetDimension, position );
	}
	
	private int targetPosition( @NotNull CommandContext<CommandSourceStack> context ) throws CommandSyntaxException {
		
		String targetDimension = dimensionId( context, "target_dimension" );
		String position;
		try {
			position = TargetPosition.parse( StringArgumentType.getString( context, "position" ) ).format();
		} catch( IllegalArgumentException exception ) {
			return failure( context, Messages.invalidValue( exception.getMessage() ) );
		}
		return setTarget( context, targetDimension, position );
	}
	
	private int setTarget( @NotNull CommandContext<CommandSourceStack> context, @NotNull String targetDimension, @NotNull String position ) throws CommandSyntaxException {
		
		String id = dimensionId( context, "dimension" );
		if( targetDimension.equals( id ) ) {
			return failure( context, Messages.invalidValue( "The target can't be inside the limited dimension itself" ) );
		}
		return updateDimension( context, "target", targetDimension + " " + shown( position ), dimension -> ConfigEdits.withTarget( dimension, targetDimension, position ) );
	}
	
	// ------------------------------------------------------------------ /playtime players
	
	@NotNull
	private LiteralArgumentBuilder<CommandSourceStack> playerList(
		@NotNull String list,
		@NotNull Supplier<List<PlayerRef>> getter,
		@NotNull Consumer<List<PlayerRef>> setter ) {
		
		return Commands.literal( list )
			.then( Commands.literal( "list" ).executes( context -> success( context, Messages.playersList(
				list,
				getter.get().stream().map( PlayerRef::name ).reduce( ( a, b ) -> a + ", " + b ).orElse( "-" )
			) ) ) )
			.then( Commands.literal( "add" ).then( players().executes( context -> {
				List<PlayerRef> players = new ArrayList<>( getter.get() );
				for( GameProfile profile : GameProfileArgument.getGameProfiles( context, "players" ) ) {
					if( players.stream().anyMatch( player -> player.uuid().equals( profile.getId().toString() ) ) ) {
						failure( context, Messages.playerAlreadyListed( list, profile.getName() ) );
					} else {
						players.add( new PlayerRef( profile.getName(), profile.getId().toString() ) );
						success( context, Messages.playerAdded( list, profile.getName() ) );
					}
				}
				setter.accept( players );
				reloadSettings( context );
				return 1;
			} ) ) )
			.then( Commands.literal( "remove" ).then( players().executes( context -> {
				List<PlayerRef> players = new ArrayList<>( getter.get() );
				for( GameProfile profile : GameProfileArgument.getGameProfiles( context, "players" ) ) {
					boolean removed = players.removeIf( player -> player.uuid().equals( profile.getId().toString() )
						|| player.uuid().isBlank() && player.name().equalsIgnoreCase( profile.getName() ) );
					if( removed ) {
						success( context, Messages.playerRemoved( list, profile.getName() ) );
					} else {
						failure( context, Messages.playerNotListed( list, profile.getName() ) );
					}
				}
				setter.accept( players );
				reloadSettings( context );
				return 1;
			} ) ) );
	}
}
