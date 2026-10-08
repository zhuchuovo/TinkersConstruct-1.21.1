package slimeknights.tconstruct.plugin.jei.modifiers;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.library.focus.Focus;
import mezz.jei.library.focus.FocusGroup;
import mezz.jei.library.ingredients.TypedIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierFixture;
import slimeknights.tconstruct.library.recipe.modifiers.adding.DisplayModifierRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.adding.IDisplayModifierRecipe;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.ToolItemTest;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static slimeknights.tconstruct.fixture.MaterialFixture.MATERIAL_WITH_EXTRA;
import static slimeknights.tconstruct.fixture.MaterialFixture.MATERIAL_WITH_HANDLE;
import static slimeknights.tconstruct.fixture.MaterialFixture.MATERIAL_WITH_HEAD;

/**
 * Verifies the modifier category shows the tool the JEI focus points at (e.g. the tool in the
 * player's hand) instead of the recipe's generic render tool with fixed NBT.
 * Uses JDK dynamic proxies instead of Mockito for the same class-identity reasons as
 * {@code EntityMeltingRecipeCategoryTest}.
 */
class ModifierRecipeCategoryTest extends ToolItemTest {
  /** Slot positions of the two tool slots in the category layout. */
  private static final int WITHOUT_MODIFIER_X = 25, WITHOUT_MODIFIER_Y = 38;
  private static final int WITH_MODIFIER_X = 105, WITH_MODIFIER_Y = 34;

  private ClassLoader previousLoader;

  @BeforeEach
  void useModuleClassLoader() {
    previousLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
  }

  @AfterEach
  void restoreClassLoader() {
    Thread.currentThread().setContextClassLoader(previousLoader);
  }

