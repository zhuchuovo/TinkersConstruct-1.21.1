package slimeknights.tconstruct.smeltery.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

/** This class exists simply to allow us to have a block entity renderer for obsidian gauges. Though it is useful as a cache for the capability to render. */
public class GaugeBlockEntity extends BlockEntity {
  public GaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
  }

  public GaugeBlockEntity(BlockPos pos, BlockState state) {
    this(TinkerSmeltery.gauge.get(), pos, state);
  }

  /** Gets the neighbor fluid handler. Used mainly for rendering client side */
  public IFluidHandler getTank() {
    if (level == null) {
      return EmptyFluidHandler.INSTANCE;
    }
    Direction side = getBlockState().getValue(BlockStateProperties.FACING);
    IFluidHandler neighbor = level.getCapability(Capabilities.FluidHandler.BLOCK, getBlockPos().relative(side.getOpposite()), side);
    return neighbor == null ? EmptyFluidHandler.INSTANCE : neighbor;
  }
}
