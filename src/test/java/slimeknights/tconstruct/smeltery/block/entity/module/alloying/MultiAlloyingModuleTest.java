package slimeknights.tconstruct.smeltery.block.entity.module.alloying;

import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe;
import slimeknights.tconstruct.library.recipe.alloying.IMutableAlloyTank;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiAlloyingModuleTest extends BaseMcTest {
  @Test
  @SuppressWarnings("unchecked")
  void removesRecipeFromCacheAfterItStopsMatching() {
    MantleBlockEntity parent = mock(MantleBlockEntity.class);
    IMutableAlloyTank alloyTank = mock(IMutableAlloyTank.class);
    Level level = mock(Level.class);
    RecipeManager recipeManager = mock(RecipeManager.class);
    RecipeHolder<AlloyRecipe> holder = mock(RecipeHolder.class);
    AlloyRecipe recipe = mock(AlloyRecipe.class);

    when(parent.getLevel()).thenReturn(level);
    when(level.getRecipeManager()).thenReturn(recipeManager);
    when(recipeManager.getRecipesFor(TinkerRecipeTypes.ALLOYING.get(), alloyTank, level)).thenReturn(List.of(holder));
    when(holder.value()).thenReturn(recipe);
    when(recipe.matches(alloyTank, level)).thenReturn(true, false);

    MultiAlloyingModule module = new MultiAlloyingModule(parent, alloyTank);
    module.doAlloy();

    assertThatCode(module::doAlloy).doesNotThrowAnyException();
    verify(recipe, times(1)).performRecipe(alloyTank);
  }
}
