package slimeknights.tconstruct.library.modifiers.modules.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link BlockTransformModule#getTransformedState}, focusing on the soil above the block being tilled.
 * Vanilla hoes refuse to till unless the block above is air, which meant tools with the tilling trait could not till soil underwater.
 */
class BlockTransformModuleTest extends BaseMcTest {
  private static final BlockPos POS = new BlockPos(0, 64, 0);

  /** Creates a context where the given block is at {@link #POS} and the given state is directly above it */
  private static UseOnContext context(BlockState target, BlockState above) {
    Level level = mock(Level.class);
    when(level.isClientSide()).thenReturn(false);
    when(level.getBlockState(eq(POS))).thenReturn(target);
    when(level.getBlockState(eq(POS.above()))).thenReturn(above);

    ItemStack stack = mock(ItemStack.class);
    when(stack.canPerformAction(eq(ItemAbilities.HOE_TILL))).thenReturn(true);
    return new UseOnContext(level, null, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.ZERO, Direction.UP, POS, false));
  }

  private static BlockState till(BlockState target, BlockState above) {
    return BlockTransformModule.getTransformedState(context(target, above), target, POS, ItemAbilities.HOE_TILL, false);
  }

  /** Sanity check: normal vanilla behavior is unchanged */
  @Test
  void tillDirtWithAirAboveMakesFarmland() {
    assertThat(till(Blocks.DIRT.defaultBlockState(), Blocks.AIR.defaultBlockState())).isEqualTo(Blocks.FARMLAND.defaultBlockState());
    assertThat(till(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.AIR.defaultBlockState())).isEqualTo(Blocks.FARMLAND.defaultBlockState());
  }

  /** The bug this module works around: soil under water must still be tillable */
  @Test
  void tillDirtWithWaterAboveMakesFarmland() {
    BlockState water = Blocks.WATER.defaultBlockState();
    assertThat(till(Blocks.DIRT.defaultBlockState(), water)).isEqualTo(Blocks.FARMLAND.defaultBlockState());
    assertThat(till(Blocks.GRASS_BLOCK.defaultBlockState(), water)).isEqualTo(Blocks.FARMLAND.defaultBlockState());
    assertThat(till(Blocks.DIRT_PATH.defaultBlockState(), water)).isEqualTo(Blocks.FARMLAND.defaultBlockState());
  }

  /** Coarse dirt becomes dirt, matching vanilla */
  @Test
  void tillCoarseDirtUnderWaterMakesDirt() {
    assertThat(till(Blocks.COARSE_DIRT.defaultBlockState(), Blocks.WATER.defaultBlockState())).isEqualTo(Blocks.DIRT.defaultBlockState());
  }

  /** A solid block above still blocks tilling, matching vanilla */
  @Test
  void tillDirtWithSolidBlockAboveDoesNothing() {
    assertThat(till(Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState())).isNull();
    assertThat(till(Blocks.DIRT.defaultBlockState(), Blocks.BEDROCK.defaultBlockState())).isNull();
  }

  /** Blocks that vanilla cannot till stay untillable underwater */
  @Test
  void tillUntillableBlockUnderWaterDoesNothing() {
    assertThat(till(Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState())).isNull();
    assertThat(till(Blocks.SAND.defaultBlockState(), Blocks.WATER.defaultBlockState())).isNull();
  }

  /** The extra case only applies to tilling, other actions keep vanilla results */
  @Test
  void otherActionsUnaffectedByWaterAbove() {
    BlockState log = Blocks.OAK_LOG.defaultBlockState();
    UseOnContext context = context(log, Blocks.WATER.defaultBlockState());
    ItemAbility stripping = ItemAbilities.AXE_STRIP;

    // the axe action is not tilling, so it must not gain farmland behavior
    assertThat(BlockTransformModule.getTransformedState(context, log, POS, stripping, false)).isNull();
  }
}
