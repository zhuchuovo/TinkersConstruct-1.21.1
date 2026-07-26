package slimeknights.tconstruct.library.utils;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;

/** Compatibility accessors for legacy fluid NBT stored in the 1.21 custom-data component. */
public final class FluidStackUtil {
  private FluidStackUtil() {}

  @Nullable
  public static CompoundTag getTag(FluidStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    return data == null ? null : data.getUnsafe();
  }

  public static CompoundTag getOrCreateTag(FluidStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    if (data == null) {
      data = CustomData.of(new CompoundTag());
      stack.set(DataComponents.CUSTOM_DATA, data);
    }
    return data.getUnsafe();
  }

  public static void setTag(FluidStack stack, @Nullable CompoundTag tag) {
    if (tag == null) {
      stack.remove(DataComponents.CUSTOM_DATA);
    } else {
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
  }
}
