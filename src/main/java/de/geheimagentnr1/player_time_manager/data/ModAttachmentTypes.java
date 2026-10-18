package de.geheimagentnr1.player_time_manager.data;

import de.geheimagentnr1.player_time_manager.PlayerTimeManager;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;


public final class ModAttachmentTypes {
	
	
	@NotNull
	public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
		DeferredRegister.create( NeoForgeRegistries.ATTACHMENT_TYPES, PlayerTimeManager.MODID );
	
	// Remaining world time of all players, only attached to the Overworld
	@NotNull
	public static final Supplier<AttachmentType<TimeData>> WORLD_TIME_DATA = ATTACHMENT_TYPES.register(
		"world_time_data",
		() -> AttachmentType.builder( () -> new TimeData() ).serialize( TimeData.CODEC ).build()
	);
	
	// Remaining time of all players in the dimension the attachment belongs to
	@NotNull
	public static final Supplier<AttachmentType<TimeData>> DIMENSION_TIME_DATA = ATTACHMENT_TYPES.register(
		"dimension_time_data",
		() -> AttachmentType.builder( () -> new TimeData() ).serialize( TimeData.CODEC ).build()
	);
	
	private ModAttachmentTypes() {
	
	}
}
