package slimeknights.mantle.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.mantle.registration.MantleRegistrations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Hanging sign block entity to make it easier for signs to be registered, as the vanilla block entity has a closed set of blocks */
public class MantleHangingSignBlockEntity extends HangingSignBlockEntity {
  /** Sign blocks to use for the block entity valid blocks */
  private static final List<Supplier<? extends Block>> SIGN_BLOCKS = new ArrayList<>();
  public MantleHangingSignBlockEntity(BlockPos pos, BlockState state) {
    super(pos, state);
  }

  @Override
  public BlockEntityType<?> getType() {
    return MantleRegistrations.HANGING_SIGN;
  }

  /**
   * Registers a sign block to be injected into the tile entity, should be called before common setup
   * @param sign  Sign block supplier
   */
  public static void registerSignBlock(Supplier<? extends Block> sign) {
    synchronized (SIGN_BLOCKS) {
      SIGN_BLOCKS.add(sign);
    }
  }

  /** Builds the list of sign blocks for TE registration */
  public static Set<Block> buildSignBlocks() {
    return SIGN_BLOCKS.stream().map(Supplier::get).collect(Collectors.toSet());
  }
}
