package net.minecraft.world.item;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class Item implements FeatureElement, ItemLike, net.neoforged.neoforge.common.extensions.IItemExtension {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Map<Block, Item> BY_BLOCK = net.neoforged.neoforge.registries.GameData.getBlockItemMap();
    public static final ResourceLocation BASE_ATTACK_DAMAGE_ID = ResourceLocation.withDefaultNamespace("base_attack_damage");
    public static final ResourceLocation BASE_ATTACK_SPEED_ID = ResourceLocation.withDefaultNamespace("base_attack_speed");
    public static final int DEFAULT_MAX_STACK_SIZE = 64;
    public static final int ABSOLUTE_MAX_STACK_SIZE = 99;
    public static final int MAX_BAR_WIDTH = 13;
    private final Holder.Reference<Item> builtInRegistryHolder = BuiltInRegistries.ITEM.createIntrusiveHolder(this);
    private DataComponentMap components;
    @Nullable
    private final Item craftingRemainingItem;
    @Nullable
    private String descriptionId;
    private final FeatureFlagSet requiredFeatures;

    public static int getId(Item item) {
        return item == null ? 0 : BuiltInRegistries.ITEM.getId(item);
    }

    public static Item byId(int id) {
        return BuiltInRegistries.ITEM.byId(id);
    }

    @Deprecated
    public static Item byBlock(Block block) {
        return BY_BLOCK.getOrDefault(block, Items.AIR);
    }

    public Item(Item.Properties properties) {
        this.components = properties.buildAndValidateComponents();
        this.craftingRemainingItem = properties.craftingRemainingItem;
        this.requiredFeatures = properties.requiredFeatures;
        if (SharedConstants.IS_RUNNING_IN_IDE && false) {
            String s = this.getClass().getSimpleName();
            if (!s.endsWith("Item")) {
                LOGGER.error("Item classes should end with Item and {} doesn't.", s);
            }
        }
        this.canRepair = properties.canRepair;
    }

    @Deprecated
    public Holder.Reference<Item> builtInRegistryHolder() {
        return this.builtInRegistryHolder;
    }

    public DataComponentMap components() {
        return this.components;
    }

    /** @deprecated Neo: do not use, use {@link net.neoforged.neoforge.event.ModifyDefaultComponentsEvent the event} instead */
    @org.jetbrains.annotations.ApiStatus.Internal @Deprecated
    public void modifyDefaultComponentsFrom(net.minecraft.core.component.DataComponentPatch patch) {
        if (!net.neoforged.neoforge.internal.RegistrationEvents.canModifyComponents()) throw new IllegalStateException("Default components cannot be modified now!");
        var builder = DataComponentMap.builder().addAll(components);
        patch.entrySet().forEach(entry -> builder.set((DataComponentType)entry.getKey(), entry.getValue().orElse(null)));
        components = Properties.COMPONENT_INTERNER.intern(Properties.validateComponents(builder.build()));
    }

    public int getDefaultMaxStackSize() {
        return this.components.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
    }

    /**
     * Called as the item is being used by an entity.
     */
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
    }

    /**
 * @deprecated Forge: {@link
 *             net.neoforged.neoforge.common.extensions.IItemExtension#onDestroyed
 *             (ItemEntity, DamageSource) Use damage source sensitive version}
 */
    @Deprecated
    public void onDestroyed(ItemEntity itemEntity) {
    }

    public void verifyComponentsAfterLoad(ItemStack stack) {
    }

    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return true;
    }

    @Override
    public Item asItem() {
        return this;
    }

    /**
     * Called when this item is used when targeting a Block
     */
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    public float getDestroySpeed(ItemStack stack, BlockState state) {
        Tool tool = stack.get(DataComponents.TOOL);
        return tool != null ? tool.getMiningSpeed(state) : 1.0F;
    }

    /**
     * Called to trigger the item's "innate" right click behavior. To handle when this item is used on a Block, see {@link net.minecraft.world.item.Item#useOn(net.minecraft.world.item.context.UseOnContext)}.
     */
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemstack = player.getItemInHand(usedHand);
        FoodProperties foodproperties = itemstack.getFoodProperties(player);
        if (foodproperties != null) {
            if (player.canEat(foodproperties.canAlwaysEat())) {
                player.startUsingItem(usedHand);
                return InteractionResultHolder.consume(itemstack);
            } else {
                return InteractionResultHolder.fail(itemstack);
            }
        } else {
            return InteractionResultHolder.pass(player.getItemInHand(usedHand));
        }
    }

    /**
     * Called when the player finishes using this Item (E.g. finishes eating.). Not called when the player stops using the Item before the action is complete.
     */
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        FoodProperties foodproperties = stack.getFoodProperties(livingEntity);
        return foodproperties != null ? livingEntity.eat(level, stack, foodproperties) : stack;
    }

    public boolean isBarVisible(ItemStack stack) {
        return stack.isDamaged();
    }

    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F - (float)stack.getDamageValue() * 13.0F / (float)this.getMaxDamage(stack));
    }

    public int getBarColor(ItemStack stack) {
        int i = stack.getMaxDamage();
        float stackMaxDamage = this.getMaxDamage(stack);
        float f = Math.max(0.0F, (stackMaxDamage - (float)stack.getDamageValue()) / stackMaxDamage);
        return Mth.hsvToRgb(f / 3.0F, 1.0F, 1.0F);
    }

    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        return false;
    }

    public boolean overrideOtherStackedOnMe(
        ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access
    ) {
        return false;
    }

    public float getAttackDamageBonus(Entity target, float damage, DamageSource damageSource) {
        return 0.0F;
    }

    /**
     * Current implementations of this method in child classes do not use the entry argument beside ev. They just raise the damage on the stack.
     */
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return false;
    }

    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
    }

    /**
     * Called when a {@link net.minecraft.world.level.block.Block} is destroyed using this Item. Return {@code true} to trigger the "Use Item" statistic.
     */
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miningEntity) {
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool == null) {
            return false;
        } else {
            if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F && tool.damagePerBlock() > 0) {
                stack.hurtAndBreak(tool.damagePerBlock(), miningEntity, EquipmentSlot.MAINHAND);
            }

            return true;
        }
    }

    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        Tool tool = stack.get(DataComponents.TOOL);
        return tool != null && tool.isCorrectForDrops(state);
    }

    /**
     * Try interacting with given entity. Return {@code InteractionResult.PASS} if nothing should happen.
     */
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget, InteractionHand usedHand) {
        return InteractionResult.PASS;
    }

    public Component getDescription() {
        return Component.translatable(this.getDescriptionId());
    }

    @Override
    public String toString() {
        return BuiltInRegistries.ITEM.wrapAsHolder(this).getRegisteredName();
    }

    protected String getOrCreateDescriptionId() {
        if (this.descriptionId == null) {
            this.descriptionId = Util.makeDescriptionId("item", BuiltInRegistries.ITEM.getKey(this));
        }

        return this.descriptionId;
    }

    public String getDescriptionId() {
        return this.getOrCreateDescriptionId();
    }

    /**
     * Returns the unlocalized name of this item. This version accepts an ItemStack so different stacks can have different names based on their damage or NBT.
     */
    public String getDescriptionId(ItemStack stack) {
        return this.getDescriptionId();
    }

    @Nullable
    @Deprecated // Use ItemStack sensitive version.
    public final Item getCraftingRemainingItem() {
        return this.craftingRemainingItem;
    }

    @Deprecated // Use ItemStack sensitive version.
    public boolean hasCraftingRemainingItem() {
        return this.craftingRemainingItem != null;
    }

    /**
     * Called each tick as long the item is in a player's inventory. Used by maps to check if it's in a player's hand and update its contents.
     */
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
    }

    /**
     * Called when item is crafted/smelted. Used only by maps so far.
     */
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        this.onCraftedPostProcess(stack, level);
    }

    public void onCraftedPostProcess(ItemStack stack, Level level) {
    }

    public boolean isComplex() {
        return false;
    }

    /**
     * Returns the action that specifies what animation to play when the item is being used.
     */
    public UseAnim getUseAnimation(ItemStack stack) {
        return stack.has(DataComponents.FOOD) ? UseAnim.EAT : UseAnim.NONE;
    }

    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        FoodProperties foodproperties = stack.getFoodProperties(null);
        return foodproperties != null ? foodproperties.eatDurationTicks() : 0;
    }

    /**
     * Called when the player stops using an Item (stops holding the right mouse button).
     */
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
    }

    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
    }

    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.empty();
    }

    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack));
    }

    /**
     * Returns {@code true} if this item has an enchantment glint. By default, this returns {@code stack.isEnchanted()}, but other items can override it (for instance, written books always return true).
     *
     * Note that if you override this method, you generally want to also call the super version (on {@link Item}) to get the glint for enchanted items. Of course, that is unnecessary if the overwritten version always returns true.
     */
    public boolean isFoil(ItemStack stack) {
        return stack.isEnchanted();
    }

    /**
     * Checks isDamagable and if it cannot be stacked
     */
    public boolean isEnchantable(ItemStack stack) {
        return stack.getMaxStackSize() == 1 && stack.has(DataComponents.MAX_DAMAGE);
    }

    public static BlockHitResult getPlayerPOVHitResult(Level level, Player player, ClipContext.Fluid fluidMode) {
        Vec3 vec3 = player.getEyePosition();
        Vec3 vec31 = vec3.add(player.calculateViewVector(player.getXRot(), player.getYRot()).scale(player.blockInteractionRange()));
        return level.clip(new ClipContext(vec3, vec31, ClipContext.Block.OUTLINE, fluidMode, player));
    }

    /** @deprecated Neo: Use ItemStack sensitive version. */
    @Deprecated
    public int getEnchantmentValue() {
        return 0;
    }

    /**
     * Return whether this item is repairable in an anvil.
     */
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return false;
    }

    /**
     * @deprecated Neo: Use {@link Item#getDefaultAttributeModifiers(ItemStack)}
     */
    @Deprecated
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return ItemAttributeModifiers.EMPTY;
    }

    protected final boolean canRepair;

    @Override
    public boolean isRepairable(ItemStack stack) {
        return canRepair && isDamageable(stack);
    }

    /**
     * If this stack's item is a crossbow
     */
    public boolean useOnRelease(ItemStack stack) {
        return stack.getItem() == Items.CROSSBOW;
    }

    public ItemStack getDefaultInstance() {
        return new ItemStack(this);
    }

    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_EAT;
    }

    public SoundEvent getBreakingSound() {
        return SoundEvents.ITEM_BREAK;
    }

    /**
     * @deprecated Neo: call {@link net.neoforged.neoforge.common.extensions.IItemStackExtension#canFitInsideContainerItems()} instead,
     * prefer overriding {@link net.neoforged.neoforge.common.extensions.IItemExtension#canFitInsideContainerItems(ItemStack)}
     * @implNote It is recommended to still override this to ensure mods calling this instead of the extension still behave safely.
     */
    @Deprecated
    public boolean canFitInsideContainerItems() {
        return true;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    /**
     * Neo: Allowing mods to define client behavior for their Items
     * @deprecated Use {@link net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent} instead
     */
    @Deprecated(forRemoval = true, since = "1.21")
    public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
    }

    public static class Properties implements net.neoforged.neoforge.common.extensions.IItemPropertiesExtensions {
        private static final Interner<DataComponentMap> COMPONENT_INTERNER = Interners.newStrongInterner();
        @Nullable
        private DataComponentMap.Builder components;
        @Nullable
        Item craftingRemainingItem;
        FeatureFlagSet requiredFeatures = FeatureFlags.VANILLA_SET;
        private boolean canRepair = true;

        public Item.Properties food(FoodProperties food) {
            return this.component(DataComponents.FOOD, food);
        }

        public Item.Properties stacksTo(int maxStackSize) {
            return this.component(DataComponents.MAX_STACK_SIZE, maxStackSize);
        }

        public Item.Properties durability(int maxDamage) {
            this.component(DataComponents.MAX_DAMAGE, maxDamage);
            this.component(DataComponents.MAX_STACK_SIZE, 1);
            this.component(DataComponents.DAMAGE, 0);
            return this;
        }

        public Item.Properties craftRemainder(Item craftingRemainingItem) {
            this.craftingRemainingItem = craftingRemainingItem;
            return this;
        }

        public Item.Properties rarity(Rarity rarity) {
            return this.component(DataComponents.RARITY, rarity);
        }

        public Item.Properties fireResistant() {
            return this.component(DataComponents.FIRE_RESISTANT, Unit.INSTANCE);
        }

        public Item.Properties jukeboxPlayable(ResourceKey<JukeboxSong> song) {
            return this.component(DataComponents.JUKEBOX_PLAYABLE, new JukeboxPlayable(new EitherHolder<>(song), true));
        }

        public Item.Properties setNoRepair() {
            canRepair = false;
            return this;
        }

        public Item.Properties requiredFeatures(FeatureFlag... requiredFeatures) {
            this.requiredFeatures = FeatureFlags.REGISTRY.subset(requiredFeatures);
            return this;
        }

        public <T> Item.Properties component(DataComponentType<T> component, T value) {
            net.neoforged.neoforge.common.CommonHooks.validateComponent(value);
            if (this.components == null) {
                this.components = DataComponentMap.builder().addAll(DataComponents.COMMON_ITEM_COMPONENTS);
            }

            this.components.set(component, value);
            return this;
        }

        public Item.Properties attributes(ItemAttributeModifiers attributes) {
            return this.component(DataComponents.ATTRIBUTE_MODIFIERS, attributes);
        }

        DataComponentMap buildAndValidateComponents() {
            DataComponentMap datacomponentmap = this.buildComponents();
            return validateComponents(datacomponentmap);
        }

        public static DataComponentMap validateComponents(DataComponentMap datacomponentmap) {
            if (datacomponentmap.has(DataComponents.DAMAGE) && datacomponentmap.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1) {
                throw new IllegalStateException("Item cannot have both durability and be stackable");
            } else {
                return datacomponentmap;
            }
        }

        private DataComponentMap buildComponents() {
            return this.components == null ? DataComponents.COMMON_ITEM_COMPONENTS : COMPONENT_INTERNER.intern(this.components.build());
        }
    }

    public interface TooltipContext {
        Item.TooltipContext EMPTY = new Item.TooltipContext() {
            @Nullable
            @Override
            public HolderLookup.Provider registries() {
                return null;
            }

            @Override
            public float tickRate() {
                return 20.0F;
            }

            @Nullable
            @Override
            public MapItemSavedData mapData(MapId p_339618_) {
                return null;
            }
        };

        @Nullable
        HolderLookup.Provider registries();

        float tickRate();

        @Nullable
        MapItemSavedData mapData(MapId mapId);

        /**
         * Neo: Returns the level if it's available.
         */
        @Nullable
        default Level level() {
            return null;
        }

        static Item.TooltipContext of(@Nullable final Level level) {
            return level == null ? EMPTY : new Item.TooltipContext() {
                @Override
                public HolderLookup.Provider registries() {
                    return level.registryAccess();
                }

                @Override
                public float tickRate() {
                    return level.tickRateManager().tickrate();
                }

                @Override
                public MapItemSavedData mapData(MapId p_339628_) {
                    return level.getMapData(p_339628_);
                }

                @Override
                public Level level() {
                    return level;
                }
            };
        }

        static Item.TooltipContext of(final HolderLookup.Provider registries) {
            return new Item.TooltipContext() {
                @Override
                public HolderLookup.Provider registries() {
                    return registries;
                }

                @Override
                public float tickRate() {
                    return 20.0F;
                }

                @Nullable
                @Override
                public MapItemSavedData mapData(MapId p_339679_) {
                    return null;
                }
            };
        }
    }
}
