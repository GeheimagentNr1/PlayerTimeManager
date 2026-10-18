package de.geheimagentnr1.player_time_manager.teleport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


class TeleportPlanTest {
	
	
	@Test
	void decidesWhoIsTeleported() {
		
		assertEquals( TeleportPlan.Mode.PLAYER_ONLY, TeleportPlan.decide( false, 0 ) );
		assertEquals( TeleportPlan.Mode.WHOLE_VEHICLE, TeleportPlan.decide( true, 0 ) );
		// Other players in the same vehicle stay where they are
		assertEquals( TeleportPlan.Mode.PLAYER_ONLY, TeleportPlan.decide( true, 1 ) );
	}
}
