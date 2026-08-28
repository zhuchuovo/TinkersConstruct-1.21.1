package slimeknights.tconstruct.library.tools.item;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;
import slimeknights.tconstruct.tools.TinkerTools;

import static org.assertj.core.api.Assertions.assertThat;

class LauncherItemDurabilityTest extends BaseMcTest {
  @Test
  void launcherStacksHaveDamageComponents() {
    assertThat(new ItemStack(TinkerTools.longbow.get()).isDamageableItem()).isTrue();
    assertThat(new ItemStack(TinkerTools.crossbow.get()).isDamageableItem()).isTrue();
    assertThat(new ItemStack(TinkerTools.warPick.get()).isDamageableItem()).isTrue();
  }
}
