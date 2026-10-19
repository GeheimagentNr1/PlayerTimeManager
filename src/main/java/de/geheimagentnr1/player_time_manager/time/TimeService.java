package de.geheimagentnr1.player_time_manager.time;

import com.mojang.logging.LogUtils;
import de.geheimagentnr1.player_time_manager.config.SettingsHolder;
import de.geheimagentnr1.player_time_manager.config.raw.PlayerRef;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedDimension;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedScope;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedSettings;
import de.geheimagentnr1.player_time_manager.data.TimeData;
import de.geheimagentnr1.player_time_manager.data.TimeStore;
import de.geheimagentnr1.player_time_manager.display.DisplayManager;
import de.geheimagentnr1.player_time_manager.display.ScopeStatus;
import de.geheimagentnr1.player_time_manager.display.WarningTracker;
import de.geheimagentnr1.player_time_manager.teleport.AnchorRefund;
import de.geheimagentnr1.player_time_manager.teleport.Destination;
import de.geheimagentnr1.player_time_manager.teleport.PlayerTeleporter;
import de.geheimagentnr1.player_time_manager.teleport.TargetResolver;
import de.geheimagentnr1.player_time_manager.text.Messages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;


public class TimeService {
	
	
	@NotNull
	private static final Logger LOGGER = LogUtils.getLogger();
	
	private static final long NOTICE_INTERVAL_MILLIS = 5_000L;
	
	@NotNull
	private final MinecraftServer server;
	
	@NotNull
	private final SettingsHolder settings;
	
	@NotNull
	private final DisplayManager display = new DisplayManager();
	
	@NotNull
	private final Map<UUID, Long> lastBlockedNotice = new HashMap<>();
	
	@NotNull
	private final Map<UUID, Long> lastExpiryAttempt = new HashMap<>();
	
	// Dimension a respawn was redirected away from, the message is sent to the respawned player
	@NotNull
	private final Map<UUID, String> redirectedRespawns = new HashMap<>();
	
	private long lastCountMillis = -1;
	
	public TimeService( @NotNull MinecraftServer _server, @NotNull SettingsHolder _settings ) {
		
		server = _server;
		settings = _settings;
	}
	
	@NotNull
	public MinecraftServer server() {
		
		return server;
	}
	
	@NotNull
	public DisplayManager display() {
		
		return display;
	}
	
	@NotNull
	public ResolvedScope scopeSettings( @NotNull Scope scope ) {
		
		ResolvedSettings current = settings.current();
		if( scope.isWorld() ) {
			return current.world();
		}
		ResolvedDimension dimension = current.dimensions().get( scope.dimension() );
		return dimension == null ? ResolvedScope.INACTIVE : dimension.scope();
	}
	
	@NotNull
	public List<Scope> activeScopes() {
		
		List<Scope> scopes = new ArrayList<>();
		ResolvedSettings current = settings.current();
		if( current.world().active() ) {
			scopes.add( Scope.WORLD );
		}
		current.dimensions().values().stream()
			.filter( dimension -> dimension.scope().active() )
			.forEach( dimension -> scopes.add( Scope.dimension( dimension.dimension() ) ) );
		return scopes;
	}
	
	@NotNull
	public Optional<TimeEntry> entry( @NotNull Scope scope, @NotNull UUID player, long now, boolean create ) {
		
		ResolvedScope scopeSettings = scopeSettings( scope );
		TimeData data = TimeStore.data( server, scope );
		if( !scopeSettings.active() || scopeSettings.refillRule() == null || data == null ) {
			return Optional.empty();
		}
		Optional<TimeEntry> existing = data.get( player );
		if( existing.isEmpty() && !create ) {
			return Optional.empty();
		}
		TimeEntry entry = existing
			.map( value -> RefillCalculator.apply( value, scopeSettings.refillRule(), now, settings.current().zone() ) )
			.orElseGet( () -> RefillCalculator.create( scopeSettings.refillRule(), now ) );
		data.put( player, entry );
		return Optional.of( entry );
	}
	
