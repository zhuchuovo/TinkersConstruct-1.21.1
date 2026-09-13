package net.minecraft.world.item.enchantment;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableObject;

public class EnchantmentHelper {
    /**
     * @deprecated Neo: Use {@link #getTagEnchantmentLevel(Holder, ItemStack)} for NBT
     *             enchantments, or {@link ItemStack#getEnchantmentLevel(Holder)} for
     *             gameplay.
     */
    @Deprecated
    public static int getItemEnchantmentLevel(Holder<Enchantment> enchantment, ItemStack stack) {
        // Neo: To reduce patch size, update this method to always check gameplay enchantments, and add getTagEnchantmentLevel as a helper for mods.
        return stack.getEnchantmentLevel(enchantment);
    }

    /**
     * Gets the level of an enchantment from NBT. Use {@link ItemStack#getEnchantmentLevel(Holder)} for gameplay logic.
     */
    public static int getTagEnchantmentLevel(Holder<Enchantment> p_346179_, ItemStack p_44845_) {
        ItemEnchantments itemenchantments = p_44845_.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        return itemenchantments.getLevel(p_346179_);
    }

    public static ItemEnchantments updateEnchantments(ItemStack stack, Consumer<ItemEnchantments.Mutable> updater) {
        DataComponentType<ItemEnchantments> datacomponenttype = getComponentType(stack);
        ItemEnchantments itemenchantments = stack.get(datacomponenttype);
        if (itemenchantments == null) {
            return ItemEnchantments.EMPTY;
        } else {
            ItemEnchantments.Mutable itemenchantments$mutable = new ItemEnchantments.Mutable(itemenchantments);
            updater.accept(itemenchantments$mutable);
            ItemEnchantments itemenchantments1 = itemenchantments$mutable.toImmutable();
            stack.set(datacomponenttype, itemenchantments1);
            return itemenchantments1;
        }
    }

    public static boolean canStoreEnchantments(ItemStack stack) {
        return stack.has(getComponentType(stack));
    }

    public static void setEnchantments(ItemStack stack, ItemEnchantments enchantments) {
        stack.set(getComponentType(stack), enchantments);
    }

    public static ItemEnchantments getEnchantmentsForCrafting(ItemStack stack) {
        return stack.getOrDefault(getComponentType(stack), ItemEnchantments.EMPTY);
    }

    public static DataComponentType<ItemEnchantments> getComponentType(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
    }

