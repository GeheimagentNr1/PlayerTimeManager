package de.geheimagentnr1.player_time_manager.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


class TargetPositionTest {
	
	
	@Test
	void parsesPositionWithAndWithoutRotation() {
		
		TargetPosition plain = TargetPosition.parse( "120 64 -35" );
		assertEquals( new TargetPosition( 120, 64, -35, null, null ), plain );
		assertNull( plain.yaw() );
		assertEquals( new TargetPosition( 120.5, 64, -35.25, 90f, -10f ), TargetPosition.parse( " 120.5  64 -35.25 90 -10 " ) );
	}
	
	@Test
	void rejectsInvalidPositions() {
		
		for( String invalid : new String[] { "", "1 2", "1 2 3 4", "1 2 3 4 5 6", "a 2 3", "1 2 NaN", "1 2 3 x 5" } ) {
			assertThrows( IllegalArgumentException.class, () -> TargetPosition.parse( invalid ), invalid );
		}
	}
	
	@Test
	void formatsShortAndRoundTrips() {
		
		assertEquals( "120 64 -35", new TargetPosition( 120, 64, -35, null, null ).format() );
		assertEquals( "120.5 64 -35.25 90 -10", new TargetPosition( 120.5, 64, -35.25, 90f, -10f ).format() );
		assertEquals( "1.23 2 3", new TargetPosition( 1.234, 2, 3, null, null ).format() );
		TargetPosition position = new TargetPosition( 10.75, 70, -3.5, 180f, 0f );
		assertEquals( position, TargetPosition.parse( position.format() ) );
	}
}
