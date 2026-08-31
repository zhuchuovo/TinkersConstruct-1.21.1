package slimeknights.tconstruct.library.tools.helper;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
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

  private static void applySlotAttributes(LivingEntity holder, ItemStack stack, EquipmentSlot slot) {
    stack.forEachModifier(slot, (attribute, modifier) -> {
      var instance = holder.getAttribute(attribute);
      if (instance != null) {
        instance.addTransientModifier(modifier);
      }
    });
  }

  @Test
  void offhandDamageMatchesMainhand() {
    float toolStat = ToolStack.from(testItemStack).getStats().get(ToolStats.ATTACK_DAMAGE);
    ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
      .add(Attributes.ATTACK_DAMAGE,
        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, toolStat, AttributeModifier.Operation.ADD_VALUE),
        EquipmentSlotGroup.MAINHAND)
      .add(Attributes.ATTACK_DAMAGE,
        new AttributeModifier(ResourceLocation.fromNamespaceAndPath("test", "offhand_damage"), 7, AttributeModifier.Operation.ADD_VALUE),
        EquipmentSlotGroup.OFFHAND)
      .build();
    testItemStack.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes);

    LivingEntity mainhandHolder = createHolder();
    mainhandHolder.setItemInHand(InteractionHand.MAIN_HAND, testItemStack);
    applySlotAttributes(mainhandHolder, testItemStack, EquipmentSlot.MAINHAND);
    float mainDamage = (float)mainhandHolder.getAttributeValue(Attributes.ATTACK_DAMAGE);
    assertThat(mainDamage).isEqualTo(1.0f + toolStat);

    LivingEntity offhandHolder = createHolder();
    offhandHolder.setItemInHand(InteractionHand.OFF_HAND, testItemStack);
    applySlotAttributes(offhandHolder, testItemStack, EquipmentSlot.OFFHAND);
    assertThat(offhandHolder.getAttributeValue(Attributes.ATTACK_DAMAGE)).isEqualTo(8.0);

    // The real offhand attribute is already active on the holder. It must be removed before
    // simulating the tool as a mainhand weapon, or it gets counted twice.
    float offDamage = ToolAttackUtil.getToolAttribute(ToolStack.from(testItemStack), offhandHolder, EquipmentSlot.OFFHAND, Attributes.ATTACK_DAMAGE.value(), toolStat);
    assertThat(offDamage).isEqualTo(mainDamage);
  }
}
