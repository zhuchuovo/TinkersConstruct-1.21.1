package net.minecraft.world.entity.player;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class Inventory implements Container, Nameable {
    public static final int POP_TIME_DURATION = 5;
    public static final int INVENTORY_SIZE = 36;
    private static final int SELECTION_SIZE = 9;
    public static final int SLOT_OFFHAND = 40;
    public static final int NOT_FOUND_INDEX = -1;
    public static final int[] ALL_ARMOR_SLOTS = new int[]{0, 1, 2, 3};
    public static final int[] HELMET_SLOT_ONLY = new int[]{3};
    public final NonNullList<ItemStack> items = NonNullList.withSize(36, ItemStack.EMPTY);
    public final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);
    public final NonNullList<ItemStack> offhand = NonNullList.withSize(1, ItemStack.EMPTY);
    private final List<NonNullList<ItemStack>> compartments = ImmutableList.of(this.items, this.armor, this.offhand);
    public int selected;
    public final Player player;
    private int timesChanged;

    public Inventory(Player player) {
        this.player = player;
    }

    public ItemStack getSelected() {
        return isHotbarSlot(this.selected) ? this.items.get(this.selected) : ItemStack.EMPTY;
    }

    public static int getSelectionSize() {
        return 9;
    }

    private boolean hasRemainingSpaceForItem(ItemStack destination, ItemStack origin) {
        return !destination.isEmpty()
            && ItemStack.isSameItemSameComponents(destination, origin)
            && destination.isStackable()
            && destination.getCount() < this.getMaxStackSize(destination);
    }

    public int getFreeSlot() {
        for (int i = 0; i < this.items.size(); i++) {
            if (this.items.get(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    public void setPickedItem(ItemStack stack) {
        int i = this.findSlotMatchingItem(stack);
        if (isHotbarSlot(i)) {
            this.selected = i;
        } else {
            if (i == -1) {
                this.selected = this.getSuitableHotbarSlot();
                if (!this.items.get(this.selected).isEmpty()) {
                    int j = this.getFreeSlot();
                    if (j != -1) {
                        this.items.set(j, this.items.get(this.selected));
                    }
                }

                this.items.set(this.selected, stack);
            } else {
                this.pickSlot(i);
            }
        }
    }

    public void pickSlot(int index) {
        this.selected = this.getSuitableHotbarSlot();
        ItemStack itemstack = this.items.get(this.selected);
        this.items.set(this.selected, this.items.get(index));
        this.items.set(index, itemstack);
    }

    public static boolean isHotbarSlot(int index) {
        return index >= 0 && index < 9;
    }

    /**
     * Finds the stack or an equivalent one in the main inventory
     */
    public int findSlotMatchingItem(ItemStack stack) {
        for (int i = 0; i < this.items.size(); i++) {
            if (!this.items.get(i).isEmpty() && ItemStack.isSameItemSameComponents(stack, this.items.get(i))) {
                return i;
            }
        }

        return -1;
    }

    public int findSlotMatchingUnusedItem(ItemStack stack) {
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack itemstack = this.items.get(i);
            if (!itemstack.isEmpty()
                && ItemStack.isSameItemSameComponents(stack, itemstack)
                && !itemstack.isDamaged()
                && !itemstack.isEnchanted()
                && !itemstack.has(DataComponents.CUSTOM_NAME)) {
                return i;
            }
        }

        return -1;
    }

    public int getSuitableHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            int j = (this.selected + i) % 9;
            if (this.items.get(j).isEmpty()) {
                return j;
            }
        }

        for (int k = 0; k < 9; k++) {
            int l = (this.selected + k) % 9;
            if (!this.items.get(l).isNotReplaceableByPickAction(this.player, l)) {
                return l;
            }
        }

        return this.selected;
    }

    /**
     * Change the selected item in the hotbar after a mouse scroll. Select the slot to the left if {@code direction} is positive, or to the right if negative.
     */
    public void swapPaint(double direction) {
        int i = (int)Math.signum(direction);
        this.selected -= i;

        while (this.selected < 0) {
            this.selected += 9;
        }

        while (this.selected >= 9) {
            this.selected -= 9;
        }
    }

    public int clearOrCountMatchingItems(Predicate<ItemStack> stackPredicate, int maxCount, Container inventory) {
        int i = 0;
        boolean flag = maxCount == 0;
        i += ContainerHelper.clearOrCountMatchingItems(this, stackPredicate, maxCount - i, flag);
        i += ContainerHelper.clearOrCountMatchingItems(inventory, stackPredicate, maxCount - i, flag);
        ItemStack itemstack = this.player.containerMenu.getCarried();
        i += ContainerHelper.clearOrCountMatchingItems(itemstack, stackPredicate, maxCount - i, flag);
        if (itemstack.isEmpty()) {
            this.player.containerMenu.setCarried(ItemStack.EMPTY);
        }

        return i;
    }

    /**
     * This function stores as many items of an ItemStack as possible in a matching slot and returns the quantity of left over items.
     */
    private int addResource(ItemStack stack) {
        int i = this.getSlotWithRemainingSpace(stack);
        if (i == -1) {
            i = this.getFreeSlot();
        }

        return i == -1 ? stack.getCount() : this.addResource(i, stack);
    }

    private int addResource(int slot, ItemStack stack) {
        int i = stack.getCount();
        ItemStack itemstack = this.getItem(slot);
        if (itemstack.isEmpty()) {
            itemstack = stack.copyWithCount(0);
            this.setItem(slot, itemstack);
        }

        int j = this.getMaxStackSize(itemstack) - itemstack.getCount();
        int k = Math.min(i, j);
        if (k == 0) {
            return i;
        } else {
            i -= k;
            itemstack.grow(k);
            itemstack.setPopTime(5);
            return i;
        }
    }

    /**
     * Stores a stack in the player's inventory. It first tries to place it in the selected slot in the player's hotbar, then the offhand slot, then any available/empty slot in the player's inventory.
     */
    public int getSlotWithRemainingSpace(ItemStack stack) {
        if (this.hasRemainingSpaceForItem(this.getItem(this.selected), stack)) {
            return this.selected;
        } else if (this.hasRemainingSpaceForItem(this.getItem(40), stack)) {
            return 40;
        } else {
            for (int i = 0; i < this.items.size(); i++) {
                if (this.hasRemainingSpaceForItem(this.items.get(i), stack)) {
                    return i;
                }
            }

            return -1;
        }
    }

    public void tick() {
        int slot = 0;
        for (NonNullList<ItemStack> nonnulllist : this.compartments) {
            for (int i = 0; i < nonnulllist.size(); i++) {
                if (!nonnulllist.get(i).isEmpty()) {
                    // Neo: Fix the slot param to be the global index instead of the per-compartment index.
                    // Neo: Fix the selected param to only be true for hotbar slots.
                    nonnulllist.get(i).inventoryTick(this.player.level(), this.player, slot, this.selected == slot);
                }
                slot++;
            }
        }
    }

    /**
     * Adds the stack to the first empty slot in the player's inventory. Returns {@code false} if it's not possible to place the entire stack in the inventory.
     */
    public boolean add(ItemStack stack) {
        return this.add(-1, stack);
    }

    /**
     * Adds the stack to the specified slot in the player's inventory. Returns {@code false} if it's not possible to place the entire stack in the inventory.
     */
    public boolean add(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else {
            try {
                if (stack.isDamaged()) {
                    if (slot == -1) {
                        slot = this.getFreeSlot();
                    }

                    if (slot >= 0) {
                        this.items.set(slot, stack.copyAndClear());
                        this.items.get(slot).setPopTime(5);
                        return true;
                    } else if (this.player.hasInfiniteMaterials()) {
                        stack.setCount(0);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    int i;
                    do {
                        i = stack.getCount();
                        if (slot == -1) {
                            stack.setCount(this.addResource(stack));
                        } else {
                            stack.setCount(this.addResource(slot, stack));
                        }
                    } while (!stack.isEmpty() && stack.getCount() < i);

                    if (stack.getCount() == i && this.player.hasInfiniteMaterials()) {
                        stack.setCount(0);
                        return true;
                    } else {
                        return stack.getCount() < i;
                    }
                }
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Adding item to inventory");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Item being added");
                crashreportcategory.setDetail("Registry Name", () -> String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())));
                crashreportcategory.setDetail("Item Class", () -> stack.getItem().getClass().getName());
                crashreportcategory.setDetail("Item ID", Item.getId(stack.getItem()));
                crashreportcategory.setDetail("Item data", stack.getDamageValue());
                crashreportcategory.setDetail("Item name", () -> stack.getHoverName().getString());
                throw new ReportedException(crashreport);
            }
        }
    }

    public void placeItemBackInInventory(ItemStack stack) {
        this.placeItemBackInInventory(stack, true);
    }

    public void placeItemBackInInventory(ItemStack stack, boolean sendPacket) {
        while (!stack.isEmpty()) {
            int i = this.getSlotWithRemainingSpace(stack);
            if (i == -1) {
                i = this.getFreeSlot();
            }

            if (i == -1) {
                this.player.drop(stack, false);
                break;
            }

            int j = stack.getMaxStackSize() - this.getItem(i).getCount();
            if (this.add(i, stack.split(j)) && sendPacket && this.player instanceof ServerPlayer) {
                ((ServerPlayer)this.player).connection.send(new ClientboundContainerSetSlotPacket(-2, 0, i, this.getItem(i)));
            }
        }
    }

    /**
     * Removes up to a specified number of items from an inventory slot and returns them in a new stack.
     */
    @Override
    public ItemStack removeItem(int index, int count) {
        List<ItemStack> list = null;

        for (NonNullList<ItemStack> nonnulllist : this.compartments) {
            if (index < nonnulllist.size()) {
                list = nonnulllist;
                break;
            }

            index -= nonnulllist.size();
        }

        return list != null && !list.get(index).isEmpty() ? ContainerHelper.removeItem(list, index, count) : ItemStack.EMPTY;
    }

    public void removeItem(ItemStack stack) {
        for (NonNullList<ItemStack> nonnulllist : this.compartments) {
            for (int i = 0; i < nonnulllist.size(); i++) {
                if (nonnulllist.get(i) == stack) {
                    nonnulllist.set(i, ItemStack.EMPTY);
                    break;
                }
            }
        }
    }

    /**
     * Removes a stack from the given slot and returns it.
     */
    @Override
    public ItemStack removeItemNoUpdate(int index) {
        NonNullList<ItemStack> nonnulllist = null;

        for (NonNullList<ItemStack> nonnulllist1 : this.compartments) {
            if (index < nonnulllist1.size()) {
                nonnulllist = nonnulllist1;
                break;
            }

            index -= nonnulllist1.size();
        }

        if (nonnulllist != null && !nonnulllist.get(index).isEmpty()) {
            ItemStack itemstack = nonnulllist.get(index);
            nonnulllist.set(index, ItemStack.EMPTY);
            return itemstack;
        } else {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Sets the given item stack to the specified slot in the inventory (can be crafting or armor sections).
     */
    @Override
    public void setItem(int index, ItemStack stack) {
        NonNullList<ItemStack> nonnulllist = null;

        for (NonNullList<ItemStack> nonnulllist1 : this.compartments) {
            if (index < nonnulllist1.size()) {
                nonnulllist = nonnulllist1;
                break;
            }

            index -= nonnulllist1.size();
        }

        if (nonnulllist != null) {
            nonnulllist.set(index, stack);
        }
    }

    public float getDestroySpeed(BlockState state) {
        return this.items.get(this.selected).getDestroySpeed(state);
    }

    /**
     * Writes the inventory out as a list of compound tags. This is where the slot indices are used (+100 for armor, +80 for crafting).
     */
    public ListTag save(ListTag listTag) {
        for (int i = 0; i < this.items.size(); i++) {
            if (!this.items.get(i).isEmpty()) {
                CompoundTag compoundtag = new CompoundTag();
                compoundtag.putByte("Slot", (byte)i);
                listTag.add(this.items.get(i).save(this.player.registryAccess(), compoundtag));
            }
        }

        for (int j = 0; j < this.armor.size(); j++) {
            if (!this.armor.get(j).isEmpty()) {
                CompoundTag compoundtag1 = new CompoundTag();
                compoundtag1.putByte("Slot", (byte)(j + 100));
                listTag.add(this.armor.get(j).save(this.player.registryAccess(), compoundtag1));
            }
        }

        for (int k = 0; k < this.offhand.size(); k++) {
            if (!this.offhand.get(k).isEmpty()) {
                CompoundTag compoundtag2 = new CompoundTag();
                compoundtag2.putByte("Slot", (byte)(k + 150));
                listTag.add(this.offhand.get(k).save(this.player.registryAccess(), compoundtag2));
            }
        }

        return listTag;
    }

    /**
     * Reads from the given tag list and fills the slots in the inventory with the correct items.
     */
    public void load(ListTag listTag) {
        this.items.clear();
        this.armor.clear();
        this.offhand.clear();

        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag compoundtag = listTag.getCompound(i);
            int j = compoundtag.getByte("Slot") & 255;
            ItemStack itemstack = ItemStack.parse(this.player.registryAccess(), compoundtag).orElse(ItemStack.EMPTY);
            if (j >= 0 && j < this.items.size()) {
                this.items.set(j, itemstack);
            } else if (j >= 100 && j < this.armor.size() + 100) {
                this.armor.set(j - 100, itemstack);
            } else if (j >= 150 && j < this.offhand.size() + 150) {
                this.offhand.set(j - 150, itemstack);
            }
        }
    }

    @Override
    public int getContainerSize() {
        return this.items.size() + this.armor.size() + this.offhand.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : this.items) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }

        for (ItemStack itemstack1 : this.armor) {
            if (!itemstack1.isEmpty()) {
                return false;
            }
        }

        for (ItemStack itemstack2 : this.offhand) {
            if (!itemstack2.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the stack in the given slot.
     */
    @Override
    public ItemStack getItem(int index) {
        List<ItemStack> list = null;

        for (NonNullList<ItemStack> nonnulllist : this.compartments) {
            if (index < nonnulllist.size()) {
                list = nonnulllist;
                break;
            }

            index -= nonnulllist.size();
        }

        return list == null ? ItemStack.EMPTY : list.get(index);
    }

    @Override
    public Component getName() {
        return Component.translatable("container.inventory");
    }

    /**
     * @return a player armor item (as an {@code ItemStack}) contained in specified armor slot
     */
    public ItemStack getArmor(int slot) {
        return this.armor.get(slot);
    }

    public void dropAll() {
        for (List<ItemStack> list : this.compartments) {
            for (int i = 0; i < list.size(); i++) {
                ItemStack itemstack = list.get(i);
                if (!itemstack.isEmpty()) {
                    this.player.drop(itemstack, true, false);
                    list.set(i, ItemStack.EMPTY);
                }
            }
        }
    }

    @Override
    public void setChanged() {
        this.timesChanged++;
    }

    public int getTimesChanged() {
        return this.timesChanged;
    }

    /**
     * Don't rename this method to canInteractWith due to conflicts with Container
     */
    @Override
    public boolean stillValid(Player player) {
        return player.canInteractWithEntity(this.player, 4.0);
    }

    /**
     * Returns {@code true} if the specified {@link net.minecraft.world.item.ItemStack} exists in the inventory.
     */
    public boolean contains(ItemStack stack) {
        for (List<ItemStack> list : this.compartments) {
            for (ItemStack itemstack : list) {
                if (!itemstack.isEmpty() && ItemStack.isSameItemSameComponents(itemstack, stack)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean contains(TagKey<Item> tag) {
        for (List<ItemStack> list : this.compartments) {
            for (ItemStack itemstack : list) {
                if (!itemstack.isEmpty() && itemstack.is(tag)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean contains(Predicate<ItemStack> predicate) {
        for (List<ItemStack> list : this.compartments) {
            for (ItemStack itemstack : list) {
                if (predicate.test(itemstack)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Copy the ItemStack contents from another InventoryPlayer instance
     */
    public void replaceWith(Inventory playerInventory) {
        for (int i = 0; i < this.getContainerSize(); i++) {
            this.setItem(i, playerInventory.getItem(i));
        }

        this.selected = playerInventory.selected;
    }

    @Override
    public void clearContent() {
        for (List<ItemStack> list : this.compartments) {
            list.clear();
        }
    }

    public void fillStackedContents(StackedContents stackedContent) {
        for (ItemStack itemstack : this.items) {
            stackedContent.accountSimpleStack(itemstack);
        }
    }

    /**
     * @param removeStack Whether to remove the entire stack of items. If {@code false
     *                    }, removes a single item.
     */
    public ItemStack removeFromSelected(boolean removeStack) {
        ItemStack itemstack = this.getSelected();
        return itemstack.isEmpty() ? ItemStack.EMPTY : this.removeItem(this.selected, removeStack ? itemstack.getCount() : 1);
    }
}
