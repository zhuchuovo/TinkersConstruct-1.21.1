package slimeknights.tconstruct.smeltery.block.entity.component;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import slimeknights.mantle.block.entity.IRetexturedBlockEntity;
import slimeknights.mantle.inventory.EmptyItemHandler;
import slimeknights.mantle.util.RetexturedHelper;
import slimeknights.tconstruct.common.multiblock.IMasterLogic;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.tank.ISmelteryTankHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

import static slimeknights.mantle.util.RetexturedHelper.TAG_TEXTURE;

/**
 * Shared logic between drains and ducts
 */
public abstract class SmelteryInputOutputBlockEntity<T> extends SmelteryComponentBlockEntity implements IRetexturedBlockEntity {
  /** Capability this TE watches */
  private final BlockCapability<T,Direction> capability;
  /** Empty capability for in case the valid capability becomes invalid without invalidating */
  protected final T emptyInstance;
  @Nullable
  private T capabilityHolder = null;
  private boolean capabilityCached = false;

  /* Retexturing */
  @Nonnull
  @Getter
  private Block texture = Blocks.AIR;

  protected SmelteryInputOutputBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, BlockCapability<T,Direction> capability, T emptyInstance) {
    super(type, pos, state);
    this.capability = capability;
    this.emptyInstance = emptyInstance;
  }

  /** Clears all cached capabilities */
  private void clearHandler() {
    capabilityHolder = null;
    capabilityCached = false;
  }

  @Override
  public void onMasterLoad(IMasterLogic master) {
    clearHandler();
  }

  @Override
  protected void setMaster(@Nullable BlockPos master, @Nullable Block block) {
    assert level != null;

    // if we have a new master, invalidate handlers
    boolean masterChanged = false;
    if (!Objects.equals(getMasterPos(), master)) {
      clearHandler();
      masterChanged = true;
    }
    super.setMaster(master, block);
    // notify neighbors of the change (state change skips the notify flag)
    if (masterChanged) {
      level.blockUpdated(worldPosition, getBlockState().getBlock());
    }
  }

  /**
   * Gets the capability to store in this IO block. Capability parent should have the proper listeners attached
   * @param parent  Parent tile entity
   * @return  Capability from parent, or empty if absent
   */
  @Nullable
  protected T findCapability(BlockEntity parent) {
    return level == null ? null : level.getCapability(capability, parent.getBlockPos(), null);
  }

  /**
   * Fetches the capability handlers if missing
   */
  private T getCachedCapability() {
    if (!capabilityCached) {
      capabilityCached = true;
      if (validateMaster()) {
        BlockPos master = getMasterPos();
        if (master != null && this.level != null) {
          BlockEntity te = level.getBlockEntity(master);
          if (te != null) {
            capabilityHolder = findCapability(te);
          }
        }
      }
    }
    return capabilityHolder == null ? emptyInstance : capabilityHolder;
  }

  /** Capability provider entry point registered from {@link TinkerSmeltery}. */
  public T getHandler(@Nullable Direction facing) {
    return getCachedCapability();
  }


  /* Retexturing */

  @Override
  @Nonnull
  public ModelData getModelData() {
    return RetexturedHelper.getModelData(getTexture());
  }

  @Override
  public String getTextureName() {
    return RetexturedHelper.getTextureName(texture);
  }

  @Override
  public void updateTexture(String name) {
    Block oldTexture = texture;
    texture = RetexturedHelper.getBlock(name);
    if (oldTexture != texture) {
      setChangedFast();
      RetexturedHelper.onTextureUpdated(this);
    }
  }


  /* NBT */

  @Override
  protected boolean shouldSyncOnUpdate() {
    return true;
  }

  @Override
  protected void saveSynced(CompoundTag tags) {
    super.saveSynced(tags);
    if (texture != Blocks.AIR) {
      tags.putString(TAG_TEXTURE, getTextureName());
    }
  }

  @Override
  public void load(CompoundTag tags) {
    super.load(tags);
    if (tags.contains(TAG_TEXTURE, Tag.TAG_STRING)) {
      texture = RetexturedHelper.getBlock(tags.getString(TAG_TEXTURE));
      RetexturedHelper.onTextureUpdated(this);
    }
  }


  /** Fluid implementation of smeltery IO */
  public static abstract class SmelteryFluidIO extends SmelteryInputOutputBlockEntity<IFluidHandler> {
    protected SmelteryFluidIO(BlockEntityType<?> type, BlockPos pos, BlockState state) {
      super(type, pos, state, Capabilities.FluidHandler.BLOCK, EmptyFluidHandler.INSTANCE);
    }

    /** Wraps the given capability */
    protected IFluidHandler makeWrapper(IFluidHandler capability) {
      return capability;
    }

    @Override
    protected IFluidHandler findCapability(BlockEntity parent) {
      // fluid capability is not exposed directly in the smeltery
      if (parent instanceof ISmelteryTankHandler tankHandler) {
        IFluidHandler capability = tankHandler.getFluidHandler();
        if (capability != null) {
          return makeWrapper(capability);
        }
      }
      return null;
    }
  }

  /** Item implementation of smeltery IO */
  public static class ChuteBlockEntity extends SmelteryInputOutputBlockEntity<IItemHandler> {
    public ChuteBlockEntity(BlockPos pos, BlockState state) {
      this(TinkerSmeltery.chute.get(), pos, state);
    }

    protected ChuteBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
      super(type, pos, state, Capabilities.ItemHandler.BLOCK, EmptyItemHandler.INSTANCE);
    }
  }

}
