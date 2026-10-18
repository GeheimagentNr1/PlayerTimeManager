package de.geheimagentnr1.player_time_manager.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;


// Without NeoForge classes, so the validator can be unit tested
public final class ConfigTables {


	private ConfigTables() {

	}

	// Only checks that the element is a table: a stricter validator would make NeoForge delete entries with typos
	public static boolean isTable( Object value ) {

		return value instanceof UnmodifiableConfig;
	}
}
