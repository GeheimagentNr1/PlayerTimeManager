package de.geheimagentnr1.player_time_manager.config;

import com.mojang.logging.LogUtils;
import de.geheimagentnr1.player_time_manager.config.resolved.ResolvedSettings;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class SettingsHolder {
	
	
	@NotNull
	private static final Logger LOGGER = LogUtils.getLogger();
	
	@NotNull
	private final ServerConfig config;
	
	@Nullable
	private volatile ResolvedSettings current;
	
	public SettingsHolder( @NotNull ServerConfig _config ) {
		
		config = _config;
	}
	
	@NotNull
	public ServerConfig config() {
		
		return config;
	}
	
	public boolean isLoaded() {
		
		return current != null;
	}
	
	@NotNull
	public ResolvedSettings current() {
		
		ResolvedSettings settings = current;
		if( settings == null ) {
			throw new IllegalStateException( "Player Time Manager settings are not loaded yet" );
		}
		return settings;
	}
	
	@NotNull
	public List<String> reload( @NotNull MinecraftServer server ) {
		
		Set<String> knownDimensions = server.levelKeys().stream()
			.map( key -> key.location().toString() )
			.collect( Collectors.toSet() );
		ResolvedSettings settings = SettingsResolver.resolve( config.read(), knownDimensions, ZoneId.systemDefault() );
		current = settings;
		settings.problems().forEach( problem -> LOGGER.warn( "Player Time Manager config: {}", problem ) );
		LOGGER.info( "Player Time Manager settings loaded ({} problem(s))", settings.problems().size() );
		return settings.problems();
	}
}
