package de.geheimagentnr1.player_time_manager.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.InMemoryCommentedFormat;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;


// Without NeoForge classes, so the validator can be unit tested
public final class ConfigTables {
	
	
	private ConfigTables() {
	
	}
	
	// Only checks that the element is a table: a stricter validator would make NeoForge delete entries with typos
	public static boolean isTable( Object value ) {
		
		return value instanceof UnmodifiableConfig;
	}
	
	@NotNull
	public static CommentedConfig toTable( @NotNull Map<String, Object> values, @NotNull Map<String, String> comments ) {
		
		// Keeps the keys in the order of the comments map, so the file is readable
		CommentedConfig config = CommentedConfig.of( LinkedHashMap::new, InMemoryCommentedFormat.withUniversalSupport() );
		values.forEach( ( key, value ) -> {
			config.set( key, value );
			String comment = comments.get( key );
			if( comment != null ) {
				config.setComment( key, comment );
			}
		} );
		return config;
	}
}
