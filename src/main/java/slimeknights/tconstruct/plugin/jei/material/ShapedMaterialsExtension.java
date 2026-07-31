package slimeknights.tconstruct.plugin.jei.material;

import net.minecraft.world.item.crafting.Ingredient;
import slimeknights.tconstruct.library.recipe.material.ShapedMaterialsRecipe;

import java.util.List;
import java.util.stream.IntStream;

/** Logic to show {@link ShapedMaterialsRecipe} in JEI */
public class ShapedMaterialsExtension extends MaterialsCraftingExtension<ShapedMaterialsRecipe> {
  @Override
  protected int[] getMaterialSlots(ShapedMaterialsRecipe recipe, Ingredient firstPart) {
    List<Ingredient> inputs = recipe.getIngredients();
    return IntStream.range(0, inputs.size()).filter(i -> inputs.get(i) == firstPart).toArray();
  }

  @Override
  protected int getRecipeWidth(ShapedMaterialsRecipe recipe) {
    return recipe.getWidth();
  }

  @Override
  protected int getRecipeHeight(ShapedMaterialsRecipe recipe) {
    return recipe.getHeight();
  }
}
