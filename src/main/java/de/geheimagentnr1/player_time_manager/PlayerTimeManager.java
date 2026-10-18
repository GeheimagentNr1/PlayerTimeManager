package de.geheimagentnr1.player_time_manager;

import de.geheimagentnr1.player_time_manager.config.ServerConfig;
import de.geheimagentnr1.player_time_manager.config.SettingsHolder;
import de.geheimagentnr1.player_time_manager.data.LegacyAttachmentMigration;
import de.geheimagentnr1.player_time_manager.data.ModAttachmentTypes;
import de.geheimagentnr1.player_time_manager.handlers.ConfigReloadHandler;
import de.geheimagentnr1.player_time_manager.handlers.GameEventHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;


@Mod( PlayerTimeManager.MODID )
public class PlayerTimeManager {
	
	
	@NotNull
	public static final String MODID = "player_time_manager";
	
	public PlayerTimeManager( @NotNull IEventBus modEventBus, @NotNull ModContainer modContainer ) {
		
		ServerConfig serverConfig = new ServerConfig();
		// NeoForge 26.3 renamed SERVER to SYNCED (default file name "-synced"); the old file name keeps existing configs
		modContainer.registerConfig( ModConfig.Type.SYNCED, serverConfig.getSpec(), MODID + "-server.toml" );
		SettingsHolder settings = new SettingsHolder( serverConfig );
		
		ModAttachmentTypes.ATTACHMENT_TYPES.register( modEventBus );
		modEventBus.register( new ConfigReloadHandler( settings, serverConfig.getSpec() ) );
		NeoForge.EVENT_BUS.register( new GameEventHandler( settings ) );
		NeoForge.EVENT_BUS.register( new LegacyAttachmentMigration() );
	}
}
