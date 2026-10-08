package slimeknights.tconstruct.plugin.jei.entity;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.library.focus.FocusGroup;
import mezz.jei.library.gui.recipes.layout.builder.RecipeLayoutBuilder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import slimeknights.mantle.plugin.jei.MantleJEIConstants;
import slimeknights.mantle.plugin.jei.entity.EntityIngredientHelper;
import slimeknights.mantle.recipe.helper.FluidOutput;
import slimeknights.mantle.recipe.ingredient.EntityIngredient;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.recipe.entitymelting.EntityMeltingRecipe;
import slimeknights.tconstruct.test.BaseMcTest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the entity melting category builds a JEI recipe layout for a magma cube recipe.
 * Uses JDK dynamic proxies instead of Mockito: the ModDevGradle unit test environment loads
 * JEI both into the module layer and onto the plain test classpath (jei.library internals
 * need the full jar), and Mockito generated proxies against the classpath copy, which cannot
 * be cast inside module-loaded category code. Dynamic proxies are defined against the test
 * class's own view of the interfaces, keeping class identity consistent.
 */
class EntityMeltingRecipeCategoryTest extends BaseMcTest {
  private ClassLoader previousLoader;

  @BeforeEach
  void useModuleClassLoader() {
    // JEI's Services.load uses ServiceLoader with the TCCL; the module layer loader finds the
    // module-bound platform helper before scanning the duplicate classpath copy
    previousLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
  }

  @AfterEach
  void restoreClassLoader() {
    Thread.currentThread().setContextClassLoader(previousLoader);
  }

  /** Creates a proxy implementing {@code iface}. Unhandled object returns become nested proxies, primitives zero out. */
  @SuppressWarnings("unchecked")
  private static <T> T deepProxy(Class<T> iface, Map<String, Function<Object[],Object>> stubs) {
    InvocationHandler handler = (proxy, method, args) -> {
      Function<Object[],Object> stub = stubs.get(method.getName());
      if (stub != null) {
        return stub.apply(args);
      }
      if (method.getReturnType() == boolean.class) return false;
      if (method.getReturnType().isPrimitive()) return 0;
      if (method.getReturnType() == String.class) return "";
      if (method.getReturnType() == void.class) return null;
      return deepProxy(method.getReturnType(), stubs);
    };
    return (T)Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] { iface }, handler);
  }

  private static IIngredientHelper<ItemStack> itemHelper() {
    return deepProxy(IIngredientHelper.class, Map.of(
      "isValidIngredient", args -> !((ItemStack)args[0]).isEmpty(),
      "normalizeIngredient", args -> args[0]
    ));
  }

  private static IIngredientHelper<FluidStack> fluidHelper() {
    return deepProxy(IIngredientHelper.class, Map.of(
      "isValidIngredient", args -> !((FluidStack)args[0]).isEmpty(),
      "normalizeIngredient", args -> args[0]
    ));
  }

  @Test
  void magmaCubeRecipeBuildsWithJeiLayoutBuilder() {
    Map<String, Function<Object[],Object>> guiStubs = new HashMap<>();
    guiStubs.put("createDrawable", args -> deepProxy(mezz.jei.api.gui.drawable.IDrawableStatic.class, Map.of(
      "getWidth", a -> 150,
      "getHeight", a -> 62
    )));
    IGuiHelper gui = deepProxy(IGuiHelper.class, guiStubs);
    EntityMeltingRecipeCategory category = new EntityMeltingRecipeCategory(gui);
    EntityMeltingRecipe recipe = new EntityMeltingRecipe(TConstruct.getResource("smeltery/entity_melting/magma_cube"),
      EntityIngredient.of(EntityType.MAGMA_CUBE), FluidOutput.fromFluid(Fluids.LAVA, 25), 2);

    // ingredient manager resolving helpers per type; unchecked due to generic helper types
    @SuppressWarnings("rawtypes")
    Map<IIngredientType, IIngredientHelper> helpers = Map.of(
      MantleJEIConstants.ENTITY_TYPE, new EntityIngredientHelper(),
      VanillaTypes.ITEM_STACK, itemHelper(),
      NeoForgeTypes.FLUID_STACK, fluidHelper()
    );
    IIngredientManager ingredients = deepProxy(IIngredientManager.class, Map.of(
      "getIngredientHelper", args -> helpers.get(args[0])
    ));

    RecipeLayoutBuilder<EntityMeltingRecipe> builder = new RecipeLayoutBuilder<>(category, recipe, ingredients);
    category.setRecipe(builder, recipe, FocusGroup.EMPTY);
    assertThat(builder.buildRecipeLayout(FocusGroup.EMPTY, List.of(),
      deepProxy(mezz.jei.api.gui.drawable.IScalableDrawable.class, Map.of()), 0)).isNotNull();
  }
}
