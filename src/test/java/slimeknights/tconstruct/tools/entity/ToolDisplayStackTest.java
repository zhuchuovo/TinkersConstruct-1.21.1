package slimeknights.tconstruct.tools.entity;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.utils.ItemStackUtil;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies tool projectile metadata never carries data a network packet cannot write. */
class ToolDisplayStackTest extends BaseMcTest {
  private static final RegistryAccess ACCESS = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
  private static final String LARGE_KEY = "tconstruct:test_large";
  /** Long enough that the value cannot be written into a packet at all */
  private static final int UNENCODABLE_LENGTH = 100_000;
  /** Long enough to take the slow path, but still writable */
  private static final int ENCODABLE_LENGTH = 20_000;

  @Test
  void normalToolDataIsSentUntouched() {
    ItemStack source = toolStack();
    ItemStackUtil.getOrCreateTag(source).putString("tconstruct:variant", "tconstruct:amethyst");
    CompoundTag sourceTag = ItemStackUtil.getTag(source).copy();

    ItemStack display = ToolDisplayStack.getDisplayStack(source, ACCESS);

    // rendering reads potion colors, dye colors and trim materials from persistent data, so nothing may be dropped
    assertThat(ItemStackUtil.getTag(display)).isEqualTo(sourceTag);
    assertThat(encode(display)).isGreaterThan(0);
  }

  @Test
  void largeButWritableDataIsSentUntouched() {
    ItemStack source = toolStack();
    persistent(source).putString(LARGE_KEY, "x".repeat(ENCODABLE_LENGTH));

    ItemStack display = ToolDisplayStack.getDisplayStack(source, ACCESS);

    assertThat(encode(display)).isGreaterThan(0);
    assertThat(persistent(display).getString(LARGE_KEY)).hasSize(ENCODABLE_LENGTH);
  }

  @Test
  void unencodableValueIsDroppedFromTheSyncedCopyOnly() {
    ItemStack source = toolStack();
    persistent(source).putString(LARGE_KEY, "x".repeat(UNENCODABLE_LENGTH));

    // the stack the server holds cannot be written into entity metadata at all
    assertThatThrownBy(() -> encode(source)).isInstanceOf(EncoderException.class);

    ItemStack display = ToolDisplayStack.getDisplayStack(source, ACCESS);
    CompoundTag displayTag = ItemStackUtil.getTag(display);
    assertThat(displayTag).isNotNull();
    // everything the item model reads survives, only the value no packet can carry is shortened
    assertThat(displayTag.contains(ToolStack.TAG_MATERIALS, Tag.TAG_LIST)).isTrue();
    assertThat(displayTag.contains(ToolStack.TAG_UPGRADES, Tag.TAG_LIST)).isTrue();
    assertThat(displayTag.contains(ToolStack.TAG_MODIFIERS, Tag.TAG_LIST)).isTrue();
    assertThat(displayTag.getCompound(ToolStack.TAG_PERSISTENT_MOD_DATA).getString(LARGE_KEY).length())
      .isLessThanOrEqualTo(ToolDisplayStack.MAX_NETWORK_STRING);
    assertThat(encode(display)).isGreaterThan(0);
    // the server keeps the complete value, so gameplay is unaffected
    assertThat(persistent(source).getString(LARGE_KEY)).hasSize(UNENCODABLE_LENGTH);
  }

  @Test
  void unencodableListValueKeepsListOrder() {
    ItemStack source = toolStack();
    ListTag materials = new ListTag();
    materials.add(StringTag.valueOf("x".repeat(UNENCODABLE_LENGTH)));
    materials.add(StringTag.valueOf("tconstruct:wood"));
    ItemStackUtil.getOrCreateTag(source).put(ToolStack.TAG_MATERIALS, materials);

    ItemStack display = ToolDisplayStack.getDisplayStack(source, ACCESS);
    ListTag displayMaterials = ItemStackUtil.getTag(display).getList(ToolStack.TAG_MATERIALS, Tag.TAG_STRING);

    // materials are indexed by tool part, so entries must stay in place
    assertThat(displayMaterials.size()).isEqualTo(2);
    assertThat(displayMaterials.getString(0).length()).isLessThanOrEqualTo(ToolDisplayStack.MAX_NETWORK_STRING);
    assertThat(displayMaterials.getString(1)).isEqualTo("tconstruct:wood");
    assertThat(encode(display)).isGreaterThan(0);
  }

  @Test
  void oversizedCustomDataKeepsRenderDataAndSourceIntact() {
    ItemStack source = toolStack();
    persistent(source).putByteArray(LARGE_KEY, new byte[2_097_152]);

    ItemStack display = ToolDisplayStack.getDisplayStack(source, ACCESS);

    assertRoundTrip(display);
    assertThat(display.isEmpty()).isFalse();
    assertThat(ItemStackUtil.getTag(display).contains(ToolStack.TAG_MATERIALS)).isTrue();
    assertThat(persistent(source).getByteArray(LARGE_KEY)).hasSize(2_097_152);
  }

