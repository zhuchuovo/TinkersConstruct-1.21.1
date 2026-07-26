package slimeknights.tconstruct.library.tools.capability;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.tools.TinkerModifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Stores projectile modifiers using NeoForge 1.21 data attachments. */
public final class EntityModifierCapability {
  public static final EntityModifiers EMPTY = new EntityModifiers() {
    @Override public ModifierNBT getModifiers() { return ModifierNBT.EMPTY; }
    @Override public void setModifiers(ModifierNBT nbt) {}
  };

  private static final List<Predicate<Entity>> ENTITY_PREDICATES = new ArrayList<>();

  private EntityModifierCapability() {}

  /** Creates the registered attachment type. */
  public static AttachmentType<ModifierNBT> createAttachmentType() {
    return AttachmentType.builder(() -> ModifierNBT.EMPTY)
      .serialize(new IAttachmentSerializer<ListTag,ModifierNBT>() {
        @Override
        public ModifierNBT read(IAttachmentHolder holder, ListTag tag, HolderLookup.Provider provider) {
          return ModifierNBT.readFromNBT(tag);
        }

        @Override
        public ListTag write(ModifierNBT attachment, HolderLookup.Provider provider) {
          return attachment.serializeToNBT();
        }
      })
      .build();
  }

  private static AttachmentType<ModifierNBT> type() {
    return TinkerModifiers.entityModifiersAttachment.get();
  }

  /** Gets a mutable view of the modifier attachment for supported entities. */
  public static EntityModifiers getCapability(Entity entity) {
    return supportCapability(entity) ? new AttachedModifiers(entity) : EMPTY;
  }

  /** Gets stored modifiers without creating an attachment. */
  public static ModifierNBT getOrEmpty(Entity entity) {
    return entity.getExistingData(type()).orElse(ModifierNBT.EMPTY);
  }

  public static boolean supportCapability(Entity entity) {
    for (Predicate<Entity> predicate : ENTITY_PREDICATES) {
      if (predicate.test(entity)) {
        return true;
      }
    }
    return false;
  }

  public static void registerEntityPredicate(Predicate<Entity> predicate) {
    ENTITY_PREDICATES.add(predicate);
  }

  /** Retained for source compatibility; attachments are registered through TinkerModule. */
  public static void register() {}

  private record AttachedModifiers(Entity entity) implements EntityModifiers {
    @Override public ModifierNBT getModifiers() { return entity.getData(type()); }
    @Override public void setModifiers(ModifierNBT nbt) { entity.setData(type(), nbt); }
  }

  public interface EntityModifiers {
    ModifierNBT getModifiers();
    void setModifiers(ModifierNBT nbt);

    default void addModifiers(ModifierNBT nbt) {
      ModifierNBT existing = getModifiers();
      setModifiers(existing.isEmpty() ? nbt : ModifierNBT.builder().add(existing).add(nbt).build());
    }
  }
}
