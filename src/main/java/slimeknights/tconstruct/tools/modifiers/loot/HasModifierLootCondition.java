package slimeknights.tconstruct.tools.modifiers.loot;

import com.mojang.serialization.MapCodec;
import lombok.RequiredArgsConstructor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.tools.TinkerModifiers;

/** Condition to check if a held tool has the given modifier */
@RequiredArgsConstructor
public class HasModifierLootCondition implements LootItemCondition {
  public static final MapCodec<HasModifierLootCondition> CODEC = ResourceLocation.CODEC
    .xmap(id -> new HasModifierLootCondition(new ModifierId(id)), condition -> condition.modifier)
    .fieldOf("modifier");
  private final ModifierId modifier;

  @Override
  public LootItemConditionType getType() {
    return TinkerModifiers.hasModifierLootCondition.get();
  }

  @Override
  public boolean test(LootContext context) {
    ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
    return tool != null && tool.is(TinkerTags.Items.MODIFIABLE) && ModifierUtil.getModifierLevel(tool, modifier) > 0;
  }

}
