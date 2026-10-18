package de.geheimagentnr1.player_time_manager.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;


public final class FlatMapCodec<K, V> extends MapCodec<Map<K, V>> {
	
	
	@NotNull
	private final Codec<K> keyCodec;
	
	@NotNull
	private final Codec<V> valueCodec;
	
	private FlatMapCodec( @NotNull Codec<K> keyCodec, @NotNull Codec<V> valueCodec ) {
		
		this.keyCodec = keyCodec;
		this.valueCodec = valueCodec;
	}
	
	@NotNull
	public static <K, V> FlatMapCodec<K, V> of( @NotNull Codec<K> keyCodec, @NotNull Codec<V> valueCodec ) {
		
		return new FlatMapCodec<>( keyCodec, valueCodec );
	}
	
	@Override
	public <T> Stream<T> keys( DynamicOps<T> ops ) {
		
		return Stream.empty();
	}
	
	// Broken entries are skipped and reported, the others are kept
	@Override
	public <T> DataResult<Map<K, V>> decode( DynamicOps<T> ops, MapLike<T> input ) {
		
		Map<K, V> result = new LinkedHashMap<>();
		List<String> errors = new ArrayList<>();
		input.entries().forEach( entry -> {
			DataResult<K> key = keyCodec.parse( ops, entry.getFirst() );
			DataResult<V> value = valueCodec.parse( ops, entry.getSecond() );
			key.error().ifPresent( error -> errors.add( error.message() ) );
			value.error().ifPresent( error -> errors.add( error.message() ) );
			key.result().ifPresent( k -> value.result().ifPresent( v -> result.put( k, v ) ) );
		} );
		return errors.isEmpty()
			? DataResult.success( result )
			: DataResult.error( () -> String.join( "; ", errors ), result );
	}
	
	@Override
	public <T> RecordBuilder<T> encode( Map<K, V> input, DynamicOps<T> ops, RecordBuilder<T> prefix ) {
		
		input.forEach( ( key, value ) -> prefix.add( keyCodec.encodeStart( ops, key ), valueCodec.encodeStart( ops, value ) ) );
		return prefix;
	}
}
