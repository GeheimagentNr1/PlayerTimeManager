package de.geheimagentnr1.player_time_manager.teleport;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;


public record Destination( @NotNull ServerLevel level, @NotNull Vec3 position, float yaw, float pitch ) {

}
