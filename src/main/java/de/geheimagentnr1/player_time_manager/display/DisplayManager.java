package de.geheimagentnr1.player_time_manager.display;

import de.geheimagentnr1.player_time_manager.config.resolved.DisplaySettings;
import de.geheimagentnr1.player_time_manager.text.Messages;
import de.geheimagentnr1.player_time_manager.time.DurationParser;
import de.geheimagentnr1.player_time_manager.time.Scope;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


public class DisplayManager {
	
	
	@NotNull
	private final Map<UUID, Map<Scope, ServerBossEvent>> bossbars = new HashMap<>();
	
	public void update( @NotNull ServerPlayer player, @NotNull List<ScopeStatus> statuses, @NotNull DisplaySettings settings ) {
		
		switch( settings.mode() ) {
			case NONE -> remove( player );
			case ACTIONBAR -> {
				remove( player );
				if( !statuses.isEmpty() ) {
					player.sendOverlayMessage( actionbar( statuses ) );
				}
			}
			case BOSSBARS -> showBossbars( player, statuses, settings );
			case BOSSBAR_SHORTEST -> showBossbars(
				player,
				statuses.stream().min( Comparator.comparingLong( ScopeStatus::remainingMillis ) ).map( List::of ).orElse( List.of() ),
				settings
			);
		}
	}
	
	@NotNull
	private static Component actionbar( @NotNull List<ScopeStatus> statuses ) {
		
		MutableComponent text = Component.empty();
		for( int i = 0; i < statuses.size(); i++ ) {
			ScopeStatus status = statuses.get( i );
			if( i > 0 ) {
				text.append( " | " );
			}
			text.append( Messages.bossbar( Messages.scopeName( status.scope() ), DurationParser.formatClock( status.remainingMillis() ), status.paused() ) );
		}
		return text;
	}
	
	private void showBossbars( @NotNull ServerPlayer player, @NotNull List<ScopeStatus> statuses, @NotNull DisplaySettings settings ) {
		
		Map<Scope, ServerBossEvent> playerBars = bossbars.computeIfAbsent( player.getUUID(), uuid -> new HashMap<>() );
		Set<Scope> shown = statuses.stream().map( ScopeStatus::scope ).collect( Collectors.toSet() );
		playerBars.entrySet().removeIf( entry -> {
			if( shown.contains( entry.getKey() ) ) {
				return false;
			}
			entry.getValue().removeAllPlayers();
			return true;
		} );
		for( ScopeStatus status : statuses ) {
			ServerBossEvent bar = playerBars.computeIfAbsent(
				status.scope(),
				scope -> new ServerBossEvent( UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS )
			);
			// After a respawn the player is a new object, the bossbar has to be sent to it again
			if( !bar.getPlayers().contains( player ) ) {
				bar.removeAllPlayers();
				bar.addPlayer( player );
			}
			bar.setName( Messages.bossbar(
				Messages.scopeName( status.scope() ),
				DurationParser.formatClock( status.remainingMillis() ),
				status.paused()
			) );
			bar.setProgress( BarColor.progress( status.remainingMillis(), status.limitMillis() ) );
			bar.setColor( switch( BarColor.of(
				status.remainingMillis(),
				status.limitMillis(),
				settings.yellowBelowPercent(),
				settings.redBelowPercent()
			) ) {
				case GREEN -> BossEvent.BossBarColor.GREEN;
				case YELLOW -> BossEvent.BossBarColor.YELLOW;
				case RED -> BossEvent.BossBarColor.RED;
			} );
		}
	}
	
	public void warn( @NotNull ServerPlayer player, @NotNull Scope scope, long thresholdMillis, @NotNull DisplaySettings settings ) {
		
		player.sendSystemMessage( Messages.warning( DurationParser.format( thresholdMillis ), Messages.scopeName( scope ) ) );
		if( settings.warningSound() ) {
			// Minecraft 1.21.11 removed playNotifySound, which sent exactly this packet to the player only
			player.connection.send( new ClientboundSoundPacket(
				SoundEvents.NOTE_BLOCK_BELL,
				SoundSource.MASTER,
				player.getX(),
				player.getY(),
				player.getZ(),
				1.0F,
				1.0F,
				player.getRandom().nextLong()
			) );
		}
	}
	
	public void remove( @NotNull ServerPlayer player ) {
		
		Map<Scope, ServerBossEvent> playerBars = bossbars.remove( player.getUUID() );
		if( playerBars != null ) {
			playerBars.values().forEach( ServerBossEvent::removeAllPlayers );
		}
	}
	
	public void removeAll() {
		
		bossbars.values().forEach( playerBars -> playerBars.values().forEach( ServerBossEvent::removeAllPlayers ) );
		bossbars.clear();
	}
}
