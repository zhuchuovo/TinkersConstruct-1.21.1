package slimeknights.mantle.recipe.helper;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.function.Function;

/** Serializer for recipes with no serialized properties. */
public record SimpleRecipeSerializer<T extends Recipe<?>>(Function<ResourceLocation,T> constructor) implements RecipeSerializer<T> {
  private T create() {
    return constructor.apply(LoadableRecipeSerializer.UNBOUND_ID);
  }

  @Override
  public MapCodec<T> codec() {
    return MapCodec.unit(this::create);
  }

  @Override
  public StreamCodec<RegistryFriendlyByteBuf,T> streamCodec() {
    // Unit codecs compare the value being encoded with the single instance
    // captured by the codec. Recipes are independently constructed while
    // loading on the server, so identity-based Recipe implementations fail
    // that comparison during the 1.21 recipe synchronization packet.
    return StreamCodec.of((buffer, recipe) -> {}, buffer -> create());
  }
}
