package slimeknights.tconstruct.library.recipe.ingredient;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;

/** Ingredient matching an item with no container item, used to ensure NBT fluid items are empty */
public class NoContainerIngredient extends NestedIngredient {
  public static final ResourceLocation ID = TConstruct.getResource("no_container");
  public static final MapCodec<NoContainerIngredient> CODEC = Ingredient.CODEC_NONEMPTY.fieldOf("match")
    .xmap(NoContainerIngredient::new, ingredient -> ingredient.nested);
  public static final StreamCodec<RegistryFriendlyByteBuf,NoContainerIngredient> STREAM_CODEC = StreamCodec.composite(
    Ingredient.CONTENTS_STREAM_CODEC, ingredient -> ingredient.nested, NoContainerIngredient::new);
  public static final IngredientType<NoContainerIngredient> TYPE = new IngredientType<>(CODEC, STREAM_CODEC);

  protected NoContainerIngredient(Ingredient nested) {
    super(nested);
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    return stack != null && super.test(stack) && !stack.hasCraftingRemainingItem();
  }

  @Override
  public boolean isSimple() {
    return false;
  }

  @Override
  public IngredientType<?> getType() {
    return TYPE;
  }


  /* Static constructors */

  /** Creates an instance from the given nested ingredient */
  public static Ingredient of(Ingredient ingredient) {
    return new NoContainerIngredient(ingredient).toVanilla();
  }

  /** Creates an instance from the given items */
  public static Ingredient of(ItemLike... items) {
    return of(Ingredient.of(items));
  }

  /** Creates an instance from the given stacks */
  public static Ingredient of(ItemStack... stacks) {
    return of(Ingredient.of(stacks));
  }

  /** Creates an instance from the given tag */
  public static Ingredient of(TagKey<Item> tag) {
    return of(Ingredient.of(tag));
  }
}
