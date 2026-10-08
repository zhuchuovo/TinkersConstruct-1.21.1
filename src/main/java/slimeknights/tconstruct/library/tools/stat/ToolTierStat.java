package slimeknights.tconstruct.library.tools.stat;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.mantle.util.RegistryHelper;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.utils.HarvestTiers;
import slimeknights.tconstruct.library.utils.Util;

import javax.annotation.Nullable;
import java.util.Objects;

/** Tool stat for comparing tool tiers */
@SuppressWarnings("ClassCanBeRecord")
@Getter @RequiredArgsConstructor
public class ToolTierStat implements IToolStat<Tier> {
  /** Name of this tool stat */
  private final ToolStatId name;

  @Override
  public boolean supports(Item item) {
    return RegistryHelper.contains(TinkerTags.Items.HARVEST, item);
  }

  @Override
  public Tier getDefaultValue() {
    return HarvestTiers.minTier();
  }

  @Override
  public Object makeBuilder() {
    return new TierBuilder(getDefaultValue());
  }

  @Override
  public Tier build(ModifierStatsBuilder parent, Object builder) {
    return ((TierBuilder) builder).value;
  }

  /**
   * Sets the tier to the new tier, keeping the largest
   * @param builder  Builder instance
   * @param value    Amount to add
   */
  @Override
  public void update(ModifierStatsBuilder builder, Tier value) {
    builder.<TierBuilder>updateStat(this, b -> b.value = HarvestTiers.max(b.value, value));
  }

  @Nullable
  @Override
  public Tier read(Tag tag) {
    if (tag.getId() == Tag.TAG_STRING) {
      ResourceLocation tierId = ResourceLocation.tryParse(tag.getAsString());
      if (tierId != null) {
        return HarvestTiers.byId(tierId);
      }
    }
    return null;
  }

  @Override
  public Tag write(Tier value) {
    ResourceLocation id = HarvestTiers.getId(value);
    if (id != null) {
      return StringTag.valueOf(id.toString());
    }
    return null;
  }

  @Override
  public Tier deserialize(JsonElement json) {
    ResourceLocation id = JsonHelper.convertToResourceLocation(json, getName().toString());
    // tiers from data packs may not be registered yet, so resolve to a placeholder rather than failing the load
    return HarvestTiers.byIdOrPlaceholder(id);
  }

  @Override
  public JsonElement serialize(Tier value) {
    return new JsonPrimitive(Objects.requireNonNull(HarvestTiers.getId(value)).toString());
  }

  @Override
  public Tier fromNetwork(FriendlyByteBuf buffer) {
    // a tier may be missing on the client if its data failed to load, so do not fail the packet
    return HarvestTiers.byIdOrPlaceholder(buffer.readResourceLocation());
  }

  @Override
  public void toNetwork(FriendlyByteBuf buffer, Tier value) {
    buffer.writeResourceLocation(Objects.requireNonNull(HarvestTiers.getId(value)));
  }

  @Override
  public Component formatValue(Tier value) {
    return Component.translatable(Util.makeTranslationKey("tool_stat", getName())).append(HarvestTiers.getName(value));
  }

  @Override
  public String toString() {
    return "ToolTierStat{" + name + '}';
  }

  /** Builder for a tier object */
  @AllArgsConstructor
  private static class TierBuilder {
    private Tier value;
  }
}
