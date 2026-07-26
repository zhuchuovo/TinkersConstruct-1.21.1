package slimeknights.mantle.recipe.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import slimeknights.mantle.recipe.MantleRecipes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shaped recipe which only matches if none of a set of preferred recipes match. */
public class ShapedFallbackRecipe extends ShapedRecipe {
  private final List<ResourceLocation> alternatives;
  private List<CraftingRecipe> alternativeCache;

  public ShapedFallbackRecipe(ResourceLocation ignoredId, String group, CraftingBookCategory category, int width, int height,
                              NonNullList<Ingredient> ingredients, ItemStack output, List<ResourceLocation> alternatives) {
    super(group, category, new ShapedRecipePattern(width, height, ingredients, Optional.empty()), output);
    this.alternatives = alternatives;
  }

  public ShapedFallbackRecipe(ShapedRecipe base, List<ResourceLocation> alternatives) {
    super(base.getGroup(), base.category(), base.pattern, base.getResultItem(RegistryAccess.EMPTY).copy(), base.showNotification());
    this.alternatives = alternatives;
  }

  @Override
  public boolean matches(CraftingInput input, Level level) {
    if (!super.matches(input, level)) return false;
    if (alternativeCache == null) {
      alternativeCache = alternatives.stream()
        .map(level.getRecipeManager()::byKey)
        .flatMap(Optional::stream)
        .map(holder -> holder.value())
        .filter(recipe -> recipe.getClass() == ShapedRecipe.class || recipe.getClass() == ShapelessRecipe.class)
        .map(recipe -> (CraftingRecipe)recipe)
        .toList();
    }
    return alternativeCache.stream().noneMatch(recipe -> recipe.matches(input, level));
  }

  @Override public RecipeSerializer<?> getSerializer() { return MantleRecipes.CRAFTING_SHAPED_FALLBACK.get(); }

  public static class Serializer implements RecipeSerializer<ShapedFallbackRecipe> {
    private static final MapCodec<ShapedFallbackRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
      ShapedRecipe.Serializer.CODEC.forGetter(recipe -> recipe),
      ResourceLocation.CODEC.listOf().fieldOf("alternatives").forGetter(recipe -> recipe.alternatives)
    ).apply(instance, ShapedFallbackRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf,ShapedFallbackRecipe> STREAM_CODEC = StreamCodec.of(
      (buffer, recipe) -> {
        ShapedRecipe.Serializer.STREAM_CODEC.encode(buffer, recipe);
        buffer.writeVarInt(recipe.alternatives.size());
        recipe.alternatives.forEach(buffer::writeResourceLocation);
      },
      buffer -> {
        ShapedRecipe base = ShapedRecipe.Serializer.STREAM_CODEC.decode(buffer);
        int size = buffer.readVarInt();
        List<ResourceLocation> alternatives = new ArrayList<>(size);
        for (int i = 0; i < size; i++) alternatives.add(buffer.readResourceLocation());
        return new ShapedFallbackRecipe(base, List.copyOf(alternatives));
      });

    @Override public MapCodec<ShapedFallbackRecipe> codec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf,ShapedFallbackRecipe> streamCodec() { return STREAM_CODEC; }
  }
}
