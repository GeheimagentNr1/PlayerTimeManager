package de.geheimagentnr1.player_time_manager.commands;

import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;


class ConfigEditsTest {
	
	
	@Test
	void editsKeepOtherValues() {
		
		DimensionRaw nether = new DimensionRaw( "minecraft:the_nether", new ScopeRaw( false, "1h", false, "0 6 * * *", "SET", "" ), "minecraft:overworld", "" );
		DimensionRaw enabled = ConfigEdits.withScope( nether, scope -> ConfigEdits.withEnabled( scope, true ) );
		assertEquals( new ScopeRaw( true, "1h", false, "0 6 * * *", "SET", "" ), enabled.scope() );
		assertEquals( nether.targetDimension(), enabled.targetDimension() );
		DimensionRaw target = ConfigEdits.withTarget( nether, "minecraft:the_end", "1 2 3" );
		assertEquals( "minecraft:the_end", target.targetDimension() );
		assertEquals( "1 2 3", target.targetPosition() );
		assertEquals( nether.scope(), target.scope() );
	}
	
	@Test
	void newDimensionIsDisabled() {
		
		DimensionRaw dimension = ConfigEdits.newDimension( "mymod:mining" );
		assertEquals( "mymod:mining", dimension.dimension() );
		assertFalse( dimension.scope().enabled() );
		assertEquals( "minecraft:overworld", dimension.targetDimension() );
	}
	
	@Test
	void valueParsing() {
		
		assertEquals( "", ConfigEdits.cronValue( "none" ) );
		assertEquals( "0 8 * * 6,0", ConfigEdits.cronValue( " 0 8  * * 6,0 " ) );
		assertThrows( IllegalArgumentException.class, () -> ConfigEdits.cronValue( "0 25 * * *" ) );
		assertEquals( "", ConfigEdits.durationValue( "NONE", true ) );
		assertEquals( "1h30m", ConfigEdits.durationValue( "1h30m", false ) );
		assertThrows( IllegalArgumentException.class, () -> ConfigEdits.durationValue( "none", false ) );
		assertThrows( IllegalArgumentException.class, () -> ConfigEdits.durationValue( "0m", false ) );
		assertEquals( "ADD", ConfigEdits.modeValue( "add" ) );
		assertThrows( IllegalArgumentException.class, () -> ConfigEdits.modeValue( "multiply" ) );
	}
}
