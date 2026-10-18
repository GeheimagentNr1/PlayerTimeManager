package de.geheimagentnr1.player_time_manager.config;

import de.geheimagentnr1.player_time_manager.config.raw.PlayerRef;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;


public final class PlayerEntries {
	
	
	@NotNull
	public static final Map<String, String> COMMENTS = Map.of(
		"name", "Player name (only used to show the entry and to fill in the uuid on the next login)",
		"uuid", "Player uuid (\"\" = filled in automatically on the next login of a player with this name)"
	);
	
	private PlayerEntries() {
	
	}
	
	@NotNull
	public static boolean matches( @NotNull PlayerRef player, @NotNull String name, @NotNull String uuid ) {
		
		return player.uuid().equals( uuid ) || player.uuid().isBlank() && player.name().equalsIgnoreCase( name );
	}
	
	@NotNull
	public static PlayerRef fromValues( @NotNull Map<String, ?> values ) {
		
		return new PlayerRef( DimensionEntries.string( values, "name", "" ), DimensionEntries.string( values, "uuid", "" ) );
	}
	
	@NotNull
	public static Map<String, Object> toValues( @NotNull PlayerRef player ) {
		
		Map<String, Object> values = new LinkedHashMap<>();
		values.put( "name", player.name() );
		values.put( "uuid", player.uuid() );
		return values;
	}
}
