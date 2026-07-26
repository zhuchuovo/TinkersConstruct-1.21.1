package slimeknights.mantle.loot.function;

import java.util.List;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import slimeknights.mantle.data.loadable.common.FluidStackLoadable;
import slimeknights.mantle.data.loadable.LoadableCodec;
import slimeknights.mantle.loot.MantleLoot;

/**
 * Loot function to set the fluid on a dropped item
 */
public class SetFluidLootFunction extends LootItemConditionalFunction {
  public static final MapCodec<SetFluidLootFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
    commonFields(instance).and(new LoadableCodec<>(FluidStackLoadable.REQUIRED_STACK_NBT).fieldOf("fluid").forGetter(value -> value.fluid))
      .apply(instance, SetFluidLootFunction::new));

  /** Fluid to add to the item */
  private final FluidStack fluid;
  protected SetFluidLootFunction(List<LootItemCondition> conditionsIn, FluidStack fluid) {
    super(conditionsIn);
    this.fluid = fluid;
  }

  @Override
  protected ItemStack run(ItemStack stack, LootContext context) {
    IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
    if (handler != null) {
      handler.fill(fluid.copy(), FluidAction.EXECUTE);
      return handler.getContainer();
    }
    return stack;
  }

  @Override
  public LootItemFunctionType getType() {
    return MantleLoot.SET_FLUID_FUNCTION;
  }

  /**
   * Creates a new builder with the given fluid
   * @param fluid  Fluid to set
   * @return  Builder instance
   */
  public static Builder<?> builder(FluidStack fluid) {
    return simpleBuilder(conditions -> new SetFluidLootFunction(conditions, fluid));
  }
}
