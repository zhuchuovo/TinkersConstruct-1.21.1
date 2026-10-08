package slimeknights.tconstruct.library.utils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import slimeknights.mantle.client.ResourceColorManager;
import slimeknights.mantle.data.listener.ISafeManagerReloadListener;
import slimeknights.tconstruct.TConstruct;

import java.util.List;
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
  /** Registries used to resolve non-vanilla tiers, queried in registration order. Multiple mods may provide tiers, so a resolver must not replace an existing one. */
  private static final List<TierResolver> tierResolvers = Lists.newArrayList();
  /** Placeholders for tiers that are not registered yet, cached so all lookups of one ID share an instance */
  private static final Map<ResourceLocation, DynamicTier> dynamicTiers = Maps.newConcurrentMap();
  /** Listener to clear name cache so we get new colors, and to let tiers from data driven sources resolve once again */
  public static final ISafeManagerReloadListener RELOAD_LISTENER = manager -> {
    harvestLevelNames.clear();
    dynamicTiers.values().forEach(DynamicTier::invalidate);
  };

  /** Adds a registry used to resolve non-vanilla tiers. Resolvers are queried in registration order, so registering a tier never hides tiers provided by other mods. */
  public static void registerTierResolver(Function<ResourceLocation, Tier> lookup, Function<Tier, ResourceLocation> idLookup) {
    tierResolvers.add(new TierResolver(lookup, idLookup));
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

  /** Gets the stable datapack ID for a tier, which works for tiers that are not registered yet */
  @Nullable
  public static ResourceLocation getId(Tier tier) {
    if (tier instanceof Tiers vanilla) {
      return ResourceLocation.withDefaultNamespace(vanilla.name().toLowerCase(Locale.ROOT));
    }
    if (tier instanceof DynamicTier dynamic) {
      return dynamic.id;
    }
    for (TierResolver resolver : tierResolvers) {
      ResourceLocation id = resolver.idLookup().apply(tier);
      if (id != null) {
        return id;
      }
    }
    return null;
  }

  /** Resolves a tier ID, returning null when the tier is not registered. Data driven tiers may legitimately be missing while their data loads. */
  @Nullable
  public static Tier byId(ResourceLocation id) {
    if (id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
      for (Tiers tier : Tiers.values()) {
        if (tier.name().equalsIgnoreCase(id.getPath())) {
          return tier;
        }
      }
    }
    for (TierResolver resolver : tierResolvers) {
      Tier tier = resolver.lookup().apply(id);
      if (tier != null) {
        return tier;
      }
    }
    return null;
  }

  /**
   * Resolves a tier ID, falling back to a placeholder when the tier is not registered yet.
   * Tiers from data packs or from Json Things may load after the content referencing them, so an unknown tier must not fail the load.
   */
  public static Tier byIdOrPlaceholder(ResourceLocation id) {
    Tier tier = byId(id);
    if (tier != null) {
      return tier;
    }
    return dynamicTiers.computeIfAbsent(id, missing -> {
      TConstruct.LOG.warn("Unknown harvest tier {}, treating it as the lowest tier until it resolves. This is expected while data driven tiers are still loading; "
        + "if it persists, check that the mod or datapack providing this tier loaded successfully. Registered tier resolvers: {}", missing, tierResolvers.size());
      return new DynamicTier(missing);
    });
  }

  /** Gets the real tier behind a placeholder, or the tier itself */
  private static Tier unwrap(Tier tier) {
    return tier instanceof DynamicTier dynamic ? dynamic.delegate() : tier;
  }

  private static int compare(Tier a, Tier b) {
    if (a == b) {
      return 0;
    }
    // a tier waiting on its data may end up larger than any registered tier, so keep it on top rather than dropping its ID
    if (a instanceof DynamicTier dynamicA && !dynamicA.isResolved()) {
      return 1;
    }
    if (b instanceof DynamicTier dynamicB && !dynamicB.isResolved()) {
      return -1;
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
    return BuiltInRegistries.BLOCK.getTag(unwrap(tier).getIncorrectBlocksForDrops())
      .map(tag -> tag.stream().map(holder -> holder.value()).collect(Collectors.toSet()))
      .orElseGet(Set::of);
  }

  private static int vanillaRank(Tier tier) {
    Tier real = unwrap(tier);
    if (real == Tiers.WOOD || real == Tiers.GOLD) return 0;
    if (real == Tiers.STONE) return 1;
    if (real == Tiers.IRON) return 2;
    if (real == Tiers.DIAMOND) return 3;
    if (real == Tiers.NETHERITE) return 4;
    return -1;
  }

  /** Pair of functions resolving tiers from one registry */
  private record TierResolver(Function<ResourceLocation, Tier> lookup, Function<Tier, ResourceLocation> idLookup) {}

  /**
   * Placeholder for a tier ID that is not registered yet, delegating to the real tier once one of the resolvers provides it.
   * While the real tier is missing this behaves as the lowest tier, which keeps tools usable without granting unintended harvest levels.
   */
  public static class DynamicTier implements Tier {
    /** ID of the tier this placeholder stands for */
    private final ResourceLocation id;
    /** Real tier, set once a resolver provides it */
    @Nullable
    private volatile Tier resolved;

    private DynamicTier(ResourceLocation id) {
      this.id = id;
    }

    /** Checks if the real tier is available, resolving it if needed */
    public boolean isResolved() {
      return resolveOrNull() != null;
    }

    /** Clears the resolved tier so a reloaded data pack gets picked up */
    private void invalidate() {
      resolved = null;
    }

    /** Gets the real tier when it is available, without falling back to the lowest tier */
    @Nullable
    private Tier resolveOrNull() {
      Tier tier = resolved;
      if (tier == null) {
        tier = byId(id);
        if (tier != null) {
          resolved = tier;
        }
      }
      return tier;
    }

    /** Gets the real tier, using the lowest tier as a stand in while the real tier is missing */
    private Tier delegate() {
      Tier tier = resolveOrNull();
      return tier != null ? tier : minTier();
    }

    @Override
    public int getUses() {
      return delegate().getUses();
    }

    @Override
    public float getSpeed() {
      return delegate().getSpeed();
    }

    @Override
    public float getAttackDamageBonus() {
      return delegate().getAttackDamageBonus();
    }

    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {
      return delegate().getIncorrectBlocksForDrops();
    }

    @Override
    public int getEnchantmentValue() {
      return delegate().getEnchantmentValue();
    }

    @Override
    public Ingredient getRepairIngredient() {
      return delegate().getRepairIngredient();
    }

    @Override
    public String toString() {
      return "DynamicTier{" + id + '}';
    }
  }
}
