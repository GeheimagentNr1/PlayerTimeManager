package de.geheimagentnr1.player_time_manager.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


class BarColorTest {
	
	
	@Test
	void colorsByPercentage() {
		
		assertEquals( BarColor.GREEN, BarColor.of( 60, 100, 50, 10 ) );
		assertEquals( BarColor.GREEN, BarColor.of( 50, 100, 50, 10 ) );
		assertEquals( BarColor.YELLOW, BarColor.of( 49, 100, 50, 10 ) );
		assertEquals( BarColor.YELLOW, BarColor.of( 10, 100, 50, 10 ) );
		assertEquals( BarColor.RED, BarColor.of( 9, 100, 50, 10 ) );
		assertEquals( BarColor.GREEN, BarColor.of( 500, 100, 50, 10 ) );
		assertEquals( BarColor.RED, BarColor.of( 0, 0, 50, 10 ) );
	}
	
	@Test
	void progressIsClamped() {
		
		assertEquals( 0.25f, BarColor.progress( 25, 100 ) );
		assertEquals( 1f, BarColor.progress( 250, 100 ) );
		assertEquals( 0f, BarColor.progress( -5, 100 ) );
		assertEquals( 0f, BarColor.progress( 5, 0 ) );
	}
}
