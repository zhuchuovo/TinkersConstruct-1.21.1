package slimeknights.tconstruct.fluids.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.brewing.BrewingRecipe;

/** Recipe for transforming a bottle, depending on a vanilla brewing recipe to get the ingredient */
public class BottleBrewingRecipe extends BrewingRecipe {
  public BottleBrewingRecipe(Ingredient input, Ingredient ingredient, ItemStack output) {
    super(input, ingredient, output);
  }
}
