package slimeknights.tconstruct.library.tools.capability.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.library.utils.RegistryAccessUtil;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryModuleTest extends BaseMcTest {
  private static final ResourceLocation KEY = ResourceLocation.fromNamespaceAndPath("test", "inventory");
  private final InventoryModule inventory = InventoryModule.builder().key(KEY).flatSlots(2);
  private IToolStackView tool;
  private ModifierEntry modifier;
  private ToolDataNBT data;

  @BeforeEach
  void setupTool() {
    tool = mock(IToolStackView.class);
    modifier = mock(ModifierEntry.class);
    when(modifier.getLevel()).thenReturn(1);
    when(modifier.getEffectiveLevel()).thenReturn(1f);
    data = new ToolDataNBT();
    when(tool.getPersistentData()).thenReturn(data);
  }

  @Test
  void replacingAnEntryDoesNotKeepOldComponents() {
    ItemStack named = new ItemStack(Items.ARROW);
    named.set(DataComponents.CUSTOM_NAME, Component.literal("Old name"));
    inventory.setStack(tool, modifier, 0, named);

    inventory.setStack(tool, modifier, 0, new ItemStack(Items.ARROW, 2));

    ItemStack result = inventory.getStack(tool, modifier, 0);
    assertThat(result.getCount()).isEqualTo(2);
    assertThat(result.has(DataComponents.CUSTOM_NAME)).isFalse();
  }

  @Test
  void failedReplacementPreservesTheOldSlot() {
    inventory.setStack(tool, modifier, 0, new ItemStack(Items.DIAMOND, 3));
    CompoundTag before = data.getCopy();
    ItemStack invalid = mock(ItemStack.class);
    when(invalid.save(any(net.minecraft.core.HolderLookup.Provider.class))).thenThrow(new IllegalStateException("codec failed"));

    assertThatThrownBy(() -> inventory.setStack(tool, modifier, 0, invalid)).isInstanceOf(IllegalStateException.class);
    assertThat(data.getCopy()).isEqualTo(before);
    assertThat(inventory.getStack(tool, modifier, 0).getCount()).isEqualTo(3);
  }

  @Test
  void failedFirstInsertionDoesNotCreateAnEmptyInventoryTag() {
    ItemStack invalid = mock(ItemStack.class);
    when(invalid.save(any(net.minecraft.core.HolderLookup.Provider.class))).thenThrow(new IllegalStateException("codec failed"));

    assertThatThrownBy(() -> inventory.setStack(tool, modifier, 0, invalid)).isInstanceOf(IllegalStateException.class);
    assertThat(data.contains(KEY)).isFalse();
  }

  @Test
  void negativeSlotsAreRejectedAndMalformedStoredSlotsCanBeRecovered() {
    inventory.setStack(tool, modifier, -1, new ItemStack(Items.DIAMOND));
    assertThat(data.contains(KEY)).isFalse();

    ListTag entries = new ListTag();
    CompoundTag invalidSlot = (CompoundTag)new ItemStack(Items.DIAMOND).save(RegistryAccessUtil.BUILTIN);
    invalidSlot.putInt(InventoryModule.TAG_SLOT, -1);
    entries.add(invalidSlot);
    data.put(KEY, entries);
    assertThat(inventory.getAllStacks(tool, modifier, new ArrayList<>())).isEmpty();
    assertThat(inventory.getStack(tool, modifier, -1).isEmpty()).isTrue();
    assertThat(inventory.findStack(tool, modifier, stack -> true).stack().isEmpty()).isTrue();

    assertThat(inventory.validate(tool, modifier)).isNull();
    assertThat(data.getList(KEY, Tag.TAG_COMPOUND).getCompound(0).getInt(InventoryModule.TAG_SLOT)).isZero();
    assertThat(inventory.getStack(tool, modifier, 0).is(Items.DIAMOND)).isTrue();
  }
}
