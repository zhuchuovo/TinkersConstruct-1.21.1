package slimeknights.tconstruct.library.utils;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Compatibility accessors for Tinkers' legacy compound data stored in the 1.21 custom-data component. */
public final class ItemStackUtil {
  /**
   * Longest string a network packet can carry, capped below the 65535 byte limit of the 16 bit length prefix.
   * Capped at 16384 characters, which cannot exceed 49152 bytes even when every character needs the 3 bytes of the
   * widest single UTF-16 code unit.
   */
  public static final int MAX_NETWORK_STRING = 16384;
  /** Bound allocations while testing a stack against the packet codec, leaving room for the rest of the packet */
  private static final int MAX_NETWORK_BYTES = 2_000_000;
  /** Paths already reported to the log, so one broken stack does not spam a warning per sync */
  private static final Set<String> REPORTED_PATHS = ConcurrentHashMap.newKeySet();

  private ItemStackUtil() {}

  @Nullable
  public static CompoundTag getTag(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    return data == null ? null : data.getUnsafe();
  }

  public static CompoundTag getOrCreateTag(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    if (data == null) {
      data = CustomData.of(new CompoundTag());
      stack.set(DataComponents.CUSTOM_DATA, data);
    }
    return data.getUnsafe();
  }

  public static void setTag(ItemStack stack, @Nullable CompoundTag tag) {
    if (tag == null) {
      stack.remove(DataComponents.CUSTOM_DATA);
    } else {
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
  }

  @Nullable
  public static CompoundTag getTagElement(ItemStack stack, String key) {
    CompoundTag tag = getTag(stack);
    return tag != null && tag.contains(key, Tag.TAG_COMPOUND) ? tag.getCompound(key) : null;
  }

  public static CompoundTag getOrCreateTagElement(ItemStack stack, String key) {
    CompoundTag tag = getOrCreateTag(stack);
    if (!tag.contains(key, Tag.TAG_COMPOUND)) {
      tag.put(key, new CompoundTag());
    }
    return tag.getCompound(key);
  }

  public static void removeTagKey(ItemStack stack, String key) {
    CompoundTag tag = getTag(stack);
    if (tag != null) {
      tag.remove(key);
      if (tag.isEmpty()) {
        stack.remove(DataComponents.CUSTOM_DATA);
      }
    }
  }


  /* Network safety */

  /**
   * Checks the stack can be written into a packet as is. Server side tool data is unbounded, and the packet codec is
   * the same codec the clientbound set-entity-data, set-equipment and container packets use, so a failure here means
   * every client that sees the stack gets disconnected.
   */
  public static boolean canWriteToNetwork(ItemStack stack, RegistryAccess access) {
    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(256, MAX_NETWORK_BYTES), access);
    try {
      EntityDataSerializers.ITEM_STACK.codec().encode(buffer, stack);
      EntityDataSerializers.ITEM_STACK.codec().decode(buffer);
      return !buffer.isReadable();
    } catch (RuntimeException e) {
      return false;
    } finally {
      buffer.release();
    }
  }

  /**
   * Ensures the given stack can be written into a packet, so a client watching it does not get disconnected.
   * <p>
   * Only values no packet can carry are touched: network NBT writes every string with a 16 bit length prefix, so a
   * string above that limit throws {@code UTFDataFormatException}, which the packet encoder turns into an
   * {@code EncoderException} that disconnects the receiving client. Such a string is never legitimate data, so it is
   * truncated, reporting the exact path so the writer can be found. A stack that stays unwritable is left alone, as
   * trimming further would gut data the server still needs.
   *
   * @param stack  Stack about to be handed to the client
   * @param access Registry access used to test the stack with the codec the packet uses
   * @return True if the stack was modified
   */
  public static boolean ensurePacketSafe(ItemStack stack, RegistryAccess access) {
    if (stack.isEmpty() || canWriteToNetwork(stack, access)) {
      return false;
    }
    CompoundTag tag = getTag(stack);
    if (tag == null) {
      // nothing we know how to drop, leave the stack to whatever wrote the unwritable value
      return false;
    }
    // ItemStack.copy shares components, so validate the copy we hand out instead of mutating shared data first
    ItemStack copy = stack.copy();
    stripUnencodableStrings(ItemStackUtil.getOrCreateTag(copy), copy.getItem().toString(), "");
    if (canWriteToNetwork(copy, access)) {
      setTag(stack, ItemStackUtil.getTag(copy));
      return true;
    }
    TConstruct.LOG.error("Tool data for {} cannot be written into a network packet even after trimming its strings; "
      + "clients that see it will be disconnected, find what writes that value", stack.getItem());
    return false;
  }

  /**
   * Truncates any string too long for a packet, so the remaining data can still be sent.
   *
   * @param tag      Tag to strip, modified in place
   * @param item     Item the tag belongs to, used for the log message
   * @param path     Path of the current tag, used for the log message
   * @return Number of values truncated
   */
  public static int stripUnencodableStrings(Tag tag, String item, String path) {
    int count = 0;
    if (tag instanceof CompoundTag compound) {
      // copy the keys first, the loop may replace values
      for (String key : new ArrayList<>(compound.getAllKeys())) {
        Tag value = compound.get(key);
        if (value instanceof StringTag string) {
          String text = string.getAsString();
          if (text.length() > MAX_NETWORK_STRING) {
            report(item, path + key, text.length());
            compound.putString(key, truncate(text));
            count++;
          }
        } else if (value != null) {
          count += stripUnencodableStrings(value, item, path + key + ".");
        }
      }
    } else if (tag instanceof ListTag list) {
      // list elements are replaced instead of removed so indexes, such as the material order, stay intact
      for (int i = 0; i < list.size(); i++) {
        Tag element = list.get(i);
        if (element instanceof StringTag string) {
          String text = string.getAsString();
          if (text.length() > MAX_NETWORK_STRING) {
            report(item, path + i, text.length());
            list.set(i, StringTag.valueOf(truncate(text)));
            count++;
          }
        } else {
          count += stripUnencodableStrings(element, item, path + i + ".");
        }
      }
    }
    return count;
  }

  /** Logs the first time a path holds data a packet cannot write, as that points at whatever writes that value */
  private static void report(String item, String path, int length) {
    if (REPORTED_PATHS.add(item + ' ' + path)) {
      TConstruct.LOG.warn("Tool data for {} holds a {} character string at '{}', too long for a network packet; the copy "
        + "sent to clients is truncated, find what writes that value", item, length, path);
    }
  }

  /** Truncates to {@link #MAX_NETWORK_STRING} characters without splitting a surrogate pair */
  private static String truncate(String value) {
    int end = MAX_NETWORK_STRING;
    if (Character.isHighSurrogate(value.charAt(end - 1))) {
      end--;
    }
    return value.substring(0, end);
  }
}
