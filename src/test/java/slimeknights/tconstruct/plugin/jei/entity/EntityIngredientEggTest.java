package slimeknights.tconstruct.plugin.jei.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.ingredient.EntityIngredient;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies entity ingredients resolve vanilla spawn eggs for JEI focus links.
 * Regression test: previously only modded deferred eggs were found, so every
 * vanilla entity melted via JEI lookup was unreachable.
 */
public class EntityIngredientEggTest extends BaseMcTest {
  @Test
  void vanillaEntitiesHaveEggs() {
    EntityIngredient ingredient = EntityIngredient.of(EntityType.ZOMBIE);
    assertThat(ingredient.getEggs())
      .isNotEmpty()
      .noneMatch(stack -> stack.is(Items.AIR));
    assertThat(ingredient.getEggs().get(0).getItem()).isInstanceOf(SpawnEggItem.class);
  }
}
