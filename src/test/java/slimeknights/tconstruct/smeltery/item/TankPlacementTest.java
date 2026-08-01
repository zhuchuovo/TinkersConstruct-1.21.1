package slimeknights.tconstruct.smeltery.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.utils.ItemStackUtil;
import slimeknights.tconstruct.library.utils.NBTTags;
import slimeknights.tconstruct.library.utils.RegistryAccessUtil;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.CastingTankBlock;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.entity.CastingTankBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.component.TankBlockEntity;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TankPlacementTest extends BaseMcTest {
  private static final int AMOUNT = 1000;

  @Test
  void tankFilledThroughHandlerSurvivesPlacement() {
    SearedTankBlock block = TinkerSmeltery.searedTank.get(TankType.FUEL_TANK);
    ItemStack stack = new ItemStack(block);
    FluidTank contents = new FluidTank(block.getCapacity());
    contents.setFluid(new FluidStack(Fluids.WATER, AMOUNT));
    TankItem.setTank(stack, contents);

    assertPlacedFluid(block, roundTrip(stack));
  }

  @Test
  void tankPreFilledWithFluidStackSurvivesPlacement() {
    SearedTankBlock block = TinkerSmeltery.searedTank.get(TankType.FUEL_TANK);
    ItemStack stack = TankItem.setTank(new ItemStack(block), new FluidStack(Fluids.WATER, AMOUNT));

    assertPlacedFluid(block, roundTrip(stack));
  }

  @Test
  void tankWithDirectFluidDataFromEarlyPortSurvivesPlacement() {
    SearedTankBlock block = TinkerSmeltery.searedTank.get(TankType.FUEL_TANK);
    ItemStack stack = new ItemStack(block);
    CompoundTag itemData = new CompoundTag();
    itemData.put(NBTTags.TANK, new FluidStack(Fluids.WATER, AMOUNT).save(RegistryAccessUtil.BUILTIN));
    ItemStackUtil.setTag(stack, itemData);

    assertPlacedFluid(block, roundTrip(stack));
  }

  @Test
  void castingTankPreFilledWithFluidStackSurvivesPlacement() {
    CastingTankBlock block = TinkerSmeltery.searedCastingTank.get();
    ItemStack stack = TankItem.setTank(new ItemStack(block), new FluidStack(Fluids.WATER, AMOUNT));
    stack = roundTrip(stack);
    BlockPos pos = BlockPos.ZERO;
    CastingTankBlockEntity placed = new CastingTankBlockEntity(pos, block.defaultBlockState(), block);
    Level level = mock(Level.class);
    when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    when(level.getBlockEntity(pos)).thenReturn(placed);
    placed.setLevel(level);

    block.setPlacedBy(level, pos, block.defaultBlockState(), null, stack);

    FluidStack fluid = placed.getTank().getFluid();
    assertThat(fluid.getFluid()).isSameAs(Fluids.WATER);
    assertThat(fluid.getAmount()).isEqualTo(AMOUNT);
  }

  private static ItemStack roundTrip(ItemStack stack) {
    Tag serialized = stack.save(RegistryAccessUtil.BUILTIN);
    return ItemStack.parse(RegistryAccessUtil.BUILTIN, serialized).orElseThrow();
  }

  private static void assertPlacedFluid(SearedTankBlock block, ItemStack stack) {
    BlockPos pos = BlockPos.ZERO;
    TankBlockEntity placed = new TankBlockEntity(pos, block.defaultBlockState(), block);
    Level level = mock(Level.class);
    when(level.getBlockEntity(pos)).thenReturn(placed);

    block.setPlacedBy(level, pos, block.defaultBlockState(), null, stack);

    FluidStack fluid = placed.getTank().getFluid();
    assertThat(fluid.getFluid()).isSameAs(Fluids.WATER);
    assertThat(fluid.getAmount()).isEqualTo(AMOUNT);
  }
}
