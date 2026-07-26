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
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.JsonCodec;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.recipe.helper.LoggingRecipeSerializer;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.tables.TinkerTables;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shaped recipe that copies material identities from selected ingredients into the result. */
public class ShapedMaterialsRecipe extends ShapedRecipe implements MaterialsCraftingTableRecipe {
  @Getter
  private final List<Ingredient> parts;
  private final boolean checkRepeats;
  @Getter
  private final List<MaterialVariantId> extraMaterials;

  public ShapedMaterialsRecipe(ResourceLocation ignoredId, String group, CraftingBookCategory category, int width, int height,
                               NonNullList<Ingredient> ingredients, ItemStack result, boolean showNotification,
                               List<Ingredient> parts, List<MaterialVariantId> extraMaterials) {
    super(group, category, new ShapedRecipePattern(width, height, ingredients, Optional.empty()), result, showNotification);
    this.parts = parts;
    this.checkRepeats = parts.stream().unordered().distinct().count() == parts.size();
    this.extraMaterials = extraMaterials;
  }

  public ShapedMaterialsRecipe(ShapedRecipe base, List<Ingredient> parts, List<MaterialVariantId> extraMaterials) {
    super(base.getGroup(), base.category(), base.pattern, base.getResultItem(RegistryAccess.EMPTY).copy(), base.showNotification());
    this.parts = parts;
    this.checkRepeats = parts.stream().unordered().distinct().count() == parts.size();
    this.extraMaterials = extraMaterials;
  }

  @Override
  public int getPartCount() {
    return parts.size();
  }

  @Nullable
  static MaterialVariantId[] findMaterials(CraftingInput inventory, List<Ingredient> parts, int partCount, boolean checkRepeats) {
    MaterialVariantId[] materials = new MaterialVariantId[partCount];
    for (int i = 0; i < inventory.size(); i++) {
      ItemStack stack = inventory.getItem(i);
      if (stack.isEmpty()) continue;
      for (int part = 0; part < partCount; part++) {
        MaterialVariantId current = materials[part];
        if ((current == null || checkRepeats) && parts.get(part).test(stack)) {
          MaterialVariantId matched = stack.getItem() instanceof IMaterialItem materialItem
            ? materialItem.getMaterial(stack)
            : MaterialRecipeCache.findRecipe(stack).getMaterial().getVariant();
          if (current == null) {
            materials[part] = matched;
            break;
          }
          if (!current.matchesVariant(matched)) {
            if (current.getId().equals(matched.getId())) {
              materials[part] = current.getId();
              break;
            }
            return null;
          }
        }
      }
    }
    for (MaterialVariantId material : materials) {
      if (material == null) return null;
    }
    return materials;
  }

  @Override
  public boolean matches(CraftingInput inventory, Level level) {
    return super.matches(inventory, level) && findMaterials(inventory, parts, parts.size(), checkRepeats) != null;
  }

  public static void setMaterial(ItemStack stack, MaterialVariantId material, List<MaterialVariantId> extraMaterials) {
    if (extraMaterials.isEmpty() && stack.getItem() instanceof IMaterialItem materialItem) {
      materialItem.setMaterial(stack, material);
    } else {
      MaterialNBT.Builder builder = MaterialNBT.builder().add(material).add(extraMaterials);
      ToolStack.from(stack).setMaterials(builder.build());
    }
  }

  @Override
  public void setMaterial(ItemStack stack, MaterialVariantId material) {
    setMaterial(stack, material, extraMaterials);
  }

  static ItemStack assemble(ItemStack stack, CraftingInput inventory, List<Ingredient> parts, int partCount,
                            boolean checkRepeats, List<MaterialVariantId> extraMaterials) {
    MaterialVariantId[] materials = findMaterials(inventory, parts, partCount, checkRepeats);
    if (materials != null) {
      if (materials.length == 1 && extraMaterials.isEmpty() && stack.getItem() instanceof IMaterialItem materialItem) {
        return materialItem.setMaterial(stack, materials[0]);
      }
      MaterialNBT.Builder builder = MaterialNBT.builder();
      for (MaterialVariantId material : materials) builder.add(material);
      builder.add(extraMaterials);
      ToolStack.from(stack).setMaterials(builder.build());
    }
    return stack;
  }

