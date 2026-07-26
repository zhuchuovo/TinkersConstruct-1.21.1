package slimeknights.mantle.recipe.ingredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.data.JsonCodec;
import slimeknights.mantle.registration.object.FluidObject;
import slimeknights.mantle.util.JsonHelper;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.stream.Stream;

/** Ingredient that matches a container holding exactly the requested fluid amount. */
@SuppressWarnings("unused")
public final class FluidContainerIngredient implements ICustomIngredient {
  public static final ResourceLocation ID = Mantle.getResource("fluid_container");

  private static final JsonCodec<FluidContainerIngredient> CODEC_BODY = new JsonCodec<>() {
    @Override
    public FluidContainerIngredient deserialize(JsonElement element, DynamicOps<?> ops) {
      return FluidContainerIngredient.parse(GsonHelper.convertToJsonObject(element, "fluid container ingredient"));
    }

    @Override
    public JsonElement serialize(FluidContainerIngredient ingredient, DynamicOps<?> ops) {
      return ingredient.serializeBody();
    }
  };
  public static final MapCodec<FluidContainerIngredient> CODEC = MapCodec.assumeMapUnsafe(CODEC_BODY);
  public static final StreamCodec<RegistryFriendlyByteBuf,FluidContainerIngredient> STREAM_CODEC = StreamCodec.of(
    FluidContainerIngredient::write, FluidContainerIngredient::read);
  public static final IngredientType<FluidContainerIngredient> TYPE = new IngredientType<>(CODEC, STREAM_CODEC);

  private final FluidIngredient fluidIngredient;
  @Nullable
  private final Ingredient display;

  private FluidContainerIngredient(FluidIngredient fluidIngredient, @Nullable Ingredient display) {
    this.fluidIngredient = fluidIngredient;
    this.display = display;
  }

  public static Ingredient fromIngredient(FluidIngredient ingredient, Ingredient display) {
    return new FluidContainerIngredient(ingredient, display).toVanilla();
  }

  public static Ingredient fromIngredient(FluidIngredient ingredient) {
    return new FluidContainerIngredient(ingredient, null).toVanilla();
  }

  public static Ingredient fromFluid(FluidObject<?> fluid) {
    return fromIngredient(fluid.ingredient(FluidType.BUCKET_VOLUME), Ingredient.of(fluid));
  }

  @Override
  public boolean test(@Nullable ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return false;
    }
    IFluidHandlerItem original = stack.getCapability(Capabilities.FluidHandler.ITEM);
    if (original == null || original.getTanks() != 1) {
      return false;
    }
    FluidStack contained = original.getFluidInTank(0);
    if (contained.isEmpty() || fluidIngredient.getAmount(contained.getFluid()) != contained.getAmount() || !fluidIngredient.test(contained.getFluid())) {
      return false;
    }
    ItemStack copy = stack.copyWithCount(1);
    IFluidHandlerItem handler = copy.getCapability(Capabilities.FluidHandler.ITEM);
    if (handler == null) {
      return false;
    }
    Fluid fluid = handler.getFluidInTank(0).getFluid();
    int amount = fluidIngredient.getAmount(fluid);
    FluidStack drained = handler.drain(amount, FluidAction.EXECUTE);
    return drained.getFluid() == fluid && drained.getAmount() == amount && ItemStack.matches(stack.getCraftingRemainingItem(), handler.getContainer());
  }

  @Override
  public Stream<ItemStack> getItems() {
    return display == null ? Stream.empty() : java.util.Arrays.stream(display.getItems());
  }

  @Override
  public boolean isSimple() {
    return false;
  }

  @Override
  public IngredientType<?> getType() {
    return TYPE;
  }

  /** Compatibility helper for older Mantle data generators. */
  public JsonElement toJson() {
    return Ingredient.CODEC.encodeStart(JsonOps.INSTANCE, toVanilla()).getOrThrow(JsonParseException::new);
  }

  private JsonObject serializeBody() {
    JsonElement element = fluidIngredient.serialize();
    JsonObject json;
    if (element.isJsonObject()) {
      json = element.getAsJsonObject();
    } else {
      json = new JsonObject();
      json.add("fluid", element);
    }
    if (display != null) {
      json.add("display", Ingredient.CODEC.encodeStart(JsonOps.INSTANCE, display).getOrThrow(JsonParseException::new));
    }
    return json;
  }

  private static FluidContainerIngredient parse(JsonObject json) {
    FluidIngredient fluidIngredient;
    if (json.has("fluid") && !json.get("fluid").isJsonPrimitive()) {
      fluidIngredient = FluidIngredient.LOADABLE.getIfPresent(json, "fluid");
    } else {
      fluidIngredient = FluidIngredient.LOADABLE.convert(json, "fluid");
    }
    Ingredient display = json.has("display")
      ? Ingredient.CODEC.parse(JsonOps.INSTANCE, JsonHelper.getElement(json, "display")).getOrThrow(JsonParseException::new)
      : null;
    return new FluidContainerIngredient(fluidIngredient, display);
  }

  private static FluidContainerIngredient read(RegistryFriendlyByteBuf buffer) {
    FluidIngredient fluidIngredient = FluidIngredient.LOADABLE.decode(buffer);
    Ingredient display = buffer.readBoolean() ? Ingredient.CONTENTS_STREAM_CODEC.decode(buffer) : null;
    return new FluidContainerIngredient(fluidIngredient, display);
  }

  private static void write(RegistryFriendlyByteBuf buffer, FluidContainerIngredient ingredient) {
    FluidIngredient.LOADABLE.encode(buffer, ingredient.fluidIngredient);
    buffer.writeBoolean(ingredient.display != null);
    if (ingredient.display != null) {
      Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient.display);
    }
  }

  @Override
  public boolean equals(Object other) {
    return this == other || other instanceof FluidContainerIngredient ingredient
      && Objects.equals(fluidIngredient, ingredient.fluidIngredient) && Objects.equals(display, ingredient.display);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fluidIngredient, display);
  }
}
