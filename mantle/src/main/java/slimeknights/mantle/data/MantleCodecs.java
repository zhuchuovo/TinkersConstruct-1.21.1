package slimeknights.mantle.data;

import com.mojang.serialization.Codec;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;

/** This class contains codecs for various vanilla things that we need to use in codecs. Typically the reason is forge pre-emptively moved a thing to codecs before vanilla did. */
public class MantleCodecs {
  /** Codec for loot pool entries */
  public static final Codec<LootPoolEntryContainer> LOOT_ENTRY = LootPoolEntries.CODEC;
  /** Codec for loot pool entries */
  public static final Codec<LootItemFunction[]> LOOT_FUNCTIONS = LootItemFunctions.ROOT_CODEC.listOf()
    .xmap(list -> list.toArray(LootItemFunction[]::new), java.util.List::of);
  /** Codec for ingredients, handling forge ingredient types */
  public static final Codec<Ingredient> INGREDIENT = Ingredient.CODEC;
}
