package slimeknights.mantle.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;

/**
 * Helpers to aid in reading and writing of NBT
 */
@SuppressWarnings("unused")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TagHelper {
  /* BlockPos */

  /** @deprecated use {@link net.minecraft.nbt.NbtUtils#writeBlockPos(BlockPos)} */
  @Deprecated(forRemoval = true)
  public static CompoundTag writePos(BlockPos pos) {
    CompoundTag tag = new CompoundTag();
    tag.putInt("X", pos.getX());
    tag.putInt("Y", pos.getY());
    tag.putInt("Z", pos.getZ());
    return tag;
  }

  /** @deprecated use {@link net.minecraft.nbt.NbtUtils#readBlockPos(CompoundTag)} */
  @Nullable
  @Deprecated(forRemoval = true)
  public static BlockPos readPos(CompoundTag tag) {
    if (tag.contains("X", Tag.TAG_ANY_NUMERIC) &&tag.contains("Y", Tag.TAG_ANY_NUMERIC) && tag.contains("Z", Tag.TAG_ANY_NUMERIC)) {
      return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }
    return null;
  }
}
