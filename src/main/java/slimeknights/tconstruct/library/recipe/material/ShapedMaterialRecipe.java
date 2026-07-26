package slimeknights.tconstruct.library.recipe.material;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.JsonCodec;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.recipe.helper.LoggingRecipeSerializer;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.ingredient.MaterialValueIngredient;
import slimeknights.tconstruct.tables.TinkerTables;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Legacy shaped recipe that sets one material on the result.
 * @deprecated use {@link ShapedMaterialsRecipe}
 */
@Deprecated
public class ShapedMaterialRecipe extends ShapedRecipe {
  private MaterialValueIngredient material;
  private final List<MaterialVariantId> extraMaterials;

  public ShapedMaterialRecipe(ResourceLocation ignoredId, String group, CraftingBookCategory category, int width, int height,
                              NonNullList<Ingredient> ingredients, ItemStack result, boolean showNotification,
                              List<MaterialVariantId> extraMaterials) {
    super(group, category, new ShapedRecipePattern(width, height, ingredients, Optional.empty()), result, showNotification);
    this.extraMaterials = extraMaterials;
  }

  public ShapedMaterialRecipe(ShapedRecipe recipe, List<MaterialVariantId> extraMaterials) {
    super(recipe.getGroup(), recipe.category(), recipe.pattern, recipe.getResultItem(RegistryAccess.EMPTY).copy(), recipe.showNotification());
    this.extraMaterials = extraMaterials;
  }

  @Deprecated(forRemoval = true)
  public ShapedMaterialRecipe(ResourceLocation id, String group, CraftingBookCategory category, int width, int height,
                              NonNullList<Ingredient> ingredients, ItemStack result, boolean showNotification) {
    this(id, group, category, width, height, ingredients, result, showNotification, List.of());
  }

  @Deprecated(forRemoval = true)
  public ShapedMaterialRecipe(ShapedRecipe recipe) {
    this(recipe, List.of());
  }

  @Nullable
  public MaterialValueIngredient getMaterial() {
    if (material == null) {
      for (Ingredient ingredient : getIngredients()) {
        if (ingredient.getCustomIngredient() instanceof MaterialValueIngredient materialValue) {
          material = material == null ? materialValue : material.merge(materialValue);
        }
      }
      if (material == null) {
        TConstruct.LOG.error("No material ingredient found for a shaped material recipe");
      }
    }
    return material;
  }

  @Nullable
  private MaterialVariantId findMaterial(CraftingInput inventory) {
    MaterialValueIngredient material = getMaterial();
    if (material == null) return null;
    MaterialVariantId firstMaterial = null;
    for (int i = 0; i < inventory.size(); i++) {
      ItemStack stack = inventory.getItem(i);
      if (!stack.isEmpty()) {
        MaterialVariantId matchedMaterial = material.getMaterial(stack);
        if (matchedMaterial != null) {
          if (firstMaterial == null) {
            firstMaterial = matchedMaterial;
          } else if (!firstMaterial.matchesVariant(matchedMaterial)) {
            if (firstMaterial.getId().equals(matchedMaterial.getId())) {
              firstMaterial = firstMaterial.getId();
            } else {
              return null;
            }
          }
        }
      }
    }
    return firstMaterial;
  }

  @Override
  public boolean matches(CraftingInput inventory, Level level) {
    return super.matches(inventory, level) && findMaterial(inventory) != null;
  }

  public void setMaterial(ItemStack stack, MaterialVariantId material) {
    ShapedMaterialsRecipe.setMaterial(stack, material, extraMaterials);
  }

  @Override
  public ItemStack assemble(CraftingInput inventory, HolderLookup.Provider registries) {
    ItemStack stack = super.assemble(inventory, registries);
    MaterialVariantId material = findMaterial(inventory);
    if (material != null) setMaterial(stack, material);
    return stack;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return TinkerTables.shapedMaterialRecipeSerializer.get();
  }

  public static class Serializer implements LoggingRecipeSerializer<ShapedMaterialRecipe> {
    static final Loadable<List<MaterialVariantId>> EXTRA_MATERIALS = ShapedMaterialsRecipe.Serializer.EXTRA_MATERIALS;
    static final LoadableField<List<MaterialVariantId>,ShapedMaterialRecipe> MATERIAL_FIELD =
      EXTRA_MATERIALS.defaultField("extra_materials", List.of(), recipe -> recipe.extraMaterials);

    private static final JsonCodec<ShapedMaterialRecipe> BODY_CODEC = new JsonCodec<>() {
      @Override
      public ShapedMaterialRecipe deserialize(JsonElement element, DynamicOps<?> ops) {
        JsonObject json = element.getAsJsonObject();
        ShapedRecipe base = ShapedRecipe.Serializer.CODEC.codec().parse(JsonOps.INSTANCE, element).getOrThrow(JsonParseException::new);
        ShapedMaterialRecipe recipe = new ShapedMaterialRecipe(base, MATERIAL_FIELD.get(json));
        if (recipe.getMaterial() == null) throw new JsonSyntaxException("Invalid material ingredients for shaped material recipe");
        return recipe;
      }

      @Override
      public JsonElement serialize(ShapedMaterialRecipe recipe, DynamicOps<?> ops) {
        JsonObject json = ShapedRecipe.Serializer.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow(JsonParseException::new).getAsJsonObject();
        MATERIAL_FIELD.serialize(recipe, json);
        return json;
      }
    };
    private static final MapCodec<ShapedMaterialRecipe> CODEC = MapCodec.assumeMapUnsafe(BODY_CODEC);
    private static final StreamCodec<RegistryFriendlyByteBuf,ShapedMaterialRecipe> STREAM_CODEC = StreamCodec.of(
      (buffer, recipe) -> {
        ShapedRecipe.Serializer.STREAM_CODEC.encode(buffer, recipe);
        MATERIAL_FIELD.encode(buffer, recipe);
      },
      buffer -> new ShapedMaterialRecipe(ShapedRecipe.Serializer.STREAM_CODEC.decode(buffer), MATERIAL_FIELD.decode(buffer)));

    @Override public MapCodec<ShapedMaterialRecipe> codec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf,ShapedMaterialRecipe> streamCodec() { return STREAM_CODEC; }
  }
}
