package de.geheimagentnr1.player_time_manager.config;

import de.geheimagentnr1.player_time_manager.config.raw.DimensionRaw;
import de.geheimagentnr1.player_time_manager.config.raw.GeneralRaw;
import de.geheimagentnr1.player_time_manager.config.raw.PlayerRef;
import de.geheimagentnr1.player_time_manager.config.raw.RawSettings;
import de.geheimagentnr1.player_time_manager.config.raw.ScopeRaw;

import java.util.List;


final class RawSettingsTestData {
	
	
	static GeneralRaw general() {
		
		return general( "0 6 * * *", List.of(), List.of(), "", 50, 10 );
	}
	
	static GeneralRaw general(
		String cron,
		List<PlayerRef> limited,
		List<PlayerRef> exempt,
		String timeZone,
		int yellow,
		int red ) {
		
		return new GeneralRaw( 3, true, true, true, limited, exempt, timeZone, cron, "SET", "", "BOSSBARS", yellow, red,
			List.of( "1m", "10m", "10s", "5m" ), true );
	}
	
	static ScopeRaw scope( boolean enabled, String limit ) {
		
		return new ScopeRaw( enabled, limit, false, "0 6 * * *", "SET", "" );
	}
	
	static DimensionRaw dimension( String id, String limit, String targetDimension, String targetPosition ) {
		
		return new DimensionRaw( id, scope( true, limit ), targetDimension, targetPosition );
	}
	
	static RawSettings settings( ScopeRaw world, DimensionRaw... dimensions ) {
		
		return new RawSettings( general(), world, List.of( dimensions ) );
	}
}
