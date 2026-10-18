package de.geheimagentnr1.player_time_manager.config.resolved;

import de.geheimagentnr1.player_time_manager.config.TargetPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public record TargetSpec( @NotNull String dimension, @Nullable TargetPosition position ) {

}
