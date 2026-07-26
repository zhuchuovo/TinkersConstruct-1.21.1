package slimeknights.mantle.recipe.helper;

import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import slimeknights.mantle.data.loadable.LoadableCodec;
import slimeknights.mantle.data.loadable.record.RecordLoadable;

import java.util.Objects;

/** Bridges Mantle record loadables to the NeoForge 1.21 custom ingredient codec API. */
public final class LoadableIngredientSerializer<T extends ICustomIngredient> {
  private final RecordLoadable<T> loadable;
  private final IngredientType<T> type;

  public LoadableIngredientSerializer(RecordLoadable<T> loadable) {
    this.loadable = loadable;
    MapCodec<T> codec = MapCodec.assumeMapUnsafe(new LoadableCodec<>(loadable));
    StreamCodec<RegistryFriendlyByteBuf,T> streamCodec = StreamCodec.of(
      (buffer, ingredient) -> loadable.encode(buffer, ingredient),
      loadable::decode);
    this.type = new IngredientType<>(codec, streamCodec);
  }

  public IngredientType<T> type() {
    return type;
  }

  /** Serializes the ingredient using the registered NeoForge ingredient type ID. */
  public JsonObject serialize(T ingredient) {
    JsonObject json = new JsonObject();
    json.addProperty("type", Objects.requireNonNull(NeoForgeRegistries.INGREDIENT_TYPES.getKey(type)).toString());
    loadable.serialize(ingredient, json);
    return json;
  }
}