	// Like entry, but a player without an entry only gets a preview (full limit), nothing is stored
	@NotNull
	public Optional<TimeEntry> peek( @NotNull Scope scope, @NotNull UUID player, long now ) {
		
		Optional<TimeEntry> existing = entry( scope, player, now, false );
		ResolvedScope scopeSettings = scopeSettings( scope );
		if( existing.isPresent() || !scopeSettings.active() || scopeSettings.refillRule() == null ) {
			return existing;
		}
		return Optional.of( RefillCalculator.create( scopeSettings.refillRule(), now ) );
	}
	
	@NotNull
	private Optional<TimeEntry> update( @NotNull Scope scope, @NotNull UUID player, long now, @NotNull UnaryOperator<TimeEntry> change ) {
		
		return entry( scope, player, now, true ).map( entry -> {
			TimeEntry changed = change.apply( entry );
			TimeData data = TimeStore.data( server, scope );
			if( data != null ) {
				data.put( player, changed );
			}
			return changed;
		} );
	}
	
	@NotNull
	public Optional<TimeEntry> setRemaining( @NotNull Scope scope, @NotNull UUID player, long remainingMillis, long now ) {
		
		return update( scope, player, now, entry -> entry.withRemaining( remainingMillis ) );
	}
	
	@NotNull
	public Optional<TimeEntry> refillNow( @NotNull Scope scope, @NotNull UUID player, long now ) {
		
		ResolvedScope scopeSettings = scopeSettings( scope );
		if( scopeSettings.refillRule() == null ) {
			return Optional.empty();
		}
		return update( scope, player, now, entry -> RefillCalculator.refillNow( entry, scopeSettings.refillRule() ) );
	}
	
	@NotNull
	public Map<UUID, TimeEntry> entries( @NotNull Scope scope, long now ) {
		
		TimeData data = TimeStore.data( server, scope );
		Map<UUID, TimeEntry> result = new HashMap<>();
		if( data != null ) {
			for( UUID player : List.copyOf( data.entries().keySet() ) ) {
				entry( scope, player, now, false ).ifPresent( entry -> result.put( player, entry ) );
			}
		}
		return result;
	}
	
	@NotNull
	public Optional<ZonedDateTime> nextRefill( @NotNull Scope scope, long now ) {
		
		ResolvedScope scopeSettings = scopeSettings( scope );
		if( scopeSettings.refillRule() == null || scopeSettings.refillRule().cron() == null ) {
			return Optional.empty();
		}
		return scopeSettings.refillRule().cron().next( Instant.ofEpochMilli( now ).atZone( settings.current().zone() ) );
	}
	
	@NotNull
	private Component nextRefillText( @NotNull Scope scope, long now ) {
		
		return Messages.nextRefill( nextRefill( scope, now ).orElse( null ) );
	}
	
	@NotNull
	public TimeState state( @NotNull ServerPlayer player ) {
		
		return ExemptionChecker.state(
			new PlayerFacts(
				player.getUUID(),
				player.getGameProfile().getName(),
				server.getPlayerList().isOp( player.getGameProfile() ),
				player.isCreative(),
				player.isSpectator()
			),
			settings.current().playerRules()
		);
	}
	
	public boolean isBlocked( @NotNull ServerPlayer player, @NotNull String dimension, long now ) {
		
		Scope scope = Scope.dimension( dimension );
		if( !scopeSettings( scope ).active() || state( player ) != TimeState.COUNTING ) {
			return false;
		}
		return entry( scope, player.getUUID(), now, true ).map( entry -> entry.remainingMillis() <= 0 ).orElse( false );
	}
	
	private boolean isWorldExpired( @NotNull ServerPlayer player, long now ) {
		
		if( !scopeSettings( Scope.WORLD ).active() || state( player ) != TimeState.COUNTING ) {
			return false;
		}
		return entry( Scope.WORLD, player.getUUID(), now, true ).map( entry -> entry.remainingMillis() <= 0 ).orElse( false );
	}
	
	public void countSecond( long now ) {
		
		long elapsed = TimeAccounting.elapsedToCount( lastCountMillis, now );
		lastCountMillis = now;
		if( !settings.isLoaded() ) {
			return;
		}
		ResolvedSettings current = settings.current();
		for( ServerPlayer player : List.copyOf( server.getPlayerList().getPlayers() ) ) {
			countPlayer( player, current, elapsed, now );
		}
	}
	
