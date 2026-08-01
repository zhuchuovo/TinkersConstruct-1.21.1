package slimeknights.tconstruct.library.utils;

import com.google.common.collect.Maps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import slimeknights.mantle.client.ResourceColorManager;
import slimeknights.mantle.data.listener.ISafeManagerReloadListener;
import slimeknights.tconstruct.TConstruct;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Harvest level display names
 */
public class HarvestTiers {
  private HarvestTiers() {}

  /** Cache of name for each tier */
  private static final Map<Tier, Component> harvestLevelNames = Maps.newHashMap();
  private static Function<ResourceLocation, Tier> tierLookup = id -> null;
  private static Function<Tier, ResourceLocation> tierIdLookup = tier -> null;
  /** Listener to clear name cache so we get new colors */
  public static final ISafeManagerReloadListener RELOAD_LISTENER = manager -> harvestLevelNames.clear();

  /** Adds an optional registry used to resolve non-vanilla tiers. */
  public static void registerTierResolver(Function<ResourceLocation, Tier> lookup, Function<Tier, ResourceLocation> idLookup) {
    tierLookup = lookup;
    tierIdLookup = idLookup;
  }

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
    return compare(b, a) > 0 ? b : a;
  }

  /** Gets the smaller of two tiers */
  public static Tier min(Tier a, Tier b) {
    return compare(b, a) < 0 ? b : a;
  }

  /** Gets the smallest tier in the sorting registry */
  public static Tier minTier() {
    return Tiers.WOOD;
  }

  /** Gets the stable datapack ID for a vanilla tier. */
  @Nullable
  public static ResourceLocation getId(Tier tier) {
    if (tier instanceof Tiers vanilla) {
      return ResourceLocation.withDefaultNamespace(vanilla.name().toLowerCase(Locale.ROOT));
    }
    return tierIdLookup.apply(tier);
  }

  /** Resolves a vanilla tier ID. */
  @Nullable
  public static Tier byId(ResourceLocation id) {
    if (id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
      for (Tiers tier : Tiers.values()) {
        if (tier.name().equalsIgnoreCase(id.getPath())) {
          return tier;
        }
      }
    }
    return tierLookup.apply(id);
  }

  private static int compare(Tier a, Tier b) {
    if (a == b) {
      return 0;
    }
    Set<Block> aIncorrect = incorrectBlocks(a);
    Set<Block> bIncorrect = incorrectBlocks(b);
    if (!aIncorrect.equals(bIncorrect)) {
      if (aIncorrect.containsAll(bIncorrect)) {
        return -1;
      }
      if (bIncorrect.containsAll(aIncorrect)) {
        return 1;
      }
    }
    return Integer.compare(vanillaRank(a), vanillaRank(b));
  }

  private static Set<Block> incorrectBlocks(Tier tier) {
    return BuiltInRegistries.BLOCK.getTag(tier.getIncorrectBlocksForDrops())
      .map(tag -> tag.stream().map(holder -> holder.value()).collect(Collectors.toSet()))
      .orElseGet(Set::of);
  }

  private static int vanillaRank(Tier tier) {
    if (tier == Tiers.WOOD || tier == Tiers.GOLD) return 0;
    if (tier == Tiers.STONE) return 1;
    if (tier == Tiers.IRON) return 2;
    if (tier == Tiers.DIAMOND) return 3;
    if (tier == Tiers.NETHERITE) return 4;
    return -1;
  }
}
