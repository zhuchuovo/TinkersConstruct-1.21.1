package slimeknights.tconstruct.plugin.jei.util;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import slimeknights.mantle.recipe.IMultiRecipe;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Keeps the holder-owned recipe IDs attached to recipes expanded for JEI display. */
public final class RecipeIdMap {
  private static final Map<Object,ResourceLocation> RECIPE_IDS = new IdentityHashMap<>();

  private RecipeIdMap() {}

  /** Clears IDs left over from a previous recipe reload. */
  public static void clear() {
    RECIPE_IDS.clear();
  }

  /** Gets the real data pack ID for a recipe registered with JEI. */
  @Nullable
  public static ResourceLocation getRecipeId(Object recipe, @Nullable ResourceLocation fallback) {
    return RECIPE_IDS.getOrDefault(recipe, fallback);
  }

  /** Gets recipes for JEI while preserving each {@link RecipeHolder} ID on expanded display recipes. */
  public static <I extends RecipeInput,T extends Recipe<I>,C> List<C> getRecipes(
    RegistryAccess access, RecipeManager manager, RecipeType<T> type, Class<C> displayClass
  ) {
    return manager.getAllRecipesFor(type).stream()
      .sorted(RecipeIdMap::compareRecipes)
      .flatMap(holder -> expandRecipe(access, holder))
      .filter(displayClass::isInstance)
      .map(displayClass::cast)
      .collect(Collectors.toList());
  }

  private static int compareRecipes(RecipeHolder<?> first, RecipeHolder<?> second) {
    Recipe<?> firstRecipe = first.value();
    Recipe<?> secondRecipe = second.value();
    boolean firstMulti = firstRecipe instanceof IMultiRecipe<?>;
    boolean secondMulti = secondRecipe instanceof IMultiRecipe<?>;
    if (firstMulti != secondMulti) {
      return firstMulti ? 1 : -1;
    }
    int classComparison = firstRecipe.getClass().getName().compareTo(secondRecipe.getClass().getName());
    return classComparison != 0 ? classComparison : first.id().compareTo(second.id());
  }

  private static Stream<?> expandRecipe(RegistryAccess access, RecipeHolder<?> holder) {
    Recipe<?> recipe = holder.value();
    Stream<?> displays;
    if (recipe instanceof IMultiRecipe<?> multiRecipe) {
      try {
        displays = multiRecipe.getRecipes(access).stream();
      } catch (Exception exception) {
        TConstruct.LOG.error("Failed to fetch JEI display recipes for {}", holder.id(), exception);
        return Stream.empty();
      }
    } else {
      displays = Stream.of(recipe);
    }
    return displays.peek(display -> RECIPE_IDS.put(display, holder.id()));
  }
}
