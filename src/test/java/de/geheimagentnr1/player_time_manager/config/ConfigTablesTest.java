package de.geheimagentnr1.player_time_manager.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


class ConfigTablesTest {
	
	
	@Test
	void tableKeepsKeyOrderAndComments() {
		
		DimensionRaw nether = DimensionEntries.defaults().get( 0 );
		CommentedConfig table = ConfigTables.toTable( DimensionEntries.toValues( nether ), DimensionEntries.COMMENTS );
		
		assertEquals( new ArrayList<>( DimensionEntries.COMMENTS.keySet() ), List.copyOf( table.valueMap().keySet() ) );
		assertEquals( DimensionEntries.COMMENTS.get( "dimension" ), table.getComment( "dimension" ) );
	}
}