    public static boolean hasAnyEnchantments(ItemStack stack) {
        return !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
            || !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    public static int processDurabilityChange(ServerLevel level, ItemStack stack, int damage) {
        MutableFloat mutablefloat = new MutableFloat((float)damage);
        runIterationOnItem(stack, (p_344593_, p_344594_) -> p_344593_.value().modifyDurabilityChange(level, p_344594_, stack, mutablefloat));
        return mutablefloat.intValue();
    }

    public static int processAmmoUse(ServerLevel level, ItemStack weapon, ItemStack ammo, int count) {
        MutableFloat mutablefloat = new MutableFloat((float)count);
        runIterationOnItem(weapon, (p_344545_, p_344546_) -> p_344545_.value().modifyAmmoCount(level, p_344546_, ammo, mutablefloat));
        return mutablefloat.intValue();
    }

    public static int processBlockExperience(ServerLevel level, ItemStack stack, int experience) {
        MutableFloat mutablefloat = new MutableFloat((float)experience);
        runIterationOnItem(stack, (p_344491_, p_344492_) -> p_344491_.value().modifyBlockExperience(level, p_344492_, stack, mutablefloat));
        return mutablefloat.intValue();
    }

    public static int processMobExperience(ServerLevel level, @Nullable Entity killer, Entity mob, int experience) {
        if (killer instanceof LivingEntity livingentity) {
            MutableFloat mutablefloat = new MutableFloat((float)experience);
            runIterationOnEquipment(
                livingentity,
                (p_344574_, p_344575_, p_344576_) -> p_344574_.value()
                        .modifyMobExperience(level, p_344575_, p_344576_.itemStack(), mob, mutablefloat)
            );
            return mutablefloat.intValue();
        } else {
            return experience;
        }
    }

    public static void runIterationOnItem(ItemStack stack, EnchantmentHelper.EnchantmentVisitor visitor) {
        ItemEnchantments itemenchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        // Neo: Respect gameplay-only enchantments when doing iterations
        var lookup = net.neoforged.neoforge.common.CommonHooks.resolveLookup(net.minecraft.core.registries.Registries.ENCHANTMENT);
        if (lookup != null) {
            itemenchantments = stack.getAllEnchantments(lookup);
        }

        for (Entry<Holder<Enchantment>> entry : itemenchantments.entrySet()) {
            visitor.accept(entry.getKey(), entry.getIntValue());
        }
    }

    public static void runIterationOnItem(
        ItemStack stack, EquipmentSlot slot, LivingEntity entity, EnchantmentHelper.EnchantmentInSlotVisitor visitor
    ) {
        if (!stack.isEmpty()) {
            ItemEnchantments itemenchantments = stack.get(DataComponents.ENCHANTMENTS);

            // Neo: Respect gameplay-only enchantments when doing iterations
            itemenchantments = stack.getAllEnchantments(entity.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT));

            if (itemenchantments != null && !itemenchantments.isEmpty()) {
                EnchantedItemInUse enchantediteminuse = new EnchantedItemInUse(stack, slot, entity);

                for (Entry<Holder<Enchantment>> entry : itemenchantments.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    if (holder.value().matchingSlot(slot)) {
                        visitor.accept(holder, entry.getIntValue(), enchantediteminuse);
                    }
                }
            }
        }
    }

    public static void runIterationOnEquipment(LivingEntity entity, EnchantmentHelper.EnchantmentInSlotVisitor visitor) {
        for (EquipmentSlot equipmentslot : EquipmentSlot.values()) {
            runIterationOnItem(entity.getItemBySlot(equipmentslot), equipmentslot, entity, visitor);
        }
    }

    public static boolean isImmuneToDamage(ServerLevel level, LivingEntity entity, DamageSource damageSource) {
        MutableBoolean mutableboolean = new MutableBoolean();
        runIterationOnEquipment(
            entity,
            (p_344534_, p_344535_, p_344536_) -> mutableboolean.setValue(
                    mutableboolean.isTrue() || p_344534_.value().isImmuneToDamage(level, p_344535_, entity, damageSource)
                )
        );
        return mutableboolean.isTrue();
    }

