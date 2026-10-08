package slimeknights.tconstruct.tables.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.mantle.block.InventoryBlock;
import slimeknights.mantle.block.RetexturedBlock;
import slimeknights.mantle.util.RetexturedHelper;
import slimeknights.tconstruct.shared.block.entity.TableBlockEntity;

import javax.annotation.Nullable;
import java.util.List;

public abstract class RetexturedTableBlock extends TabbedTableBlock {
  public RetexturedTableBlock(Properties builder) {
    super(builder);
  }

  @Override
  public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    RetexturedHelper.addTooltip(stack, tooltip, flag);
  }

  @Override
  public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(world, pos, state, placer, stack);
    RetexturedBlock.updateTextureBlock(world, pos, stack);
  }

  @Override
  public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) {
    return RetexturedBlock.getPickBlock(world, pos, state);
  }

  /**
   * Drops table inputs directly from the block entity. Table inventories intentionally do not expose
   * automation capabilities (see {@code TinkerTables#registerCapabilities}), so {@link InventoryBlock}'s
   * capability lookup in {@code onRemove} finds no handler and loses their contents when the block is broken.
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
