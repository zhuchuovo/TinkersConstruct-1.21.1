package slimeknights.tconstruct.smeltery.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.fluid.FluidTransferHelper;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock;
import slimeknights.tconstruct.smeltery.block.component.SearedTankBlock.TankType;
import slimeknights.tconstruct.smeltery.block.entity.component.TankBlockEntity;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests the interaction of holding a tank item and right clicking a tank block */
class TankInteractionTest extends BaseMcTest {
  /** Creates a mocked level, an empty tank block entity placed in it, and registers the block fluid capability */
  private static TankBlockEntity emptyBlockTank(SearedTankBlock block, BlockPos pos) {
    Level level = mock(Level.class);
    when(level.isClientSide()).thenReturn(false);
    when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    when(level.getBlockState(pos)).thenReturn(block.defaultBlockState());
    TankBlockEntity te = new TankBlockEntity(pos, block.defaultBlockState(), block);
    when(level.getBlockEntity(pos)).thenReturn(te);
    when(level.getCapability(eq(Capabilities.FluidHandler.BLOCK), eq(pos), any())).thenReturn(te.getFluidHandler());
    te.setLevel(level);
    return te;
  }

  /** Creates a player mock holding the given stack, capturing any stack placed in the main hand */
  private static Player mockPlayer(ItemStack held, AtomicReference<ItemStack> newHeld, Level level) {
    Player player = mock(Player.class);
    when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(held);
    when(player.hasInfiniteMaterials()).thenReturn(false);
    when(player.level()).thenReturn(level);
    doAnswer(invocation -> {
      newHeld.set(invocation.getArgument(1));
      return null;
    }).when(player).setItemInHand(eq(InteractionHand.MAIN_HAND), any(ItemStack.class));
    return player;
  }

  @Test
  void filledHeldTankRightClickEmptyTankTransfersFluid() {
    SearedTankBlock block = TinkerSmeltery.searedTank.get(TankType.FUEL_TANK);
    int capacity = block.getCapacity();
    BlockPos pos = BlockPos.ZERO;
    TankBlockEntity te = emptyBlockTank(block, pos);

    ItemStack held = TankItem.setTank(new ItemStack(block.asItem()), new FluidStack(Fluids.WATER, capacity));
    AtomicReference<ItemStack> newHeld = new AtomicReference<>();
    Player player = mockPlayer(held, newHeld, te.getLevel());

    FluidTransferHelper.FluidInteractionResult result =
      FluidTransferHelper.interactWithContainer(te.getLevel(), pos, te.getFluidHandler(), player, InteractionHand.MAIN_HAND);

    assertThat(result).isEqualTo(FluidTransferHelper.FluidInteractionResult.DRAINED_STACK);
    // block received the fluid
    assertThat(te.getTank().getFluidAmount()).isEqualTo(capacity);
    assertThat(te.getTank().getFluid().is(Fluids.WATER)).isTrue();
    // held item is now empty
    assertThat(newHeld.get()).isNotNull();
    assertThat(newHeld.get().getCount()).isEqualTo(1);
    assertThat(TankItem.getTank(newHeld.get(), 1).isEmpty()).isTrue();
  }

  @Test
  void stackedFilledHeldTankRightClickEmptyTankKeepsRemainingFluid() {
    SearedTankBlock block = TinkerSmeltery.searedTank.get(TankType.FUEL_TANK);
    int capacity = block.getCapacity();
    BlockPos pos = BlockPos.ZERO;
    TankBlockEntity te = emptyBlockTank(block, pos);

    // stack of 16 filled tanks
    ItemStack held = TankItem.setTank(new ItemStack(block.asItem(), 16), new FluidStack(Fluids.WATER, capacity));
    AtomicReference<ItemStack> newHeld = new AtomicReference<>();
    Player player = mockPlayer(held, newHeld, te.getLevel());
    Inventory inventory = mock(Inventory.class);
    when(inventory.add(any())).thenReturn(true);
    when(player.getInventory()).thenReturn(inventory);

    FluidTransferHelper.FluidInteractionResult result =
      FluidTransferHelper.interactWithContainer(te.getLevel(), pos, te.getFluidHandler(), player, InteractionHand.MAIN_HAND);

    assertThat(result).isEqualTo(FluidTransferHelper.FluidInteractionResult.DRAINED_STACK);
    // block received one tank's worth of fluid
    assertThat(te.getTank().getFluidAmount()).isEqualTo(capacity);
    // the remaining 15 tanks must keep their fluid, only one tank was emptied
    assertThat(newHeld.get()).isNotNull();
    assertThat(newHeld.get().getCount()).isEqualTo(15);
    assertThat(TankItem.getTank(newHeld.get(), 1).getFluidAmount()).isEqualTo(capacity);
    assertThat(TankItem.getTank(newHeld.get(), 1).getFluid().is(Fluids.WATER)).isTrue();
  }

