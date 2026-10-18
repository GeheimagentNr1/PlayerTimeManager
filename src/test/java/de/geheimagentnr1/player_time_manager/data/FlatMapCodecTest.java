package de.geheimagentnr1.player_time_manager.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;


class FlatMapCodecTest {
	
	
	private static final Codec<Map<String, Integer>> OLD = Codec.unboundedMap( Codec.STRING, Codec.INT );
	
	@Test
	void writesTheSameFormatAsTheOldMapCodec() {
		
		Map<String, Integer> values = new LinkedHashMap<>();
		values.put( "a", 1 );
		values.put( "b", 2 );
		JsonElement expected = OLD.encodeStart( JsonOps.INSTANCE, values ).getOrThrow();
		JsonElement actual = FlatMapCodec.of( Codec.STRING, Codec.INT ).codec().encodeStart( JsonOps.INSTANCE, values ).getOrThrow();
		assertEquals( expected, actual );
	}
	
	@Test
	void readsDataWrittenByTheOldMapCodec() {
		
		JsonElement old = OLD.encodeStart( JsonOps.INSTANCE, Map.of( "a", 1, "b", 2 ) ).getOrThrow();
		assertEquals( Map.of( "a", 1, "b", 2 ), FlatMapCodec.of( Codec.STRING, Codec.INT ).codec().parse( JsonOps.INSTANCE, old ).getOrThrow() );
	}
}
