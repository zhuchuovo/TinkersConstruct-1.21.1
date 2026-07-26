package slimeknights.tconstruct.library.recipe.ingredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.mantle.data.JsonCodec;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.json.predicate.material.MaterialPredicate;
import slimeknights.tconstruct.library.json.predicate.material.MaterialPredicateField;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

/** Ingredient matching material items with the given value. Typically, matches ingots or blocks. */
@Getter
@RequiredArgsConstructor
public class MaterialValueIngredient implements ICustomIngredient {
  public static final ResourceLocation ID = TConstruct.getResource("material_value");
  private static final LoadableField<IJsonPredicate<MaterialVariantId>,MaterialValueIngredient> MATERIAL_FIELD =
    new MaterialPredicateField<>("material", ingredient -> ingredient.material);
  private static final JsonCodec<MaterialValueIngredient> CODEC_BODY = new JsonCodec<>() {
    @Override
    public MaterialValueIngredient deserialize(JsonElement element, DynamicOps<?> ops) {
      return MaterialValueIngredient.parse(GsonHelper.convertToJsonObject(element, "material value ingredient"));
    }

    @Override
    public JsonElement serialize(MaterialValueIngredient ingredient, DynamicOps<?> ops) {
      return ingredient.serializeBody();
    }
  };
  public static final MapCodec<MaterialValueIngredient> CODEC = MapCodec.assumeMapUnsafe(CODEC_BODY);
  public static final StreamCodec<RegistryFriendlyByteBuf,MaterialValueIngredient> STREAM_CODEC =
    StreamCodec.of(MaterialValueIngredient::write, MaterialValueIngredient::read);
  public static final IngredientType<MaterialValueIngredient> TYPE = new IngredientType<>(CODEC, STREAM_CODEC);

  private final IJsonPredicate<MaterialVariantId> material;
  private final float minValue;
  private final float maxValue;
  @Nullable
  private ItemStack[] items;

  /** Creates an ingredient matching a range of values. */
  public static Ingredient of(IJsonPredicate<MaterialVariantId> materials, float minValue, float maxValue) {
    return new MaterialValueIngredient(materials, minValue, maxValue).toVanilla();
  }

  /** Creates an ingredient matching an exact value. */
  public static Ingredient of(IJsonPredicate<MaterialVariantId> materials, float value) {
    return of(materials, value, value);
  }

  /** Checks the given material recipe against our filters. */
  public boolean test(MaterialRecipe material) {
    float value = material.getValue() / (float)material.getNeeded();
    return minValue <= value && value <= maxValue && this.material.matches(material.getMaterial().getVariant());
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    if (stack == null) {
      return false;
    }
    MaterialRecipe recipe = MaterialRecipeCache.findRecipe(stack);
    return recipe != MaterialRecipe.EMPTY && test(recipe);
  }

  @Override
  public Stream<ItemStack> getItems() {
    if (items == null) {
      items = MaterialRecipeCache.getAllRecipes().stream()
        .filter(this::test)
        .flatMap(material -> Arrays.stream(material.getIngredient().getItems()))
        .toArray(ItemStack[]::new);
    }
    return Arrays.stream(items);
  }

  @Override
  public boolean isSimple() {
    return true;
  }

  @Override
  public IngredientType<?> getType() {
    return TYPE;
  }

  /* Helpers for ShapedMaterialRecipe */

  /** Checks if this ingredient fully contains the range of the other. */
  private boolean contains(MaterialValueIngredient other) {
    return this.minValue <= other.minValue && other.maxValue <= this.maxValue;
  }

  /** Creates a custom ingredient that matches anything either ingredient matches. */
  public MaterialValueIngredient merge(MaterialValueIngredient other) {
    if (this == other) {
      return this;
    }
    IJsonPredicate<MaterialVariantId> predicate = this.material;
    if (this.material.equals(other.material)) {
      if (this.contains(other)) {
        return this;
      }
      if (other.contains(this)) {
        return other;
      }
    } else {
      predicate = MaterialPredicate.or(this.material, other.material);
    }
    return new MaterialValueIngredient(predicate, Math.min(this.minValue, other.minValue), Math.max(this.maxValue, other.maxValue));
  }

  /** Gets the material matching this recipe. */
  @Nullable
  public MaterialVariantId getMaterial(ItemStack stack) {
    MaterialRecipe recipe = MaterialRecipeCache.findRecipe(stack);
    return recipe != MaterialRecipe.EMPTY && test(recipe) ? recipe.getMaterial().getVariant() : null;
  }

  private JsonObject serializeBody() {
    JsonObject json = new JsonObject();
    MATERIAL_FIELD.serialize(this, json);
    if (minValue == maxValue) {
      json.addProperty("value", minValue);
    } else {
      JsonObject value = new JsonObject();
      if (minValue > 0) {
        value.addProperty("min", minValue);
      }
      if (Float.isFinite(maxValue)) {
        value.addProperty("max", maxValue);
      }
      json.add("value", value);
    }
    return json;
  }

  private static MaterialValueIngredient parse(JsonObject json) {
    JsonElement value = json.get("value");
    if (value == null) {
      throw new com.google.gson.JsonSyntaxException("Missing value in material value ingredient");
    }
    float minValue;
    float maxValue;
    if (value.isJsonPrimitive()) {
      minValue = maxValue = value.getAsFloat();
    } else {
      JsonObject object = GsonHelper.convertToJsonObject(value, "value");
      minValue = GsonHelper.getAsFloat(object, "min", 0);
      maxValue = GsonHelper.getAsFloat(object, "max", Float.POSITIVE_INFINITY);
    }
    return new MaterialValueIngredient(MATERIAL_FIELD.get(json), minValue, maxValue);
  }

  private static MaterialValueIngredient read(RegistryFriendlyByteBuf buffer) {
    return new MaterialValueIngredient(MATERIAL_FIELD.decode(buffer), buffer.readFloat(), buffer.readFloat());
  }

  private static void write(RegistryFriendlyByteBuf buffer, MaterialValueIngredient ingredient) {
    MATERIAL_FIELD.encode(buffer, ingredient);
    buffer.writeFloat(ingredient.minValue);
    buffer.writeFloat(ingredient.maxValue);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof MaterialValueIngredient other && material.equals(other.material)
      && Float.compare(minValue, other.minValue) == 0 && Float.compare(maxValue, other.maxValue) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(material, minValue, maxValue);
  }
}
