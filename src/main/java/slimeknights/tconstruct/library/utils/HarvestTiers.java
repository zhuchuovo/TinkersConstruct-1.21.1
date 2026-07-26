package slimeknights.tconstruct.library.utils;

import com.google.common.collect.Maps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.resources.ResourceLocation;
import slimeknights.mantle.client.ResourceColorManager;
import slimeknights.mantle.data.listener.ISafeManagerReloadListener;
import slimeknights.tconstruct.TConstruct;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Harvest level display names
 */
public class HarvestTiers {
  private HarvestTiers() {}

  /** Cache of name for each tier */
  private static final Map<Tier, Component> harvestLevelNames = Maps.newHashMap();
  /** Listener to clear name cache so we get new colors */
  public static final ISafeManagerReloadListener RELOAD_LISTENER = manager -> harvestLevelNames.clear();

  /** Makes a translation key for the given name */
  private static MutableComponent makeLevelKey(Tier tier) {
    ResourceLocation id = getId(tier);
    String key = Util.makeTranslationKey("harvest_tier", id == null ? TConstruct.getResource("unknown") : id);
    TextColor color = ResourceColorManager.getTextColor(key);
    return TConstruct.makeTranslation("stat", key).withStyle(style -> style.withColor(color));
  }

  /**
   * Gets the harvest level name for the given level number
   * @param tier  Tier
   * @return  Level name
   */
  public static Component getName(Tier tier) {
    return harvestLevelNames.computeIfAbsent(tier, n ->  makeLevelKey(tier));
  }

  /** Gets the larger of two tiers */
  public static Tier max(Tier a, Tier b) {
    return rank(b) > rank(a) ? b : a;
  }

  /** Gets the smaller of two tiers */
  public static Tier min(Tier a, Tier b) {
    return rank(b) < rank(a) ? b : a;
  }

  /** Gets the smallest tier in the sorting registry */
  public static Tier minTier() {
    return Tiers.WOOD;
  }

  /** Gets the stable datapack ID for a vanilla tier. */
  @Nullable
  public static ResourceLocation getId(Tier tier) {
    return tier instanceof Tiers vanilla
      ? ResourceLocation.withDefaultNamespace(vanilla.name().toLowerCase(Locale.ROOT)) : null;
  }

  /** Resolves a vanilla tier ID. */
  @Nullable
  public static Tier byId(ResourceLocation id) {
    if (!id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
      return null;
    }
    for (Tiers tier : Tiers.values()) {
      if (tier.name().equalsIgnoreCase(id.getPath())) {
        return tier;
      }
    }
    return null;
  }

  private static int rank(Tier tier) {
    if (tier == Tiers.WOOD || tier == Tiers.GOLD) return 0;
    if (tier == Tiers.STONE) return 1;
    if (tier == Tiers.IRON) return 2;
    if (tier == Tiers.DIAMOND) return 3;
    if (tier == Tiers.NETHERITE) return 4;
    return -1;
  }
}
