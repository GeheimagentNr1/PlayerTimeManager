package de.geheimagentnr1.player_time_manager.config.resolved;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public record ResolvedDimension( @NotNull String dimension, @NotNull ResolvedScope scope, @Nullable TargetSpec target ) {

}
