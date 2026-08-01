package slimeknights.tconstruct.plugin.jsonthings;

import dev.gigaherz.jsonthings.things.ThingRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Tier;

import javax.annotation.Nullable;

/** Optional bridge to tiers registered by Json Things. */
final class JsonThingsTierRegistry {
  private JsonThingsTierRegistry() {}

  @Nullable
  static Tier byId(ResourceLocation id) {
    return ThingRegistries.TIERS.get(id);
  }

  @Nullable
  static ResourceLocation getId(Tier tier) {
    return ThingRegistries.TIERS.getKey(tier);
  }
}
