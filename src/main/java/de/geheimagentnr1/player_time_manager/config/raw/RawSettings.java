package de.geheimagentnr1.player_time_manager.config.raw;

import org.jetbrains.annotations.NotNull;

import java.util.List;


public record RawSettings( @NotNull GeneralRaw general, @NotNull ScopeRaw world, @NotNull List<DimensionRaw> dimensions ) {

}
