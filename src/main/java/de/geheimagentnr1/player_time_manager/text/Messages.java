package de.geheimagentnr1.player_time_manager.text;

import de.geheimagentnr1.player_time_manager.time.DurationParser;
import de.geheimagentnr1.player_time_manager.time.Scope;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.Map;


public final class Messages {
	
	
	@NotNull
	private static final String PREFIX = "player_time_manager.";
	
	@NotNull
	private static final Map<String, String> VANILLA_DIMENSIONS = Map.of(
		"minecraft:overworld", "Overworld",
		"minecraft:the_nether", "Nether",
		"minecraft:the_end", "The End"
	);
	
	private Messages() {
	
	}
	
	@NotNull
	private static MutableComponent tr( @NotNull String key, @NotNull String fallback, @NotNull Object... args ) {
		
		return Component.translatableWithFallback( PREFIX + key, fallback, args );
	}
	
	@NotNull
	public static MutableComponent scopeName( @NotNull Scope scope ) {
		
		if( scope.isWorld() ) {
			return tr( "scope.world", "World" );
		}
		String id = scope.dimension();
		return Component.translatableWithFallback(
			PREFIX + "dimension." + id.replace( ':', '.' ),
			VANILLA_DIMENSIONS.getOrDefault( id, id )
		);
	}
	
	@NotNull
	public static MutableComponent nextRefill( @Nullable ZonedDateTime next ) {
		
		return next == null ? tr( "refill.none", "only by an admin" ) : Component.literal( DurationParser.formatDateTime( next ) );
	}
	
	@NotNull
	public static MutableComponent dimensionExpired( @NotNull Component scope, @NotNull Component refill ) {
		
		return tr( "expired.dimension", "Your time in %s is used up. Next refill: %s", scope, refill );
	}
	
	@NotNull
	public static MutableComponent dimensionBlocked( @NotNull Component scope, @NotNull Component refill ) {
		
		return tr( "blocked.dimension", "You have no time left in %s. Next refill: %s", scope, refill );
	}
	
	@NotNull
	public static MutableComponent worldExpired( @NotNull Component refill ) {
		
		return tr( "expired.world", "Your playtime is used up. Next refill: %s", refill );
	}
	
	@NotNull
	public static MutableComponent warning( @NotNull String remaining, @NotNull Component scope ) {
		
		return tr( "warning", "Only %s left in %s", remaining, scope );
	}
	
	@NotNull
	public static MutableComponent statusHeader() {
		
		return tr( "status.header", "Your remaining time:" );
	}
	
	@NotNull
	public static MutableComponent statusLine( @NotNull Component scope, @NotNull String remaining, @NotNull Component refill, boolean paused ) {
		
		return paused
			? tr( "status.line.paused", "%s: %s, paused (next refill: %s)", scope, remaining, refill )
			: tr( "status.line", "%s: %s (next refill: %s)", scope, remaining, refill );
	}
	
	@NotNull
	public static MutableComponent statusNone() {
		
		return tr( "status.none", "No time limit applies to you." );
	}
	
	@NotNull
	public static MutableComponent bossbar( @NotNull Component scope, @NotNull String remaining, boolean paused ) {
		
		return paused
			? tr( "bossbar.paused", "%s: %s (paused)", scope, remaining )
			: tr( "bossbar", "%s: %s", scope, remaining );
	}
	
	@NotNull
	public static MutableComponent configProblems( int count ) {
		
		return tr( "config.problems", "Player Time Manager: the config has %s problem(s), see the server log.", count );
	}
	
	@NotNull
	public static MutableComponent timeGet( @NotNull String player, @NotNull Component scope, @NotNull String remaining ) {
		
		return tr( "command.time.get", "%s - %s: %s", player, scope, remaining );
	}
	
	@NotNull
	public static MutableComponent timeChanged( @NotNull String player, @NotNull Component scope, @NotNull String remaining ) {
		
		return tr( "command.time.changed", "%s - %s: now %s", player, scope, remaining );
	}
	
	@NotNull
	public static MutableComponent noLimit( @NotNull Component scope ) {
		
		return tr( "command.time.no_limit", "%s has no active time limit", scope );
	}
	
	@NotNull
	public static MutableComponent overviewHeader( @NotNull Component scope ) {
		
		return tr( "command.overview.header", "Remaining time in %s:", scope );
	}
	
	@NotNull
	public static MutableComponent overviewLine( @NotNull String player, @NotNull String remaining ) {
		
		return tr( "command.overview.line", "%s: %s", player, remaining );
	}
	
	@NotNull
	public static MutableComponent overviewEmpty() {
		
		return tr( "command.overview.empty", "No players yet" );
	}
	
	@NotNull
	public static MutableComponent configValue( @NotNull String key, @NotNull String value ) {
		
		return tr( "command.config.value", "%s = %s", key, value );
	}
	
	@NotNull
	public static MutableComponent configSet( @NotNull String key, @NotNull String value ) {
		
		return tr( "command.config.set", "%s set to %s", key, value );
	}
	
	@NotNull
	public static MutableComponent invalidValue( @NotNull String message ) {
		
		return tr( "command.invalid", "Invalid value: %s", message );
	}
	
	@NotNull
	public static MutableComponent dimensionAdded( @NotNull String id ) {
		
		return tr( "command.dimension.added", "%s added (disabled, enable it with: /playtime config dimension %s enabled true)", id, id );
	}
	
	@NotNull
	public static MutableComponent dimensionRemoved( @NotNull String id ) {
		
		return tr( "command.dimension.removed", "%s removed", id );
	}
	
	@NotNull
	public static MutableComponent dimensionUnknown( @NotNull String id ) {
		
		return tr( "command.dimension.unknown", "%s is not in the config, add it with: /playtime config dimension %s add", id, id );
	}
	
	@NotNull
	public static MutableComponent dimensionExists( @NotNull String id ) {
		
		return tr( "command.dimension.exists", "%s is already in the config", id );
	}
	
	@NotNull
	public static MutableComponent overworldNotAllowed() {
		
		return tr( "command.dimension.overworld", "The Overworld can't be limited as a dimension, use /playtime config world" );
	}
	
	@NotNull
	public static MutableComponent playersList( @NotNull String list, @NotNull String names ) {
		
		return tr( "command.players.list", "%s players: %s", list, names );
	}
	
	@NotNull
	public static MutableComponent playerAdded( @NotNull String list, @NotNull String name ) {
		
		return tr( "command.players.added", "%s added to %s players", name, list );
	}
	
	@NotNull
	public static MutableComponent playerRemoved( @NotNull String list, @NotNull String name ) {
		
		return tr( "command.players.removed", "%s removed from %s players", name, list );
	}
	
	@NotNull
	public static MutableComponent playerAlreadyListed( @NotNull String list, @NotNull String name ) {
		
		return tr( "command.players.already", "%s is already in %s players", name, list );
	}
	
	@NotNull
	public static MutableComponent playerNotListed( @NotNull String list, @NotNull String name ) {
		
		return tr( "command.players.not_listed", "%s is not in %s players", name, list );
	}
	
	@NotNull
	public static MutableComponent onlyPlayers() {
		
		return tr( "command.only_players", "Only players can use this command" );
	}
}
