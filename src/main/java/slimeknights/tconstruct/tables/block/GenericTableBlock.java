package slimeknights.tconstruct.tables.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier;
import slimeknights.mantle.block.InventoryBlock;
import slimeknights.tconstruct.shared.block.entity.TableBlockEntity;

import javax.annotation.Nullable;

/** Generic block shared by any that don't need special stuff on top */
public class GenericTableBlock extends RetexturedTableBlock {
  private final BlockEntitySupplier<? extends BlockEntity> blockEntity;
  public GenericTableBlock(Properties builder, BlockEntitySupplier<? extends BlockEntity> blockEntity) {
    super(builder);
    this.blockEntity = blockEntity;
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return blockEntity.create(pos, state);
  }

  /**
   * Drops table inputs directly from the block entity. Table inventories intentionally do not
   * expose automation capabilities, so relying on {@link InventoryBlock}'s capability lookup
   * loses their contents when the block is broken.
   */
  @Override
  @Deprecated
  public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
    if (state.getBlock() != newState.getBlock() && level.getBlockEntity(pos) instanceof TableBlockEntity table) {
      InventoryBlock.dropInventoryItems(level, pos, table.getItemHandler());
    }
    super.onRemove(state, level, pos, newState, isMoving);
  }
}
