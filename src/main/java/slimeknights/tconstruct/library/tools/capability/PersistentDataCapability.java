package slimeknights.tconstruct.library.tools.capability;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import slimeknights.tconstruct.common.network.SyncPersistentDataPacket;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.tools.TinkerModifiers;

/** Persistent modifier data stored on entities through NeoForge 1.21 data attachments. */
public final class PersistentDataCapability {
  private PersistentDataCapability() {}

  /** Creates the registered attachment type. */
  public static AttachmentType<ModDataNBT> createAttachmentType() {
    return AttachmentType.builder(ModDataNBT::new)
      .serialize(new IAttachmentSerializer<CompoundTag,ModDataNBT>() {
        @Override
        public ModDataNBT read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
          return ModDataNBT.readFromNBT(tag);
        }

        @Override
        public CompoundTag write(ModDataNBT attachment, HolderLookup.Provider provider) {
          return attachment.getCopy();
        }
      })
      .copyOnDeath()
      .build();
  }

  private static AttachmentType<ModDataNBT> type() {
    return TinkerModifiers.persistentDataAttachment.get();
  }

  /** Gets the entity data, creating an empty attachment on first access. */
  public static ModDataNBT getOrWarn(Entity entity) {
    return entity.getData(type());
  }

  /** Registers lifecycle syncing for player attachments. */
  public static void register() {
    NeoForge.EVENT_BUS.addListener(PersistentDataCapability::playerRespawn);
    NeoForge.EVENT_BUS.addListener(PersistentDataCapability::playerChangeDimension);
    NeoForge.EVENT_BUS.addListener(PersistentDataCapability::playerLoggedIn);
  }

  private static void sync(Player player) {
    player.getExistingData(type()).ifPresent(data ->
      TinkerNetwork.getInstance().sendTo(new SyncPersistentDataPacket(data.getCopy()), player));
  }

  private static void playerRespawn(PlayerEvent.PlayerRespawnEvent event) {
    sync(event.getEntity());
  }

  private static void playerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    sync(event.getEntity());
  }

  private static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
    sync(event.getEntity());
  }
}
