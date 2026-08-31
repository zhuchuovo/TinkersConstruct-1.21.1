package slimeknights.tconstruct.tools.modules;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

class MeltingModuleTest extends BaseMcTest {
  @Test
  void decoratedPotBlockDropIsPreserved() {
    ItemStack decoratedPot = new ItemStack(Items.DECORATED_POT);

    assertThat(MeltingModule.shouldPreserveDrop(Blocks.DECORATED_POT.defaultBlockState(), decoratedPot)).isTrue();
    assertThat(MeltingModule.shouldPreserveDrop(null, decoratedPot)).isFalse();
    assertThat(MeltingModule.shouldPreserveDrop(Blocks.TERRACOTTA.defaultBlockState(), decoratedPot)).isFalse();
    assertThat(MeltingModule.shouldPreserveDrop(Blocks.DECORATED_POT.defaultBlockState(), new ItemStack(Items.BRICK))).isFalse();
  }
}
