package slimeknights.tconstruct.library.recipe.casting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

class CastingContainerTest extends BaseMcTest {
  @Test
  void fluidOnlyContainerIsNotEmpty() {
    FluidStack fluid = new FluidStack(Fluids.WATER, 90);
    ICastingContainer container = container(ItemStack.EMPTY, fluid);

    assertThat(container.isEmpty()).isFalse();
  }

  @Test
  void containerWithoutItemOrFluidIsEmpty() {
    assertThat(container(ItemStack.EMPTY, FluidStack.EMPTY).isEmpty()).isTrue();
  }

  private static ICastingContainer container(ItemStack item, FluidStack fluid) {
    return new ICastingContainer() {
      @Override
      public ItemStack getStack() {
        return item;
      }

      @Override
      public Fluid getFluid() {
        return fluid.getFluid();
      }

      @Override
      public FluidStack getFluidStack() {
        return fluid;
      }
    };
  }
}
