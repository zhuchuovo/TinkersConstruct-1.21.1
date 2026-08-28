package slimeknights.tconstruct.library.tools.helper;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.tools.item.ToolItemTest;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a tool computes the same simulated base attack damage for offhand attacks as
 * for mainhand attacks, which drives dagger offhand damage. Regression test for the dagger
 * offhand damage bugs.
 */
public class DaggerOffhandDamageTest extends ToolItemTest {
  /** Minimal living entity: no held items unless explicitly set, no level use. */
  @SuppressWarnings("ConstantConditions")
  private static LivingEntity createHolder() {
    Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);
    LivingEntity entity = new LivingEntity(EntityType.PLAYER, null) {
      @Override public Iterable<ItemStack> getArmorSlots() { return java.util.List.of(); }
      @Override public ItemStack getItemBySlot(EquipmentSlot slot) { return items.getOrDefault(slot, ItemStack.EMPTY); }
      @Override public void setItemSlot(EquipmentSlot slot, ItemStack stack) { items.put(slot, stack); }
      @Override public net.minecraft.world.entity.HumanoidArm getMainArm() { return net.minecraft.world.entity.HumanoidArm.RIGHT; }
    };
    return entity;
  }

  @Test
  void offhandDamageMatchesMainhand() {
    LivingEntity holder = createHolder();
    float toolStat = ToolStack.from(testItemStack).getStats().get(ToolStats.ATTACK_DAMAGE);

    // mainhand simulation with empty hands
    float mainDamage = ToolAttackUtil.getToolAttribute(ToolStack.from(testItemStack), holder, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE.value(), toolStat);
    assertThat(mainDamage).isEqualTo(1.0f + toolStat);

    // offhand simulation: equip the tool so the offhand source-slot removal path runs, then
    // verify the tool's own offhand attributes are not double counted against the simulated
    // mainhand attributes
    holder.setItemInHand(InteractionHand.OFF_HAND, testItemStack);
    float offDamage = ToolAttackUtil.getToolAttribute(ToolStack.from(testItemStack), holder, EquipmentSlot.OFFHAND, Attributes.ATTACK_DAMAGE.value(), toolStat);
    assertThat(offDamage).isEqualTo(mainDamage);
  }
}
