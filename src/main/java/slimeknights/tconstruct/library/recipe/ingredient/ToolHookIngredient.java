package slimeknights.tconstruct.library.recipe.ingredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.mantle.data.JsonCodec;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.item.IModifiable;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Ingredient that only matches tools with a specific hook. */
public class ToolHookIngredient implements ICustomIngredient {
  public static final ResourceLocation ID = TConstruct.getResource("tool_hook");
  private static final JsonCodec<ToolHookIngredient> CODEC_BODY = new JsonCodec<>() {
    @Override
    public ToolHookIngredient deserialize(JsonElement element, DynamicOps<?> ops) {
      JsonObject json = element.getAsJsonObject();
      return new ToolHookIngredient(
        Loadables.ITEM_TAG.getOrDefault(json, "tag", TinkerTags.Items.MODIFIABLE),
        ToolHooks.LOADER.getIfPresent(json, "hook"));
    }

    @Override
    public JsonElement serialize(ToolHookIngredient ingredient, DynamicOps<?> ops) {
      JsonObject json = new JsonObject();
      json.addProperty("tag", ingredient.tag.location().toString());
      json.addProperty("hook", ingredient.hook.getId().toString());
      return json;
    }
  };
  public static final MapCodec<ToolHookIngredient> CODEC = MapCodec.assumeMapUnsafe(CODEC_BODY);
  public static final StreamCodec<RegistryFriendlyByteBuf,ToolHookIngredient> STREAM_CODEC =
    StreamCodec.of(ToolHookIngredient::write, ToolHookIngredient::read);
  public static final IngredientType<ToolHookIngredient> TYPE = new IngredientType<>(CODEC, STREAM_CODEC);

  private final TagKey<Item> tag;
  private final ModuleHook<?> hook;

  protected ToolHookIngredient(TagKey<Item> tag, ModuleHook<?> hook) {
    this.tag = tag;
    this.hook = hook;
  }

  public static net.minecraft.world.item.crafting.Ingredient of(TagKey<Item> tag, ModuleHook<?> hook) {
    return new ToolHookIngredient(tag, hook).toVanilla();
  }

  public static net.minecraft.world.item.crafting.Ingredient of(ModuleHook<?> hook) {
    return of(TinkerTags.Items.MODIFIABLE, hook);
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    return stack != null && stack.is(tag) && stack.getItem() instanceof IModifiable modifiable
      && modifiable.getToolDefinition().getData().getHooks().hasHook(hook);
  }

  @Override
  public Stream<ItemStack> getItems() {
    Stream<ItemStack> matches = StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(tag).spliterator(), false)
      .map(Holder::value)
      .filter(item -> item instanceof IModifiable modifiable && modifiable.getToolDefinition().getData().getHooks().hasHook(hook))
      .map(ItemStack::new);
    ItemStack[] items = matches.toArray(ItemStack[]::new);
    if (items.length == 0) {
      ItemStack barrier = new ItemStack(Blocks.BARRIER);
      barrier.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Empty Tag: " + tag.location()));
      return Stream.of(barrier);
    }
    return Stream.of(items);
  }

  @Override
  public boolean isSimple() {
    return true;
  }

  @Override
  public IngredientType<?> getType() {
    return TYPE;
  }

  private static ToolHookIngredient read(RegistryFriendlyByteBuf buffer) {
    return new ToolHookIngredient(Loadables.ITEM_TAG.decode(buffer), ToolHooks.LOADER.decode(buffer));
  }

  private static void write(RegistryFriendlyByteBuf buffer, ToolHookIngredient ingredient) {
    Loadables.ITEM_TAG.encode(buffer, ingredient.tag);
    ToolHooks.LOADER.encode(buffer, ingredient.hook);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ToolHookIngredient other && tag.equals(other.tag) && hook.equals(other.hook);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tag, hook);
  }
}
