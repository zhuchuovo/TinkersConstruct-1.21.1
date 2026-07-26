package slimeknights.mantle.recipe.ingredient;

import com.google.gson.JsonElement;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.LoadableIngredientSerializer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Stream;

/** Ingredient matching item types while displaying every registered potion variant. */
public final class PotionDisplayIngredient extends ItemIngredient {
  public static final LoadableIngredientSerializer<PotionDisplayIngredient> SERIALIZER = new LoadableIngredientSerializer<>(
    RecordLoadable.create(ItemsField.INSTANCE, TAG_FIELD, PotionDisplayIngredient::new));

  private PotionDisplayIngredient(List<Item> items, @Nullable TagKey<Item> tag) {
    super(items, tag);
  }

  public static Ingredient of(List<ItemLike> items) {
    return new PotionDisplayIngredient(toItem(items), null).toVanilla();
  }

  public static Ingredient of(ItemLike... items) {
    return of(List.of(items));
  }

  public static Ingredient of(TagKey<Item> tag) {
    return new PotionDisplayIngredient(List.of(), tag).toVanilla();
  }

  @Override
  public Stream<ItemStack> getItems() {
    List<ItemStack> baseStacks = super.getItems().toList();
    return BuiltInRegistries.POTION.holders().flatMap(potion -> baseStacks.stream().map(base -> {
      ItemStack stack = base.copy();
      stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
      return stack;
    }));
  }

  @Override
  public boolean isSimple() {
    return true;
  }

  @Override
  public IngredientType<?> getType() {
    return SERIALIZER.type();
  }

  public JsonElement toJson() {
    return SERIALIZER.serialize(this);
  }

  @Override
  public boolean equals(Object other) {
    return this == other || other instanceof PotionDisplayIngredient ingredient && sameItems(ingredient);
  }

  @Override
  public int hashCode() {
    return itemsHash();
  }
}
