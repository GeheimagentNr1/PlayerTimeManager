package de.geheimagentnr1.player_time_manager.teleport;

import org.jetbrains.annotations.NotNull;


// Who is teleported when the time in a dimension is used up
public final class TeleportPlan {
	
	
	public enum Mode {
		PLAYER_ONLY,
		WHOLE_VEHICLE
	}
	
	private TeleportPlan() {
	
	}
	
	// The whole vehicle with its passengers moves like through a portal, unless other players ride it, too
	@NotNull
	public static Mode decide( boolean riding, int otherPlayerPassengers ) {
		
		return riding && otherPlayerPassengers == 0 ? Mode.WHOLE_VEHICLE : Mode.PLAYER_ONLY;
	}
}
