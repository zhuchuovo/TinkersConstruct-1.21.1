package slimeknights.tconstruct.tools.entity;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.utils.ItemStackUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the tool stacks placed into entity metadata by {@link ToolProjectile}s.
 * <p>
 * Tool data is arbitrary modifier data stored in the item's custom data component, and the server is free to store
 * values far larger than a packet can carry. Network NBT writes every string with a 16 bit length prefix, so a string
 * above 65535 bytes throws a {@code UTFDataFormatException} which turns into
 * {@code EncoderException: Failed to encode packet 'clientbound/minecraft:set_entity_data'} and disconnects the client
 * that was watching the projectile. This helper keeps the synchronized copy writable, checking it with the codec the
 * packet uses for both encoding and decoding, while keeping the complete stack on the server: only the
 * copy sent to clients may lose data, so gameplay, pickup items and damage are unaffected.
 */
final class ToolDisplayStack {
  /**
   * Longest NBT string a packet can write, shared with {@link ItemStackUtil} so both sync paths agree on the limit.
   */
  static final int MAX_NETWORK_STRING = ItemStackUtil.MAX_NETWORK_STRING;

  /** Bound allocations and leave room for the rest of the entity metadata packet. */
  private static final int MAX_NETWORK_BYTES = 2_000_000;

  /** Values read by the item model, used when part of the custom data still cannot be written to a packet. */
  private static final List<String> RENDER_ONLY_TAGS = List.of(
    ToolStack.TAG_MATERIALS, ToolStack.TAG_UPGRADES, ToolStack.TAG_MODIFIERS, ToolStack.TAG_BROKEN, "Damage");
  /** Persistent value holding the modifiers hidden from the tool model. */
  private static final String INVISIBLE_MODIFIERS = TConstruct.getResource("invisible_modifiers").toString();

  /** Message keys already reported to the log, so a projectile with broken data does not spam it once per spawn. */
  private static final Set<String> REPORTED_KEYS = ConcurrentHashMap.newKeySet();

  private ToolDisplayStack() {}

  /**
   * Builds the stack sent to clients for rendering.
   * @param source  Stack to copy for the client
   * @param access  Registry access used to test the copy with the codec used by the packet
   * @return  Stack safe to place into entity metadata
   */
  static ItemStack getDisplayStack(ItemStack source, RegistryAccess access) {
    ItemStack display = source.copyWithCount(1);
    CompoundTag tag = ItemStackUtil.getTag(display);
    if (tag != null) {
      // ItemStack.copy shares components. Later server-side NBT mutations must not bypass this validation.
      ItemStackUtil.setTag(display, tag);
    }
    if (canSync(display, access)) {
      return display;
    }
    // drop only the values the packet cannot write, the rest of the data still reaches the client model
    if (tag != null) {
      CompoundTag sanitized = tag.copy();
      ItemStackUtil.stripUnencodableStrings(sanitized, BuiltInRegistries.ITEM.getKey(source.getItem()).toString(), "");
      ItemStackUtil.setTag(display, sanitized);
    }
    // Names, lore and addon components have their own limits, independent of CUSTOM_DATA.
    List<TypedDataComponent<?>> components = new ArrayList<>();
    display.getComponents().forEach(components::add);
    for (TypedDataComponent<?> component : components) {
      if (component.type() != DataComponents.CUSTOM_DATA && !canSyncComponent(component, access)) {
        reportOnce(component.type().toString(), "Dropping an unsynchronizable component from tool projectile display: " + component.type());
        display.remove(component.type());
      }
    }
    if (canSync(display, access)) {
      return display;
    }
    // data beyond the values above cannot be dropped safely, so send only what the item model needs
    reportOnce("render only", "Tool projectile data cannot be written into a network packet, sending only the data "
      + "the item model reads");
    CompoundTag renderOnly = new CompoundTag();
    if (tag != null) {
      for (String key : RENDER_ONLY_TAGS) {
        copyTag(tag, renderOnly, key);
      }
      CompoundTag persistent = tag.getCompound(ToolStack.TAG_PERSISTENT_MOD_DATA);
      if (persistent.contains(INVISIBLE_MODIFIERS, Tag.TAG_LIST)) {
        CompoundTag renderPersistent = new CompoundTag();
        copyTag(persistent, renderPersistent, INVISIBLE_MODIFIERS);
        renderOnly.put(ToolStack.TAG_PERSISTENT_MOD_DATA, renderPersistent);
      }
    }
    ItemStackUtil.stripUnencodableStrings(renderOnly, BuiltInRegistries.ITEM.getKey(source.getItem()).toString(), "");
    ItemStackUtil.setTag(display, renderOnly.isEmpty() ? null : renderOnly);
    if (canSync(display, access)) {
      return display;
    }
    // the material list is what picks the part textures, so without it a tool item renders as nothing at all
    CompoundTag materialsOnly = new CompoundTag();
    if (tag != null) {
      copyTag(tag, materialsOnly, ToolStack.TAG_MATERIALS);
    }
    ItemStackUtil.stripUnencodableStrings(materialsOnly, BuiltInRegistries.ITEM.getKey(source.getItem()).toString(), "");
    ItemStackUtil.setTag(display, materialsOnly.isEmpty() ? null : materialsOnly);
    if (canSync(display, access)) {
      reportOnce("materials only", "Tool projectile data cannot be written into a network packet, sending only the "
        + "material list so the projectile still renders");
      return display;
    }
    // the item itself is always writeable, dropping its data beats disconnecting the client
    ItemStackUtil.setTag(display, null);
    if (canSync(display, access)) {
      reportOnce("item only", "Tool projectile data cannot be written into a network packet even after trimming it, "
        + "the projectile will render without its tool data");
      return display;
    }
    // nothing left to drop, an empty stack renders as nothing which still beats disconnecting the client
    reportOnce("empty", "The item of a tool projectile cannot be written into a network packet, "
      + "the projectile will render without an item");
    return ItemStack.EMPTY;
  }

  private static boolean canSync(ItemStack stack, RegistryAccess access) {
    return canRoundTrip(EntityDataSerializers.ITEM_STACK.codec(), stack, access);
  }

  private static <T> boolean canSyncComponent(TypedDataComponent<T> component, RegistryAccess access) {
    return canRoundTrip(component.type().streamCodec(), component.value(), access);
  }

  /** Writing alone does not enforce the receiving codec's NBT quota or nesting limit. */
  private static <T> boolean canRoundTrip(StreamCodec<? super RegistryFriendlyByteBuf,T> codec, T value, RegistryAccess access) {
    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(256, MAX_NETWORK_BYTES), access);
    try {
      codec.encode(buffer, value);
      codec.decode(buffer);
      return !buffer.isReadable();
    } catch (RuntimeException e) {
      return false;
    } finally {
      buffer.release();
    }
  }

  /** Copies the given key from source to target if present. */
  private static void copyTag(CompoundTag source, CompoundTag target, String key) {
    Tag value = source.get(key);
    if (value != null) {
      target.put(key, value.copy());
    }
  }

  /** Logs the given message once per session, so a projectile with broken data does not spam the log. */
  private static void reportOnce(String key, String message) {
    if (REPORTED_KEYS.add(key)) {
      TConstruct.LOG.warn(message);
    }
  }
}
