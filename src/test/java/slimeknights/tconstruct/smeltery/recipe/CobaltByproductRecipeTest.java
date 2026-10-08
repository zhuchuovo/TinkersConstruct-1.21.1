package slimeknights.tconstruct.smeltery.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data contract for the cobalt melting recipes, in the spirit of {@code library.recipe.RecipeSyncTest}.
 * <p>
 * The cobalt ore family must carry molten diamond as its byproduct, so melting cobalt ore pays out a byproduct
 * wherever byproducts are produced. The recipes are read from {@code src/generated/resources} rather than the
 * classpath: datagen is disabled in this port, so the generated tree is the shipped source of truth and
 * {@code build/resources/main} may be a build behind.
 * <p>
 * The byproduct identity itself is deliberate and matches upstream: {@code Byproduct.SMALL_DIAMOND} is a quarter gem
 * (25 mB) at gem rate, chosen to be comparable to the 30 mB a metal byproduct yields at the default foundry byproduct
 * rate. This test locks it so a future change has to argue with the numbers.
 */
class CobaltByproductRecipeTest {
  private static final String ORE_BYPRODUCT = "tconstruct:molten_diamond";
  private static final String COBALT_DIR = "src/generated/resources/data/tconstruct/recipe/smeltery/melting/metal/cobalt";

  /** Ore recipes of the cobalt family and the byproduct amount each one ships */
  private static final Map<String, Integer> ORE_BYPRODUCT_AMOUNTS = new LinkedHashMap<>();

  static {
    // one ore unit: raw ore and sparse ore, two for a singular ore, six for a dense ore, nine for the raw block
    ORE_BYPRODUCT_AMOUNTS.put("raw", 25);
    ORE_BYPRODUCT_AMOUNTS.put("ore_sparse", 25);
    ORE_BYPRODUCT_AMOUNTS.put("ore_singular", 50);
    ORE_BYPRODUCT_AMOUNTS.put("ore_dense", 150);
    ORE_BYPRODUCT_AMOUNTS.put("raw_block", 225);
  }

  @Test
  void oreFamilyCarriesMoltenDiamond() {
    for (Map.Entry<String, Integer> entry : ORE_BYPRODUCT_AMOUNTS.entrySet()) {
      String file = entry.getKey();
      JsonObject recipe = readCobalt(file);

      assertThat(type(recipe)).as("%s recipe type", file).isEqualTo("tconstruct:ore_melting");
      JsonArray byproducts = byproducts(recipe);
      assertThat(byproducts).as("%s byproducts", file).hasSize(1);

      JsonObject byproduct = byproducts.get(0).getAsJsonObject();
      assertThat(fluid(byproduct)).as("%s byproduct fluid", file).isEqualTo(ORE_BYPRODUCT);
      assertThat(amount(byproduct)).as("%s byproduct amount", file).isEqualTo(entry.getValue());
      assertThat(byproduct.get("rate").getAsString()).as("%s byproduct rate", file).isEqualTo("gem");
    }
  }

  /**
   * The geode cluster stays a plain melting recipe with no byproduct, matching upstream. Cobalt clusters are grown in
   * the ichor geode rather than mined as an ore, and the ore family above is where byproducts belong.
   */
  @Test
  void clusterIsPlainMeltingWithoutByproduct() {
    JsonObject cluster = readCobalt("cluster");
    assertThat(type(cluster)).as("cobalt cluster recipe type").isEqualTo("tconstruct:melting");
    assertThat(byproducts(cluster)).as("cobalt cluster byproducts").isEmpty();
  }

  /**
   * The cluster stays a plain melting recipe, so its 40 mB output is not scaled by the ore rate. Making it an
   * ore melting recipe would silently change the smeltery output for anyone with a non-default nuggets per metal.
   */
  @Test
  void clusterOutputIsNotOreBoosted() {
    JsonObject cluster = readCobalt("cluster");

    assertThat(cluster.has("rate")).as("cobalt cluster declares an ore rate").isFalse();

    JsonObject result = cluster.getAsJsonObject("result");
    assertThat(amount(result)).as("cobalt cluster output").isEqualTo(40);
    assertThat(result.get("tag").getAsString()).as("cobalt cluster output fluid").isEqualTo("c:molten_cobalt");
  }

  /* Helpers */

  /** Locates the generated cobalt recipe directory, searching upwards in case the working directory is a subdirectory */
  private static Path cobaltDir() {
    Path base = Path.of("").toAbsolutePath();
    for (int i = 0; i < 4 && base != null; i++, base = base.getParent()) {
      Path candidate = base.resolve(COBALT_DIR);
      if (Files.isDirectory(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("Could not find " + COBALT_DIR + " from " + Path.of("").toAbsolutePath());
  }

  private static JsonObject readCobalt(String name) {
    Path file = cobaltDir().resolve(name + ".json");
    try {
      return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    } catch (IOException e) {
      throw new IllegalStateException("Could not read " + file, e);
    }
  }

  private static String type(JsonObject recipe) {
    return recipe.get("type").getAsString();
  }

  private static JsonArray byproducts(JsonObject recipe) {
    JsonElement byproducts = recipe.get("byproducts");
    return byproducts == null ? new JsonArray() : byproducts.getAsJsonArray();
  }

  private static String fluid(JsonObject byproduct) {
    return byproduct.get("fluid").getAsString();
  }

  private static int amount(JsonObject byproduct) {
    return byproduct.get("amount").getAsInt();
  }
}
