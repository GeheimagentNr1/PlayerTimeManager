package de.geheimagentnr1.player_time_manager.teleport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class AnchorRefundTest {
	
	
	@Test
	void refundsOnlyWhenMinecraftUsedACharge() {
		
		// Minecraft 1.21.1 uses a charge only for a not forced anchor spawn and not when leaving the End
		assertTrue( AnchorRefund.isNeeded( true, false, false ) );
		assertFalse( AnchorRefund.isNeeded( false, false, false ) );
		assertFalse( AnchorRefund.isNeeded( true, true, false ) );
		assertFalse( AnchorRefund.isNeeded( true, false, true ) );
	}
	
	@Test
	void refundedChargeIsAtMostFour() {
		
		assertEquals( 1, AnchorRefund.refundedCharge( 0 ) );
		assertEquals( 4, AnchorRefund.refundedCharge( 3 ) );
		assertEquals( 4, AnchorRefund.refundedCharge( 4 ) );
	}
}