  @Test
  void stackedHeldTankOntoOtherTankSlotKeepsRemainingFluid() {
    SearedTankBlock seared = TinkerSmeltery.searedTank.get(TankType.FUEL_TANK);
    SearedTankBlock scorched = TinkerSmeltery.scorchedTank.get(TankType.FUEL_TANK);
    int capacity = seared.getCapacity();
    BlockPos pos = BlockPos.ZERO;
    TankBlockEntity te = emptyBlockTank(seared, pos);

    // stack of 16 filled seared tanks on the cursor, empty scorched tank in the slot
    ItemStack held = TankItem.setTank(new ItemStack(seared.asItem(), 16), new FluidStack(Fluids.WATER, capacity));
    ItemStack slotStack = new ItemStack(scorched.asItem());
    AtomicReference<ItemStack> slotResult = new AtomicReference<>();
    Slot slot = mock(Slot.class);
    when(slot.allowModification(any())).thenReturn(true);
    when(slot.getItem()).thenReturn(slotStack);
    doAnswer(invocation -> {
      slotResult.set(invocation.getArgument(0));
      return null;
    }).when(slot).set(any(ItemStack.class));

    Player player = mock(Player.class);
    when(player.level()).thenReturn(te.getLevel());
    Inventory inventory = mock(Inventory.class);
    when(inventory.add(any())).thenReturn(true);
    when(player.getInventory()).thenReturn(inventory);

    boolean overridden = ((TankItem)seared.asItem()).overrideStackedOnOther(held, slot, net.minecraft.world.inventory.ClickAction.SECONDARY, player);

    assertThat(overridden).isTrue();
    // slot got a filled scorched tank
    assertThat(slotResult.get()).isNotNull();
    assertThat(slotResult.get().is(scorched.asItem())).isTrue();
    assertThat(TankItem.getTank(slotResult.get(), 1).getFluidAmount()).isEqualTo(capacity);
    // cursor keeps 15 filled tanks, fluid intact
    assertThat(held.getCount()).isEqualTo(15);
    assertThat(TankItem.getTank(held, 1).getFluidAmount()).isEqualTo(capacity);
  }

  @Test
  void filledHeldTankRightClickEmptyTankThroughWorldLookup() {
    SearedTankBlock block = TinkerSmeltery.searedTank.get(TankType.FUEL_TANK);
    int capacity = block.getCapacity();
    BlockPos pos = BlockPos.ZERO;
    TankBlockEntity te = emptyBlockTank(block, pos);

    ItemStack held = TankItem.setTank(new ItemStack(block.asItem()), new FluidStack(Fluids.WATER, capacity));
    AtomicReference<ItemStack> newHeld = new AtomicReference<>();
    Player player = mockPlayer(held, newHeld, te.getLevel());

    BlockHitResult hit = new BlockHitResult(Vec3.ZERO, Direction.UP, pos, false);
    boolean interacted = FluidTransferHelper.interactWithTank(te.getLevel(), pos, player, InteractionHand.MAIN_HAND, hit);

    assertThat(interacted).isTrue();
    assertThat(te.getTank().getFluidAmount()).isEqualTo(capacity);
    assertThat(newHeld.get()).isNotNull();
    assertThat(TankItem.getTank(newHeld.get(), 1).isEmpty()).isTrue();
  }

  @Test
  void emptyHeldTankRightClickFilledTankDrainsBlock() {
    SearedTankBlock block = TinkerSmeltery.searedTank.get(TankType.FUEL_TANK);
    int capacity = block.getCapacity();
    BlockPos pos = BlockPos.ZERO;
    TankBlockEntity te = emptyBlockTank(block, pos);
    // fill the block tank
    te.getTank().setFluid(new FluidStack(Fluids.WATER, capacity));

    ItemStack held = new ItemStack(block.asItem());
    AtomicReference<ItemStack> newHeld = new AtomicReference<>();
    Player player = mockPlayer(held, newHeld, te.getLevel());

    FluidTransferHelper.FluidInteractionResult result =
      FluidTransferHelper.interactWithContainer(te.getLevel(), pos, te.getFluidHandler(), player, InteractionHand.MAIN_HAND);

    assertThat(result).isEqualTo(FluidTransferHelper.FluidInteractionResult.FILLED_STACK);
    // block drained
    assertThat(te.getTank().isEmpty()).isTrue();
    // held item now filled
    assertThat(newHeld.get()).isNotNull();
    assertThat(TankItem.getTank(newHeld.get(), 1).getFluidAmount()).isEqualTo(capacity);
    assertThat(TankItem.getTank(newHeld.get(), 1).getFluid().is(Fluids.WATER)).isTrue();
  }

