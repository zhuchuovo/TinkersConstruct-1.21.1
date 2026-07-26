package slimeknights.mantle.recipe.ingredient;

import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.array.ArrayLoadable;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.field.RecordField;
import slimeknights.mantle.data.loadable.field.UnsyncedField;
import slimeknights.mantle.util.typed.TypedMap;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Base for custom ingredients that match a list of items or an item tag. */
public abstract class ItemIngredient implements ICustomIngredient {
  /** Field for the item tag. Tags are expanded to concrete items for network synchronization. */
  protected static final LoadableField<TagKey<Item>,ItemIngredient> TAG_FIELD = new UnsyncedField<>(Loadables.ITEM_TAG.nullableField("tag", i -> i.tag));

  protected final List<Item> items;
  @Nullable
  protected final TagKey<Item> tag;

  protected ItemIngredient(List<Item> items, @Nullable TagKey<Item> tag) {
    this.items = List.copyOf(items);
    this.tag = tag;
  }

  /** Maps item-like values to their registered items. */
  protected static List<Item> toItem(List<ItemLike> items) {
    return items.stream().map(ItemLike::asItem).toList();
  }

  /** Creates the display stack for an accepted item. Subclasses may add data components. */
  protected ItemStack createStack(Item item) {
    return new ItemStack(item);
  }

  @Override
  public Stream<ItemStack> getItems() {
    Stream<Item> explicit = items.stream();
    Stream<Item> tagged = tag == null ? Stream.empty() : StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(tag).spliterator(), false).map(Holder::value);
    return Stream.concat(explicit, tagged).distinct().map(this::createStack);
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    return stack != null && !stack.isEmpty() && (items.contains(stack.getItem()) || tag != null && stack.is(tag));
  }

  protected boolean sameItems(ItemIngredient other) {
    return items.equals(other.items) && Objects.equals(tag, other.tag);
  }

  protected int itemsHash() {
    return Objects.hash(items, tag);
  }

  /** Custom field that syncs an item tag as its resolved item values. */
  public enum ItemsField implements RecordField<List<Item>,ItemIngredient> {
    INSTANCE;

    private static final Loadable<List<Item>> ITEM_LIST = Loadables.ITEM.list(ArrayLoadable.COMPACT_OR_EMPTY);

    @Override
    public List<Item> get(JsonObject json, TypedMap context) {
      return ITEM_LIST.getOrDefault(json, "item", List.of(), context);
    }

    @Override
    public void serialize(ItemIngredient parent, JsonObject json) {
      if (!parent.items.isEmpty()) {
        json.add("item", ITEM_LIST.serialize(parent.items));
      }
    }

    @Override
    public List<Item> decode(FriendlyByteBuf buffer, TypedMap context) {
      return ITEM_LIST.decode(buffer, context);
    }

    @Override
    public void encode(FriendlyByteBuf buffer, ItemIngredient parent) {
      ITEM_LIST.encode(buffer, parent.getItems().map(ItemStack::getItem).toList());
    }
  }
}
