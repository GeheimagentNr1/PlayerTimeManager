package de.geheimagentnr1.player_time_manager.config;

import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class DimensionEntriesTest {
	
	
	@Test
	void roundTripsAllValues() {
		
		DimensionRaw nether = new DimensionRaw(
			"minecraft:the_nether",
			new ScopeRaw( true, "30m", true, "0 8 * * 6,0", "ADD", "2h" ),
			"minecraft:overworld",
			"10 64 -5 90 0"
		);
		assertEquals( nether, DimensionEntries.fromValues( DimensionEntries.toValues( nether ) ) );
	}
	
	@Test
	void everyKeyHasAComment() {
		
		Map<String, Object> values = DimensionEntries.toValues( DimensionEntries.defaults().getFirst() );
		assertEquals( List.copyOf( DimensionEntries.COMMENTS.keySet() ), List.copyOf( values.keySet() ) );
		DimensionEntries.COMMENTS.values().forEach( comment -> assertFalse( comment.isBlank() ) );
	}
	
	@Test
	void missingAndWrongTypedValuesFallBackWithoutLosingTheEntry() {
		
		DimensionRaw raw = DimensionEntries.fromValues( Map.of( "dimension", "minecraft:the_end", "limit", 30, "enabled", "true" ) );
		assertEquals( "minecraft:the_end", raw.dimension() );
		assertEquals( "30", raw.scope().limit() );
		assertTrue( raw.scope().enabled() );
		assertEquals( "minecraft:overworld", raw.targetDimension() );
		assertEquals( "", raw.targetPosition() );
		assertEquals( "SET", raw.scope().refillMode() );
	}
	
	@Test
	void defaultsAreDisabledNetherAndEnd() {
		
		List<DimensionRaw> defaults = DimensionEntries.defaults();
		assertEquals( List.of( "minecraft:the_nether", "minecraft:the_end" ), defaults.stream().map( DimensionRaw::dimension ).toList() );
		defaults.forEach( dimension -> assertFalse( dimension.scope().enabled() ) );
	}
}
