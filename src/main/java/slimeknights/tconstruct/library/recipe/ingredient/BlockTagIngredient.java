package slimeknights.tconstruct.library.recipe.ingredient;

import com.mojang.serialization.MapCodec;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.mantle.util.RegistryHelper;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Item ingredient matching items with a block form in the given tag */
@RequiredArgsConstructor
public class BlockTagIngredient implements ICustomIngredient {
  public static final ResourceLocation ID = TConstruct.getResource("block_tag");
  public static final MapCodec<BlockTagIngredient> CODEC = TagKey.codec(Registries.BLOCK).fieldOf("tag")
    .xmap(BlockTagIngredient::new, ingredient -> ingredient.tag);
  public static final StreamCodec<RegistryFriendlyByteBuf,BlockTagIngredient> STREAM_CODEC = StreamCodec.composite(
    ResourceLocation.STREAM_CODEC, ingredient -> ingredient.tag.location(),
    id -> new BlockTagIngredient(TagKey.create(Registries.BLOCK, id)));
  public static final IngredientType<BlockTagIngredient> TYPE = new IngredientType<>(CODEC, STREAM_CODEC);

  private final TagKey<Block> tag;
  @Nullable
  private Set<Item> matchingItems;
  @Nullable

  @Override
  public boolean test(@Nullable ItemStack stack) {
    return stack != null && getMatchingItems().contains(stack.getItem());
  }

  @Override
  public boolean isSimple() {
    return true;
  }

  /** Gets the ordered matching items set */
  private Set<Item> getMatchingItems() {
    if (matchingItems == null) {
      matchingItems = RegistryHelper.getTagValueStream(BuiltInRegistries.BLOCK, tag)
                                    .map(Block::asItem)
                                    .filter(item -> item != Items.AIR)
                                    .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    return matchingItems;
  }

  @Override
  public Stream<ItemStack> getItems() {
    return getMatchingItems().stream().map(ItemStack::new);
  }

  @Override
  public IngredientType<?> getType() {
    return TYPE;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof BlockTagIngredient other && tag.equals(other.tag);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tag);
  }
}
