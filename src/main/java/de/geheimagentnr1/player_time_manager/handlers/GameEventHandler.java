package de.geheimagentnr1.player_time_manager.handlers;

import de.geheimagentnr1.player_time_manager.commands.PlaytimeCommand;
import de.geheimagentnr1.player_time_manager.config.SettingsHolder;
import de.geheimagentnr1.player_time_manager.time.TimeService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public class GameEventHandler {
	
	
	@NotNull
	private final SettingsHolder settings;
	
	@Nullable
	private TimeService service;
	
	public GameEventHandler( @NotNull SettingsHolder _settings ) {
		
		settings = _settings;
	}
	
	@Nullable
	public TimeService service() {
		
		return service;
	}
	
	@SubscribeEvent
	public void handleServerStarted( @NotNull ServerStartedEvent event ) {
		
		settings.reload( event.getServer() );
		service = new TimeService( event.getServer(), settings );
		service.refillAll( System.currentTimeMillis() );
	}
	
	@SubscribeEvent
	public void handleServerStopping( @NotNull ServerStoppingEvent event ) {
		
		if( service != null ) {
			service.display().removeAll();
		}
		service = null;
	}
	
	@SubscribeEvent
	public void handleServerTick( @NotNull ServerTickEvent.Post event ) {
		
		TimeService current = service;
		if( current == null ) {
			return;
		}
		int tick = event.getServer().getTickCount();
		long now = System.currentTimeMillis();
		if( tick % 20 == 0 ) {
			current.countSecond( now );
		}
		if( tick % 1200 == 0 ) {
			current.refillAll( now );
		}
	}
	
	@SubscribeEvent
	public void handleTravel( @NotNull EntityTravelToDimensionEvent event ) {
		
		TimeService current = service;
		if( current == null ) {
			return;
		}
		String dimension = event.getDimension().identifier().toString();
		long now = System.currentTimeMillis();
		// A vehicle (horse, boat) must not take a blocked rider along through a portal
		List<Entity> travelers = new ArrayList<>();
		travelers.add( event.getEntity() );
		event.getEntity().getIndirectPassengers().forEach( travelers::add );
		for( Entity traveler : travelers ) {
			if( traveler instanceof ServerPlayer player && current.isBlocked( player, dimension, now ) ) {
				event.setCanceled( true );
				current.notifyBlocked( player, dimension, now );
			}
		}
	}
	
	@SubscribeEvent
	public void handleLogin( @NotNull PlayerEvent.PlayerLoggedInEvent event ) {
		
		if( service != null && event.getEntity() instanceof ServerPlayer player ) {
			service.onLogin( player, System.currentTimeMillis() );
		}
	}
	
	@SubscribeEvent
	public void handleRespawnPosition( @NotNull PlayerRespawnPositionEvent event ) {
		
		if( service != null && event.getEntity() instanceof ServerPlayer player ) {
			service.redirectRespawn( player, event.getTeleportTransition(), event.isFromEndFight(), System.currentTimeMillis() )
				.ifPresent( event::setTeleportTransition );
		}
	}
	
	@SubscribeEvent
	public void handleRespawn( @NotNull PlayerEvent.PlayerRespawnEvent event ) {
		
		if( service != null && event.getEntity() instanceof ServerPlayer player ) {
			service.onRespawn( player, System.currentTimeMillis() );
		}
	}
	
	@SubscribeEvent
	public void handleLogout( @NotNull PlayerEvent.PlayerLoggedOutEvent event ) {
		
		if( service != null && event.getEntity() instanceof ServerPlayer player ) {
			service.onLogout( player );
		}
	}
	
	@SubscribeEvent
	public void handleRegisterCommands( @NotNull RegisterCommandsEvent event ) {
		
		event.getDispatcher().register( new PlaytimeCommand( settings, this::service ).build() );
	}
}
