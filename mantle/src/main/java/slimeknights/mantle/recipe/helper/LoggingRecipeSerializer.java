package slimeknights.mantle.recipe.helper;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Marker for Mantle recipe serializers. In 1.21 the vanilla serializer contract is codec based, so
 * decode/encode diagnostics are implemented by each serializer's stream codec.
 * @param <T>  Recipe class
 */
public interface LoggingRecipeSerializer<T extends Recipe<?>> extends RecipeSerializer<T> {
}