  @Override
  public ItemStack assemble(CraftingInput inventory, HolderLookup.Provider registries) {
    return assemble(super.assemble(inventory, registries), inventory, parts, parts.size(), checkRepeats, extraMaterials);
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return TinkerTables.shapedMaterialsRecipeSerializer.get();
  }

  public static class Serializer implements LoggingRecipeSerializer<ShapedMaterialsRecipe> {
    static final Loadable<List<MaterialVariantId>> EXTRA_MATERIALS = MaterialVariantId.LOADABLE.list(0);
    static final LoadableField<List<MaterialVariantId>,ShapedMaterialsRecipe> MATERIAL_FIELD =
      EXTRA_MATERIALS.defaultField("extra_materials", List.of(), recipe -> recipe.extraMaterials);

    private static final JsonCodec<ShapedMaterialsRecipe> BODY_CODEC = new JsonCodec<>() {
      @Override
      public ShapedMaterialsRecipe deserialize(JsonElement element, DynamicOps<?> ops) {
        JsonObject json = element.getAsJsonObject();
        ShapedRecipe base = ShapedRecipe.Serializer.CODEC.codec().parse(JsonOps.INSTANCE, element).getOrThrow(JsonParseException::new);
        JsonElement partsElement = json.get("parts");
        if (partsElement == null) throw new JsonSyntaxException("Missing parts in shaped materials recipe");
        List<Ingredient> parts = new ArrayList<>();
        if (partsElement.isJsonPrimitive()) {
          String partPattern = partsElement.getAsString();
          JsonObject key = json.getAsJsonObject("key");
          for (int i = 0; i < partPattern.length(); i++) {
            String symbol = partPattern.substring(i, i + 1);
            JsonElement ingredient = key == null ? null : key.get(symbol);
            if (ingredient == null) throw new JsonSyntaxException("Parts references undefined symbol '" + symbol + "'");
            parts.add(Ingredient.CODEC_NONEMPTY.parse(JsonOps.INSTANCE, ingredient).getOrThrow(JsonParseException::new));
          }
        } else {
          parts.addAll(Ingredient.CODEC_NONEMPTY.listOf().parse(JsonOps.INSTANCE, partsElement).getOrThrow(JsonParseException::new));
        }
        return new ShapedMaterialsRecipe(base, List.copyOf(parts), MATERIAL_FIELD.get(json));
      }

      @Override
      public JsonElement serialize(ShapedMaterialsRecipe recipe, DynamicOps<?> ops) {
        JsonObject json = ShapedRecipe.Serializer.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow(JsonParseException::new).getAsJsonObject();
        json.add("parts", Ingredient.CODEC_NONEMPTY.listOf().encodeStart(JsonOps.INSTANCE, recipe.parts).getOrThrow(JsonParseException::new));
        MATERIAL_FIELD.serialize(recipe, json);
        return json;
      }
    };
    private static final MapCodec<ShapedMaterialsRecipe> CODEC = MapCodec.assumeMapUnsafe(BODY_CODEC);
    private static final StreamCodec<RegistryFriendlyByteBuf,ShapedMaterialsRecipe> STREAM_CODEC = StreamCodec.of(
      (buffer, recipe) -> {
        ShapedRecipe.Serializer.STREAM_CODEC.encode(buffer, recipe);
        buffer.writeVarInt(recipe.parts.size());
        recipe.parts.forEach(part -> Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, part));
        MATERIAL_FIELD.encode(buffer, recipe);
      },
      buffer -> {
        ShapedRecipe base = ShapedRecipe.Serializer.STREAM_CODEC.decode(buffer);
        int size = buffer.readVarInt();
        List<Ingredient> parts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) parts.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
        return new ShapedMaterialsRecipe(base, List.copyOf(parts), MATERIAL_FIELD.decode(buffer));
      });

    @Override public MapCodec<ShapedMaterialsRecipe> codec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf,ShapedMaterialsRecipe> streamCodec() { return STREAM_CODEC; }
  }
}
