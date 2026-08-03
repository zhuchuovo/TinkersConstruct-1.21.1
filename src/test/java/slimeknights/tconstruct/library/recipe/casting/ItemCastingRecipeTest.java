package slimeknights.tconstruct.library.recipe.casting;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.library.utils.RegistryAccessUtil;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class ItemCastingRecipeTest extends BaseMcTest {
  @Test
  void assembleDoesNotExposeCachedItemOutputStack() {
    TypeAwareRecipeSerializer<?> serializer = mock(TypeAwareRecipeSerializer.class);
    doReturn(RecipeType.CRAFTING).when(serializer).getType();
    ItemCastingRecipe recipe = new ItemCastingRecipe(
      serializer, ResourceLocation.parse("tconstruct:test_casting"), "", Ingredient.EMPTY,
      mock(FluidIngredient.class), ItemOutput.fromItem(Items.IRON_INGOT), 1, false, false);

    ItemStack first = recipe.assemble(null, (HolderLookup.Provider) RegistryAccessUtil.BUILTIN);
    first.setCount(0);
    ItemStack second = recipe.assemble(null, (HolderLookup.Provider) RegistryAccessUtil.BUILTIN);

    assertThat(second).isNotSameAs(first);
    assertThat(second.getItem()).isSameAs(Items.IRON_INGOT);
    assertThat(second.getCount()).isOne();
  }
}