	private void countPlayer( @NotNull ServerPlayer player, @NotNull ResolvedSettings current, long elapsed, long now ) {
		
		TimeState state = state( player );
		if( state == TimeState.EXCLUDED ) {
			display.remove( player );
			return;
		}
		String dimension = player.level().dimension().location().toString();
		List<ScopeStatus> statuses = new ArrayList<>();
		boolean worldExpired = false;
		boolean dimensionExpired = false;
		for( Scope scope : List.of( Scope.WORLD, Scope.dimension( dimension ) ) ) {
			ResolvedScope scopeSettings = scopeSettings( scope );
			Optional<TimeEntry> found = scopeSettings.active() ? entry( scope, player.getUUID(), now, true ) : Optional.empty();
			if( found.isEmpty() ) {
				continue;
			}
			TimeEntry entry = found.get();
			if( state == TimeState.COUNTING && elapsed > 0 && entry.remainingMillis() > 0 ) {
				long before = entry.remainingMillis();
				entry = setRemaining( scope, player.getUUID(), before - elapsed, now ).orElse( entry );
				WarningTracker.crossedThreshold( before, entry.remainingMillis(), current.display().warningTimesMillis() )
					.ifPresent( threshold -> display.warn( player, scope, threshold, current.display() ) );
			}
			if( state == TimeState.COUNTING && entry.remainingMillis() <= 0 ) {
				if( scope.isWorld() ) {
					worldExpired = true;
				} else {
					dimensionExpired = true;
				}
			}
			statuses.add( new ScopeStatus( scope, entry.remainingMillis(), scopeSettings.limitMillis(), state == TimeState.PAUSED ) );
		}
		if( worldExpired ) {
			kick( player, now );
			return;
		}
		if( dimensionExpired ) {
			sendOutOfDimension( player, dimension, now, false );
		}
		display.update( player, statuses, current.display() );
	}
	
	private void kick( @NotNull ServerPlayer player, long now ) {
		
		display.remove( player );
		player.connection.disconnect( Messages.worldExpired( nextRefillText( Scope.WORLD, now ) ) );
	}
	
	private void sendOutOfDimension( @NotNull ServerPlayer player, @NotNull String dimension, long now, boolean force ) {
		
		Long lastAttempt = lastExpiryAttempt.get( player.getUUID() );
		if( !force && lastAttempt != null && now - lastAttempt < NOTICE_INTERVAL_MILLIS ) {
			return;
		}
		lastExpiryAttempt.put( player.getUUID(), now );
		Destination destination = destination( player, dimension, now );
		boolean teleported = PlayerTeleporter.teleport( player, destination );
		if( !teleported ) {
			// e.g. another mod cancelled the teleport into the target dimension
			teleported = PlayerTeleporter.teleport( player, TargetResolver.worldSpawn( server, player ) );
		}
		if( !teleported ) {
			LOGGER.warn( "Player Time Manager: could not teleport {} out of {}", player.getGameProfile().getName(), dimension );
		}
		Scope scope = Scope.dimension( dimension );
		player.sendSystemMessage( Messages.dimensionExpired( Messages.scopeName( scope ), nextRefillText( scope, now ) ) );
	}
	
	@NotNull
	private Destination destination( @NotNull ServerPlayer player, @NotNull String dimension, long now ) {
		
		ResolvedDimension settingsOfDimension = settings.current().dimensions().get( dimension );
		return TargetResolver.resolve(
			server,
			player,
			settingsOfDimension == null ? null : settingsOfDimension.target(),
			target -> isBlocked( player, target, now )
		);
	}
	
	public void notifyBlocked( @NotNull ServerPlayer player, @NotNull String dimension, long now ) {
		
		Long last = lastBlockedNotice.get( player.getUUID() );
		if( last != null && now - last < NOTICE_INTERVAL_MILLIS ) {
			return;
		}
		lastBlockedNotice.put( player.getUUID(), now );
		Scope scope = Scope.dimension( dimension );
		player.sendSystemMessage( Messages.dimensionBlocked( Messages.scopeName( scope ), nextRefillText( scope, now ) ) );
	}
	
	public void refillAll( long now ) {
		
		if( !settings.isLoaded() ) {
			return;
		}
		for( Scope scope : activeScopes() ) {
			entries( scope, now );
		}
	}
	
