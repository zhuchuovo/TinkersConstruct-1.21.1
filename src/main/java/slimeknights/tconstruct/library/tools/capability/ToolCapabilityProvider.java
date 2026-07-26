package slimeknights.tconstruct.library.tools.capability;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import slimeknights.tconstruct.library.tools.capability.fluid.ToolFluidCapability;
import slimeknights.tconstruct.library.tools.capability.inventory.ToolInventoryCapability;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.function.Supplier;

/** Registers NeoForge item capabilities for all modifiable Tinkers' tools. */
public final class ToolCapabilityProvider {
  private ToolCapabilityProvider() {}

  /** Registers tool capabilities after all tool items have entered the item registry. */
  public static void register(RegisterCapabilitiesEvent event) {
    Item[] tools = BuiltInRegistries.ITEM.stream().filter(item -> item instanceof IModifiable).toArray(Item[]::new);
    if (tools.length == 0) {
      return;
    }
    event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ignored) -> {
      Supplier<ToolStack> tool = refreshingTool(stack);
      return tool.get().getVolatileData().getInt(ToolFluidCapability.TOTAL_TANKS) > 0
        ? new ToolFluidCapability(stack, tool) : null;
    }, tools);
    event.registerItem(Capabilities.ItemHandler.ITEM, (stack, ignored) -> {
      Supplier<ToolStack> tool = refreshingTool(stack);
      return tool.get().getVolatileData().getInt(ToolInventoryCapability.TOTAL_SLOTS) > 0
        ? new ToolInventoryCapability(tool) : null;
    }, tools);
    event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ignored) -> {
      Supplier<ToolStack> tool = refreshingTool(stack);
      return ToolEnergyCapability.getMaxEnergy(tool.get()) > 0 ? new ToolEnergyCapability(tool) : null;
    }, tools);
  }

  private static Supplier<ToolStack> refreshingTool(ItemStack stack) {
    ToolStack tool = ToolStack.from(stack);
    return () -> {
      tool.refreshTag(stack);
      return tool;
    };
  }
}
