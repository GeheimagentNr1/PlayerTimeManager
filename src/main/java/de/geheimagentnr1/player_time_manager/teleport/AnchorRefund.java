package de.geheimagentnr1.player_time_manager.teleport;


// Minecraft uses a respawn anchor charge before the respawn can be redirected, the charge is given back
public final class AnchorRefund {
	
	
	private static final int MAX_CHARGE = 4;
	
	private AnchorRefund() {
	
	}
	
	// missingRespawnBlock: Minecraft found no usable anchor (empty or blocked) and took no charge
	public static boolean isNeeded( boolean anchor, boolean forced, boolean fromEndFight, boolean missingRespawnBlock ) {
		
		return anchor && !forced && !fromEndFight && !missingRespawnBlock;
	}
	
	public static int refundedCharge( int charge ) {
		
		return Math.min( charge + 1, MAX_CHARGE );
	}
}
