package de.geheimagentnr1.player_time_manager;

import de.geheimagentnr1.player_time_manager.config.ServerConfig;
import de.geheimagentnr1.player_time_manager.config.SettingsHolder;
import de.geheimagentnr1.player_time_manager.data.ModAttachmentTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.jetbrains.annotations.NotNull;


@Mod( PlayerTimeManager.MODID )
public class PlayerTimeManager {
	
	
	@NotNull
	public static final String MODID = "player_time_manager";
	
	public PlayerTimeManager( @NotNull IEventBus modEventBus, @NotNull ModContainer modContainer ) {
		
		ServerConfig serverConfig = new ServerConfig();
		modContainer.registerConfig( ModConfig.Type.SERVER, serverConfig.getSpec() );
		ModAttachmentTypes.ATTACHMENT_TYPES.register( modEventBus );
		SettingsHolder settings = new SettingsHolder( serverConfig );
	}
}
