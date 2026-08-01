package slimeknights.tconstruct.library.client.book;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.tconstruct.test.BaseMcTest;
import slimeknights.tconstruct.tools.TinkerTools;

import static org.assertj.core.api.Assertions.assertThat;

class BookComponentIngredientTest extends BaseMcTest {
  @Test
  void componentIngredientCreatesDisplayStack() {
    SizedIngredient ingredient = SizedIngredient.LOADABLE.deserialize(JsonParser.parseString("""
      {
        "type": "neoforge:components",
        "items": ["tconstruct:pickaxe"],
        "components": {
          "minecraft:damage": 0,
          "minecraft:custom_data": {
            "tic_broken": false,
            "tic_display": true
          }
        }
      }
      """).getAsJsonObject());

    assertThat(ingredient.getMatchingStacks()).singleElement().satisfies(stack -> {
      assertThat(stack.is(TinkerTools.pickaxe.get())).isTrue();
      assertThat(stack.getDamageValue()).isZero();
    });
  }
}
