package slimeknights.mantle.recipe.helper;

import com.mojang.serialization.MapCodec;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.data.loadable.LoadableCodec;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.StringLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.mantle.util.typed.TypedMapBuilder;

import java.util.function.Supplier;

/** Bridges Mantle record loadables to the 1.21 recipe codec API. */
public class LoadableRecipeSerializer<T extends Recipe<?>> implements LoggingRecipeSerializer<T> {
  public static final ContextKey<RecipeSerializer<?>> SERIALIZER = new ContextKey<>("serializer");
  public static final ContextKey<TypeAwareRecipeSerializer<?>> TYPED_SERIALIZER = new ContextKey<>("typed_serializer");
  public static final ContextKey<RecipeType<?>> TYPE = new ContextKey<>("type");
  public static final LoadableField<String,Recipe<?>> RECIPE_GROUP = StringLoadable.DEFAULT.defaultField("group", "", Recipe::getGroup);

  /** Recipe IDs are owned by RecipeHolder in 1.21 and are not supplied to codecs. */
  public static final ResourceLocation UNBOUND_ID = Mantle.getResource("unbound_recipe");

  protected final RecordLoadable<T> loadable;

  protected LoadableRecipeSerializer(RecordLoadable<T> loadable) {
    this.loadable = loadable;
  }

  public static <T extends Recipe<?>> RecipeSerializer<T> of(RecordLoadable<T> loadable) {
    return new LoadableRecipeSerializer<>(loadable);
  }

  public static <T extends R, R extends Recipe<?>> TypeAwareRecipeSerializer<T> of(RecordLoadable<T> loadable, Supplier<? extends RecipeType<R>> type) {
    return new TypeAware<>(loadable, type);
  }

  public static <T extends Recipe<?>> RecipeSerializer<T> deprecated(RecordLoadable<T> loadable, String replacement) {
    return new Deprecated<>(loadable, replacement);
  }

  protected TypedMapBuilder contextBuilder() {
    return TypedMapBuilder.builder().put(ContextKey.ID, UNBOUND_ID).put(ContextKey.DEBUG, "Unbound recipe").put(SERIALIZER, this);
  }

  protected TypedMap buildContext() {
    return contextBuilder().build();
  }

  private T decodeNetwork(RegistryFriendlyByteBuf buffer, TypedMap context) {
    try {
      return loadable.decode(buffer, context);
    } catch (RuntimeException e) {
      Mantle.logger.error("{}: Error reading recipe packet using loadable {}", getClass().getSimpleName(), loadable, e);
      throw new DecoderException(e);
    }
  }

  private void encodeNetwork(RegistryFriendlyByteBuf buffer, T recipe) {
    try {
      loadable.encode(buffer, recipe);
    } catch (RuntimeException e) {
      Mantle.logger.error("{}: Error writing recipe {} packet using loadable {}", getClass().getSimpleName(), recipe.getClass().getSimpleName(), loadable, e);
      throw new EncoderException(e);
    }
  }

  @Override
  public MapCodec<T> codec() {
    return MapCodec.assumeMapUnsafe(new LoadableCodec<>(loadable, buildContext()));
  }

  @Override
  public StreamCodec<RegistryFriendlyByteBuf,T> streamCodec() {
    return StreamCodec.of(this::encodeNetwork, buffer -> decodeNetwork(buffer, buildContext()));
  }

  public static class TypeAware<T extends Recipe<?>> extends LoadableRecipeSerializer<T> implements TypeAwareRecipeSerializer<T> {
    private final Supplier<? extends RecipeType<?>> type;

    protected TypeAware(RecordLoadable<T> loadable, Supplier<? extends RecipeType<?>> type) {
      super(loadable);
      this.type = type;
    }

    @Override
    protected TypedMapBuilder contextBuilder() {
      return super.contextBuilder().put(TYPE, getType()).put(TYPED_SERIALIZER, this);
    }

    @Override
    public RecipeType<?> getType() {
      return type.get();
    }
  }

  private static class Deprecated<T extends Recipe<?>> extends LoadableRecipeSerializer<T> {
    protected Deprecated(RecordLoadable<T> loadable, String replacement) {
      super(loadable);
      Mantle.logger.warn("Registered deprecated recipe serializer {}, {}", BuiltInRegistries.RECIPE_SERIALIZER.getKey(this), replacement);
    }
  }
}
