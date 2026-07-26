package slimeknights.tconstruct.library.recipe.casting;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import slimeknights.tconstruct.library.utils.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.core.registries.BuiltInRegistries;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.modifiers.adding.IDisplayModifierRecipe;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.Arrays;
import java.util.List;

/** Casting recipe applying a potion to a tool */
public class TippingCastingRecipe extends PotionCastingRecipe {
  protected static final LoadableField<Ingredient, PotionCastingRecipe> TOOL_FIELD = IngredientLoadable.DISALLOW_EMPTY.requiredField("tools", r -> r.bottle);
  public static final RecordLoadable<TippingCastingRecipe> LOADER = RecordLoadable.create(
    LoadableRecipeSerializer.TYPED_SERIALIZER.requiredField(), ContextKey.ID.requiredField(), LoadableRecipeSerializer.RECIPE_GROUP,
    TOOL_FIELD, FLUID_FIELD, COOLING_TIME_FIELD,
    ModifierId.PARSER.requiredField("modifier", r -> r.modifier),
    TippingCastingRecipe::new);

  private final ModifierId modifier;
  public TippingCastingRecipe(TypeAwareRecipeSerializer<?> serializer, ResourceLocation id, String group, Ingredient tool, FluidIngredient fluid, int coolingTime, ModifierId modifier) {
    super(serializer, id, group, tool, fluid, Items.AIR, coolingTime);
    this.modifier = modifier;
  }

  @Override
  public boolean matches(ICastingContainer inv, Level level) {
    // must have the modifier to cast
    ItemStack stack = inv.getStack();
    if (super.matches(inv, level) && ModifierUtil.getModifierLevel(stack, modifier) > 0) {
      // must also have a specific potion, it's what we are going to copy
      // but it can't match what is already on the stack
      Holder<Potion> potion = PotionUtils.getPotion(inv.getFluidStack());
      return potion != null && potion.unwrapKey().isPresent()
        && !ModifierUtil.getPersistentString(stack, modifier).equals(potion.unwrapKey().orElseThrow().location().toString());
    }
    return false;
  }

  @Override
  public ItemStack assemble(ICastingContainer inv, HolderLookup.Provider access) {
    return assemble0(inv);
  }

  @SuppressWarnings("removal")
  @Override
  @Deprecated
  public ItemStack assemble(ICastingContainer inv, RegistryAccess access) {
    return assemble0(inv);
  }

  private ItemStack assemble0(ICastingContainer inv) {
    ItemStack result = inv.getStack().copy();
    Holder<Potion> potion = PotionUtils.getPotion(inv.getFluidStack());
    if (potion != null && potion.unwrapKey().isPresent()) {
      ToolStack.from(result).getPersistentData().putString(modifier, potion.unwrapKey().orElseThrow().location().toString());
    }
    return result;
  }


  /* JEI */

  @Override
  public List<DisplayCastingRecipe> getRecipes(RegistryAccess access) {
    if (displayRecipes == null) {
      // create a list of tools with the modifier
      List<ItemStack> tools = Arrays.stream(bottle.getItems())
        .map(stack -> IDisplayModifierRecipe.withModifiers(IModifiableDisplay.getDisplayStack(stack), List.of(new ModifierEntry(modifier, 1))))
        .toList();
      displayRecipes = BuiltInRegistries.POTION.holders()
        .map(potion -> {
          // add the potion to the tool list
          String id = potion.key().location().toString();
          List<ItemStack> results = tools.stream().map(stack -> {
            ToolStack tool = ToolStack.copyFrom(stack);
            tool.getPersistentData().putString(modifier, id);
            return tool.copyStack(stack);
          }).toList();
          // add the potion to the fluid
          // create the recipe
          return new DisplayCastingRecipe(getId(), getType(), tools, fluid.getFluids().stream()
            .map(fluid -> PotionUtils.setPotion(new FluidStack(fluid.getFluid(), fluid.getAmount()), potion))
            .toList(),
            results, coolingTime, true);
        }).toList();
    }
    return displayRecipes;
  }
}
