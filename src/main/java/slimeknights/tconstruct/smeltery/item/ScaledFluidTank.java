package slimeknights.tconstruct.smeltery.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import slimeknights.tconstruct.library.utils.FluidStackUtil;

import javax.annotation.Nonnull;

/**
 * Fluid tank representing a stack of multiple fluid tanks. All operations must affect every tack in the stack at the same time, so must in increments of the scale.
 * Internally works the same as a fluid tank with {@code capacity * scale}, except operations are truncated to the nearest scale (e.g. if scale is 4, we must fill in 4mb increments).
 */
public class ScaledFluidTank extends FluidTank {
  private final int scale;
  private ScaledFluidTank(int capacity, int scale) {
    super(capacity * scale);
    this.scale = scale;
  }

  /** Creates a new instance */
  public static FluidTank create(int capacity, int scale) {
    return new ScaledFluidTank(capacity, scale);
  }

  /* Helpers */

  /** enforces the amount matches the scale */
  private int enforceScale(int amount) {
    // no working with fluids of partial amounts
    int remainder = amount % scale;
    if (remainder != 0) {
      amount -= remainder;
    }
    return amount;
  }

  /** enforces the fluid matches the scale */
  private FluidStack enforceScale(FluidStack stack, boolean copy) {
    // no working with fluids of partial amounts
    int remainder = stack.getAmount() % scale;
    if (remainder != 0) {
      if (copy) {
        stack = stack.copy();
      }
      stack.shrink(remainder);
    }
    return stack;
  }


  /* Fluid tank methods */

  @Override
  public FluidTank setCapacity(int capacity) {
    return super.setCapacity(enforceScale(capacity));
  }

  @Override
  public void setFluid(FluidStack stack) {
    super.setFluid(enforceScale(stack, false));
  }

  @Override
  public int fill(FluidStack resource, FluidAction action) {
    return super.fill(enforceScale(resource, true), action);
  }

  @Nonnull
  @Override
  public FluidStack drain(int maxDrain, FluidAction action) {
    return super.drain(enforceScale(maxDrain), action);
  }

  @Nonnull
  @Override
  public FluidStack drain(FluidStack resource, FluidAction action) {
    return super.drain(enforceScale(resource, true), action);
  }


  /* NBT */

  @Override
  public FluidTank readFromNBT(HolderLookup.Provider registries, CompoundTag nbt) {
    // scale the fluid on reading from NBT; as each instance should store the fluid relative to stack size 1
    FluidStack fluid = FluidStack.parseOptional(registries, nbt);
    // Preserve tanks written before 1.20.5, which used FluidName/Amount/Tag.
    if (fluid.isEmpty() && nbt.contains("FluidName", Tag.TAG_STRING)) {
      ResourceLocation id = ResourceLocation.tryParse(nbt.getString("FluidName"));
      Fluid legacyFluid = id == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(id);
      if (legacyFluid != null && legacyFluid != Fluids.EMPTY) {
        fluid = new FluidStack(legacyFluid, nbt.getInt("Amount"));
        if (nbt.contains("Tag", Tag.TAG_COMPOUND)) {
          FluidStackUtil.setTag(fluid, nbt.getCompound("Tag"));
        }
      }
    }
    fluid.setAmount(fluid.getAmount() * scale);
    setFluid(fluid);
    return this;
  }

  @Override
  public CompoundTag writeToNBT(HolderLookup.Provider registries, CompoundTag nbt) {
    // Scale the fluid on writing to NBT, as each instance stores fluid relative to a stack size of 1.
    FluidStack fluid = this.fluid.copy();
    fluid.setAmount(fluid.getAmount() / scale);
    return fluid.isEmpty() ? nbt : (CompoundTag)fluid.save(registries, nbt);
  }
}
