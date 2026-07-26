package slimeknights.tconstruct.tables.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.mantle.util.BlockEntityHelper;
import slimeknights.tconstruct.tables.block.entity.chest.TinkersChestBlockEntity;

public class TinkersChestBlock extends ChestBlock {
  public TinkersChestBlock(Properties builder, BlockEntitySupplier<? extends BlockEntity> be, boolean dropsItems) {
    super(builder, be, dropsItems);
  }

  @Override
  public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) {
    ItemStack stack = new ItemStack(this);
    BlockEntityHelper.get(TinkersChestBlockEntity.class, world, pos).ifPresent(te -> {
      if (te.hasColor()) {
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(te.getColor(), true));
      }
    });
    return stack;
  }
}
