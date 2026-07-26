package slimeknights.tconstruct.smeltery.block.entity.inventory;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import slimeknights.tconstruct.library.recipe.casting.ICastingContainer;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;


/**
 * Provides read only access to the input of a casting table. Prevents extra data from leaking
 */
@RequiredArgsConstructor
public class CastingContainerWrapper implements ICastingContainer {
  private final CastingBlockEntity tile;
  @Setter
  private FluidStack fluid;
  private boolean switchSlots = false;

  @Override
  public ItemStack getStack() {
    ItemStack stack = tile.getItem(switchSlots ? CastingBlockEntity.OUTPUT : CastingBlockEntity.INPUT);
    if (stack.is(tile.getEmptyCastTag())) {
      return ItemStack.EMPTY;
    }
    return stack;
  }

  @Override
  public Fluid getFluid() {
    return fluid.getFluid();
  }

  @Override
  public FluidStack getFluidStack() {
    return fluid;
  }

  @Override
  public boolean isEmpty() {
    return getStack().isEmpty() && fluid.isEmpty();
  }

  /** Uses the input for input (default) */
  public void useInput() {
    switchSlots = false;
  }

  /** Uses the output for input (for multistep casting) */
  public void useOutput() {
    switchSlots = true;
  }
}
