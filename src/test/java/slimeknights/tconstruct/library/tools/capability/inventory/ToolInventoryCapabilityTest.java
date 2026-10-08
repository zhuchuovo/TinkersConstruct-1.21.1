package slimeknights.tconstruct.library.tools.capability.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolInventoryCapabilityTest extends BaseMcTest {
  private IToolStackView tool;
  private ModifierEntry modifier;
  private ToolInventoryCapability.InventoryModifierHook inventory;
  private ToolInventoryCapability handler;
  private ItemStack container;

  @BeforeEach
  void setupInventory() {
    tool = mock(IToolStackView.class);
    modifier = mock(ModifierEntry.class);
    inventory = mock(ToolInventoryCapability.InventoryModifierHook.class);
    ToolDataNBT data = new ToolDataNBT();
    data.putInt(ToolInventoryCapability.TOTAL_SLOTS, 1);
    when(tool.getVolatileData()).thenReturn(data);
    when(tool.getModifierList()).thenReturn(List.of(modifier));
    when(modifier.getHook(ToolInventoryCapability.HOOK)).thenReturn(inventory);
    when(inventory.getSlots(tool, modifier)).thenReturn(1);
    when(inventory.getStack(tool, modifier, 0)).thenReturn(ItemStack.EMPTY);
    when(inventory.getSlotLimit(tool, modifier, 0)).thenReturn(64);
    when(inventory.isItemValid(eq(tool), eq(modifier), eq(0), any())).thenReturn(true);
    container = new ItemStack(Items.STICK);
    handler = new ToolInventoryCapability(container, () -> tool);
  }

  @Test
  void differentPotionArrowsCannotMerge() {
    ItemStack existing = new ItemStack(Items.TIPPED_ARROW);
    existing.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
    ItemStack incoming = new ItemStack(Items.TIPPED_ARROW);
    incoming.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
    when(inventory.getStack(tool, modifier, 0)).thenReturn(existing);

    assertThat(handler.insertItem(0, incoming, true)).isSameAs(incoming);
    assertThat(handler.insertItem(0, incoming, false)).isSameAs(incoming);
    assertThat(existing.getCount()).isEqualTo(1);
    verify(inventory, never()).setStack(any(), any(), eq(0), any());
  }

  @Test
  void matchingItemsMergeUpToSlotLimitAndSimulationDoesNotMutate() {
    ItemStack existing = new ItemStack(Items.ARROW, 60);
    ItemStack incoming = new ItemStack(Items.ARROW, 10);
    when(inventory.getStack(tool, modifier, 0)).thenReturn(existing);

    assertThat(handler.insertItem(0, incoming, true).getCount()).isEqualTo(6);
    assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(60);
    assertThat(handler.insertItem(0, incoming, false).getCount()).isEqualTo(6);
    assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(64);
    assertThat(incoming.getCount()).isEqualTo(10);
  }

  @Test
  void failedWritesDoNotMutateTheCachedStack() {
    ItemStack existing = new ItemStack(Items.ARROW, 10);
    when(inventory.getStack(tool, modifier, 0)).thenReturn(existing);
    doThrow(new IllegalStateException("codec failed")).when(inventory).setStack(any(), any(), eq(0), any());

    assertThatThrownBy(() -> handler.insertItem(0, new ItemStack(Items.ARROW), false)).isInstanceOf(IllegalStateException.class);
    assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(10);
    assertThatThrownBy(() -> handler.extractItem(0, 1, false)).isInstanceOf(IllegalStateException.class);
    assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(10);
  }

  @Test
  void negativeSlotsAndZeroCapacityDoNotWriteItems() {
    ItemStack incoming = new ItemStack(Items.ARROW);
    assertThat(handler.insertItem(-1, incoming, false)).isSameAs(incoming);
    assertThat(handler.getStackInSlot(-1).isEmpty()).isTrue();
    assertThat(handler.extractItem(-1, 1, false).isEmpty()).isTrue();
    when(inventory.getSlotLimit(tool, modifier, 0)).thenReturn(0);
    assertThat(handler.insertItem(0, incoming, false)).isSameAs(incoming);
    verify(inventory, never()).setStack(any(), any(), anyInt(), any());
  }

  @Test
  void replacedContainerDataInvalidatesCachedItemsAndSlotCount() {
    when(inventory.getStack(tool, modifier, 0)).thenReturn(new ItemStack(Items.ARROW, 10));
    assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(10);
    assertThat(handler.getSlots()).isEqualTo(1);

    when(inventory.getStack(tool, modifier, 0)).thenReturn(new ItemStack(Items.ARROW, 2));
    ToolDataNBT replacement = new ToolDataNBT();
    replacement.putInt(ToolInventoryCapability.TOTAL_SLOTS, 2);
    when(tool.getVolatileData()).thenReturn(replacement);
    container.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));

    assertThat(handler.getSlots()).isEqualTo(2);
    assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(2);
  }

  @Test
  void veryLargeInsertCountsDoNotOverflow() {
    when(inventory.getStack(tool, modifier, 0)).thenReturn(new ItemStack(Items.ARROW, 10));

    ItemStack remainder = handler.insertItem(0, new ItemStack(Items.ARROW, Integer.MAX_VALUE), false);

    assertThat(remainder.getCount()).isEqualTo(Integer.MAX_VALUE - 54);
    assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(64);
  }
}
