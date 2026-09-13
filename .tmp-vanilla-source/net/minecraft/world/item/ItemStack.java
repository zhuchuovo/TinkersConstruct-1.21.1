package net.minecraft.world.item;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.NullOps;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;

public final class ItemStack implements DataComponentHolder, net.neoforged.neoforge.common.MutableDataComponentHolder, net.neoforged.neoforge.common.extensions.IItemStackExtension {
    public static final Codec<Holder<Item>> ITEM_NON_AIR_CODEC = BuiltInRegistries.ITEM
        .holderByNameCodec()
        .validate(
            p_330100_ -> p_330100_.is(Items.AIR.builtInRegistryHolder())
                    ? DataResult.error(() -> "Item must not be minecraft:air")
                    : DataResult.success(p_330100_)
        );
    public static final Codec<ItemStack> CODEC = Codec.lazyInitialized(
        () -> RecordCodecBuilder.create(
                p_347288_ -> p_347288_.group(
                            ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
                            ExtraCodecs.intRange(1, 99).fieldOf("count").orElse(1).forGetter(ItemStack::getCount),
                            DataComponentPatch.CODEC
                                .optionalFieldOf("components", DataComponentPatch.EMPTY)
                                .forGetter(p_330103_ -> p_330103_.components.asPatch())
                        )
                        .apply(p_347288_, ItemStack::new)
            )
    );
    public static final Codec<ItemStack> SINGLE_ITEM_CODEC = Codec.lazyInitialized(
        () -> RecordCodecBuilder.create(
                p_337931_ -> p_337931_.group(
                            ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
                            DataComponentPatch.CODEC
                                .optionalFieldOf("components", DataComponentPatch.EMPTY)
                                .forGetter(p_332616_ -> p_332616_.components.asPatch())
                        )
                        .apply(p_337931_, (p_332614_, p_332615_) -> new ItemStack(p_332614_, 1, p_332615_))
            )
    );
    public static final Codec<ItemStack> STRICT_CODEC = CODEC.validate(ItemStack::validateStrict);
    public static final Codec<ItemStack> STRICT_SINGLE_ITEM_CODEC = SINGLE_ITEM_CODEC.validate(ItemStack::validateStrict);
    public static final Codec<ItemStack> OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC)
        .xmap(p_330099_ -> p_330099_.orElse(ItemStack.EMPTY), p_330101_ -> p_330101_.isEmpty() ? Optional.empty() : Optional.of(p_330101_));
    public static final Codec<ItemStack> SIMPLE_ITEM_CODEC = ITEM_NON_AIR_CODEC.xmap(ItemStack::new, ItemStack::getItemHolder);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
        private static final StreamCodec<RegistryFriendlyByteBuf, Holder<Item>> ITEM_STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ITEM);

        public ItemStack decode(RegistryFriendlyByteBuf p_320491_) {
            int i = p_320491_.readVarInt();
            if (i <= 0) {
                return ItemStack.EMPTY;
            } else {
                Holder<Item> holder = ITEM_STREAM_CODEC.decode(p_320491_);
                DataComponentPatch datacomponentpatch = DataComponentPatch.STREAM_CODEC.decode(p_320491_);
                return new ItemStack(holder, i, datacomponentpatch);
            }
        }

        public void encode(RegistryFriendlyByteBuf p_320527_, ItemStack p_320873_) {
            if (p_320873_.isEmpty()) {
                p_320527_.writeVarInt(0);
            } else {
                p_320527_.writeVarInt(p_320873_.getCount());
                ITEM_STREAM_CODEC.encode(p_320527_, p_320873_.getItemHolder());
                DataComponentPatch.STREAM_CODEC.encode(p_320527_, p_320873_.components.asPatch());
            }
        }
    };
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
        public ItemStack decode(RegistryFriendlyByteBuf p_330597_) {
            ItemStack itemstack = ItemStack.OPTIONAL_STREAM_CODEC.decode(p_330597_);
            if (itemstack.isEmpty()) {
                throw new DecoderException("Empty ItemStack not allowed");
            } else {
                return itemstack;
            }
        }

        public void encode(RegistryFriendlyByteBuf p_331762_, ItemStack p_331138_) {
            if (p_331138_.isEmpty()) {
                throw new EncoderException("Empty ItemStack not allowed");
            } else {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(p_331762_, p_331138_);
            }
        }
    };
    public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> OPTIONAL_LIST_STREAM_CODEC = OPTIONAL_STREAM_CODEC.apply(
        ByteBufCodecs.collection(NonNullList::createWithCapacity)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> LIST_STREAM_CODEC = STREAM_CODEC.apply(
        ByteBufCodecs.collection(NonNullList::createWithCapacity)
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ItemStack EMPTY = new ItemStack((Void)null);
    private static final Component DISABLED_ITEM_TOOLTIP = Component.translatable("item.disabled").withStyle(ChatFormatting.RED);
    private int count;
    private int popTime;
    @Deprecated
    @Nullable
    private final Item item;
    final PatchedDataComponentMap components;
    /**
     * The entity the item is attached to, like an Item Frame.
     */
    @Nullable
    private Entity entityRepresentation;

    private static DataResult<ItemStack> validateStrict(ItemStack stack) {
        DataResult<Unit> dataresult = validateComponents(stack.getComponents());
        if (dataresult.isError()) {
            return dataresult.map(p_340777_ -> stack);
        } else {
            return stack.getCount() > stack.getMaxStackSize()
                ? DataResult.error(() -> "Item stack with stack size of " + stack.getCount() + " was larger than maximum: " + stack.getMaxStackSize())
                : DataResult.success(stack);
        }
    }

    public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> validatedStreamCodec(final StreamCodec<RegistryFriendlyByteBuf, ItemStack> codec) {
        return new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
            public ItemStack decode(RegistryFriendlyByteBuf p_341238_) {
                ItemStack itemstack = codec.decode(p_341238_);
                if (!itemstack.isEmpty()) {
                    RegistryOps<Unit> registryops = p_341238_.registryAccess().createSerializationContext(NullOps.INSTANCE);
                    ItemStack.CODEC.encodeStart(registryops, itemstack).getOrThrow(DecoderException::new);
                }

                return itemstack;
            }

            public void encode(RegistryFriendlyByteBuf p_341112_, ItemStack p_341358_) {
                codec.encode(p_341112_, p_341358_);
            }
        };
    }

    public Optional<TooltipComponent> getTooltipImage() {
        return this.getItem().getTooltipImage(this);
    }

    @Override
    public DataComponentMap getComponents() {
        return (DataComponentMap)(!this.isEmpty() ? this.components : DataComponentMap.EMPTY);
    }

    public DataComponentMap getPrototype() {
        return !this.isEmpty() ? this.getItem().components() : DataComponentMap.EMPTY;
    }

    public DataComponentPatch getComponentsPatch() {
        return !this.isEmpty() ? this.components.asPatch() : DataComponentPatch.EMPTY;
    }

    public boolean isComponentsPatchEmpty() {
        return !this.isEmpty() ? this.components.isPatchEmpty() : true;
    }

    public ItemStack(ItemLike item) {
        this(item, 1);
    }

    public ItemStack(Holder<Item> tag) {
        this(tag.value(), 1);
    }

    public ItemStack(Holder<Item> tag, int count, DataComponentPatch components) {
        this(tag.value(), count, PatchedDataComponentMap.fromPatch(tag.value().components(), components));
    }

    public ItemStack(Holder<Item> item, int count) {
        this(item.value(), count);
    }

    public ItemStack(ItemLike item, int count) {
        this(item, count, new PatchedDataComponentMap(item.asItem().components()));
    }

    private ItemStack(ItemLike item, int count, PatchedDataComponentMap components) {
        this.item = item.asItem();
        this.count = count;
        this.components = components;
        this.getItem().verifyComponentsAfterLoad(this);
    }

    private ItemStack(@Nullable Void unused) {
        this.item = null;
        this.components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
    }

    public static DataResult<Unit> validateComponents(DataComponentMap components) {
        if (components.has(DataComponents.MAX_DAMAGE) && components.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1) {
            return DataResult.error(() -> "Item cannot be both damageable and stackable");
        } else {
            ItemContainerContents itemcontainercontents = components.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);

            for (ItemStack itemstack : itemcontainercontents.nonEmptyItems()) {
                int i = itemstack.getCount();
                int j = itemstack.getMaxStackSize();
                if (i > j) {
                    return DataResult.error(() -> "Item stack with count of " + i + " was larger than maximum: " + j);
                }
            }

            return DataResult.success(Unit.INSTANCE);
        }
    }

    public static Optional<ItemStack> parse(HolderLookup.Provider lookupProvider, Tag tag) {
        return CODEC.parse(lookupProvider.createSerializationContext(NbtOps.INSTANCE), tag)
            .resultOrPartial(p_330102_ -> LOGGER.error("Tried to load invalid item: '{}'", p_330102_));
    }

    public static ItemStack parseOptional(HolderLookup.Provider lookupProvider, CompoundTag tag) {
        return tag.isEmpty() ? EMPTY : parse(lookupProvider, tag).orElse(EMPTY);
    }

    public boolean isEmpty() {
        return this == EMPTY || this.item == Items.AIR || this.count <= 0;
    }

    public boolean isItemEnabled(FeatureFlagSet enabledFlags) {
        return this.isEmpty() || this.getItem().isEnabled(enabledFlags);
    }

    /**
     * Splits off a stack of the given amount of this stack and reduces this stack by the amount.
     */
    public ItemStack split(int amount) {
        int i = Math.min(amount, this.getCount());
        ItemStack itemstack = this.copyWithCount(i);
        this.shrink(i);
        return itemstack;
    }

    public ItemStack copyAndClear() {
        if (this.isEmpty()) {
            return EMPTY;
        } else {
            ItemStack itemstack = this.copy();
            this.setCount(0);
            return itemstack;
        }
    }

    public Item getItem() {
        return this.isEmpty() ? Items.AIR : this.item;
    }

    public Holder<Item> getItemHolder() {
        return this.getItem().builtInRegistryHolder();
    }

    public boolean is(TagKey<Item> tag) {
        return this.getItem().builtInRegistryHolder().is(tag);
    }

    public boolean is(Item item) {
        return this.getItem() == item;
    }

    public boolean is(Predicate<Holder<Item>> item) {
        return item.test(this.getItem().builtInRegistryHolder());
    }

    public boolean is(Holder<Item> item) {
        return is(item.value()); // Neo: Fix comparing for custom holders such as DeferredHolders
    }

    public boolean is(HolderSet<Item> item) {
        return item.contains(this.getItemHolder());
    }

    public Stream<TagKey<Item>> getTags() {
        return this.getItem().builtInRegistryHolder().tags();
    }

    public InteractionResult useOn(UseOnContext context) {
        var e = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent(context, net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK));
        if (e.isCanceled()) return e.getCancellationResult().result();
        if (!context.getLevel().isClientSide) return net.neoforged.neoforge.common.CommonHooks.onPlaceItemIntoWorld(context);
        return onItemUse(context, (c) -> getItem().useOn(context));
    }

    public InteractionResult onItemUseFirst(UseOnContext p_41662_) {
        var e = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent(p_41662_, net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent.UsePhase.ITEM_BEFORE_BLOCK));
        if (e.isCanceled()) return e.getCancellationResult().result();
        return onItemUse(p_41662_, (c) -> getItem().onItemUseFirst(this, p_41662_));
    }

    private InteractionResult onItemUse(UseOnContext p_41662_, java.util.function.Function<UseOnContext, InteractionResult> callback) {
        Player player = p_41662_.getPlayer();
        BlockPos blockpos = p_41662_.getClickedPos();
        if (player != null && !player.getAbilities().mayBuild && !this.canPlaceOnBlockInAdventureMode(new BlockInWorld(p_41662_.getLevel(), blockpos, false))) {
            return InteractionResult.PASS;
        } else {
            Item item = this.getItem();
            InteractionResult interactionresult = callback.apply(p_41662_);
            if (player != null && interactionresult.indicateItemUse()) {
                player.awardStat(Stats.ITEM_USED.get(item));
            }

            return interactionresult;
        }
    }

    public float getDestroySpeed(BlockState state) {
        return this.getItem().getDestroySpeed(this, state);
    }

    /**
     * Called when the {@code ItemStack} is equipped and right-clicked. Replaces the {@code ItemStack} with the return value.
     */
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        return this.getItem().use(level, player, usedHand);
    }

    /**
     * Called when the item in use count reach 0, e.g. item food eaten. Return the new ItemStack. Args : world, entity
     */
    public ItemStack finishUsingItem(Level level, LivingEntity livingEntity) {
        return this.getItem().finishUsingItem(this, level, livingEntity);
    }

    public Tag save(HolderLookup.Provider levelRegistryAccess, Tag outputTag) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Cannot encode empty ItemStack");
        } else {
            // Neo: Logs extra information about this ItemStack on error
            return net.neoforged.neoforge.common.util.DataComponentUtil.wrapEncodingExceptions(this, CODEC, levelRegistryAccess, outputTag);
        }
    }

    public Tag save(HolderLookup.Provider levelRegistryAccess) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Cannot encode empty ItemStack");
        } else {
            // Neo: Logs extra information about this ItemStack on error
            return net.neoforged.neoforge.common.util.DataComponentUtil.wrapEncodingExceptions(this, CODEC, levelRegistryAccess);
        }
    }

    public Tag saveOptional(HolderLookup.Provider levelRegistryAccess) {
        return (Tag)(this.isEmpty() ? new CompoundTag() : this.save(levelRegistryAccess, new CompoundTag()));
    }

    public int getMaxStackSize() {
        return this.getItem().getMaxStackSize(this);
    }

    public boolean isStackable() {
        return this.getMaxStackSize() > 1 && (!this.isDamageableItem() || !this.isDamaged());
    }

    public boolean isDamageableItem() {
        return this.has(DataComponents.MAX_DAMAGE) && !this.has(DataComponents.UNBREAKABLE) && this.has(DataComponents.DAMAGE);
    }

    public boolean isDamaged() {
        return this.isDamageableItem() && getItem().isDamaged(this);
    }

    public int getDamageValue() {
        return this.getItem().getDamage(this);
    }

    public void setDamageValue(int damage) {
        this.getItem().setDamage(this, damage);
    }

    public int getMaxDamage() {
        return this.getItem().getMaxDamage(this);
    }

    public void hurtAndBreak(int damage, ServerLevel level, @Nullable ServerPlayer player, Consumer<Item> onBreak) {
        this.hurtAndBreak(damage, level, (LivingEntity) player, onBreak);
    }

    public void hurtAndBreak(int p_220158_, ServerLevel p_346256_, @Nullable LivingEntity p_220160_, Consumer<Item> p_348596_) {
        if (this.isDamageableItem()) {
            p_220158_ = getItem().damageItem(this, p_220158_, p_220160_, p_348596_);
            if (p_220160_ == null || !p_220160_.hasInfiniteMaterials()) {
                if (p_220158_ > 0) {
                    p_220158_ = EnchantmentHelper.processDurabilityChange(p_346256_, this, p_220158_);
                    if (p_220158_ <= 0) {
                        return;
                    }
                }

                if (p_220160_ instanceof ServerPlayer sp && p_220158_ != 0) {
                    CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(sp, this, this.getDamageValue() + p_220158_);
                }

                int i = this.getDamageValue() + p_220158_;
                this.setDamageValue(i);
                if (i >= this.getMaxDamage()) {
                    Item item = this.getItem();
                    this.shrink(1);
                    p_348596_.accept(item);
                }
            }
        }
    }

    public void hurtAndBreak(int amount, LivingEntity entity, EquipmentSlot slot) {
        if (entity.level() instanceof ServerLevel serverlevel) {
            this.hurtAndBreak(
                amount,
                serverlevel,
                entity,
                p_348383_ -> entity.onEquippedItemBroken(p_348383_, slot)
            );
        }
    }

    public ItemStack hurtAndConvertOnBreak(int amount, ItemLike item, LivingEntity entity, EquipmentSlot slot) {
        this.hurtAndBreak(amount, entity, slot);
        if (this.isEmpty()) {
            ItemStack itemstack = this.transmuteCopyIgnoreEmpty(item, 1);
            if (itemstack.isDamageableItem()) {
                itemstack.setDamageValue(0);
            }

            return itemstack;
        } else {
            return this;
        }
    }

    public boolean isBarVisible() {
        return this.getItem().isBarVisible(this);
    }

    public int getBarWidth() {
        return this.getItem().getBarWidth(this);
    }

    public int getBarColor() {
        return this.getItem().getBarColor(this);
    }

    public boolean overrideStackedOnOther(Slot slot, ClickAction action, Player player) {
        return this.getItem().overrideStackedOnOther(this, slot, action, player);
    }

    public boolean overrideOtherStackedOnMe(ItemStack stack, Slot slot, ClickAction action, Player player, SlotAccess access) {
        return this.getItem().overrideOtherStackedOnMe(this, stack, slot, action, player, access);
    }

    public boolean hurtEnemy(LivingEntity target, Player attacker) {
        Item item = this.getItem();
        if (item.hurtEnemy(this, target, attacker)) {
            attacker.awardStat(Stats.ITEM_USED.get(item));
            return true;
        } else {
            return false;
        }
    }

    public void postHurtEnemy(LivingEntity target, Player attacker) {
        this.getItem().postHurtEnemy(this, target, attacker);
    }

    /**
     * Called when a Block is destroyed using this ItemStack
     */
    public void mineBlock(Level level, BlockState state, BlockPos pos, Player player) {
        Item item = this.getItem();
        if (item.mineBlock(this, level, state, pos, player)) {
            player.awardStat(Stats.ITEM_USED.get(item));
        }
    }

    /**
     * Check whether the given Block can be harvested using this ItemStack.
     */
    public boolean isCorrectToolForDrops(BlockState state) {
        return this.getItem().isCorrectToolForDrops(this, state);
    }

    public InteractionResult interactLivingEntity(Player player, LivingEntity entity, InteractionHand usedHand) {
        return this.getItem().interactLivingEntity(this, player, entity, usedHand);
    }

    public ItemStack copy() {
        if (this.isEmpty()) {
            return EMPTY;
        } else {
            ItemStack itemstack = new ItemStack(this.getItem(), this.count, this.components.copy());
            itemstack.setPopTime(this.getPopTime());
            return itemstack;
        }
    }

    public ItemStack copyWithCount(int count) {
        if (this.isEmpty()) {
            return EMPTY;
        } else {
            ItemStack itemstack = this.copy();
            itemstack.setCount(count);
            return itemstack;
        }
    }

    public ItemStack transmuteCopy(ItemLike item) {
        return this.transmuteCopy(item, this.getCount());
    }

    public ItemStack transmuteCopy(ItemLike item, int count) {
        return this.isEmpty() ? EMPTY : this.transmuteCopyIgnoreEmpty(item, count);
    }

    private ItemStack transmuteCopyIgnoreEmpty(ItemLike item, int count) {
        return new ItemStack(item.asItem().builtInRegistryHolder(), count, this.components.asPatch());
    }

    /**
     * Compares both {@code ItemStacks}, returns {@code true} if both {@code ItemStacks} are equal.
     */
    public static boolean matches(ItemStack stack, ItemStack other) {
        if (stack == other) {
            return true;
        } else {
            return stack.getCount() != other.getCount() ? false : isSameItemSameComponents(stack, other);
        }
    }

    @Deprecated
    public static boolean listMatches(List<ItemStack> list, List<ItemStack> other) {
        if (list.size() != other.size()) {
            return false;
        } else {
            for (int i = 0; i < list.size(); i++) {
                if (!matches(list.get(i), other.get(i))) {
                    return false;
                }
            }

            return true;
        }
    }

    public static boolean isSameItem(ItemStack stack, ItemStack other) {
        return stack.is(other.getItem());
    }

    public static boolean isSameItemSameComponents(ItemStack stack, ItemStack other) {
        if (!stack.is(other.getItem())) {
            return false;
        } else {
            return stack.isEmpty() && other.isEmpty() ? true : Objects.equals(stack.components, other.components);
        }
    }

    public static MapCodec<ItemStack> lenientOptionalFieldOf(String fieldName) {
        return CODEC.lenientOptionalFieldOf(fieldName)
            .xmap(p_323389_ -> p_323389_.orElse(EMPTY), p_323388_ -> p_323388_.isEmpty() ? Optional.empty() : Optional.of(p_323388_));
    }

    public static int hashItemAndComponents(@Nullable ItemStack stack) {
        if (stack != null) {
            int i = 31 + stack.getItem().hashCode();
            return 31 * i + stack.getComponents().hashCode();
        } else {
            return 0;
        }
    }

    @Deprecated
    public static int hashStackList(List<ItemStack> list) {
        int i = 0;

        for (ItemStack itemstack : list) {
            i = i * 31 + hashItemAndComponents(itemstack);
        }

        return i;
    }

    public String getDescriptionId() {
        return this.getItem().getDescriptionId(this);
    }

    @Override
    public String toString() {
        return this.getCount() + " " + this.getItem();
    }

    /**
     * Called each tick as long the {@code ItemStack} in in player's inventory. Used to progress the pickup animation and update maps.
     */
    public void inventoryTick(Level level, Entity entity, int inventorySlot, boolean isCurrentItem) {
        if (this.popTime > 0) {
            this.popTime--;
        }

        if (this.getItem() != null) {
            this.getItem().inventoryTick(this, level, entity, inventorySlot, isCurrentItem);
        }
    }

    public void onCraftedBy(Level level, Player player, int amount) {
        player.awardStat(Stats.ITEM_CRAFTED.get(this.getItem()), amount);
        this.getItem().onCraftedBy(this, level, player);
    }

    public void onCraftedBySystem(Level level) {
        this.getItem().onCraftedPostProcess(this, level);
    }

    public int getUseDuration(LivingEntity entity) {
        return this.getItem().getUseDuration(this, entity);
    }

    public UseAnim getUseAnimation() {
        return this.getItem().getUseAnimation(this);
    }

    /**
     * Called when the player releases the use item button.
     */
    public void releaseUsing(Level level, LivingEntity livingEntity, int timeLeft) {
        this.getItem().releaseUsing(this, level, livingEntity, timeLeft);
    }

    public boolean useOnRelease() {
        return this.getItem().useOnRelease(this);
    }

    @Nullable
    public <T> T set(DataComponentType<? super T> component, @Nullable T value) {
        return this.components.set(component, value);
    }

    @Nullable
    public <T, U> T update(DataComponentType<T> component, T defaultValue, U updateValue, BiFunction<T, U, T> updater) {
        return this.set(component, updater.apply(this.getOrDefault(component, defaultValue), updateValue));
    }

    @Nullable
    public <T> T update(DataComponentType<T> component, T defaultValue, UnaryOperator<T> updater) {
        T t = this.getOrDefault(component, defaultValue);
        return this.set(component, updater.apply(t));
    }

    @Nullable
    public <T> T remove(DataComponentType<? extends T> component) {
        return this.components.remove(component);
    }

    public void applyComponentsAndValidate(DataComponentPatch components) {
        DataComponentPatch datacomponentpatch = this.components.asPatch();
        this.components.applyPatch(components);
        Optional<Error<ItemStack>> optional = validateStrict(this).error();
        if (optional.isPresent()) {
            LOGGER.error("Failed to apply component patch '{}' to item: '{}'", components, optional.get().message());
            this.components.restorePatch(datacomponentpatch);
        } else {
            this.getItem().verifyComponentsAfterLoad(this);
        }
    }

    public void applyComponents(DataComponentPatch components) {
        this.components.applyPatch(components);
        this.getItem().verifyComponentsAfterLoad(this);
    }

    public void applyComponents(DataComponentMap components) {
        this.components.setAll(components);
        this.getItem().verifyComponentsAfterLoad(this);
    }

    public Component getHoverName() {
        Component component = this.get(DataComponents.CUSTOM_NAME);
        if (component != null) {
            return component;
        } else {
            Component component1 = this.get(DataComponents.ITEM_NAME);
            return component1 != null ? component1 : this.getItem().getName(this);
        }
    }

    public <T extends TooltipProvider> void addToTooltip(
        DataComponentType<T> component, Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag
    ) {
        T t = (T)this.get(component);
        if (t != null) {
            t.addToTooltip(context, tooltipAdder, tooltipFlag);
        }
    }

    public List<Component> getTooltipLines(Item.TooltipContext tooltipContext, @Nullable Player player, TooltipFlag tooltipFlag) {
        if (!tooltipFlag.isCreative() && this.has(DataComponents.HIDE_TOOLTIP)) {
            return List.of();
        } else {
            List<Component> list = Lists.newArrayList();
            MutableComponent mutablecomponent = Component.empty().append(this.getHoverName()).withStyle(this.getRarity().getStyleModifier());
            if (this.has(DataComponents.CUSTOM_NAME)) {
                mutablecomponent.withStyle(ChatFormatting.ITALIC);
            }

            list.add(mutablecomponent);
            if (!tooltipFlag.isAdvanced() && !this.has(DataComponents.CUSTOM_NAME) && this.is(Items.FILLED_MAP)) {
                MapId mapid = this.get(DataComponents.MAP_ID);
                if (mapid != null) {
                    list.add(MapItem.getTooltipForId(mapid));
                }
            }

            Consumer<Component> consumer = list::add;
            if (!this.has(DataComponents.HIDE_ADDITIONAL_TOOLTIP)) {
                this.getItem().appendHoverText(this, tooltipContext, list, tooltipFlag);
            }

            this.addToTooltip(DataComponents.JUKEBOX_PLAYABLE, tooltipContext, consumer, tooltipFlag);
            this.addToTooltip(DataComponents.TRIM, tooltipContext, consumer, tooltipFlag);
            this.addToTooltip(DataComponents.STORED_ENCHANTMENTS, tooltipContext, consumer, tooltipFlag);
            this.addToTooltip(DataComponents.ENCHANTMENTS, tooltipContext, consumer, tooltipFlag);
            this.addToTooltip(DataComponents.DYED_COLOR, tooltipContext, consumer, tooltipFlag);
            this.addToTooltip(DataComponents.LORE, tooltipContext, consumer, tooltipFlag);
            // Neo: Replace attribute tooltips with custom handling
            net.neoforged.neoforge.common.util.AttributeUtil.addAttributeTooltips(this, consumer,
                    net.neoforged.neoforge.common.util.AttributeTooltipContext.of(player, tooltipContext, tooltipFlag));
            this.addToTooltip(DataComponents.UNBREAKABLE, tooltipContext, consumer, tooltipFlag);
            AdventureModePredicate adventuremodepredicate = this.get(DataComponents.CAN_BREAK);
            if (adventuremodepredicate != null && adventuremodepredicate.showInTooltip()) {
                consumer.accept(CommonComponents.EMPTY);
                consumer.accept(AdventureModePredicate.CAN_BREAK_HEADER);
                adventuremodepredicate.addToTooltip(consumer);
            }

            AdventureModePredicate adventuremodepredicate1 = this.get(DataComponents.CAN_PLACE_ON);
            if (adventuremodepredicate1 != null && adventuremodepredicate1.showInTooltip()) {
                consumer.accept(CommonComponents.EMPTY);
                consumer.accept(AdventureModePredicate.CAN_PLACE_HEADER);
                adventuremodepredicate1.addToTooltip(consumer);
            }

            if (tooltipFlag.isAdvanced()) {
                if (this.isDamaged()) {
                    list.add(Component.translatable("item.durability", this.getMaxDamage() - this.getDamageValue(), this.getMaxDamage()));
                }

                list.add(Component.literal(BuiltInRegistries.ITEM.getKey(this.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
                int i = this.components.size();
                if (i > 0) {
                    list.add(Component.translatable("item.components", i).withStyle(ChatFormatting.DARK_GRAY));
                }
            }

            if (player != null && !this.getItem().isEnabled(player.level().enabledFeatures())) {
                list.add(DISABLED_ITEM_TOOLTIP);
            }

            net.neoforged.neoforge.event.EventHooks.onItemTooltip(this, player, list, tooltipFlag, tooltipContext);
            return list;
        }
    }

    /**
     * @deprecated Neo: Use {@link net.neoforged.neoforge.client.util.TooltipUtil#
     *             addAttributeTooltips}
     */
    @Deprecated
    private void addAttributeTooltips(Consumer<Component> tooltipAdder, @Nullable Player player) {
        ItemAttributeModifiers itemattributemodifiers = this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (itemattributemodifiers.showInTooltip()) {
            for (EquipmentSlotGroup equipmentslotgroup : EquipmentSlotGroup.values()) {
                MutableBoolean mutableboolean = new MutableBoolean(true);
                this.forEachModifier(equipmentslotgroup, (p_348379_, p_348380_) -> {
                    if (mutableboolean.isTrue()) {
                        tooltipAdder.accept(CommonComponents.EMPTY);
                        tooltipAdder.accept(Component.translatable("item.modifiers." + equipmentslotgroup.getSerializedName()).withStyle(ChatFormatting.GRAY));
                        mutableboolean.setFalse();
                    }

                    this.addModifierTooltip(tooltipAdder, player, p_348379_, p_348380_);
                });
            }
        }
    }

    private void addModifierTooltip(Consumer<Component> tooltipAdder, @Nullable Player player, Holder<Attribute> attribute, AttributeModifier modifier) {
        double d0 = modifier.amount();
        boolean flag = false;
        if (player != null) {
            if (modifier.is(Item.BASE_ATTACK_DAMAGE_ID)) {
                d0 += player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
                flag = true;
            } else if (modifier.is(Item.BASE_ATTACK_SPEED_ID)) {
                d0 += player.getAttributeBaseValue(Attributes.ATTACK_SPEED);
                flag = true;
            }
        }

        double d1;
        if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            || modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            d1 = d0 * 100.0;
        } else if (attribute.is(Attributes.KNOCKBACK_RESISTANCE)) {
            d1 = d0 * 10.0;
        } else {
            d1 = d0;
        }

        if (flag) {
            tooltipAdder.accept(
                CommonComponents.space()
                    .append(
                        Component.translatable(
                            "attribute.modifier.equals." + modifier.operation().id(),
                            ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1),
                            Component.translatable(attribute.value().getDescriptionId())
                        )
                    )
                    .withStyle(ChatFormatting.DARK_GREEN)
            );
        } else if (d0 > 0.0) {
            tooltipAdder.accept(
                Component.translatable(
                        "attribute.modifier.plus." + modifier.operation().id(),
                        ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1),
                        Component.translatable(attribute.value().getDescriptionId())
                    )
                    .withStyle(attribute.value().getStyle(true))
            );
        } else if (d0 < 0.0) {
            tooltipAdder.accept(
                Component.translatable(
                        "attribute.modifier.take." + modifier.operation().id(),
                        ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(-d1),
                        Component.translatable(attribute.value().getDescriptionId())
                    )
                    .withStyle(attribute.value().getStyle(false))
            );
        }
    }

    public boolean hasFoil() {
        Boolean obool = this.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        return obool != null ? obool : this.getItem().isFoil(this);
    }

    public Rarity getRarity() {
        Rarity rarity = this.getOrDefault(DataComponents.RARITY, Rarity.COMMON);
        if (!this.isEnchanted()) {
            return rarity;
        } else {
            return switch (rarity) {
                case COMMON, UNCOMMON -> Rarity.RARE;
                case RARE -> Rarity.EPIC;
                default -> rarity;
            };
        }
    }

    public boolean isEnchantable() {
        if (!this.getItem().isEnchantable(this)) {
            return false;
        } else {
            ItemEnchantments itemenchantments = this.get(DataComponents.ENCHANTMENTS);
            return itemenchantments != null && itemenchantments.isEmpty();
        }
    }

    public void enchant(Holder<Enchantment> enchantment, int level) {
        EnchantmentHelper.updateEnchantments(this, p_344404_ -> p_344404_.upgrade(enchantment, level));
    }

    public boolean isEnchanted() {
        return !this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    /**
     * Gets all enchantments from NBT. Use {@link ItemStack#getAllEnchantments} for gameplay logic.
     */
    public ItemEnchantments getTagEnchantments() {
        return getEnchantments();
    }

    /**
     * @deprecated Neo: Use {@link #getTagEnchantments()} for NBT enchantments, or {@link #getAllEnchantments} for gameplay.
     */
    @Deprecated
    public ItemEnchantments getEnchantments() {
        return this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
    }

    public boolean isFramed() {
        return this.entityRepresentation instanceof ItemFrame;
    }

    public void setEntityRepresentation(@Nullable Entity entity) {
        if (!this.isEmpty()) {
            this.entityRepresentation = entity;
        }
    }

    @Nullable
    public ItemFrame getFrame() {
        return this.entityRepresentation instanceof ItemFrame ? (ItemFrame)this.getEntityRepresentation() : null;
    }

    @Nullable
    public Entity getEntityRepresentation() {
        return !this.isEmpty() ? this.entityRepresentation : null;
    }

    public void forEachModifier(EquipmentSlotGroup slotGroup, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        // Neo: Reflect real attribute modifiers when doing iteration
        this.getAttributeModifiers().forEach(slotGroup, action);

        if (false) {
        // Start disabled vanilla code

        ItemAttributeModifiers itemattributemodifiers = this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (!itemattributemodifiers.modifiers().isEmpty()) {
            itemattributemodifiers.forEach(slotGroup, action);
        } else {
            this.getItem().getDefaultAttributeModifiers().forEach(slotGroup, action);
        }

        // end disabled vanilla code
        }

        EnchantmentHelper.forEachModifier(this, slotGroup, action);
    }

    public void forEachModifier(EquipmentSlot equipmentSLot, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        // Neo: Reflect real attribute modifiers when doing iteration
        this.getAttributeModifiers().forEach(equipmentSLot, action);

        if (false) {
        // Start disabled vanilla code

        ItemAttributeModifiers itemattributemodifiers = this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (!itemattributemodifiers.modifiers().isEmpty()) {
            itemattributemodifiers.forEach(equipmentSLot, action);
        } else {
            this.getItem().getDefaultAttributeModifiers().forEach(equipmentSLot, action);
        }

        // end disabled vanilla code
        }

        EnchantmentHelper.forEachModifier(this, equipmentSLot, action);
    }

    public Component getDisplayName() {
        MutableComponent mutablecomponent = Component.empty().append(this.getHoverName());
        if (this.has(DataComponents.CUSTOM_NAME)) {
            mutablecomponent.withStyle(ChatFormatting.ITALIC);
        }

        MutableComponent mutablecomponent1 = ComponentUtils.wrapInSquareBrackets(mutablecomponent);
        if (!this.isEmpty()) {
            mutablecomponent1.withStyle(this.getRarity().getStyleModifier())
                .withStyle(p_220170_ -> p_220170_.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(this))));
        }

        return mutablecomponent1;
    }

    public boolean canPlaceOnBlockInAdventureMode(BlockInWorld block) {
        AdventureModePredicate adventuremodepredicate = this.get(DataComponents.CAN_PLACE_ON);
        return adventuremodepredicate != null && adventuremodepredicate.test(block);
    }

    public boolean canBreakBlockInAdventureMode(BlockInWorld block) {
        AdventureModePredicate adventuremodepredicate = this.get(DataComponents.CAN_BREAK);
        return adventuremodepredicate != null && adventuremodepredicate.test(block);
    }

    public int getPopTime() {
        return this.popTime;
    }

    public void setPopTime(int popTime) {
        this.popTime = popTime;
    }

    public int getCount() {
        return this.isEmpty() ? 0 : this.count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void limitSize(int maxSize) {
        if (!this.isEmpty() && this.getCount() > maxSize) {
            this.setCount(maxSize);
        }
    }

    public void grow(int increment) {
        this.setCount(this.getCount() + increment);
    }

    public void shrink(int decrement) {
        this.grow(-decrement);
    }

    public void consume(int amount, @Nullable LivingEntity entity) {
        if (entity == null || !entity.hasInfiniteMaterials()) {
            this.shrink(amount);
        }
    }

    public ItemStack consumeAndReturn(int amount, @Nullable LivingEntity entity) {
        ItemStack itemstack = this.copyWithCount(amount);
        this.consume(amount, entity);
        return itemstack;
    }

    /**
     * Called as the stack is being used by an entity.
     */
    public void onUseTick(Level level, LivingEntity livingEntity, int count) {
        this.getItem().onUseTick(level, livingEntity, this, count);
    }

    /**
 * @deprecated Forge: Use {@linkplain
 *             net.neoforged.neoforge.common.extensions.IItemStackExtension#
 *             onDestroyed(ItemEntity,
 *             net.minecraft.world.damagesource.DamageSource) damage source
 *             sensitive version}
 */
    @Deprecated
    public void onDestroyed(ItemEntity itemEntity) {
        this.getItem().onDestroyed(itemEntity);
    }

    public SoundEvent getDrinkingSound() {
        return this.getItem().getDrinkingSound();
    }

    public SoundEvent getEatingSound() {
        return this.getItem().getEatingSound();
    }

    public SoundEvent getBreakingSound() {
        return this.getItem().getBreakingSound();
    }

    public boolean canBeHurtBy(DamageSource damageSource) {
        if (!getItem().canBeHurtBy(this, damageSource)) return false;
        return !this.has(DataComponents.FIRE_RESISTANT) || !damageSource.is(DamageTypeTags.IS_FIRE);
    }
}
