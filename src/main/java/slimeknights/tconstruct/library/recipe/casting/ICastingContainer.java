package slimeknights.tconstruct.library.recipe.casting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.tconstruct.library.utils.FluidStackUtil;
import slimeknights.mantle.recipe.container.ISingleStackContainer;

import javax.annotation.Nullable;

/**
 * Inventory containing a single item and a fluid
 */
public interface ICastingContainer extends ISingleStackContainer {
  /**
   * Casting recipes can intentionally have no item input, so the contained fluid also makes this recipe input
   * non-empty. Minecraft 1.21 skips recipe matching entirely when {@link #isEmpty()} returns true.
   */
  @Override
  default boolean isEmpty() {
    return getStack().isEmpty() && getFluidStack().isEmpty();
  }

  /**
   * Gets the contained fluid in this inventory
   * @return  Contained fluid
   */
  Fluid getFluid();

  /** Gets the full contained fluid, including its data components. The returned stack must not be modified. */
  FluidStack getFluidStack();

  /**
   * Gets the NBT for the contained fluid
   * @return  Fluid's NBT
   */
  @Nullable
  default CompoundTag getFluidTag() {
    return FluidStackUtil.getTag(getFluidStack());
  }
}