    public static float getDamageProtection(ServerLevel level, LivingEntity entity, DamageSource damageSource) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);
        runIterationOnEquipment(
            entity,
            (p_344604_, p_344605_, p_344606_) -> p_344604_.value()
                    .modifyDamageProtection(level, p_344605_, p_344606_.itemStack(), entity, damageSource, mutablefloat)
        );
        return mutablefloat.floatValue();
    }

    public static float modifyDamage(ServerLevel level, ItemStack tool, Entity entity, DamageSource damageSource, float damage) {
        MutableFloat mutablefloat = new MutableFloat(damage);
        runIterationOnItem(
            tool, (p_344525_, p_344526_) -> p_344525_.value().modifyDamage(level, p_344526_, tool, entity, damageSource, mutablefloat)
        );
        return mutablefloat.floatValue();
    }

    public static float modifyFallBasedDamage(ServerLevel level, ItemStack tool, Entity enity, DamageSource damageSource, float fallBasedDamage) {
        MutableFloat mutablefloat = new MutableFloat(fallBasedDamage);
        runIterationOnItem(
            tool, (p_344552_, p_344553_) -> p_344552_.value().modifyFallBasedDamage(level, p_344553_, tool, enity, damageSource, mutablefloat)
        );
        return mutablefloat.floatValue();
    }

    public static float modifyArmorEffectiveness(ServerLevel level, ItemStack tool, Entity entity, DamageSource damageSource, float armorEffectiveness) {
        MutableFloat mutablefloat = new MutableFloat(armorEffectiveness);
        runIterationOnItem(
            tool, (p_344468_, p_344469_) -> p_344468_.value().modifyArmorEffectivness(level, p_344469_, tool, entity, damageSource, mutablefloat)
        );
        return mutablefloat.floatValue();
    }

    public static float modifyKnockback(ServerLevel level, ItemStack tool, Entity entity, DamageSource damageSource, float knockback) {
        MutableFloat mutablefloat = new MutableFloat(knockback);
        runIterationOnItem(
            tool, (p_344446_, p_344447_) -> p_344446_.value().modifyKnockback(level, p_344447_, tool, entity, damageSource, mutablefloat)
        );
        return mutablefloat.floatValue();
    }

    public static void doPostAttackEffects(ServerLevel level, Entity entity, DamageSource damageSource) {
        if (damageSource.getEntity() instanceof LivingEntity livingentity) {
            doPostAttackEffectsWithItemSource(level, entity, damageSource, livingentity.getWeaponItem());
        } else {
            doPostAttackEffectsWithItemSource(level, entity, damageSource, null);
        }
    }

    public static void doPostAttackEffectsWithItemSource(ServerLevel level, Entity entity, DamageSource damageSource, @Nullable ItemStack itemSource) {
        if (entity instanceof LivingEntity livingentity) {
            runIterationOnEquipment(
                livingentity,
                (p_344427_, p_344428_, p_344429_) -> p_344427_.value()
                        .doPostAttack(level, p_344428_, p_344429_, EnchantmentTarget.VICTIM, entity, damageSource)
            );
        }

        if (itemSource != null && damageSource.getEntity() instanceof LivingEntity livingentity1) {
            runIterationOnItem(
                itemSource,
                EquipmentSlot.MAINHAND,
                livingentity1,
                (p_344557_, p_344558_, p_344559_) -> p_344557_.value()
                        .doPostAttack(level, p_344558_, p_344559_, EnchantmentTarget.ATTACKER, entity, damageSource)
            );
        }
    }

    public static void runLocationChangedEffects(ServerLevel level, LivingEntity entity) {
        runIterationOnEquipment(
            entity, (p_344496_, p_344497_, p_344498_) -> p_344496_.value().runLocationChangedEffects(level, p_344497_, p_344498_, entity)
        );
    }

    public static void runLocationChangedEffects(ServerLevel level, ItemStack stack, LivingEntity entity, EquipmentSlot slot) {
        runIterationOnItem(
            stack,
            slot,
            entity,
            (p_344615_, p_344616_, p_344617_) -> p_344615_.value().runLocationChangedEffects(level, p_344616_, p_344617_, entity)
        );
    }

    public static void stopLocationBasedEffects(LivingEntity entity) {
        runIterationOnEquipment(entity, (p_344643_, p_344644_, p_344645_) -> p_344643_.value().stopLocationBasedEffects(p_344644_, p_344645_, entity));
    }

    public static void stopLocationBasedEffects(ItemStack stack, LivingEntity entity, EquipmentSlot slot) {
        runIterationOnItem(
            stack, slot, entity, (p_344480_, p_344481_, p_344482_) -> p_344480_.value().stopLocationBasedEffects(p_344481_, p_344482_, entity)
        );
    }

    public static void tickEffects(ServerLevel level, LivingEntity entity) {
        runIterationOnEquipment(entity, (p_344432_, p_344433_, p_344434_) -> p_344432_.value().tick(level, p_344433_, p_344434_, entity));
    }

    public static int getEnchantmentLevel(Holder<Enchantment> enchantment, LivingEntity entity) {
        Iterable<ItemStack> iterable = enchantment.value().getSlotItems(entity).values();
        int i = 0;

        for (ItemStack itemstack : iterable) {
            int j = getItemEnchantmentLevel(enchantment, itemstack);
            if (j > i) {
                i = j;
            }
        }

        return i;
    }

    public static int processProjectileCount(ServerLevel level, ItemStack tool, Entity entity, int projectileCount) {
        MutableFloat mutablefloat = new MutableFloat((float)projectileCount);
        runIterationOnItem(
            tool, (p_344634_, p_344635_) -> p_344634_.value().modifyProjectileCount(level, p_344635_, tool, entity, mutablefloat)
        );
        return Math.max(0, mutablefloat.intValue());
    }

    public static float processProjectileSpread(ServerLevel level, ItemStack tool, Entity entity, float projectileSpread) {
        MutableFloat mutablefloat = new MutableFloat(projectileSpread);
        runIterationOnItem(
            tool, (p_344474_, p_344475_) -> p_344474_.value().modifyProjectileSpread(level, p_344475_, tool, entity, mutablefloat)
        );
        return Math.max(0.0F, mutablefloat.floatValue());
    }

    public static int getPiercingCount(ServerLevel level, ItemStack firedFromWeapon, ItemStack pickupItemStack) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);
        runIterationOnItem(firedFromWeapon, (p_344598_, p_344599_) -> p_344598_.value().modifyPiercingCount(level, p_344599_, pickupItemStack, mutablefloat));
        return Math.max(0, mutablefloat.intValue());
    }

    public static void onProjectileSpawned(ServerLevel level, ItemStack firedFromWeapon, AbstractArrow arrow, Consumer<Item> onBreak) {
        LivingEntity livingentity = arrow.getOwner() instanceof LivingEntity livingentity1 ? livingentity1 : null;
        EnchantedItemInUse enchantediteminuse = new EnchantedItemInUse(firedFromWeapon, null, livingentity, onBreak);
        runIterationOnItem(firedFromWeapon, (p_344580_, p_344581_) -> p_344580_.value().onProjectileSpawned(level, p_344581_, enchantediteminuse, arrow));
    }

    public static void onHitBlock(
        ServerLevel level,
        ItemStack stack,
        @Nullable LivingEntity owner,
        Entity entity,
        @Nullable EquipmentSlot slot,
        Vec3 pos,
        BlockState state,
        Consumer<Item> onBreak
    ) {
        EnchantedItemInUse enchantediteminuse = new EnchantedItemInUse(stack, slot, owner, onBreak);
        runIterationOnItem(
            stack, (p_350196_, p_350197_) -> p_350196_.value().onHitBlock(level, p_350197_, enchantediteminuse, entity, pos, state)
        );
    }

    public static int modifyDurabilityToRepairFromXp(ServerLevel level, ItemStack stack, int duabilityToRepairFromXp) {
        MutableFloat mutablefloat = new MutableFloat((float)duabilityToRepairFromXp);
        runIterationOnItem(stack, (p_344540_, p_344541_) -> p_344540_.value().modifyDurabilityToRepairFromXp(level, p_344541_, stack, mutablefloat));
        return Math.max(0, mutablefloat.intValue());
    }

    public static float processEquipmentDropChance(ServerLevel level, LivingEntity entity, DamageSource damageSource, float equipmentDropChance) {
        MutableFloat mutablefloat = new MutableFloat(equipmentDropChance);
        RandomSource randomsource = entity.getRandom();
        runIterationOnEquipment(entity, (p_347320_, p_347321_, p_347322_) -> {
            LootContext lootcontext = Enchantment.damageContext(level, p_347321_, entity, damageSource);
            p_347320_.value().getEffects(EnchantmentEffectComponents.EQUIPMENT_DROPS).forEach(p_347345_ -> {
                if (p_347345_.enchanted() == EnchantmentTarget.VICTIM && p_347345_.affected() == EnchantmentTarget.VICTIM && p_347345_.matches(lootcontext)) {
                    mutablefloat.setValue(p_347345_.effect().process(p_347321_, randomsource, mutablefloat.floatValue()));
                }
            });
        });
        if (damageSource.getEntity() instanceof LivingEntity livingentity) {
            runIterationOnEquipment(
                livingentity,
                (p_347338_, p_347339_, p_347340_) -> {
                    LootContext lootcontext = Enchantment.damageContext(level, p_347339_, entity, damageSource);
                    p_347338_.value()
                        .getEffects(EnchantmentEffectComponents.EQUIPMENT_DROPS)
                        .forEach(
                            p_347327_ -> {
                                if (p_347327_.enchanted() == EnchantmentTarget.ATTACKER
                                    && p_347327_.affected() == EnchantmentTarget.VICTIM
                                    && p_347327_.matches(lootcontext)) {
                                    mutablefloat.setValue(p_347327_.effect().process(p_347339_, randomsource, mutablefloat.floatValue()));
                                }
                            }
                        );
                }
            );
        }

        return mutablefloat.floatValue();
    }

    public static void forEachModifier(ItemStack stack, EquipmentSlotGroup slotGroup, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        runIterationOnItem(stack, (p_344461_, p_344462_) -> p_344461_.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES).forEach(p_350185_ -> {
                if (((Enchantment)p_344461_.value()).definition().slots().contains(slotGroup)) {
                    action.accept(p_350185_.attribute(), p_350185_.getModifier(p_344462_, slotGroup));
                }
            }));
    }

    public static void forEachModifier(ItemStack stack, EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        runIterationOnItem(stack, (p_348409_, p_348410_) -> p_348409_.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES).forEach(p_350180_ -> {
                if (((Enchantment)p_348409_.value()).matchingSlot(slot)) {
                    action.accept(p_350180_.attribute(), p_350180_.getModifier(p_348410_, slot));
                }
            }));
    }

    public static int getFishingLuckBonus(ServerLevel level, ItemStack stack, Entity entity) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);
        runIterationOnItem(
            stack, (p_344564_, p_344565_) -> p_344564_.value().modifyFishingLuckBonus(level, p_344565_, stack, entity, mutablefloat)
        );
        return Math.max(0, mutablefloat.intValue());
    }

    public static float getFishingTimeReduction(ServerLevel level, ItemStack stack, Entity entity) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);
        runIterationOnItem(
            stack, (p_344611_, p_344612_) -> p_344611_.value().modifyFishingTimeReduction(level, p_344612_, stack, entity, mutablefloat)
        );
        return Math.max(0.0F, mutablefloat.floatValue());
    }

    public static int getTridentReturnToOwnerAcceleration(ServerLevel level, ItemStack stack, Entity entity) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);
        runIterationOnItem(
            stack,
            (p_344516_, p_344517_) -> p_344516_.value().modifyTridentReturnToOwnerAcceleration(level, p_344517_, stack, entity, mutablefloat)
        );
        return Math.max(0, mutablefloat.intValue());
    }

    public static float modifyCrossbowChargingTime(ItemStack stack, LivingEntity entity, float crossbowChargingTime) {
        MutableFloat mutablefloat = new MutableFloat(crossbowChargingTime);
        runIterationOnItem(stack, (p_352869_, p_352870_) -> p_352869_.value().modifyCrossbowChargeTime(entity.getRandom(), p_352870_, mutablefloat));
        return Math.max(0.0F, mutablefloat.floatValue());
    }

    public static float getTridentSpinAttackStrength(ItemStack stack, LivingEntity entity) {
        MutableFloat mutablefloat = new MutableFloat(0.0F);
        runIterationOnItem(
            stack, (p_352865_, p_352866_) -> p_352865_.value().modifyTridentSpinAttackStrength(entity.getRandom(), p_352866_, mutablefloat)
        );
        return mutablefloat.floatValue();
    }

    public static boolean hasTag(ItemStack stack, TagKey<Enchantment> tag) {
        ItemEnchantments itemenchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        // Neo: Respect gameplay-only enchantments when enchantment effect tag checks
        var lookup = net.neoforged.neoforge.common.CommonHooks.resolveLookup(net.minecraft.core.registries.Registries.ENCHANTMENT);
        if (lookup != null) {
            itemenchantments = stack.getAllEnchantments(lookup);
        }

        for (Entry<Holder<Enchantment>> entry : itemenchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.is(tag)) {
                return true;
            }
        }

        return false;
    }

    public static boolean has(ItemStack stack, DataComponentType<?> componentType) {
        MutableBoolean mutableboolean = new MutableBoolean(false);
        runIterationOnItem(stack, (p_344620_, p_344621_) -> {
            if (p_344620_.value().effects().has(componentType)) {
                mutableboolean.setTrue();
            }
        });
        return mutableboolean.booleanValue();
    }

    public static <T> Optional<T> pickHighestLevel(ItemStack stack, DataComponentType<List<T>> componentType) {
        Pair<List<T>, Integer> pair = getHighestLevel(stack, componentType);
        if (pair != null) {
            List<T> list = pair.getFirst();
            int i = pair.getSecond();
            return Optional.of(list.get(Math.min(i, list.size()) - 1));
        } else {
            return Optional.empty();
        }
    }

    @Nullable
    public static <T> Pair<T, Integer> getHighestLevel(ItemStack stack, DataComponentType<T> componentType) {
        MutableObject<Pair<T, Integer>> mutableobject = new MutableObject<>();
        runIterationOnItem(stack, (p_344457_, p_344458_) -> {
            if (mutableobject.getValue() == null || mutableobject.getValue().getSecond() < p_344458_) {
                T t = p_344457_.value().effects().get(componentType);
                if (t != null) {
                    mutableobject.setValue(Pair.of(t, p_344458_));
                }
            }
        });
        return mutableobject.getValue();
    }

    public static Optional<EnchantedItemInUse> getRandomItemWith(DataComponentType<?> componentType, LivingEntity entity, Predicate<ItemStack> filter) {
        List<EnchantedItemInUse> list = new ArrayList<>();

        for (EquipmentSlot equipmentslot : EquipmentSlot.values()) {
            ItemStack itemstack = entity.getItemBySlot(equipmentslot);
            if (filter.test(itemstack)) {
                ItemEnchantments itemenchantments = itemstack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

                for (Entry<Holder<Enchantment>> entry : itemenchantments.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    if (holder.value().effects().has(componentType) && holder.value().matchingSlot(equipmentslot)) {
                        list.add(new EnchantedItemInUse(itemstack, equipmentslot, entity));
                    }
                }
            }
        }

        return Util.getRandomSafe(list, entity.getRandom());
    }

    /**
     * Returns the enchantability of itemstack, using a separate calculation for each enchantNum (0, 1 or 2), cutting to the max enchantability power of the table, which is locked to a max of 15.
     */
    public static int getEnchantmentCost(RandomSource random, int enchantNum, int power, ItemStack stack) {
        Item item = stack.getItem();
        int i = stack.getEnchantmentValue();
        if (i <= 0) {
            return 0;
        } else {
            if (power > 15) {
                power = 15;
            }

            int j = random.nextInt(8) + 1 + (power >> 1) + random.nextInt(power + 1);
            if (enchantNum == 0) {
                return Math.max(j / 3, 1);
            } else {
                return enchantNum == 1 ? j * 2 / 3 + 1 : Math.max(j, power * 2);
            }
        }
    }

    public static ItemStack enchantItem(
        RandomSource random, ItemStack stack, int level, RegistryAccess registryAccess, Optional<? extends HolderSet<Enchantment>> possibleEnchantments
    ) {
        return enchantItem(
            random,
            stack,
            level,
            possibleEnchantments.map(HolderSet::stream)
                .orElseGet(() -> registryAccess.registryOrThrow(Registries.ENCHANTMENT).holders().map(p_344499_ -> (Holder<Enchantment>)p_344499_))
        );
    }

    public static ItemStack enchantItem(RandomSource random, ItemStack stack, int level, Stream<Holder<Enchantment>> possibleEnchantments) {
        List<EnchantmentInstance> list = selectEnchantment(random, stack, level, possibleEnchantments);
        if (stack.is(Items.BOOK)) {
            stack = new ItemStack(Items.ENCHANTED_BOOK);
        }

        for (EnchantmentInstance enchantmentinstance : list) {
            stack.enchant(enchantmentinstance.enchantment, enchantmentinstance.level);
        }

        return stack;
    }

    public static List<EnchantmentInstance> selectEnchantment(RandomSource random, ItemStack stack, int level, Stream<Holder<Enchantment>> possibleEnchantments) {
        List<EnchantmentInstance> list = Lists.newArrayList();
        Item item = stack.getItem();
        int i = stack.getEnchantmentValue();
        if (i <= 0) {
            return list;
        } else {
            level += 1 + random.nextInt(i / 4 + 1) + random.nextInt(i / 4 + 1);
            float f = (random.nextFloat() + random.nextFloat() - 1.0F) * 0.15F;
            level = Mth.clamp(Math.round((float)level + (float)level * f), 1, Integer.MAX_VALUE);
            List<EnchantmentInstance> list1 = getAvailableEnchantmentResults(level, stack, possibleEnchantments);
            if (!list1.isEmpty()) {
                WeightedRandom.getRandomItem(random, list1).ifPresent(list::add);

                while (random.nextInt(50) <= level) {
                    if (!list.isEmpty()) {
                        filterCompatibleEnchantments(list1, Util.lastOf(list));
                    }

                    if (list1.isEmpty()) {
                        break;
                    }

                    WeightedRandom.getRandomItem(random, list1).ifPresent(list::add);
                    level /= 2;
                }
            }

            return list;
        }
    }

    public static void filterCompatibleEnchantments(List<EnchantmentInstance> dataList, EnchantmentInstance data) {
        dataList.removeIf(p_344519_ -> !Enchantment.areCompatible(data.enchantment, p_344519_.enchantment));
    }

    public static boolean isEnchantmentCompatible(Collection<Holder<Enchantment>> currentEnchantments, Holder<Enchantment> newEnchantment) {
        for (Holder<Enchantment> holder : currentEnchantments) {
            if (!Enchantment.areCompatible(holder, newEnchantment)) {
                return false;
            }
        }

        return true;
    }

    public static List<EnchantmentInstance> getAvailableEnchantmentResults(int level, ItemStack stack, Stream<Holder<Enchantment>> possibleEnchantments) {
        List<EnchantmentInstance> list = Lists.newArrayList();
        boolean flag = stack.is(Items.BOOK);
        // Neo: Rewrite filter logic to call isPrimaryItemFor instead of hardcoded vanilla logic.
        // The original logic is recorded in the default implementation of IItemExtension#isPrimaryItemFor.
        possibleEnchantments.filter(stack::isPrimaryItemFor).forEach(p_344478_ -> {
            Enchantment enchantment = p_344478_.value();

            for (int i = enchantment.getMaxLevel(); i >= enchantment.getMinLevel(); i--) {
                if (level >= enchantment.getMinCost(i) && level <= enchantment.getMaxCost(i)) {
                    list.add(new EnchantmentInstance((Holder<Enchantment>)p_344478_, i));
                    break;
                }
            }
        });
        return list;
    }

    public static void enchantItemFromProvider(
        ItemStack stack, RegistryAccess registries, ResourceKey<EnchantmentProvider> key, DifficultyInstance difficulty, RandomSource random
    ) {
        EnchantmentProvider enchantmentprovider = registries.registryOrThrow(Registries.ENCHANTMENT_PROVIDER).get(key);
        if (enchantmentprovider != null) {
            updateEnchantments(stack, p_348401_ -> enchantmentprovider.enchant(stack, p_348401_, random, difficulty));
        }
    }

    @FunctionalInterface
    public interface EnchantmentInSlotVisitor {
        void accept(Holder<Enchantment> enchantment, int level, EnchantedItemInUse item);
    }

    @FunctionalInterface
    public interface EnchantmentVisitor {
        void accept(Holder<Enchantment> enchantment, int level);
    }
}
