package slimeknights.tconstruct.library.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.helper.FluidOutput;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.PartSwappingOverrideRecipe;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.test.BaseMcTest;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the {@code clientbound/minecraft:update_recipes} packet and the data feeding it.
 * <p>
 * Every recipe in the recipe manager is written into that packet when a player joins a server, so a single recipe whose
 * network encoding throws makes joining impossible: the client reports
 * {@code io.netty.handler.codec.EncoderException: Failed to encode packet 'clientbound/minecraft:update_recipes'} and is
 * disconnected before it can spawn. Encoding goes through {@link Recipe#getSerializer()} and that serializer's
 * {@code StreamCodec}, a completely different path from reading the JSON while loading, so a recipe can load fine and
 * still break the connection. Known causes, both covered here:
 * <ul>
 *   <li>a recipe class returning a serializer registered for a different recipe class, as the serializer casts back to
 *       its own class while encoding;</li>
 *   <li>a recipe whose result is a tag output ({@code "result": {"tag": "..."}}) where the tag is not filled at sync
 *       time, which throws while writing the output.</li>
 * </ul>
 */
class RecipeSyncTest extends BaseMcTest {
  /** Directories inside the mod resources holding recipes */
  private static final List<String> RECIPE_DIRS = List.of("data/tconstruct/recipe", "data/mantle/recipe");

  /** Locates the shipped recipe directories, either from the classpath or from the build output */
  private static List<Path> recipeRoots() throws Exception {
    List<Path> roots = new ArrayList<>();
    ClassLoader loader = RecipeSyncTest.class.getClassLoader();
    for (String dir : RECIPE_DIRS) {
      Enumeration<URL> urls = loader.getResources(dir);
      while (urls.hasMoreElements()) {
        URL url = urls.nextElement();
        if ("file".equals(url.getProtocol())) {
          roots.add(Path.of(url.toURI()));
        }
      }
    }
    if (roots.isEmpty()) {
      // fall back to the build output, searching upwards in case the working directory is a subdirectory of the project
      Path base = Path.of("").toAbsolutePath();
      for (int i = 0; i < 4 && base != null; i++, base = base.getParent()) {
        for (String dir : RECIPE_DIRS) {
          Path candidate = base.resolve("build/resources/main").resolve(dir);
          if (Files.isDirectory(candidate)) {
            roots.add(candidate);
          }
        }
        if (!roots.isEmpty()) {
          break;
        }
      }
    }
    return roots;
  }

  /** Gets the {@code data} directories holding the shipped resources of the mods on the classpath */
  private static List<Path> dataRoots() throws Exception {
    List<Path> roots = new ArrayList<>();
    for (Path recipeRoot : recipeRoots()) {
      // <resources>/data/<namespace>/recipe
      Path data = recipeRoot.getParent().getParent();
      if (data != null && Files.isDirectory(data) && !roots.contains(data)) {
        roots.add(data);
      }
    }
    return roots;
  }

  /** Gets all files with the given extension below the given directory */
  private static List<Path> findFiles(Path root, String extension) throws IOException {
    try (Stream<Path> stream = Files.walk(root)) {
      return stream.filter(Files::isRegularFile)
                   .filter(path -> path.getFileName().toString().endsWith(extension))
                   // vanilla skips files whose name starts with an underscore
                   .filter(path -> !path.getFileName().toString().startsWith("_"))
                   .toList();
    }
  }

  /** Converts a recipe path into its resource location */
  private static ResourceLocation idFor(Path root, Path file) {
    String path = root.relativize(file).toString().replace('\\', '/');
    return ResourceLocation.fromNamespaceAndPath("tconstruct", path.substring(0, path.length() - ".json".length()));
  }

  @Test
  void recipesAreFound() throws Exception {
    List<Path> roots = recipeRoots();
    List<Path> recipes = new ArrayList<>();
    for (Path root : roots) {
      recipes.addAll(findFiles(root, ".json"));
    }
    assertThat(recipes).as("shipped recipe files in %s", roots).hasSizeGreaterThan(100);
  }

  /**
   * Encodes every shipped recipe using the same codec the server uses to sync recipes to clients.
   * Any failure here is a recipe that disconnects players with an encoder exception on join.
   */
  @Test
  void allRecipesEncodeToNetwork() throws Exception {
    List<Path> roots = recipeRoots();
    assertThat(roots).as("shipped recipe directories").isNotEmpty();

    RegistryAccess registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    HolderLookup.Provider provider = registryAccess;
    RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, provider);

    List<String> failures = new ArrayList<>();
    int encoded = 0;
    for (Path root : roots) {
      for (Path file : findFiles(root, ".json")) {
        ResourceLocation id = idFor(root, file);
        JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        Recipe<?> recipe;
        try {
          recipe = Recipe.CODEC.parse(ops, json).getOrThrow(JsonParseException::new);
        } catch (RuntimeException e) {
          // recipes referencing datapack registries cannot be parsed without a loaded level
          continue;
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
        try {
          RecipeHolder.STREAM_CODEC.encode(buffer, new RecipeHolder<>(id, recipe));
          encoded++;
        } catch (RuntimeException e) {
          failures.add(id + " [" + recipe.getClass().getSimpleName() + " declared as " + serializerName(recipe) + "] -> " + rootCause(e));
        } finally {
          buffer.release();
        }
      }
    }

    assertThat(encoded).as("recipes encoded").isGreaterThan(100);
    assertThat(failures).as("recipes that fail to encode for the update_recipes packet (%d encoded)", encoded).isEmpty();
  }

  /**
   * A serializer must accept the recipes it produces. Recipe serializers cast the recipe to their own class while
   * encoding, so returning the serializer of a sibling recipe class throws on every recipe sync.
   */
  @Test
  void partSwappingOverrideSerializerRoundTrips() {
    RegistryAccess registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
    JsonObject json = new JsonObject();
    json.addProperty("type", "tconstruct:part_swapping_override");
    json.add("tools", JsonParser.parseString("{\"item\": \"minecraft:iron_ingot\"}"));
    json.addProperty("part", "tconstruct:pick_head");
    json.addProperty("index", 0);

    Recipe<?> recipe = Recipe.CODEC.parse(ops, json).getOrThrow(JsonParseException::new);
    assertThat(recipe).isInstanceOf(PartSwappingOverrideRecipe.class);
    assertThat(recipe.getSerializer()).as("serializer of a part swapping override recipe").isSameAs(TinkerTables.partSwappingOverride.get());

    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
    try {
      RecipeHolder.STREAM_CODEC.encode(buffer, new RecipeHolder<>(ResourceLocation.parse("tconstruct:test_part_swapping"), recipe));
    } finally {
      buffer.release();
    }
  }

  /**
   * A tag output whose tag is unfilled resolves to an empty stack. Syncing that must not throw, since the failure is
   * raised while writing the update_recipes packet and disconnects every player instead of leaving one recipe without a
   * result. JSON stays strict, so a broken output is still reported while reloading or generating data.
   */
  @Test
  void unfilledTagOutputEncodesAsEmpty() {
    RegistryAccess registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    TagKey<Fluid> fluidTag = TagKey.create(Registries.FLUID, ResourceLocation.parse("tconstruct:test_empty_fluid_tag"));
    TagKey<Item> itemTag = TagKey.create(Registries.ITEM, ResourceLocation.parse("tconstruct:test_empty_item_tag"));
    FluidOutput fluid = FluidOutput.fromTag(fluidTag, 250);
    ItemOutput item = ItemOutput.fromTag(itemTag, 4);

    // nothing fills these tags, so the outputs resolve to nothing while still reporting their tag
    assertThat(fluid.get().isEmpty()).isTrue();
    assertThat(item.get().isEmpty()).isTrue();
    assertThat(fluid.getTag()).isSameAs(fluidTag);
    assertThat(item.getTag()).isSameAs(itemTag);

    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
    try {
      FluidOutput.Loadable.REQUIRED.encode(buffer, fluid);
      ItemOutput.Loadable.REQUIRED_STACK.encode(buffer, item);
      // decoding mirrors the encoding, so an empty output must survive the round trip
      assertThat(FluidOutput.Loadable.REQUIRED.decode(buffer, TypedMap.EMPTY)).isSameAs(FluidOutput.EMPTY);
      assertThat(ItemOutput.Loadable.REQUIRED_STACK.decode(buffer, TypedMap.EMPTY)).isSameAs(ItemOutput.EMPTY);
    } finally {
      buffer.release();
    }

    // JSON keeps the tag, so the mistake stays visible in generated resources
    assertThat(FluidOutput.Loadable.REQUIRED.serialize(fluid).toString()).contains("tconstruct:test_empty_fluid_tag");
    assertThat(ItemOutput.Loadable.REQUIRED_STACK.serialize(item).toString()).contains("tconstruct:test_empty_item_tag");
  }

  /**
   * The 1.20 to 1.21 port renamed the {@code forge} namespace to {@code c}. The legacy {@code data/forge} tree is
   * excluded from the build, so any leftover {@code forge:} reference points at a tag that never exists at runtime.
   * That silently breaks an ingredient, and for a recipe result it is fatal: the tag output resolves empty and syncing
   * recipes to a joining player fails.
   */
  @Test
  void shippedDataHasNoLegacyForgeNamespace() throws Exception {    List<String> offenders = new ArrayList<>();
    for (Path dataRoot : dataRoots()) {
      for (Path file : findFiles(dataRoot, ".json")) {
        if (Files.readString(file, StandardCharsets.UTF_8).contains("\"forge:")) {
          offenders.add(dataRoot.relativize(file).toString().replace('\\', '/'));
        }
      }
    }
    assertThat(offenders)
      .as("shipped files referencing the legacy forge namespace, migrate them to c or neoforge")
      .isEmpty();
  }

  /** Gets the registry name of a recipe's serializer, for readable failures */
  private static String serializerName(Recipe<?> recipe) {
    try {
      return String.valueOf(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()));
    } catch (RuntimeException e) {
      return "<threw " + rootCause(e) + ">";
    }
  }

  /** Gets the most specific message in an exception chain */
  private static String rootCause(Throwable throwable) {
    Throwable cause = throwable;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    String message = cause.getMessage();
    return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
  }
}
