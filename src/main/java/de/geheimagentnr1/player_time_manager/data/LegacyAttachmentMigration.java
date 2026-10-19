package de.geheimagentnr1.player_time_manager.data;

import com.mojang.logging.LogUtils;
import de.geheimagentnr1.player_time_manager.PlayerTimeManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;


// Since 26.1 NeoForge stores level attachments in dimensions/<namespace>/<path>/data/neoforge/data_attachments.dat and the
// world upgrade leaves the old <dimension folder>/data/neoforge_data_attachments.dat behind. The remaining times of the old
// file are taken over once, as long as the new file doesn't contain them yet (same format since 1.21.1, see TimeData.CODEC).
public class LegacyAttachmentMigration {
	
	
	@NotNull
	private static final Logger LOGGER = LogUtils.getLogger();
	
	@NotNull
	private static final String LEGACY_ATTACHMENTS_FILE = "neoforge_data_attachments.dat";
	
	@NotNull
	private static final String ATTACHMENTS_FILE = "neoforge/data_attachments.dat";
	
	@SubscribeEvent
	public void handleLevelLoad( @NotNull LevelEvent.Load event ) {
		
		if( !( event.getLevel() instanceof ServerLevel level ) ) {
			return;
		}
		Path worldFolder = level.getServer().getWorldPath( LevelResource.ROOT );
		CompoundTag legacyAttachments = readAttachments(
			level,
			legacyDimensionFolder( level.dimension(), worldFolder ).resolve( "data" ).resolve( LEGACY_ATTACHMENTS_FILE )
		);
		if( legacyAttachments.isEmpty() ) {
			return;
		}
		CompoundTag attachments = readAttachments(
			level,
			DimensionType.getStorageFolder( level.dimension(), worldFolder ).resolve( "data" ).resolve( ATTACHMENTS_FILE )
		);
		if( level.dimension() == Level.OVERWORLD ) {
			migrate( level, legacyAttachments, attachments, "world_time_data", ModAttachmentTypes.WORLD_TIME_DATA );
		}
		migrate( level, legacyAttachments, attachments, "dimension_time_data", ModAttachmentTypes.DIMENSION_TIME_DATA );
	}
	
	// Dimension folders up to 1.21.11
	@NotNull
	private static Path legacyDimensionFolder( @NotNull ResourceKey<Level> dimension, @NotNull Path worldFolder ) {
		
		if( dimension == Level.OVERWORLD ) {
			return worldFolder;
		}
		if( dimension == Level.NETHER ) {
			return worldFolder.resolve( "DIM-1" );
		}
		if( dimension == Level.END ) {
			return worldFolder.resolve( "DIM1" );
		}
		return dimension.identifier().resolveAgainst( worldFolder.resolve( "dimensions" ) );
	}
	
	@NotNull
	private static CompoundTag readAttachments( @NotNull ServerLevel level, @NotNull Path file ) {
		
		if( !Files.exists( file ) ) {
			return new CompoundTag();
		}
		try {
			return NbtIo.readCompressed( file, NbtAccounter.unlimitedHeap() ).getCompoundOrEmpty( "data" );
		} catch( IOException exception ) {
			LOGGER.error( "Player Time Manager: failed to read the level attachments {} of {}", file, level.dimension(), exception );
			return new CompoundTag();
		}
	}
	
	private static void migrate(
		@NotNull ServerLevel level,
		@NotNull CompoundTag legacyAttachments,
		@NotNull CompoundTag attachments,
		@NotNull String name,
		@NotNull Supplier<AttachmentType<TimeData>> type ) {
		
		String key = PlayerTimeManager.MODID + ":" + name;
		if( attachments.contains( key ) || !( legacyAttachments.get( key ) instanceof CompoundTag nbt ) ) {
			return;
		}
		// A broken entry must not cost the other remaining times of the dimension
		TimeData.CODEC.codec().parse( NbtOps.INSTANCE, nbt )
			.resultOrPartial( error -> LOGGER.warn( "Player Time Manager: could not take over all of {} of {}: {}", key, level.dimension(), error ) )
			.ifPresent( data -> {
				level.setData( type, data );
				LOGGER.info( "Player Time Manager: took over {} remaining times of {} from the old save file", data.entries().size(), level.dimension() );
			} );
	}
}
