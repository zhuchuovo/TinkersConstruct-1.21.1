package slimeknights.tconstruct.tables.block.entity.table;

import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.tables.block.entity.inventory.CraftingContainerWrapper;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CraftingStationBlockEntityTest extends BaseMcTest {
  @Test
  void updateInputsConsumesSingleIngredientFromEveryGridPosition() {
    for (int slot = 0; slot < 9; slot++) {
      SimpleContainer inventory = new SimpleContainer(9);
      inventory.setItem(slot, new ItemStack(Items.STONE, 2));
      CraftingContainerWrapper craftingInventory = new CraftingContainerWrapper(inventory, 3, 3);
      CraftingInput.Positioned positionedInput = craftingInventory.asPositionedCraftInput();
      NonNullList<ItemStack> remaining = NonNullList.withSize(positionedInput.input().size(), ItemStack.EMPTY);

      CraftingStationBlockEntity.updateInputs(inventory, craftingInventory.getWidth(), positionedInput, remaining, mock(Player.class));

      assertThat(inventory.getItem(slot).getCount()).as("ingredient in slot %s", slot).isEqualTo(1);
    }
  }

  @Test
  void updateInputsReturnsContainerItemToOffsetGridPosition() {
    SimpleContainer inventory = new SimpleContainer(9);
    inventory.setItem(8, new ItemStack(Items.WATER_BUCKET));
    CraftingContainerWrapper craftingInventory = new CraftingContainerWrapper(inventory, 3, 3);
    CraftingInput.Positioned positionedInput = craftingInventory.asPositionedCraftInput();
    NonNullList<ItemStack> remaining = NonNullList.of(ItemStack.EMPTY, new ItemStack(Items.BUCKET));

    CraftingStationBlockEntity.updateInputs(inventory, craftingInventory.getWidth(), positionedInput, remaining, mock(Player.class));

    assertThat(inventory.getItem(0).isEmpty()).isTrue();
    assertThat(inventory.getItem(8).is(Items.BUCKET)).isTrue();
  }
}