  @Test
  void smallEncodedDataStillRespectsDecodedNbtAllocationQuota() {
    ItemStack source = toolStack();
    ListTag entries = new ListTag();
    for (int i = 0; i < 50_000; i++) {
      entries.add(new CompoundTag());
    }
    persistent(source).put(LARGE_KEY, entries);
    assertThat(encode(source)).isLessThan(100_000);
    assertThatThrownBy(() -> assertRoundTrip(source)).isInstanceOf(RuntimeException.class);

    assertRoundTrip(ToolDisplayStack.getDisplayStack(source, ACCESS));
    assertThat(persistent(source).getList(LARGE_KEY, Tag.TAG_COMPOUND)).hasSize(50_000);
  }

  @Test
  void oversizedNameWithoutCustomDataIsRemovedOnlyFromDisplay() {
    ItemStack source = new ItemStack(Items.STICK);
    Component name = Component.literal("x".repeat(UNENCODABLE_LENGTH));
    source.set(DataComponents.CUSTOM_NAME, name);

    ItemStack display = ToolDisplayStack.getDisplayStack(source, ACCESS);

    assertRoundTrip(display);
    assertThat(display.is(Items.STICK)).isTrue();
    assertThat(display.has(DataComponents.CUSTOM_NAME)).isFalse();
    assertThat(source.get(DataComponents.CUSTOM_NAME)).isEqualTo(name);
  }

  @Test
  void normalNameSurvivesCustomDataSanitization() {
    ItemStack source = toolStack();
    source.set(DataComponents.CUSTOM_NAME, Component.literal("My tool"));
    persistent(source).putString(LARGE_KEY, "x".repeat(UNENCODABLE_LENGTH));

    ItemStack display = ToolDisplayStack.getDisplayStack(source, ACCESS);

    assertRoundTrip(display);
    assertThat(display.get(DataComponents.CUSTOM_NAME)).isEqualTo(source.get(DataComponents.CUSTOM_NAME));
  }

  @Test
  void serverMutationsCannotChangeTheValidatedDisplaySnapshot() {
    ItemStack source = toolStack();
    ItemStack display = ToolDisplayStack.getDisplayStack(source, ACCESS);
    CompoundTag displayTag = ItemStackUtil.getTag(display).copy();

    persistent(source).putString(LARGE_KEY, "x".repeat(UNENCODABLE_LENGTH));

    assertThat(ItemStackUtil.getTag(display)).isEqualTo(displayTag);
    assertRoundTrip(display);
  }

  @Test
  void oversizedRenderDataFallsBackToTheBareItem() {
    ItemStack source = toolStack();
    ItemStackUtil.getOrCreateTag(source).putByteArray(ToolStack.TAG_MATERIALS, new byte[2_097_152]);

    ItemStack display = ToolDisplayStack.getDisplayStack(source, ACCESS);

    // the material list is unencodable, but the item itself is not: dropping the data keeps the projectile visible
    assertThat(display.isEmpty()).isFalse();
    assertThat(display.is(Items.STICK)).isTrue();
    assertThat(ItemStackUtil.getTag(display)).isNull();
    assertRoundTrip(display);
    assertThat(source.isEmpty()).isFalse();
  }

  @Test
  void oversizedModifierDataStillKeepsTheMaterialList() {
    ItemStack source = toolStack();
    ListTag modifiers = new ListTag();
    CompoundTag modifier = new CompoundTag();
    modifier.putByteArray(LARGE_KEY, new byte[2_097_152]);
    modifiers.add(modifier);
    ItemStackUtil.getOrCreateTag(source).put(ToolStack.TAG_MODIFIERS, modifiers);

    ItemStack display = ToolDisplayStack.getDisplayStack(source, ACCESS);

    // the item model cannot resolve a tool without materials, so the material list must survive the trim
    assertRoundTrip(display);
    assertThat(display.isEmpty()).isFalse();
    assertThat(ItemStackUtil.getTag(display).getList(ToolStack.TAG_MATERIALS, Tag.TAG_STRING).getString(0))
      .isEqualTo("test:material");
  }

  private static void assertRoundTrip(ItemStack stack) {
    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), ACCESS);
    try {
      EntityDataSerializers.ITEM_STACK.codec().encode(buffer, stack);
      ItemStack decoded = EntityDataSerializers.ITEM_STACK.codec().decode(buffer);
      assertThat(ItemStack.matches(stack, decoded)).isTrue();
      assertThat(buffer.isReadable()).isFalse();
    } finally {
      buffer.release();
    }
  }

  /** Creates a minimal tool stack carrying the tags the item model reads */
  private static ItemStack toolStack() {
    ItemStack stack = new ItemStack(Items.STICK);
    CompoundTag tag = new CompoundTag();
    ListTag materials = new ListTag();
    materials.add(StringTag.valueOf("test:material"));
    tag.put(ToolStack.TAG_MATERIALS, materials);
    tag.put(ToolStack.TAG_UPGRADES, new ListTag());
    tag.put(ToolStack.TAG_MODIFIERS, new ListTag());
    ItemStackUtil.setTag(stack, tag);
    return stack;
  }

  /** Gets the persistent data of the given stack, creating it if needed */
  private static CompoundTag persistent(ItemStack stack) {
    return ItemStackUtil.getOrCreateTagElement(stack, ToolStack.TAG_PERSISTENT_MOD_DATA);
  }

  /** Writes the stack with the codec used by the set entity data packet, returning the encoded size */
  private static int encode(ItemStack stack) {
    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), ACCESS);
    try {
      EntityDataSerializers.ITEM_STACK.codec().encode(buffer, stack);
      return buffer.readableBytes();
    } finally {
      buffer.release();
    }
  }
}