	public void onLogin( @NotNull ServerPlayer player, long now ) {
		
		fillMissingUuids( player );
		ResolvedSettings current = settings.current();
		if( server.getPlayerList().isOp( player.getGameProfile() ) && !current.problems().isEmpty() ) {
			player.sendSystemMessage( Messages.configProblems( current.problems().size() ) );
		}
		if( isWorldExpired( player, now ) ) {
			kick( player, now );
			return;
		}
		String dimension = player.level().dimension().location().toString();
		if( isBlocked( player, dimension, now ) ) {
			sendOutOfDimension( player, dimension, now, true );
		}
	}
	
	// Called before the respawn, so the player doesn't appear in the blocked dimension first
	@NotNull
	public Optional<DimensionTransition> redirectRespawn(
		@NotNull ServerPlayer player,
		@NotNull DimensionTransition transition,
		boolean fromEndFight,
		long now ) {
		
		String dimension = transition.newLevel().dimension().location().toString();
		if( !isBlocked( player, dimension, now ) ) {
			return Optional.empty();
		}
		Destination destination = destination( player, dimension, now );
		refundAnchorCharge( player, transition, fromEndFight );
		redirectedRespawns.put( player.getUUID(), dimension );
		return Optional.of( new DimensionTransition(
			destination.level(),
			destination.position(),
			Vec3.ZERO,
			destination.yaw(),
			destination.pitch(),
			DimensionTransition.DO_NOTHING
		) );
	}
	
	private void refundAnchorCharge( @NotNull ServerPlayer player, @NotNull DimensionTransition transition, boolean fromEndFight ) {
		
		BlockPos position = player.getRespawnPosition();
		ServerLevel level = server.getLevel( player.getRespawnDimension() );
		if( position == null || level == null || level != transition.newLevel() ) {
			return;
		}
		BlockState state = level.getBlockState( position );
		if( AnchorRefund.isNeeded( state.getBlock() instanceof RespawnAnchorBlock, player.isRespawnForced(), fromEndFight, transition.missingRespawnBlock() ) ) {
			int charge = state.getValue( RespawnAnchorBlock.CHARGE );
			level.setBlock( position, state.setValue( RespawnAnchorBlock.CHARGE, AnchorRefund.refundedCharge( charge ) ), 3 );
		}
	}
	
	public void onRespawn( @NotNull ServerPlayer player, long now ) {
		
		String redirectedFrom = redirectedRespawns.remove( player.getUUID() );
		if( redirectedFrom != null ) {
			Scope scope = Scope.dimension( redirectedFrom );
			player.sendSystemMessage( Messages.dimensionExpired( Messages.scopeName( scope ), nextRefillText( scope, now ) ) );
		}
		String dimension = player.level().dimension().location().toString();
		if( isBlocked( player, dimension, now ) ) {
			sendOutOfDimension( player, dimension, now, true );
		}
	}
	
	public void onLogout( @NotNull ServerPlayer player ) {
		
		display.remove( player );
		lastBlockedNotice.remove( player.getUUID() );
		lastExpiryAttempt.remove( player.getUUID() );
		redirectedRespawns.remove( player.getUUID() );
	}
	
	// Entries added by hand with only a name get their uuid on the first login of that player
	private void fillMissingUuids( @NotNull ServerPlayer player ) {
		
		String name = player.getGameProfile().getName();
		String uuid = player.getUUID().toString();
		List<PlayerRef> limited = settings.config().read().general().limitedPlayers();
		List<PlayerRef> exempt = settings.config().read().general().exemptPlayers();
		List<PlayerRef> newLimited = withUuid( limited, name, uuid );
		List<PlayerRef> newExempt = withUuid( exempt, name, uuid );
		if( !newLimited.equals( limited ) ) {
			settings.config().setLimitedPlayers( newLimited );
		}
		if( !newExempt.equals( exempt ) ) {
			settings.config().setExemptPlayers( newExempt );
		}
		if( !newLimited.equals( limited ) || !newExempt.equals( exempt ) ) {
			settings.reload( server );
		}
	}
	
	@NotNull
	private static List<PlayerRef> withUuid( @NotNull List<PlayerRef> players, @NotNull String name, @NotNull String uuid ) {
		
		return players.stream()
			.map( player -> player.uuid().isBlank() && player.name().equalsIgnoreCase( name ) ? new PlayerRef( name, uuid ) : player )
			.toList();
	}
}