  /** Creates a proxy implementing {@code iface}, stubbing methods by name. */
  @SuppressWarnings("unchecked")
  private static <T> T deepProxy(Class<T> iface, Map<String, Function<Object[],Object>> stubs) {
    InvocationHandler handler = (proxy, method, args) -> {
      Function<Object[],Object> stub = stubs.get(method.getName());
      if (stub != null) {
        return stub.apply(args);
      }
      if (method.getReturnType() == boolean.class) return false;
      if (method.getReturnType().isPrimitive()) return 0;
      if (method.getReturnType() == void.class) return null;
      return deepProxy(method.getReturnType(), stubs);
    };
    return (T)Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] { iface }, handler);
  }

  /** Records the item stacks added to one recipe slot. */
  private static final class SlotRecord {
    private final int x, y;
    private List<ItemStack> stacks = List.of();

    private SlotRecord(int x, int y) {
      this.x = x;
      this.y = y;
    }

    /** Creates a proxy builder that records item stacks added to this slot. */
    private IRecipeSlotBuilder builder() {
      InvocationHandler handler = (proxy, method, args) -> {
        if (method.getName().equals("addItemStacks")) {
          //noinspection unchecked
          stacks = new ArrayList<>((List<ItemStack>)args[0]);
          return proxy;
        }
        Class<?> returnType = method.getReturnType();
        if (returnType == boolean.class) return false;
        if (returnType.isPrimitive()) return 0;
        if (returnType == void.class) return null;
        if (returnType == IRecipeSlotBuilder.class) return proxy;
        return deepProxy(returnType, Map.of());
      };
      return (IRecipeSlotBuilder)Proxy.newProxyInstance(IRecipeSlotBuilder.class.getClassLoader(), new Class<?>[] { IRecipeSlotBuilder.class }, handler);
    }
  }

  /** Layout builder recording visible slots by position, paired with the recorded slots. */
  private record RecordingLayout(IRecipeLayoutBuilder builder, Map<Integer,SlotRecord> slots) {
    static RecordingLayout create() {
      Map<Integer,SlotRecord> slots = new HashMap<>();
      IRecipeLayoutBuilder builder = deepProxy(IRecipeLayoutBuilder.class, new HashMap<>(Map.of(
        "addSlot", args -> {
          SlotRecord record = new SlotRecord((int)args[1], (int)args[2]);
          slots.put(record.y * 1000 + record.x, record);
          return record.builder();
        }
      )));
      return new RecordingLayout(builder, slots);
    }
  }

  private static SlotRecord slot(RecordingLayout layout, int x, int y) {
    SlotRecord slot = layout.slots().get(y * 1000 + x);
    assertThat(slot).as("slot at " + x + "," + y).isNotNull();
    return slot;
  }

  /** Creates the category with a proxy gui helper, same pattern as the entity melting test. */
  private static ModifierRecipeCategory makeCategory() {
    Map<String, Function<Object[],Object>> guiStubs = new HashMap<>();
    guiStubs.put("createDrawable", args -> deepProxy(mezz.jei.api.gui.drawable.IDrawableStatic.class, Map.of(
      "getWidth", a -> 150,
      "getHeight", a -> 62
    )));
    return new ModifierRecipeCategory(deepProxy(IGuiHelper.class, guiStubs));
  }

  /** Creates a display recipe whose tool lists hold the fixed render tool. */
  private static DisplayModifierRecipe makeRecipe(ItemStack renderTool, ModifierEntry result) {
    return DisplayModifierRecipe.builder()
      .inputs(List.of(List.of(new ItemStack(Items.REDSTONE))))
      .toolWithoutModifier(List.of(renderTool.copy()))
      .toolWithModifier(List.of(IDisplayModifierRecipe.withModifiers(renderTool, List.of(result))))
      .result(result)
      .build();
  }

  /** Builds a tool with a modifier on it, so it differs from the plain render tool. */
  private ItemStack buildFocusedTool() {
    ItemStack stack = ToolBuildHandler.buildItemFromMaterials(tool, MaterialNBT.of(MATERIAL_WITH_HEAD, MATERIAL_WITH_HANDLE, MATERIAL_WITH_EXTRA));
    ToolStack.from(stack).addModifier(ModifierFixture.TEST_2, 1);
    return stack;
  }

  @Test
  void focusShowsFocusedToolInsteadOfRenderTool() {
    ModifierEntry result = new ModifierEntry(ModifierFixture.TEST_1, 1);
    ItemStack renderTool = buildTestTool(tool);
    ItemStack focusedTool = buildFocusedTool();
    assertThat(ItemStack.matches(renderTool, focusedTool)).as("sanity: focused tool differs from render tool").isFalse();

    ModifierRecipeCategory category = makeCategory();
    IFocus<ItemStack> focus = new Focus<>(RecipeIngredientRole.CATALYST, TypedIngredient.createUnvalidated(VanillaTypes.ITEM_STACK, focusedTool));
    var record = RecordingLayout.create();
    category.setRecipe(record.builder(), makeRecipe(renderTool, result), (IFocusGroup)focus);

    // the tool slot shows the exact focused stack, not the generic render tool
    List<ItemStack> withoutModifier = slot(record, WITHOUT_MODIFIER_X, WITHOUT_MODIFIER_Y).stacks;
    assertThat(withoutModifier).singleElement().matches(shown -> ItemStack.matches(shown, focusedTool));

    // the result slot shows the focused tool with the recipe modifier merged in, keeping its own modifier
    List<ItemStack> withModifier = slot(record, WITH_MODIFIER_X, WITH_MODIFIER_Y).stacks;
    assertThat(withModifier).singleElement().matches(shown -> !ItemStack.matches(shown, focusedTool));
    ToolStack preview = ToolStack.from(withModifier.get(0));
    assertThat(preview.getUpgrades().getLevel(ModifierFixture.TEST_1)).isEqualTo(1);
    assertThat(preview.getUpgrades().getLevel(ModifierFixture.TEST_2)).isEqualTo(1);
    assertThat(preview.getMaterials()).isEqualTo(ToolStack.from(focusedTool).getMaterials());
  }

  @Test
  void noFocusKeepsRecipeToolLists() {
    ModifierEntry result = new ModifierEntry(ModifierFixture.TEST_1, 1);
    ItemStack renderTool = buildTestTool(tool);

    ModifierRecipeCategory category = makeCategory();
    var record = RecordingLayout.create();
    category.setRecipe(record.builder(), makeRecipe(renderTool, result), FocusGroup.EMPTY);

    // without a focus, the recipe's own display lists are shown unchanged
    assertThat(slot(record, WITHOUT_MODIFIER_X, WITHOUT_MODIFIER_Y).stacks)
      .singleElement()
      .matches(shown -> ItemStack.matches(shown, renderTool));
  }
}
