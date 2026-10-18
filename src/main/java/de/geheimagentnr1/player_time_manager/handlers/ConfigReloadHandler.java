package de.geheimagentnr1.player_time_manager.handlers;

import de.geheimagentnr1.player_time_manager.config.SettingsHolder;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;


public class ConfigReloadHandler {
	
	
	@NotNull
	private final SettingsHolder settings;
	
	@NotNull
	private final ModConfigSpec spec;
	
	public ConfigReloadHandler( @NotNull SettingsHolder _settings, @NotNull ModConfigSpec _spec ) {
		
		settings = _settings;
		spec = _spec;
	}
	
	// Fired when the TOML file was changed while the server is running (also after a command saved it)
	@SubscribeEvent
	public void handleReloading( @NotNull ModConfigEvent.Reloading event ) {
		
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if( event.getConfig().getSpec() == spec && server != null && settings.isLoaded() ) {
			server.execute( () -> settings.reload( server ) );
		}
	}
}
