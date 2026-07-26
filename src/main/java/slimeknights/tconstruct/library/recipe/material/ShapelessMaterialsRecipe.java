package slimeknights.tconstruct.library.recipe.material;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import slimeknights.mantle.data.JsonCodec;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.recipe.helper.LoggingRecipeSerializer;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.tables.TinkerTables;

import java.util.List;

/** Shapeless recipe that copies material identities from its first material ingredients into the result. */
public class ShapelessMaterialsRecipe extends ShapelessRecipe implements MaterialsCraftingTableRecipe {
  @Getter
  private final int partCount;
  @Getter
  private final List<MaterialVariantId> extraMaterials;

  public ShapelessMaterialsRecipe(ResourceLocation ignoredId, String group, CraftingBookCategory category, ItemStack result,
                                  NonNullList<Ingredient> ingredients, int partCount, List<MaterialVariantId> extraMaterials) {
    super(group, category, result, ingredients);
    this.partCount = partCount;
    this.extraMaterials = extraMaterials;
  }

  public ShapelessMaterialsRecipe(ShapelessRecipe recipe, int partCount, List<MaterialVariantId> extraMaterials) {
    super(recipe.getGroup(), recipe.category(), recipe.getResultItem(RegistryAccess.EMPTY).copy(), recipe.getIngredients());
    this.partCount = partCount;
    this.extraMaterials = extraMaterials;
  }

  @Override
  public List<Ingredient> getParts() {
    return getIngredients();
  }

  @Override
  public void setMaterial(ItemStack stack, MaterialVariantId material) {
    ShapedMaterialsRecipe.setMaterial(stack, material, extraMaterials);
  }

  @Override
  public ItemStack assemble(CraftingInput inventory, HolderLookup.Provider registries) {
    return ShapedMaterialsRecipe.assemble(super.assemble(inventory, registries), inventory, getIngredients(), partCount, false, extraMaterials);
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return TinkerTables.shapelessMaterialsRecipeSerializer.get();
  }

  public static class Serializer implements LoggingRecipeSerializer<ShapelessMaterialsRecipe> {
    static final Loadable<List<MaterialVariantId>> EXTRA_MATERIALS = ShapedMaterialsRecipe.Serializer.EXTRA_MATERIALS;
    static final LoadableField<List<MaterialVariantId>,ShapelessMaterialsRecipe> MATERIAL_FIELD =
      EXTRA_MATERIALS.defaultField("extra_materials", List.of(), recipe -> recipe.extraMaterials);

    private static final JsonCodec<ShapelessMaterialsRecipe> BODY_CODEC = new JsonCodec<>() {
      @Override
      public ShapelessMaterialsRecipe deserialize(JsonElement element, DynamicOps<?> ops) {
        JsonObject json = element.getAsJsonObject();
        ShapelessRecipe vanilla = RecipeSerializer.SHAPELESS_RECIPE.codec().codec().parse(JsonOps.INSTANCE, element).getOrThrow(JsonParseException::new);
        int parts = json.has("parts") ? json.get("parts").getAsInt() : 0;
        if (parts < 1 || parts > vanilla.getIngredients().size()) {
          throw new JsonSyntaxException("Parts must be between 1 and the number of ingredients");
        }
        return new ShapelessMaterialsRecipe(vanilla, parts, MATERIAL_FIELD.get(json));
      }

      @Override
      public JsonElement serialize(ShapelessMaterialsRecipe recipe, DynamicOps<?> ops) {
        JsonObject json = RecipeSerializer.SHAPELESS_RECIPE.codec().codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow(JsonParseException::new).getAsJsonObject();
        json.addProperty("parts", recipe.partCount);
        MATERIAL_FIELD.serialize(recipe, json);
        return json;
      }
    };
    private static final MapCodec<ShapelessMaterialsRecipe> CODEC = MapCodec.assumeMapUnsafe(BODY_CODEC);
    private static final StreamCodec<RegistryFriendlyByteBuf,ShapelessMaterialsRecipe> STREAM_CODEC = StreamCodec.of(
      (buffer, recipe) -> {
        ShapelessRecipe.Serializer.STREAM_CODEC.encode(buffer, recipe);
        buffer.writeVarInt(recipe.partCount);
        MATERIAL_FIELD.encode(buffer, recipe);
      },
      buffer -> new ShapelessMaterialsRecipe(ShapelessRecipe.Serializer.STREAM_CODEC.decode(buffer), buffer.readVarInt(), MATERIAL_FIELD.decode(buffer)));

    @Override public MapCodec<ShapelessMaterialsRecipe> codec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf,ShapelessMaterialsRecipe> streamCodec() { return STREAM_CODEC; }
  }
}
