package slimeknights.tconstruct.tools.entity;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.utils.ItemStackUtil;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies thrown tool entity metadata cannot contain unencodable server-only item data. */
class ThrownToolTest extends BaseMcTest {
  @Test
  void displayStackDropsUnboundedPersistentDataBeforeEntitySync() {
    ItemStack source = new ItemStack(Items.STICK);
    CompoundTag sourceTag = new CompoundTag();
    ListTag materials = new ListTag();
    materials.add(StringTag.valueOf("test:material"));
    sourceTag.put(ToolStack.TAG_MATERIALS, materials);
    sourceTag.put(ToolStack.TAG_UPGRADES, new ListTag());
    sourceTag.put(ToolStack.TAG_MODIFIERS, new ListTag());

    CompoundTag persistent = new CompoundTag();
    persistent.putString("tconstruct:test_large", "x".repeat(100_000));
    sourceTag.put(ToolStack.TAG_PERSISTENT_MOD_DATA, persistent);
    ItemStackUtil.setTag(source, sourceTag);

    assertThatThrownBy(() -> encode(source))
      .isInstanceOf(EncoderException.class);

    ItemStack display = ThrownTool.getDisplayStack(source);
    CompoundTag displayTag = ItemStackUtil.getTag(display);
    assertThat(displayTag).isNotNull();
    assertThat(displayTag.contains(ToolStack.TAG_MATERIALS)).isTrue();
    assertThat(displayTag.contains(ToolStack.TAG_UPGRADES)).isTrue();
    assertThat(displayTag.contains(ToolStack.TAG_MODIFIERS)).isTrue();
    assertThat(displayTag.contains(ToolStack.TAG_PERSISTENT_MOD_DATA)).isFalse();
    assertThat(ItemStackUtil.getTag(source).getCompound(ToolStack.TAG_PERSISTENT_MOD_DATA).getString("tconstruct:test_large"))
      .hasSize(100_000);

    assertThat(encode(display)).isGreaterThan(0);
  }

  private static int encode(ItemStack stack) {
    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
      Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    try {
      EntityDataSerializers.ITEM_STACK.codec().encode(buffer, stack);
      return buffer.readableBytes();
    } finally {
      buffer.release();
    }
  }
}

