package slimeknights.mantle.recipe.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.data.JsonCodec;
import slimeknights.mantle.recipe.MantleRecipes;
import slimeknights.mantle.recipe.helper.LoggingRecipeSerializer;
import slimeknights.mantle.util.RetexturedHelper;

import java.util.Optional;

/** Shaped recipe which copies a block texture from an input to the result. */
public class ShapedRetexturedRecipe extends ShapedRecipe {
  @Getter
  private final Ingredient texture;
  private final boolean matchAll;

  protected ShapedRetexturedRecipe(ResourceLocation ignoredId, String group, CraftingBookCategory category, int width, int height,
                                   NonNullList<Ingredient> ingredients, ItemStack result, boolean showNotification,
                                   Ingredient texture, boolean matchAll) {
    super(group, category, new ShapedRecipePattern(width, height, ingredients, Optional.empty()), result, showNotification);
    this.texture = texture;
    this.matchAll = matchAll;
  }

  protected ShapedRetexturedRecipe(ShapedRecipe original, Ingredient texture, boolean matchAll) {
    super(original.getGroup(), original.category(), original.pattern,
      original.getResultItem(RegistryAccess.EMPTY).copy(), original.showNotification());
    this.texture = texture;
    this.matchAll = matchAll;
  }

  public ItemStack getResultItem(Item texture, RegistryAccess access) {
    return RetexturedHelper.setTexture(getResultItem(access).copy(), Block.byItem(texture));
  }

  @Override
  public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
    ItemStack result = super.assemble(input, registries);
    Block currentTexture = null;
    for (int i = 0; i < input.size(); i++) {
      ItemStack stack = input.getItem(i);
      if (!stack.isEmpty() && texture.test(stack)) {
        Block block = RetexturedHelper.getTexture(stack);
        if (block == Blocks.AIR && stack.getItem() != result.getItem()) {
          block = Block.byItem(stack.getItem());
        }
        if (block == Blocks.AIR) continue;
        if (currentTexture == null) {
          currentTexture = block;
          if (!matchAll) break;
        } else if (currentTexture != block) {
          currentTexture = null;
          break;
        }
      }
    }
    return currentTexture == null ? result : RetexturedHelper.setTexture(result, currentTexture);
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return MantleRecipes.CRAFTING_SHAPED_RETEXTURED.get();
  }

  public static class Serializer implements LoggingRecipeSerializer<ShapedRetexturedRecipe> {
    private static final JsonCodec<ShapedRetexturedRecipe> BODY_CODEC = new JsonCodec<>() {
      @Override
      public ShapedRetexturedRecipe deserialize(JsonElement element, DynamicOps<?> ops) {
        JsonObject json = element.getAsJsonObject();
        ShapedRecipe base = ShapedRecipe.Serializer.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow(JsonParseException::new);
        JsonElement textureJson = json.get("texture");
        if (textureJson == null) throw new JsonSyntaxException("Missing texture ingredient");
        Ingredient texture;
        if (textureJson.isJsonPrimitive()) {
          String symbol = textureJson.getAsString();
          if (symbol.length() != 1) throw new JsonSyntaxException("Texture must be a single pattern symbol");
          JsonObject key = json.getAsJsonObject("key");
          JsonElement ingredientJson = key == null ? null : key.get(symbol);
          if (ingredientJson == null) throw new JsonSyntaxException("Texture symbol '" + symbol + "' is not defined in key");
          texture = Ingredient.CODEC_NONEMPTY.parse(JsonOps.INSTANCE, ingredientJson).getOrThrow(JsonParseException::new);
        } else {
          texture = Ingredient.CODEC_NONEMPTY.parse(JsonOps.INSTANCE, textureJson).getOrThrow(JsonParseException::new);
          Mantle.logger.warn("Using deprecated full ingredient format for mantle:crafting_shaped_retextured texture");
        }
        return new ShapedRetexturedRecipe(base, texture, json.has("match_all") && json.get("match_all").getAsBoolean());
      }

      @Override
      public JsonElement serialize(ShapedRetexturedRecipe recipe, DynamicOps<?> ops) {
        JsonObject json = ShapedRecipe.Serializer.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow(JsonParseException::new).getAsJsonObject();
        json.add("texture", Ingredient.CODEC.encodeStart(JsonOps.INSTANCE, recipe.texture).getOrThrow(JsonParseException::new));
        if (recipe.matchAll) json.addProperty("match_all", true);
        return json;
      }
    };
    private static final MapCodec<ShapedRetexturedRecipe> CODEC = MapCodec.assumeMapUnsafe(BODY_CODEC);
    private static final StreamCodec<RegistryFriendlyByteBuf,ShapedRetexturedRecipe> STREAM_CODEC = StreamCodec.of(
      (buffer, recipe) -> {
        ShapedRecipe.Serializer.STREAM_CODEC.encode(buffer, recipe);
        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.texture);
        buffer.writeBoolean(recipe.matchAll);
      },
      buffer -> new ShapedRetexturedRecipe(ShapedRecipe.Serializer.STREAM_CODEC.decode(buffer),
        Ingredient.CONTENTS_STREAM_CODEC.decode(buffer), buffer.readBoolean()));

    @Override public MapCodec<ShapedRetexturedRecipe> codec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf,ShapedRetexturedRecipe> streamCodec() { return STREAM_CODEC; }
  }
}
