package slimeknights.tconstruct.library.tools.helper;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.modules.capacity.OverslimeModule;
import slimeknights.tconstruct.library.tools.item.ToolItemTest;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.modifiers.slotless.OverslimeModifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class OverslimeDurabilityRegressionTest extends ToolItemTest {
  @Test
  void overslimeAbsorbsMiningDamage() {
    ToolStack tool = ToolStack.from(testItemStack);
    ModifierId overslimeId = TinkerModifiers.overslime.getId();
    tool.addModifier(overslimeId, 1);
    OverslimeModule.INSTANCE.setAmountRaw(tool.getPersistentData(), 10);
    ModifierEntry entry = new ModifierEntry((OverslimeModifier) TinkerModifiers.overslime.get(), 1);

    int damageBefore = tool.getDamage();
    int overslimeBefore = OverslimeModule.INSTANCE.getAmount(tool);
    int remaining = entry.getHook(slimeknights.tconstruct.library.modifiers.ModifierHooks.TOOL_DAMAGE)
      .onDamageTool(tool, entry, 1, (LivingEntity) null, testItemStack, ModifierId.EMPTY);
    assertThat(remaining).isZero();
    assertThat(tool.getDamage()).isEqualTo(damageBefore);
    assertThat(OverslimeModule.INSTANCE.getAmount(tool)).isEqualTo(overslimeBefore - 1);
  }

  @Test
  void miningDamageRefreshesCustomDataComponent() {
    ToolStack tool = ToolStack.from(testItemStack);
    tool.addModifier(TinkerModifiers.overslime.getId(), 1);
    OverslimeModule.INSTANCE.setAmountRaw(tool.getPersistentData(), 10);
    CustomData beforeComponent = testItemStack.get(DataComponents.CUSTOM_DATA);
    ItemStack syncSnapshot = testItemStack.copy();

    IToolStackView toolView = spy(tool);
    doReturn(true).when(toolView).hasTag(TinkerTags.Items.DURABILITY);

    assertThat(ToolDamageUtil.damage(toolView, 1, null, testItemStack)).isFalse();
    assertThat(OverslimeModule.INSTANCE.getAmount(ToolStack.from(testItemStack))).isEqualTo(9);
    assertThat(testItemStack.get(DataComponents.CUSTOM_DATA)).isNotSameAs(beforeComponent);
    assertThat(OverslimeModule.INSTANCE.getAmount(ToolStack.from(syncSnapshot))).isEqualTo(10);

    // The tool view must be rebound after the component refresh so repeated mining hits keep consuming the live stack.
    assertThat(ToolDamageUtil.damage(toolView, 1, null, testItemStack)).isFalse();
    assertThat(OverslimeModule.INSTANCE.getAmount(ToolStack.from(testItemStack))).isEqualTo(8);
  }
}