  @Test
  void getTankScalesFluidByStackSize() {
    SearedTankBlock block = TinkerSmeltery.searedTank.get(TankType.FUEL_TANK);
    int capacity = block.getCapacity();
    // 16 tanks, each holding 1000 mb
    ItemStack stack = TankItem.setTank(new ItemStack(block.asItem(), 16), new FluidStack(Fluids.WATER, 1000));

    // unscaled reads return the per item fluid
    assertThat(TankItem.getTank(stack, 1).getFluidAmount()).isEqualTo(1000);
    // scaled reads represent the whole stack, matching the scaled capacity
    assertThat(((TankItem)block.asItem()).getTank(stack).getFluidAmount()).isEqualTo(16 * 1000);
    assertThat(((TankItem)block.asItem()).getTank(stack).getCapacity()).isEqualTo(16 * capacity);
  }

  @Test
  void emptyHeldTankRightClickFullTankSlotFillsOnlyOneTank() {
    SearedTankBlock block = TinkerSmeltery.searedTank.get(TankType.FUEL_TANK);
    int capacity = block.getCapacity();
    BlockPos pos = BlockPos.ZERO;
    TankBlockEntity te = emptyBlockTank(block, pos);

    // slot: a stack of 16 full tanks, cursor: one empty tank
    ItemStack slotStack = TankItem.setTank(new ItemStack(block.asItem(), 16), new FluidStack(Fluids.WATER, capacity));
    ItemStack held = new ItemStack(block.asItem());

    Slot slot = mock(Slot.class);
    when(slot.allowModification(any())).thenReturn(true);

    AtomicReference<ItemStack> newCarried = new AtomicReference<>();
    AbstractContainerMenu menu = mock(AbstractContainerMenu.class);
    when(menu.getCarried()).thenReturn(held);
    doAnswer(invocation -> {
      newCarried.set(invocation.getArgument(0));
      return null;
    }).when(menu).setCarried(any(ItemStack.class));

    Player player = mock(Player.class);
    when(player.level()).thenReturn(te.getLevel());
    // containerMenu is a public field on Player, set it via reflection
    try {
      java.lang.reflect.Field field = Player.class.getField("containerMenu");
      field.set(player, menu);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }

    boolean overridden = ((TankItem)block.asItem()).overrideOtherStackedOnMe(slotStack, held, slot, ClickAction.SECONDARY, player, null);

    assertThat(overridden).isTrue();
    // cursor received one full tank
    assertThat(newCarried.get()).isNotNull();
    assertThat(newCarried.get().getCount()).isEqualTo(1);
    assertThat(TankItem.getTank(newCarried.get(), 1).getFluidAmount()).isEqualTo(capacity);
    // the slot stack lost exactly one tank's worth: 16 tanks now hold 3750 mb each, not empty
    assertThat(slotStack.getCount()).isEqualTo(16);
    assertThat(TankItem.getTank(slotStack, 1).getFluidAmount()).isEqualTo(capacity * 15 / 16);
    assertThat(TankItem.getTank(slotStack, 1).getFluid().is(Fluids.WATER)).isTrue();
    // total fluid is conserved
    assertThat(((TankItem)block.asItem()).getTank(slotStack).getFluidAmount() + TankItem.getTank(newCarried.get(), 1).getFluidAmount()).isEqualTo(16 * capacity);
  }

  @Test
  void tankItemCapabilityScalesWithStackSize() {
    SearedTankBlock block = TinkerSmeltery.searedTank.get(TankType.FUEL_TANK);
    int capacity = block.getCapacity();
    ItemStack stack = TankItem.setTank(new ItemStack(block.asItem(), 16), new FluidStack(Fluids.WATER, capacity));
    TankItemFluidHandler handler = new TankItemFluidHandler((TankItem)block.asItem(), stack);

    // capability sees the whole stack, capacity and fluid agree
    assertThat(handler.getFluidInTank(0).getAmount()).isEqualTo(16 * capacity);
    assertThat(handler.getTankCapacity(0)).isEqualTo(16 * capacity);
    // draining one tank's worth keeps the other 15 tanks' fluid
    FluidStack drained = handler.drain(capacity, IFluidHandler.FluidAction.EXECUTE);
    assertThat(drained.getAmount()).isEqualTo(capacity);
    assertThat(TankItem.getTank(stack, 1).getFluidAmount()).isEqualTo(capacity * 15 / 16);
    assertThat(((TankItem)block.asItem()).getTank(stack).getFluidAmount()).isEqualTo(capacity * 15);
  }
}
