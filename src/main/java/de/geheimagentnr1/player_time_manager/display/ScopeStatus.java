package de.geheimagentnr1.player_time_manager.display;

import de.geheimagentnr1.player_time_manager.time.Scope;
import org.jetbrains.annotations.NotNull;


public record ScopeStatus( @NotNull Scope scope, long remainingMillis, long limitMillis, boolean paused ) {

}
