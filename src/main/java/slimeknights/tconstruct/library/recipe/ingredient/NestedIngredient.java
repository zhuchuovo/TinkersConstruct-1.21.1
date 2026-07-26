package slimeknights.tconstruct.library.recipe.ingredient;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

/** Ingredient that contains another ingredient nested inside */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class NestedIngredient implements ICustomIngredient {
  protected final Ingredient nested;


  /* Defer to nested */

  @Override
  public boolean test(@Nullable ItemStack stack) {
    return nested.test(stack);
  }

  @Override
  public Stream<ItemStack> getItems() {
    return Arrays.stream(nested.getItems());
  }

  @Override
  public boolean isSimple() {
    return nested.isSimple();
  }

  @Override
  public boolean equals(Object obj) {
    return obj != null && obj.getClass() == getClass() && nested.equals(((NestedIngredient)obj).nested);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), nested);
  }
}
