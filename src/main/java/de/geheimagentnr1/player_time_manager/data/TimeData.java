package de.geheimagentnr1.player_time_manager.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.geheimagentnr1.player_time_manager.time.TimeEntry;
import net.minecraft.core.UUIDUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


public class TimeData {
	
	
	@NotNull
	public static final Codec<TimeEntry> ENTRY_CODEC = RecordCodecBuilder.create( instance -> instance.group(
		Codec.LONG.fieldOf( "remaining" ).forGetter( TimeEntry::remainingMillis ),
		Codec.LONG.fieldOf( "last_refill" ).forGetter( TimeEntry::lastRefillMillis ),
		Codec.STRING.optionalFieldOf( "schedule", "" ).forGetter( TimeEntry::scheduleId )
	).apply( instance, TimeEntry::new ) );
	
	@NotNull
	public static final Codec<TimeData> CODEC = Codec.unboundedMap( UUIDUtil.STRING_CODEC, ENTRY_CODEC )
		.xmap( TimeData::new, TimeData::entries );
	
	@NotNull
	private final Map<UUID, TimeEntry> entries;
	
	public TimeData() {
		
		this( Map.of() );
	}
	
	private TimeData( @NotNull Map<UUID, TimeEntry> _entries ) {
		
		entries = new HashMap<>( _entries );
	}
	
	@NotNull
	public Optional<TimeEntry> get( @NotNull UUID player ) {
		
		return Optional.ofNullable( entries.get( player ) );
	}
	
	public void put( @NotNull UUID player, @NotNull TimeEntry entry ) {
		
		entries.put( player, entry );
	}
	
	@NotNull
	public Map<UUID, TimeEntry> entries() {
		
		return Collections.unmodifiableMap( entries );
	}
}
