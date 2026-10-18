package de.geheimagentnr1.player_time_manager.time;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;


public record PlayerFacts( @NotNull UUID uuid, @NotNull String name, boolean op, boolean creative, boolean spectator ) {

}
