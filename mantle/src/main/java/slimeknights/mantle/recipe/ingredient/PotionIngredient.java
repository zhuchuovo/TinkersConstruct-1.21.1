package slimeknights.mantle.recipe.ingredient;

import com.google.gson.JsonElement;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.IngredientType;
import org.jetbrains.annotations.Nullable;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.LoadableIngredientSerializer;

import java.util.Arrays;
import java.util.List;

/** Custom ingredient checking for an item with a specific base potion component. */
public final class PotionIngredient extends ItemIngredient {
  public static final LoadableIngredientSerializer<PotionIngredient> SERIALIZER = new LoadableIngredientSerializer<>(RecordLoadable.create(
    ItemsField.INSTANCE, TAG_FIELD,
    Loadables.POTION.requiredField("potion", i -> i.potion),
    PotionIngredient::new));

  private final Potion potion;

  private PotionIngredient(List<Item> items, @Nullable TagKey<Item> itemTag, Potion potion) {
    super(items, itemTag);
    this.potion = potion;
  }

  /** Creates a vanilla Ingredient backed by this custom potion matcher. */
  public static Ingredient of(Potion potion, List<ItemLike> items) {
    return new PotionIngredient(toItem(items), null, potion).toVanilla();
  }

  public static Ingredient of(Potion potion, ItemLike... items) {
    return of(potion, Arrays.asList(items));
  }

  public static Ingredient of(Potion potion, TagKey<Item> tag) {
    return new PotionIngredient(List.of(), tag, potion).toVanilla();
  }

  /** Convenience overload for the holder-valued vanilla potion constants in 1.21. */
  public static Ingredient of(Holder<Potion> potion, ItemLike... items) {
    return of(potion.value(), items);
  }

  private Holder<Potion> potionHolder() {
    return BuiltInRegistries.POTION.wrapAsHolder(potion);
  }

  @Override
  protected ItemStack createStack(Item item) {
    return PotionContents.createItemStack(item, potionHolder());
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    return stack != null && super.test(stack)
      && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion().map(Holder::value).orElse(null) == potion;
  }

  @Override
  public boolean isSimple() {
    return false;
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
    return this == other || other instanceof PotionIngredient ingredient && potion == ingredient.potion && sameItems(ingredient);
  }

  @Override
  public int hashCode() {
    return 31 * itemsHash() + BuiltInRegistries.POTION.getId(potion);
  }
}
