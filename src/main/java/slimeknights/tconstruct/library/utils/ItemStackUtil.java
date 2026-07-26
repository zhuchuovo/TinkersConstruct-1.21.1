package slimeknights.tconstruct.library.utils;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;

/** Compatibility accessors for Tinkers' legacy compound data stored in the 1.21 custom-data component. */
public final class ItemStackUtil {
  private ItemStackUtil() {}

  @Nullable
  public static CompoundTag getTag(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    return data == null ? null : data.getUnsafe();
  }

  public static CompoundTag getOrCreateTag(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    if (data == null) {
      data = CustomData.of(new CompoundTag());
      stack.set(DataComponents.CUSTOM_DATA, data);
    }
    return data.getUnsafe();
  }

  public static void setTag(ItemStack stack, @Nullable CompoundTag tag) {
    if (tag == null) {
      stack.remove(DataComponents.CUSTOM_DATA);
    } else {
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
  }

  @Nullable
  public static CompoundTag getTagElement(ItemStack stack, String key) {
    CompoundTag tag = getTag(stack);
    return tag != null && tag.contains(key, Tag.TAG_COMPOUND) ? tag.getCompound(key) : null;
  }

  public static CompoundTag getOrCreateTagElement(ItemStack stack, String key) {
    CompoundTag tag = getOrCreateTag(stack);
    if (!tag.contains(key, Tag.TAG_COMPOUND)) {
      tag.put(key, new CompoundTag());
    }
    return tag.getCompound(key);
  }

  public static void removeTagKey(ItemStack stack, String key) {
    CompoundTag tag = getTag(stack);
    if (tag != null) {
      tag.remove(key);
      if (tag.isEmpty()) {
        stack.remove(DataComponents.CUSTOM_DATA);
      }
    }
  }
}
