package net.minecraft.world.item.component;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import com.mojang.serialization.MapLike;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

public final class CustomData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final CustomData EMPTY = new CustomData(new CompoundTag());
    public static final Codec<CustomData> CODEC = Codec.withAlternative(CompoundTag.CODEC, TagParser.AS_CODEC)
        .xmap(CustomData::new, p_331996_ -> p_331996_.tag);
    public static final Codec<CustomData> CODEC_WITH_ID = CODEC.validate(
        p_331848_ -> p_331848_.getUnsafe().contains("id", 8) ? DataResult.success(p_331848_) : DataResult.error(() -> "Missing id for entity in: " + p_331848_)
    );
    @Deprecated
    public static final StreamCodec<ByteBuf, CustomData> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(CustomData::new, p_331280_ -> p_331280_.tag);
    private final CompoundTag tag;

    private CustomData(CompoundTag tag) {
        this.tag = tag;
    }

    public static CustomData of(CompoundTag tag) {
        return new CustomData(tag.copy());
    }

    public static Predicate<ItemStack> itemMatcher(DataComponentType<CustomData> componentType, CompoundTag tag) {
        return p_332154_ -> {
            CustomData customdata = p_332154_.getOrDefault(componentType, EMPTY);
            return customdata.matchedBy(tag);
        };
    }

    public boolean matchedBy(CompoundTag tag) {
        return NbtUtils.compareNbt(tag, this.tag, true);
    }

    public static void update(DataComponentType<CustomData> componentType, ItemStack stack, Consumer<CompoundTag> updater) {
        CustomData customdata = stack.getOrDefault(componentType, EMPTY).update(updater);
        if (customdata.tag.isEmpty()) {
            stack.remove(componentType);
        } else {
            stack.set(componentType, customdata);
        }
    }

    public static void set(DataComponentType<CustomData> componentType, ItemStack stack, CompoundTag tag) {
        if (!tag.isEmpty()) {
            stack.set(componentType, of(tag));
        } else {
            stack.remove(componentType);
        }
    }

    public CustomData update(Consumer<CompoundTag> updater) {
        CompoundTag compoundtag = this.tag.copy();
        updater.accept(compoundtag);
        return new CustomData(compoundtag);
    }

    public void loadInto(Entity entity) {
        CompoundTag compoundtag = entity.saveWithoutId(new CompoundTag());
        UUID uuid = entity.getUUID();
        compoundtag.merge(this.tag);
        entity.load(compoundtag);
        entity.setUUID(uuid);
    }

    public boolean loadInto(BlockEntity blockEntity, HolderLookup.Provider levelRegistry) {
        CompoundTag compoundtag = blockEntity.saveCustomOnly(levelRegistry);
        CompoundTag compoundtag1 = compoundtag.copy();
        compoundtag.merge(this.tag);
        if (!compoundtag.equals(compoundtag1)) {
            try {
                blockEntity.loadCustomOnly(compoundtag, levelRegistry);
                blockEntity.setChanged();
                return true;
            } catch (Exception exception1) {
                LOGGER.warn("Failed to apply custom data to block entity at {}", blockEntity.getBlockPos(), exception1);

                try {
                    blockEntity.loadCustomOnly(compoundtag1, levelRegistry);
                } catch (Exception exception) {
                    LOGGER.warn("Failed to rollback block entity at {} after failure", blockEntity.getBlockPos(), exception);
                }
            }
        }

        return false;
    }

    public <T> DataResult<CustomData> update(DynamicOps<Tag> ops, MapEncoder<T> encoder, T value) {
        return encoder.encode(value, ops, ops.mapBuilder()).build(this.tag).map(p_330397_ -> new CustomData((CompoundTag)p_330397_));
    }

    public <T> DataResult<T> read(MapDecoder<T> decoder) {
        return this.read(NbtOps.INSTANCE, decoder);
    }

    public <T> DataResult<T> read(DynamicOps<Tag> ops, MapDecoder<T> decoder) {
        MapLike<Tag> maplike = ops.getMap(this.tag).getOrThrow();
        return decoder.decode(ops, maplike);
    }

    public int size() {
        return this.tag.size();
    }

    public boolean isEmpty() {
        return this.tag.isEmpty();
    }

    public CompoundTag copyTag() {
        return this.tag.copy();
    }

    public boolean contains(String key) {
        return this.tag.contains(key);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        } else {
            return other instanceof CustomData customdata ? this.tag.equals(customdata.tag) : false;
        }
    }

    @Override
    public int hashCode() {
        return this.tag.hashCode();
    }

    @Override
    public String toString() {
        return this.tag.toString();
    }

    @Deprecated
    public CompoundTag getUnsafe() {
        return this.tag;
    }
}
