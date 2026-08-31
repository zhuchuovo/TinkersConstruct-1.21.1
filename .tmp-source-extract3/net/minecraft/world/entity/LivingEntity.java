package net.minecraft.world.entity;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;

/**
 * @return null or the {@linkplain LivingEntity} it was ignited by
 */
public abstract class LivingEntity extends Entity implements Attackable, net.neoforged.neoforge.common.extensions.ILivingEntityExtension {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_ACTIVE_EFFECTS = "active_effects";
    private static final ResourceLocation SPEED_MODIFIER_POWDER_SNOW_ID = ResourceLocation.withDefaultNamespace("powder_snow");
    private static final ResourceLocation SPRINTING_MODIFIER_ID = ResourceLocation.withDefaultNamespace("sprinting");
    private static final AttributeModifier SPEED_MODIFIER_SPRINTING = new AttributeModifier(
        SPRINTING_MODIFIER_ID, 0.3F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );
    public static final int HAND_SLOTS = 2;
    public static final int ARMOR_SLOTS = 4;
    public static final int EQUIPMENT_SLOT_OFFSET = 98;
    public static final int ARMOR_SLOT_OFFSET = 100;
    public static final int BODY_ARMOR_OFFSET = 105;
    public static final int SWING_DURATION = 6;
    public static final int PLAYER_HURT_EXPERIENCE_TIME = 100;
    private static final int DAMAGE_SOURCE_TIMEOUT = 40;
    public static final double MIN_MOVEMENT_DISTANCE = 0.003;
    public static final double DEFAULT_BASE_GRAVITY = 0.08;
    public static final int DEATH_DURATION = 20;
    private static final int TICKS_PER_ELYTRA_FREE_FALL_EVENT = 10;
    private static final int FREE_FALL_EVENTS_PER_ELYTRA_BREAK = 2;
    public static final int USE_ITEM_INTERVAL = 4;
    public static final float BASE_JUMP_POWER = 0.42F;
    private static final double MAX_LINE_OF_SIGHT_TEST_RANGE = 128.0;
    protected static final int LIVING_ENTITY_FLAG_IS_USING = 1;
    protected static final int LIVING_ENTITY_FLAG_OFF_HAND = 2;
    protected static final int LIVING_ENTITY_FLAG_SPIN_ATTACK = 4;
    protected static final EntityDataAccessor<Byte> DATA_LIVING_ENTITY_FLAGS = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_HEALTH_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<List<ParticleOptions>> DATA_EFFECT_PARTICLES = SynchedEntityData.defineId(
        LivingEntity.class, EntityDataSerializers.PARTICLES
    );
    private static final EntityDataAccessor<Boolean> DATA_EFFECT_AMBIENCE_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ARROW_COUNT_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STINGER_COUNT_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> SLEEPING_POS_ID = SynchedEntityData.defineId(
        LivingEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS
    );
    private static final int PARTICLE_FREQUENCY_WHEN_INVISIBLE = 15;
    protected static final EntityDimensions SLEEPING_DIMENSIONS = EntityDimensions.fixed(0.2F, 0.2F).withEyeHeight(0.2F);
    public static final float EXTRA_RENDER_CULLING_SIZE_WITH_BIG_HAT = 0.5F;
    public static final float DEFAULT_BABY_SCALE = 0.5F;
    private static final float ITEM_USE_EFFECT_START_FRACTION = 0.21875F;
    public static final String ATTRIBUTES_FIELD = "attributes";
    private final AttributeMap attributes;
    private final CombatTracker combatTracker = new CombatTracker(this);
    private final Map<Holder<MobEffect>, MobEffectInstance> activeEffects = Maps.newHashMap();
    private final NonNullList<ItemStack> lastHandItemStacks = NonNullList.withSize(2, ItemStack.EMPTY);
    private final NonNullList<ItemStack> lastArmorItemStacks = NonNullList.withSize(4, ItemStack.EMPTY);
    private ItemStack lastBodyItemStack = ItemStack.EMPTY;
    public boolean swinging;
    private boolean discardFriction = false;
    public InteractionHand swingingArm;
    public int swingTime;
    public int removeArrowTime;
    public int removeStingerTime;
    public int hurtTime;
    public int hurtDuration;
    public int deathTime;
    public float oAttackAnim;
    public float attackAnim;
    protected int attackStrengthTicker;
    public final WalkAnimationState walkAnimation = new WalkAnimationState();
    public final int invulnerableDuration = 20;
    public final float timeOffs;
    public final float rotA;
    public float yBodyRot;
    public float yBodyRotO;
    public float yHeadRot;
    public float yHeadRotO;
    @Nullable
    protected Player lastHurtByPlayer;
    protected int lastHurtByPlayerTime;
    protected boolean dead;
    protected int noActionTime;
    protected float oRun;
    protected float run;
    protected float animStep;
    protected float animStepO;
    protected float rotOffs;
    protected int deathScore;
    /**
     * Damage taken in the last hit. Mobs are resistant to damage less than this for a short time after taking damage.
     */
    protected float lastHurt;
    protected boolean jumping;
    public float xxa;
    public float yya;
    public float zza;
    protected int lerpSteps;
    protected double lerpX;
    protected double lerpY;
    protected double lerpZ;
    protected double lerpYRot;
    protected double lerpXRot;
    protected double lerpYHeadRot;
    protected int lerpHeadSteps;
    private boolean effectsDirty = true;
    @Nullable
    private LivingEntity lastHurtByMob;
    private int lastHurtByMobTimestamp;
    @Nullable
    private LivingEntity lastHurtMob;
    /**
     * Holds the value of ticksExisted when setLastAttacker was last called.
     */
    private int lastHurtMobTimestamp;
    private float speed;
    private int noJumpDelay;
    private float absorptionAmount;
    protected ItemStack useItem = ItemStack.EMPTY;
    protected int useItemRemaining;
    protected int fallFlyTicks;
    public BlockPos lastPos;
    private Optional<BlockPos> lastClimbablePos = Optional.empty();
    @Nullable
    private DamageSource lastDamageSource;
    private long lastDamageStamp;
    protected int autoSpinAttackTicks;
    protected float autoSpinAttackDmg;
    @Nullable
    protected ItemStack autoSpinAttackItemStack;
    private float swimAmount;
    private float swimAmountO;
    protected Brain<?> brain;
    private boolean skipDropExperience;
    private final Reference2ObjectMap<Enchantment, Set<EnchantmentLocationBasedEffect>> activeLocationDependentEnchantments = new Reference2ObjectArrayMap<>();
    protected float appliedScale = 1.0F;
    /**
     * This field stores information about damage dealt to this entity.
     * a new {@link net.neoforged.neoforge.common.damagesource.DamageContainer} is instantiated
     * via {@link #hurt(DamageSource, float)} after invulnerability checks, and is removed from
     * the stack before the method's return.
    **/
    @Nullable
    protected java.util.Stack<net.neoforged.neoforge.common.damagesource.DamageContainer> damageContainers = new java.util.Stack<>();

    protected LivingEntity(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
        this.attributes = new AttributeMap(DefaultAttributes.getSupplier(entityType));
        this.setHealth(this.getMaxHealth());
        this.blocksBuilding = true;
        this.rotA = (float)((Math.random() + 1.0) * 0.01F);
        this.reapplyPosition();
        this.timeOffs = (float)Math.random() * 12398.0F;
        this.setYRot((float)(Math.random() * (float) (Math.PI * 2)));
        this.yHeadRot = this.getYRot();
        NbtOps nbtops = NbtOps.INSTANCE;
        this.brain = this.makeBrain(new Dynamic<>(nbtops, nbtops.createMap(ImmutableMap.of(nbtops.createString("memories"), nbtops.emptyMap()))));
    }

    public Brain<?> getBrain() {
        return this.brain;
    }

    protected Brain.Provider<?> brainProvider() {
        return Brain.provider(ImmutableList.of(), ImmutableList.of());
    }

    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return this.brainProvider().makeBrain(dynamic);
    }

    @Override
    public void kill() {
        this.hurt(this.damageSources().genericKill(), Float.MAX_VALUE);
    }

    public boolean canAttackType(EntityType<?> entityType) {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIVING_ENTITY_FLAGS, (byte)0);
        builder.define(DATA_EFFECT_PARTICLES, List.of());
        builder.define(DATA_EFFECT_AMBIENCE_ID, false);
        builder.define(DATA_ARROW_COUNT_ID, 0);
        builder.define(DATA_STINGER_COUNT_ID, 0);
        builder.define(DATA_HEALTH_ID, 1.0F);
        builder.define(SLEEPING_POS_ID, Optional.empty());
    }

    public static AttributeSupplier.Builder createLivingAttributes() {
        return AttributeSupplier.builder()
            .add(Attributes.MAX_HEALTH)
            .add(Attributes.KNOCKBACK_RESISTANCE)
            .add(Attributes.MOVEMENT_SPEED)
            .add(Attributes.ARMOR)
            .add(Attributes.ARMOR_TOUGHNESS)
            .add(Attributes.MAX_ABSORPTION)
            .add(Attributes.STEP_HEIGHT)
            .add(Attributes.SCALE)
            .add(Attributes.GRAVITY)
            .add(Attributes.SAFE_FALL_DISTANCE)
            .add(Attributes.FALL_DAMAGE_MULTIPLIER)
            .add(Attributes.JUMP_STRENGTH)
            .add(Attributes.OXYGEN_BONUS)
            .add(Attributes.BURNING_TIME)
            .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)
            .add(Attributes.WATER_MOVEMENT_EFFICIENCY)
            .add(Attributes.MOVEMENT_EFFICIENCY)
            .add(Attributes.ATTACK_KNOCKBACK)
            .add(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED)
            .add(net.neoforged.neoforge.common.NeoForgeMod.NAMETAG_DISTANCE);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (!this.isInWater()) {
            this.updateInWaterStateAndDoWaterCurrentPushing();
        }

        if (this.level() instanceof ServerLevel serverlevel && onGround && this.fallDistance > 0.0F) {
            this.onChangedBlock(serverlevel, pos);
            double d7 = this.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
            if ((double)this.fallDistance > d7 && !state.isAir()) {
                double d0 = this.getX();
                double d1 = this.getY();
                double d2 = this.getZ();
                BlockPos blockpos = this.blockPosition();
                if (pos.getX() != blockpos.getX() || pos.getZ() != blockpos.getZ()) {
                    double d3 = d0 - (double)pos.getX() - 0.5;
                    double d5 = d2 - (double)pos.getZ() - 0.5;
                    double d6 = Math.max(Math.abs(d3), Math.abs(d5));
                    d0 = (double)pos.getX() + 0.5 + d3 / d6 * 0.5;
                    d2 = (double)pos.getZ() + 0.5 + d5 / d6 * 0.5;
                }

                float f = (float)Mth.ceil((double)this.fallDistance - d7);
                double d4 = Math.min((double)(0.2F + f / 15.0F), 2.5);
                int i = (int)(150.0 * d4);
                if (!state.addLandingEffects((ServerLevel) this.level(), pos, state, this, i))
                ((ServerLevel)this.level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state).setPos(pos), d0, d1, d2, i, 0.0, 0.0, 0.0, 0.15F);
            }
        }

        super.checkFallDamage(y, onGround, state, pos);
        if (onGround) {
            this.lastClimbablePos = Optional.empty();
        }
    }

    @Deprecated //FORGE: Use canDrownInFluidType instead
    public final boolean canBreatheUnderwater() {
        return this.getType().is(EntityTypeTags.CAN_BREATHE_UNDER_WATER);
    }

    public float getSwimAmount(float partialTicks) {
        return Mth.lerp(partialTicks, this.swimAmountO, this.swimAmount);
    }

    public boolean hasLandedInLiquid() {
        return this.getDeltaMovement().y() < 1.0E-5F && this.isInLiquid();
    }

    @Override
    public void baseTick() {
        this.oAttackAnim = this.attackAnim;
        if (this.firstTick) {
            this.getSleepingPos().ifPresent(this::setPosToBed);
        }

        if (this.level() instanceof ServerLevel serverlevel) {
            EnchantmentHelper.tickEffects(serverlevel, this);
        }

        super.baseTick();
        this.level().getProfiler().push("livingEntityBaseTick");
        if (this.fireImmune() || this.level().isClientSide) {
            this.clearFire();
        }

        if (this.isAlive()) {
            boolean flag = this instanceof Player;
            if (!this.level().isClientSide) {
                if (this.isInWall()) {
                    this.hurt(this.damageSources().inWall(), 1.0F);
                } else if (flag && !this.level().getWorldBorder().isWithinBounds(this.getBoundingBox())) {
                    double d4 = this.level().getWorldBorder().getDistanceToBorder(this) + this.level().getWorldBorder().getDamageSafeZone();
                    if (d4 < 0.0) {
                        double d0 = this.level().getWorldBorder().getDamagePerBlock();
                        if (d0 > 0.0) {
                            this.hurt(this.damageSources().outOfBorder(), (float)Math.max(1, Mth.floor(-d4 * d0)));
                        }
                    }
                }
            }

            int airSupply = this.getAirSupply();
            net.neoforged.neoforge.common.CommonHooks.onLivingBreathe(this, airSupply - decreaseAirSupply(airSupply), increaseAirSupply(airSupply) - airSupply);
            if (false) // Forge: Handled in ForgeHooks#onLivingBreathe(LivingEntity, int, int)
            if (this.isEyeInFluid(FluidTags.WATER)
                && !this.level().getBlockState(BlockPos.containing(this.getX(), this.getEyeY(), this.getZ())).is(Blocks.BUBBLE_COLUMN)) {
                boolean flag1 = !this.canBreatheUnderwater()
                    && !MobEffectUtil.hasWaterBreathing(this)
                    && (!flag || !((Player)this).getAbilities().invulnerable);
                if (flag1) {
                    this.setAirSupply(this.decreaseAirSupply(this.getAirSupply()));
                    if (this.getAirSupply() == -20) {
                        this.setAirSupply(0);
                        Vec3 vec3 = this.getDeltaMovement();

                        for (int i = 0; i < 8; i++) {
                            double d1 = this.random.nextDouble() - this.random.nextDouble();
                            double d2 = this.random.nextDouble() - this.random.nextDouble();
                            double d3 = this.random.nextDouble() - this.random.nextDouble();
                            this.level().addParticle(ParticleTypes.BUBBLE, this.getX() + d1, this.getY() + d2, this.getZ() + d3, vec3.x, vec3.y, vec3.z);
                        }

                        this.hurt(this.damageSources().drown(), 2.0F);
                    }
                }

                if (!this.level().isClientSide && this.isPassenger() && this.getVehicle() != null && this.getVehicle().dismountsUnderwater()) {
                    this.stopRiding();
                }
            } else if (this.getAirSupply() < this.getMaxAirSupply()) {
                this.setAirSupply(this.increaseAirSupply(this.getAirSupply()));
            }

            if (this.level() instanceof ServerLevel serverlevel1) {
                BlockPos blockpos = this.blockPosition();
                if (!Objects.equal(this.lastPos, blockpos)) {
                    this.lastPos = blockpos;
                    this.onChangedBlock(serverlevel1, blockpos);
                }
            }
        }

        if (this.isAlive() && (this.isInWaterRainOrBubble() || this.isInPowderSnow || this.isInFluidType((fluidType, height) -> this.canFluidExtinguish(fluidType)))) {
            this.extinguishFire();
        }

        if (this.hurtTime > 0) {
            this.hurtTime--;
        }

        if (this.invulnerableTime > 0 && !(this instanceof ServerPlayer)) {
            this.invulnerableTime--;
        }

        if (this.isDeadOrDying() && this.level().shouldTickDeath(this)) {
            this.tickDeath();
        }

        if (this.lastHurtByPlayerTime > 0) {
            this.lastHurtByPlayerTime--;
        } else {
            this.lastHurtByPlayer = null;
        }

        if (this.lastHurtMob != null && !this.lastHurtMob.isAlive()) {
            this.lastHurtMob = null;
        }

        if (this.lastHurtByMob != null) {
            if (!this.lastHurtByMob.isAlive()) {
                this.setLastHurtByMob(null);
            } else if (this.tickCount - this.lastHurtByMobTimestamp > 100) {
                this.setLastHurtByMob(null);
            }
        }

        this.tickEffects();
        this.animStepO = this.animStep;
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.level().getProfiler().pop();
    }

    @Override
    protected float getBlockSpeedFactor() {
        return Mth.lerp((float)this.getAttributeValue(Attributes.MOVEMENT_EFFICIENCY), super.getBlockSpeedFactor(), 1.0F);
    }

    protected void removeFrost() {
        AttributeInstance attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attributeinstance != null) {
            if (attributeinstance.getModifier(SPEED_MODIFIER_POWDER_SNOW_ID) != null) {
                attributeinstance.removeModifier(SPEED_MODIFIER_POWDER_SNOW_ID);
            }
        }
    }

    protected void tryAddFrost() {
        if (!this.getBlockStateOnLegacy().isAir()) {
            int i = this.getTicksFrozen();
            if (i > 0) {
                AttributeInstance attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
                if (attributeinstance == null) {
                    return;
                }

                float f = -0.05F * this.getPercentFrozen();
                attributeinstance.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_POWDER_SNOW_ID, (double)f, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    protected void onChangedBlock(ServerLevel level, BlockPos pos) {
        EnchantmentHelper.runLocationChangedEffects(level, this);
    }

    public boolean isBaby() {
        return false;
    }

    public float getAgeScale() {
        return this.isBaby() ? 0.5F : 1.0F;
    }

    public float getScale() {
        AttributeMap attributemap = this.getAttributes();
        return attributemap == null ? 1.0F : this.sanitizeScale((float)attributemap.getValue(Attributes.SCALE));
    }

    protected float sanitizeScale(float scale) {
        return scale;
    }

    protected boolean isAffectedByFluids() {
        return true;
    }

    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime >= 20 && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte)60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    public boolean shouldDropExperience() {
        return !this.isBaby();
    }

    protected boolean shouldDropLoot() {
        return !this.isBaby();
    }

    /**
     * Decrements the entity's air supply when underwater
     */
    protected int decreaseAirSupply(int currentAir) {
        AttributeInstance attributeinstance = this.getAttribute(Attributes.OXYGEN_BONUS);
        double d0;
        if (attributeinstance != null) {
            d0 = attributeinstance.getValue();
        } else {
            d0 = 0.0;
        }

        return d0 > 0.0 && this.random.nextDouble() >= 1.0 / (d0 + 1.0) ? currentAir : currentAir - 1;
    }

    protected int increaseAirSupply(int currentAir) {
        return Math.min(currentAir + 4, this.getMaxAirSupply());
    }

    public final int getExperienceReward(ServerLevel level, @Nullable Entity killer) {
        return EnchantmentHelper.processMobExperience(level, killer, this, this.getBaseExperienceReward());
    }

    protected int getBaseExperienceReward() {
        return 0;
    }

    protected boolean isAlwaysExperienceDropper() {
        return false;
    }

    @Nullable
    public LivingEntity getLastHurtByMob() {
        return this.lastHurtByMob;
    }

    @Override
    public LivingEntity getLastAttacker() {
        return this.getLastHurtByMob();
    }

    public int getLastHurtByMobTimestamp() {
        return this.lastHurtByMobTimestamp;
    }

    public void setLastHurtByPlayer(@Nullable Player player) {
        this.lastHurtByPlayer = player;
        this.lastHurtByPlayerTime = this.tickCount;
    }

    /**
     * Hint to AI tasks that we were attacked by the passed EntityLivingBase and should retaliate. Is not guaranteed to change our actual active target (for example if we are currently busy attacking someone else)
     */
    public void setLastHurtByMob(@Nullable LivingEntity livingEntity) {
        this.lastHurtByMob = livingEntity;
        this.lastHurtByMobTimestamp = this.tickCount;
    }

    @Nullable
    public LivingEntity getLastHurtMob() {
        return this.lastHurtMob;
    }

    public int getLastHurtMobTimestamp() {
        return this.lastHurtMobTimestamp;
    }

    public void setLastHurtMob(Entity entity) {
        if (entity instanceof LivingEntity) {
            this.lastHurtMob = (LivingEntity)entity;
        } else {
            this.lastHurtMob = null;
        }

        this.lastHurtMobTimestamp = this.tickCount;
    }

    public int getNoActionTime() {
        return this.noActionTime;
    }

    public void setNoActionTime(int idleTime) {
        this.noActionTime = idleTime;
    }

    public boolean shouldDiscardFriction() {
        return this.discardFriction;
    }

    public void setDiscardFriction(boolean discardFriction) {
        this.discardFriction = discardFriction;
    }

    protected boolean doesEmitEquipEvent(EquipmentSlot slot) {
        return true;
    }

    public void onEquipItem(EquipmentSlot slot, ItemStack oldItem, ItemStack newItem) {
        boolean flag = newItem.isEmpty() && oldItem.isEmpty();
        if (!flag && !ItemStack.isSameItemSameComponents(oldItem, newItem) && !this.firstTick) {
            Equipable equipable = Equipable.get(newItem);
            if (!this.level().isClientSide() && !this.isSpectator()) {
                if (!this.isSilent() && equipable != null && equipable.getEquipmentSlot() == slot) {
                    this.level()
                        .playSeededSound(
                            null, this.getX(), this.getY(), this.getZ(), equipable.getEquipSound(), this.getSoundSource(), 1.0F, 1.0F, this.random.nextLong()
                        );
                }

                if (this.doesEmitEquipEvent(slot)) {
                    this.gameEvent(equipable != null ? GameEvent.EQUIP : GameEvent.UNEQUIP);
                }
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            this.triggerOnDeathMobEffects(reason);
        }

        super.remove(reason);
        this.brain.clearMemories();
    }

    protected void triggerOnDeathMobEffects(Entity.RemovalReason removalReason) {
        for (MobEffectInstance mobeffectinstance : this.getActiveEffects()) {
            mobeffectinstance.onMobRemoved(this, removalReason);
        }

        this.activeEffects.clear();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        compound.putFloat("Health", this.getHealth());
        compound.putShort("HurtTime", (short)this.hurtTime);
        compound.putInt("HurtByTimestamp", this.lastHurtByMobTimestamp);
        compound.putShort("DeathTime", (short)this.deathTime);
        compound.putFloat("AbsorptionAmount", this.getAbsorptionAmount());
        compound.put("attributes", this.getAttributes().save());
        if (!this.activeEffects.isEmpty()) {
            ListTag listtag = new ListTag();

            for (MobEffectInstance mobeffectinstance : this.activeEffects.values()) {
                listtag.add(mobeffectinstance.save());
            }

            compound.put("active_effects", listtag);
        }

        compound.putBoolean("FallFlying", this.isFallFlying());
        this.getSleepingPos().ifPresent(p_21099_ -> {
            compound.putInt("SleepingX", p_21099_.getX());
            compound.putInt("SleepingY", p_21099_.getY());
            compound.putInt("SleepingZ", p_21099_.getZ());
        });
        DataResult<Tag> dataresult = this.brain.serializeStart(NbtOps.INSTANCE);
        dataresult.resultOrPartial(LOGGER::error).ifPresent(p_21102_ -> compound.put("Brain", p_21102_));
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        this.internalSetAbsorptionAmount(compound.getFloat("AbsorptionAmount"));
        if (compound.contains("attributes", 9) && this.level() != null && !this.level().isClientSide) {
            this.getAttributes().load(compound.getList("attributes", 10));
        }

        if (compound.contains("active_effects", 9)) {
            ListTag listtag = compound.getList("active_effects", 10);

            for (int i = 0; i < listtag.size(); i++) {
                CompoundTag compoundtag = listtag.getCompound(i);
                MobEffectInstance mobeffectinstance = MobEffectInstance.load(compoundtag);
                if (mobeffectinstance != null) {
                    this.activeEffects.put(mobeffectinstance.getEffect(), mobeffectinstance);
                }
            }
        }

        if (compound.contains("Health", 99)) {
            this.setHealth(compound.getFloat("Health"));
        }

        this.hurtTime = compound.getShort("HurtTime");
        this.deathTime = compound.getShort("DeathTime");
        this.lastHurtByMobTimestamp = compound.getInt("HurtByTimestamp");
        if (compound.contains("Team", 8)) {
            String s = compound.getString("Team");
            Scoreboard scoreboard = this.level().getScoreboard();
            PlayerTeam playerteam = scoreboard.getPlayerTeam(s);
            boolean flag = playerteam != null && scoreboard.addPlayerToTeam(this.getStringUUID(), playerteam);
            if (!flag) {
                LOGGER.warn("Unable to add mob to team \"{}\" (that team probably doesn't exist)", s);
            }
        }

        if (compound.getBoolean("FallFlying")) {
            this.setSharedFlag(7, true);
        }

        if (compound.contains("SleepingX", 99) && compound.contains("SleepingY", 99) && compound.contains("SleepingZ", 99)) {
            BlockPos blockpos = new BlockPos(compound.getInt("SleepingX"), compound.getInt("SleepingY"), compound.getInt("SleepingZ"));
            this.setSleepingPos(blockpos);
            this.entityData.set(DATA_POSE, Pose.SLEEPING);
            if (!this.firstTick) {
                this.setPosToBed(blockpos);
            }
        }

        if (compound.contains("Brain", 10)) {
            this.brain = this.makeBrain(new Dynamic<>(NbtOps.INSTANCE, compound.get("Brain")));
        }
    }

    protected void tickEffects() {
        Iterator<Holder<MobEffect>> iterator = this.activeEffects.keySet().iterator();

        try {
            while (iterator.hasNext()) {
                Holder<MobEffect> holder = iterator.next();
                MobEffectInstance mobeffectinstance = this.activeEffects.get(holder);
                if (!mobeffectinstance.tick(this, () -> this.onEffectUpdated(mobeffectinstance, true, null))) {
                    if (!this.level().isClientSide && !net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.MobEffectEvent.Expired(this, mobeffectinstance)).isCanceled()) {
                        iterator.remove();
                        this.onEffectRemoved(mobeffectinstance);
                    }
                } else if (mobeffectinstance.getDuration() % 600 == 0) {
                    this.onEffectUpdated(mobeffectinstance, false, null);
                }
            }
        } catch (ConcurrentModificationException concurrentmodificationexception) {
        }

        if (this.effectsDirty) {
            if (!this.level().isClientSide) {
                this.updateInvisibilityStatus();
                this.updateGlowingStatus();
            }

            this.effectsDirty = false;
        }

        List<ParticleOptions> list = this.entityData.get(DATA_EFFECT_PARTICLES);
        if (!list.isEmpty()) {
            boolean flag = this.entityData.get(DATA_EFFECT_AMBIENCE_ID);
            int i = this.isInvisible() ? 15 : 4;
            int j = flag ? 5 : 1;
            if (this.random.nextInt(i * j) == 0) {
                this.level().addParticle(Util.getRandom(list, this.random), this.getRandomX(0.5), this.getRandomY(), this.getRandomZ(0.5), 1.0, 1.0, 1.0);
            }
        }
    }

    protected void updateInvisibilityStatus() {
        if (this.activeEffects.isEmpty()) {
            this.removeEffectParticles();
            this.setInvisible(false);
        } else {
            this.setInvisible(this.hasEffect(MobEffects.INVISIBILITY));
            this.updateSynchronizedMobEffectParticles();
        }
    }

    private void updateSynchronizedMobEffectParticles() {
        List<ParticleOptions> list = this.activeEffects
            .values()
            .stream()
            .map(effect -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent(this, effect)))
            .filter(net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent::isVisible)
            .map(net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent::getParticleOptions)
            .toList();
        this.entityData.set(DATA_EFFECT_PARTICLES, list);
        this.entityData.set(DATA_EFFECT_AMBIENCE_ID, areAllEffectsAmbient(this.activeEffects.values()));
    }

    private void updateGlowingStatus() {
        boolean flag = this.isCurrentlyGlowing();
        if (this.getSharedFlag(6) != flag) {
            this.setSharedFlag(6, flag);
        }
    }

    public double getVisibilityPercent(@Nullable Entity lookingEntity) {
        double d0 = 1.0;
        if (this.isDiscrete()) {
            d0 *= 0.8;
        }

        if (this.isInvisible()) {
            float f = this.getArmorCoverPercentage();
            if (f < 0.1F) {
                f = 0.1F;
            }

            d0 *= 0.7 * (double)f;
        }

        if (lookingEntity != null) {
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.HEAD);
            EntityType<?> entitytype = lookingEntity.getType();
            if (entitytype == EntityType.SKELETON && itemstack.is(Items.SKELETON_SKULL)
                || entitytype == EntityType.ZOMBIE && itemstack.is(Items.ZOMBIE_HEAD)
                || entitytype == EntityType.PIGLIN && itemstack.is(Items.PIGLIN_HEAD)
                || entitytype == EntityType.PIGLIN_BRUTE && itemstack.is(Items.PIGLIN_HEAD)
                || entitytype == EntityType.CREEPER && itemstack.is(Items.CREEPER_HEAD)) {
                d0 *= 0.5;
            }
        }

        d0 = net.neoforged.neoforge.common.CommonHooks.getEntityVisibilityMultiplier(this, lookingEntity, d0);
        return d0;
    }

    public boolean canAttack(LivingEntity target) {
        return target instanceof Player && this.level().getDifficulty() == Difficulty.PEACEFUL ? false : target.canBeSeenAsEnemy();
    }

    public boolean canAttack(LivingEntity livingentity, TargetingConditions condition) {
        return condition.test(this, livingentity);
    }

    public boolean canBeSeenAsEnemy() {
        return !this.isInvulnerable() && this.canBeSeenByAnyone();
    }

    public boolean canBeSeenByAnyone() {
        return !this.isSpectator() && this.isAlive();
    }

    /**
     * Returns {@code true} if all the potion effects in the specified collection are ambient.
     */
    public static boolean areAllEffectsAmbient(Collection<MobEffectInstance> potionEffects) {
        for (MobEffectInstance mobeffectinstance : potionEffects) {
            if (mobeffectinstance.isVisible() && !mobeffectinstance.isAmbient()) {
                return false;
            }
        }

        return true;
    }

    protected void removeEffectParticles() {
        this.entityData.set(DATA_EFFECT_PARTICLES, List.of());
    }

    public boolean removeAllEffects() {
        if (this.level().isClientSide) {
            return false;
        } else {
            Iterator<MobEffectInstance> iterator = this.activeEffects.values().iterator();

            boolean flag;
            for (flag = false; iterator.hasNext(); flag = true) {
                MobEffectInstance effect = iterator.next();
                if(net.neoforged.neoforge.event.EventHooks.onEffectRemoved(this, effect, null)) continue;
                this.onEffectRemoved(effect);
                iterator.remove();
            }

            return flag;
        }
    }

    public Collection<MobEffectInstance> getActiveEffects() {
        return this.activeEffects.values();
    }

    public Map<Holder<MobEffect>, MobEffectInstance> getActiveEffectsMap() {
        return this.activeEffects;
    }

    public boolean hasEffect(Holder<MobEffect> effect) {
        return this.activeEffects.containsKey(effect);
    }

    @Nullable
    public MobEffectInstance getEffect(Holder<MobEffect> effect) {
        return this.activeEffects.get(effect);
    }

    public final boolean addEffect(MobEffectInstance effectInstance) {
        return this.addEffect(effectInstance, null);
    }

    public boolean addEffect(MobEffectInstance effectInstance, @Nullable Entity entity) {
        if (!net.neoforged.neoforge.common.CommonHooks.canMobEffectBeApplied(this, effectInstance, entity)) {
            return false;
        } else {
            MobEffectInstance mobeffectinstance = this.activeEffects.get(effectInstance.getEffect());
            boolean flag = false;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added(this, mobeffectinstance, effectInstance, entity));
            if (mobeffectinstance == null) {
                this.activeEffects.put(effectInstance.getEffect(), effectInstance);
                this.onEffectAdded(effectInstance, entity);
                flag = true;
                effectInstance.onEffectAdded(this);
            } else if (mobeffectinstance.update(effectInstance)) {
                this.onEffectUpdated(mobeffectinstance, true, entity);
                flag = true;
            }

            effectInstance.onEffectStarted(this);
            return flag;
        }
    }

    /**
     * Neo: Override-Only. Call via
     * {@link net.neoforged.neoforge.common.CommonHooks#canMobEffectBeApplied(LivingEntity, MobEffectInstance,Entity)}
     *
     * @param effectInstance A mob effect instance
     * @return If the mob effect instance can be applied to this entity
     */
    @Deprecated
    @org.jetbrains.annotations.ApiStatus.OverrideOnly
    public boolean canBeAffected(MobEffectInstance effectInstance) {
        if (this.getType().is(EntityTypeTags.IMMUNE_TO_INFESTED)) {
            return !effectInstance.is(MobEffects.INFESTED);
        } else if (this.getType().is(EntityTypeTags.IMMUNE_TO_OOZING)) {
            return !effectInstance.is(MobEffects.OOZING);
        } else {
            return !this.getType().is(EntityTypeTags.IGNORES_POISON_AND_REGEN)
                ? true
                : !effectInstance.is(MobEffects.REGENERATION) && !effectInstance.is(MobEffects.POISON);
        }
    }

    public void forceAddEffect(MobEffectInstance instance, @Nullable Entity entity) {
        if (net.neoforged.neoforge.common.CommonHooks.canMobEffectBeApplied(this, instance, entity)) {
            MobEffectInstance mobeffectinstance = this.activeEffects.put(instance.getEffect(), instance);
            if (mobeffectinstance == null) {
                this.onEffectAdded(instance, entity);
            } else {
                instance.copyBlendState(mobeffectinstance);
                this.onEffectUpdated(instance, true, entity);
            }
        }
    }

    public boolean isInvertedHealAndHarm() {
        return this.getType().is(EntityTypeTags.INVERTED_HEALING_AND_HARM);
    }

    @Nullable
    public MobEffectInstance removeEffectNoUpdate(Holder<MobEffect> effect) {
        return this.activeEffects.remove(effect);
    }

    public boolean removeEffect(Holder<MobEffect> effect) {
        if (net.neoforged.neoforge.event.EventHooks.onEffectRemoved(this, effect, null)) return false;
        MobEffectInstance mobeffectinstance = this.removeEffectNoUpdate(effect);
        if (mobeffectinstance != null) {
            this.onEffectRemoved(mobeffectinstance);
            return true;
        } else {
            return false;
        }
    }

    protected void onEffectAdded(MobEffectInstance effectInstance, @Nullable Entity entity) {
        this.effectsDirty = true;
        if (!this.level().isClientSide) {
            effectInstance.getEffect().value().addAttributeModifiers(this.getAttributes(), effectInstance.getAmplifier());
            this.sendEffectToPassengers(effectInstance);
        }
    }

    public void sendEffectToPassengers(MobEffectInstance effectInstance) {
        for (Entity entity : this.getPassengers()) {
            if (entity instanceof ServerPlayer serverplayer) {
                serverplayer.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effectInstance, false));
            }
        }
    }

    protected void onEffectUpdated(MobEffectInstance effectInstance, boolean forced, @Nullable Entity entity) {
        this.effectsDirty = true;
        if (forced && !this.level().isClientSide) {
            MobEffect mobeffect = effectInstance.getEffect().value();
            mobeffect.removeAttributeModifiers(this.getAttributes());
            mobeffect.addAttributeModifiers(this.getAttributes(), effectInstance.getAmplifier());
            this.refreshDirtyAttributes();
        }

        if (!this.level().isClientSide) {
            this.sendEffectToPassengers(effectInstance);
        }
    }

    protected void onEffectRemoved(MobEffectInstance effectInstance) {
        this.effectsDirty = true;
        if (!this.level().isClientSide) {
            effectInstance.getEffect().value().removeAttributeModifiers(this.getAttributes());
            this.refreshDirtyAttributes();

            for (Entity entity : this.getPassengers()) {
                if (entity instanceof ServerPlayer serverplayer) {
                    serverplayer.connection.send(new ClientboundRemoveMobEffectPacket(this.getId(), effectInstance.getEffect()));
                }
            }
        }
    }

    private void refreshDirtyAttributes() {
        Set<AttributeInstance> set = this.getAttributes().getAttributesToUpdate();

        for (AttributeInstance attributeinstance : set) {
            this.onAttributeUpdated(attributeinstance.getAttribute());
        }

        set.clear();
    }

    private void onAttributeUpdated(Holder<Attribute> attribute) {
        if (attribute.is(Attributes.MAX_HEALTH)) {
            float f = this.getMaxHealth();
            if (this.getHealth() > f) {
                this.setHealth(f);
            }
        } else if (attribute.is(Attributes.MAX_ABSORPTION)) {
            float f1 = this.getMaxAbsorption();
            if (this.getAbsorptionAmount() > f1) {
                this.setAbsorptionAmount(f1);
            }
        }
    }

    /**
     * Heal living entity (param: amount of half-hearts)
     */
    public void heal(float healAmount) {
        healAmount = net.neoforged.neoforge.event.EventHooks.onLivingHeal(this, healAmount);
        if (healAmount <= 0) return;
        float f = this.getHealth();
        if (f > 0.0F) {
            this.setHealth(f + healAmount);
        }
    }

    public float getHealth() {
        return this.entityData.get(DATA_HEALTH_ID);
    }

    public void setHealth(float health) {
        this.entityData.set(DATA_HEALTH_ID, Mth.clamp(health, 0.0F, this.getMaxHealth()));
    }

    public boolean isDeadOrDying() {
        return this.getHealth() <= 0.0F;
    }

    /**
     * Called when the entity is attacked.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else if (this.level().isClientSide) {
            return false;
        } else if (this.isDeadOrDying()) {
            return false;
        } else if (source.is(DamageTypeTags.IS_FIRE) && this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return false;
        } else {
            this.damageContainers.push(new net.neoforged.neoforge.common.damagesource.DamageContainer(source, amount));
            if (net.neoforged.neoforge.common.CommonHooks.onEntityIncomingDamage(this, this.damageContainers.peek())) return false;
            if (this.isSleeping() && !this.level().isClientSide) {
                this.stopSleeping();
            }

            this.noActionTime = 0;
            amount = this.damageContainers.peek().getNewDamage(); //Neo: enforce damage container as source of truth for damage amount
            float f = amount;
            boolean flag = false;
            float f1 = 0.0F;
            net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent ev;
            if (amount > 0.0F && (ev = net.neoforged.neoforge.common.CommonHooks.onDamageBlock(this, this.damageContainers.peek(), this.isDamageSourceBlocked(source))).getBlocked()) {
                this.damageContainers.peek().setBlockedDamage(ev);
                if(ev.shieldDamage() > 0) {
                    this.hurtCurrentlyUsedShield(ev.shieldDamage());
                }
                f1 = ev.getBlockedDamage();
                amount = ev.getDamageContainer().getNewDamage();
                if (!source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() instanceof LivingEntity livingentity) {
                    this.blockUsingShield(livingentity);
                }

                flag = amount <= 0;
            }

            if (source.is(DamageTypeTags.IS_FREEZING) && this.getType().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES)) {
                amount *= 5.0F;
            }

            if (source.is(DamageTypeTags.DAMAGES_HELMET) && !this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                this.hurtHelmet(source, amount);
                amount *= 0.75F;
            }

            this.damageContainers.peek().setNewDamage(amount); //update container with vanilla changes
            this.walkAnimation.setSpeed(1.5F);
            boolean flag1 = true;
            if ((float)this.invulnerableTime > 10.0F && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
                if (amount <= this.lastHurt) {
                    this.damageContainers.pop();
                    return false;
                }

                this.damageContainers.peek().setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.INVULNERABILITY, this.lastHurt);
                this.actuallyHurt(source, amount - this.lastHurt);
                this.lastHurt = amount;
                flag1 = false;
            } else {
                this.lastHurt = amount;
                this.invulnerableTime = this.damageContainers.peek().getPostAttackInvulnerabilityTicks();
                this.actuallyHurt(source, amount);
                this.hurtDuration = 10;
                this.hurtTime = this.hurtDuration;
            }

            amount = this.damageContainers.peek().getNewDamage(); //update local with container value
            Entity entity = source.getEntity();
            if (entity != null) {
                if (entity instanceof LivingEntity livingentity1
                    && !source.is(DamageTypeTags.NO_ANGER)
                    && (!source.is(DamageTypes.WIND_CHARGE) || !this.getType().is(EntityTypeTags.NO_ANGER_FROM_WIND_CHARGE))) {
                    this.setLastHurtByMob(livingentity1);
                }

                if (entity instanceof Player player1) {
                    this.lastHurtByPlayerTime = 100;
                    this.lastHurtByPlayer = player1;
                } else if (entity instanceof TamableAnimal tamableAnimal && tamableAnimal.isTame()) {
                    this.lastHurtByPlayerTime = 100;
                    if (tamableAnimal.getOwner() instanceof Player player) {
                        this.lastHurtByPlayer = player;
                    } else {
                        this.lastHurtByPlayer = null;
                    }
                }
            }

            if (flag1) {
                if (flag) {
                    this.level().broadcastEntityEvent(this, (byte)29);
                } else {
                    this.level().broadcastDamageEvent(this, source);
                }

                if (!source.is(DamageTypeTags.NO_IMPACT) && (!flag || amount > 0.0F)) {
                    this.markHurt();
                }

                if (!source.is(DamageTypeTags.NO_KNOCKBACK)) {
                    double d0 = 0.0;
                    double d1 = 0.0;
                    if (source.getDirectEntity() instanceof Projectile projectile) {
                        DoubleDoubleImmutablePair doubledoubleimmutablepair = projectile.calculateHorizontalHurtKnockbackDirection(this, source);
                        d0 = -doubledoubleimmutablepair.leftDouble();
                        d1 = -doubledoubleimmutablepair.rightDouble();
                    } else if (source.getSourcePosition() != null) {
                        d0 = source.getSourcePosition().x() - this.getX();
                        d1 = source.getSourcePosition().z() - this.getZ();
                    }

                    this.knockback(0.4F, d0, d1);
                    if (!flag) {
                        this.indicateDamage(d0, d1);
                    }
                }
            }

            if (this.isDeadOrDying()) {
                if (!this.checkTotemDeathProtection(source)) {
                    if (flag1) {
                        this.makeSound(this.getDeathSound());
                    }

                    this.die(source);
                }
            } else if (flag1) {
                this.playHurtSound(source);
            }

            boolean flag2 = !flag || amount > 0.0F;
            if (flag2) {
                this.lastDamageSource = source;
                this.lastDamageStamp = this.level().getGameTime();

                for (MobEffectInstance mobeffectinstance : this.getActiveEffects()) {
                    mobeffectinstance.onMobHurt(this, source, amount);
                }
            }

            if (this instanceof ServerPlayer) {
                CriteriaTriggers.ENTITY_HURT_PLAYER.trigger((ServerPlayer)this, source, f, amount, flag);
                if (f1 > 0.0F && f1 < 3.4028235E37F) {
                    ((ServerPlayer)this).awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, Math.round(f1 * 10.0F));
                }
            }

            if (entity instanceof ServerPlayer) {
                CriteriaTriggers.PLAYER_HURT_ENTITY.trigger((ServerPlayer)entity, this, source, f, amount, flag);
            }

            this.damageContainers.pop();
            return flag2;
        }
    }

    protected void blockUsingShield(LivingEntity attacker) {
        attacker.blockedByShield(this);
    }

    protected void blockedByShield(LivingEntity defender) {
        defender.knockback(0.5, defender.getX() - this.getX(), defender.getZ() - this.getZ());
    }

    private boolean checkTotemDeathProtection(DamageSource damageSource) {
        if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        } else {
            ItemStack itemstack = null;

            for (InteractionHand interactionhand : InteractionHand.values()) {
                ItemStack itemstack1 = this.getItemInHand(interactionhand);
                if (itemstack1.is(Items.TOTEM_OF_UNDYING) && net.neoforged.neoforge.common.CommonHooks.onLivingUseTotem(this, damageSource, itemstack1, interactionhand)) {
                    itemstack = itemstack1.copy();
                    itemstack1.shrink(1);
                    break;
                }
            }

            if (itemstack != null) {
                if (this instanceof ServerPlayer serverplayer) {
                    serverplayer.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING), 1);
                    CriteriaTriggers.USED_TOTEM.trigger(serverplayer, itemstack);
                    this.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
                }

                this.setHealth(1.0F);
                this.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.PROTECTED_BY_TOTEM);
                this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
                this.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
                this.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
                this.level().broadcastEntityEvent(this, (byte)35);
            }

            return itemstack != null;
        }
    }

    @Nullable
    public DamageSource getLastDamageSource() {
        if (this.level().getGameTime() - this.lastDamageStamp > 40L) {
            this.lastDamageSource = null;
        }

        return this.lastDamageSource;
    }

    protected void playHurtSound(DamageSource source) {
        this.makeSound(this.getHurtSound(source));
    }

    public void makeSound(@Nullable SoundEvent sound) {
        if (sound != null) {
            this.playSound(sound, this.getSoundVolume(), this.getVoicePitch());
        }
    }

    /**
     * Determines whether the entity can block the damage source based on the damage source's location, whether the damage source is blockable, and whether the entity is blocking.
     */
    public boolean isDamageSourceBlocked(DamageSource damageSource) {
        Entity entity = damageSource.getDirectEntity();
        boolean flag = false;
        if (entity instanceof AbstractArrow abstractarrow && abstractarrow.getPierceLevel() > 0) {
            flag = true;
        }

        if (!damageSource.is(DamageTypeTags.BYPASSES_SHIELD) && this.isBlocking() && !flag) {
            Vec3 vec32 = damageSource.getSourcePosition();
            if (vec32 != null) {
                Vec3 vec3 = this.calculateViewVector(0.0F, this.getYHeadRot());
                Vec3 vec31 = vec32.vectorTo(this.position());
                vec31 = new Vec3(vec31.x, 0.0, vec31.z).normalize();
                return vec31.dot(vec3) < 0.0;
            }
        }

        return false;
    }

    /**
     * Renders broken item particles using the given ItemStack
     */
    private void breakItem(ItemStack stack) {
        if (!stack.isEmpty()) {
            if (!this.isSilent()) {
                this.level()
                    .playLocalSound(
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        stack.getBreakingSound(),
                        this.getSoundSource(),
                        0.8F,
                        0.8F + this.level().random.nextFloat() * 0.4F,
                        false
                    );
            }

            this.spawnItemParticles(stack, 5);
        }
    }

    /**
     * Called when the mob's health reaches 0.
     */
    public void die(DamageSource damageSource) {
        if (net.neoforged.neoforge.common.CommonHooks.onLivingDeath(this, damageSource)) return;
        if (!this.isRemoved() && !this.dead) {
            Entity entity = damageSource.getEntity();
            LivingEntity livingentity = this.getKillCredit();
            if (this.deathScore >= 0 && livingentity != null) {
                livingentity.awardKillScore(this, this.deathScore, damageSource);
            }

            if (this.isSleeping()) {
                this.stopSleeping();
            }

            if (!this.level().isClientSide && this.hasCustomName()) {
                LOGGER.info("Named entity {} died: {}", this, this.getCombatTracker().getDeathMessage().getString());
            }

            this.dead = true;
            this.getCombatTracker().recheckStatus();
            if (this.level() instanceof ServerLevel serverlevel) {
                if (entity == null || entity.killedEntity(serverlevel, this)) {
                    this.gameEvent(GameEvent.ENTITY_DIE);
                    this.dropAllDeathLoot(serverlevel, damageSource);
                    this.createWitherRose(livingentity);
                }

                this.level().broadcastEntityEvent(this, (byte)3);
            }

            this.setPose(Pose.DYING);
        }
    }

    protected void createWitherRose(@Nullable LivingEntity entitySource) {
        if (!this.level().isClientSide) {
            boolean flag = false;
            if (entitySource instanceof WitherBoss) {
                if (net.neoforged.neoforge.event.EventHooks.canEntityGrief(this.level(), entitySource)) {
                    BlockPos blockpos = this.blockPosition();
                    BlockState blockstate = Blocks.WITHER_ROSE.defaultBlockState();
                    if (this.level().getBlockState(blockpos).isAir() && blockstate.canSurvive(this.level(), blockpos)) {
                        this.level().setBlock(blockpos, blockstate, 3);
                        flag = true;
                    }
                }

                if (!flag) {
                    ItemEntity itementity = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), new ItemStack(Items.WITHER_ROSE));
                    this.level().addFreshEntity(itementity);
                }
            }
        }
    }

    protected void dropAllDeathLoot(ServerLevel p_level, DamageSource damageSource) {
        this.captureDrops(new java.util.ArrayList<>());
        boolean flag = this.lastHurtByPlayerTime > 0;
        if (this.shouldDropLoot() && p_level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            this.dropFromLootTable(damageSource, flag);
            this.dropCustomDeathLoot(p_level, damageSource, flag);
        }

        this.dropEquipment();
        this.dropExperience(damageSource.getEntity());

        Collection<ItemEntity> drops = captureDrops(null);
        if (!net.neoforged.neoforge.common.CommonHooks.onLivingDrops(this, damageSource, drops, lastHurtByPlayerTime > 0))
            drops.forEach(e -> level().addFreshEntity(e));
    }

    protected void dropEquipment() {
    }

    protected void dropExperience(@Nullable Entity entity) {
        if (this.level() instanceof ServerLevel serverlevel
            && !this.wasExperienceConsumed()
            && (
                this.isAlwaysExperienceDropper()
                    || this.lastHurtByPlayerTime > 0 && this.shouldDropExperience() && this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)
            )) {
            int reward = net.neoforged.neoforge.event.EventHooks.getExperienceDrop(this, this.lastHurtByPlayer, this.getExperienceReward(serverlevel, entity));
            ExperienceOrb.award((ServerLevel) this.level(), this.position(), reward);
        }
    }

    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
    }

    public ResourceKey<LootTable> getLootTable() {
        return this.getType().getDefaultLootTable();
    }

    public long getLootTableSeed() {
        return 0L;
    }

    protected float getKnockback(Entity attacker, DamageSource damageSource) {
        float f = (float)this.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        return this.level() instanceof ServerLevel serverlevel
            ? EnchantmentHelper.modifyKnockback(serverlevel, this.getWeaponItem(), attacker, damageSource, f)
            : f;
    }

    protected void dropFromLootTable(DamageSource damageSource, boolean hitByPlayer) {
        ResourceKey<LootTable> resourcekey = this.getLootTable();
        LootTable loottable = this.level().getServer().reloadableRegistries().getLootTable(resourcekey);
        LootParams.Builder lootparams$builder = new LootParams.Builder((ServerLevel)this.level())
            .withParameter(LootContextParams.THIS_ENTITY, this)
            .withParameter(LootContextParams.ORIGIN, this.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
            .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, damageSource.getEntity())
            .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, damageSource.getDirectEntity());
        if (hitByPlayer && this.lastHurtByPlayer != null) {
            lootparams$builder = lootparams$builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, this.lastHurtByPlayer)
                .withLuck(this.lastHurtByPlayer.getLuck());
        }

        LootParams lootparams = lootparams$builder.create(LootContextParamSets.ENTITY);
        loottable.getRandomItems(lootparams, this.getLootTableSeed(), this::spawnAtLocation);
    }

    public void knockback(double strength, double x, double z) {
        net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent event = net.neoforged.neoforge.common.CommonHooks.onLivingKnockBack(this, (float) strength, x, z);
        if(event.isCanceled()) return;
        strength = event.getStrength();
        x = event.getRatioX();
        z = event.getRatioZ();
        strength *= 1.0 - this.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (!(strength <= 0.0)) {
            this.hasImpulse = true;
            Vec3 vec3 = this.getDeltaMovement();

            while (x * x + z * z < 1.0E-5F) {
                x = (Math.random() - Math.random()) * 0.01;
                z = (Math.random() - Math.random()) * 0.01;
            }

            Vec3 vec31 = new Vec3(x, 0.0, z).normalize().scale(strength);
            this.setDeltaMovement(vec3.x / 2.0 - vec31.x, this.onGround() ? Math.min(0.4, vec3.y / 2.0 + strength) : vec3.y, vec3.z / 2.0 - vec31.z);
        }
    }

    public void indicateDamage(double xDistance, double zDistance) {
    }

    @Nullable
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.GENERIC_HURT;
    }

    @Nullable
    protected SoundEvent getDeathSound() {
        return SoundEvents.GENERIC_DEATH;
    }

    private SoundEvent getFallDamageSound(int height) {
        return height > 4 ? this.getFallSounds().big() : this.getFallSounds().small();
    }

    public void skipDropExperience() {
        this.skipDropExperience = true;
    }

    public boolean wasExperienceConsumed() {
        return this.skipDropExperience;
    }

    public float getHurtDir() {
        return 0.0F;
    }

    public AABB getHitbox() {
        AABB aabb = this.getBoundingBox();
        Entity entity = this.getVehicle();
        if (entity != null) {
            Vec3 vec3 = entity.getPassengerRidingPosition(this);
            return aabb.setMinY(Math.max(vec3.y, aabb.minY));
        } else {
            return aabb;
        }
    }

    public Map<Enchantment, Set<EnchantmentLocationBasedEffect>> activeLocationDependentEnchantments() {
        return this.activeLocationDependentEnchantments;
    }

    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.GENERIC_SMALL_FALL, SoundEvents.GENERIC_BIG_FALL);
    }

    protected SoundEvent getDrinkingSound(ItemStack stack) {
        return stack.getDrinkingSound();
    }

    public SoundEvent getEatingSound(ItemStack stack) {
        return stack.getEatingSound();
    }

    public Optional<BlockPos> getLastClimbablePos() {
        return this.lastClimbablePos;
    }

    public boolean onClimbable() {
        if (this.isSpectator()) {
            return false;
        } else {
            BlockPos blockpos = this.blockPosition();
            BlockState blockstate = this.getInBlockState();
            Optional<BlockPos> ladderPos = net.neoforged.neoforge.common.CommonHooks.isLivingOnLadder(blockstate, level(), blockpos, this);
            if (ladderPos.isPresent()) this.lastClimbablePos = ladderPos;
            return ladderPos.isPresent();
        }
    }

    private boolean trapdoorUsableAsLadder(BlockPos pos, BlockState state) {
        if (!state.getValue(TrapDoorBlock.OPEN)) {
            return false;
        } else {
            BlockState blockstate = this.level().getBlockState(pos.below());
            return blockstate.is(Blocks.LADDER) && blockstate.getValue(LadderBlock.FACING) == state.getValue(TrapDoorBlock.FACING);
        }
    }

    @Override
    public boolean isAlive() {
        return !this.isRemoved() && this.getHealth() > 0.0F;
    }

    @Override
    public int getMaxFallDistance() {
        return this.getComfortableFallDistance(0.0F);
    }

    protected final int getComfortableFallDistance(float health) {
        return Mth.floor(health + 3.0F);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        float[] ret = net.neoforged.neoforge.common.CommonHooks.onLivingFall(this, fallDistance, multiplier);
        if (ret == null) return false;
        fallDistance = ret[0];
        multiplier = ret[1];

        boolean flag = super.causeFallDamage(fallDistance, multiplier, source);
        int i = this.calculateFallDamage(fallDistance, multiplier);
        if (i > 0) {
            this.playSound(this.getFallDamageSound(i), 1.0F, 1.0F);
            this.playBlockFallSound();
            this.hurt(source, (float)i);
            return true;
        } else {
            return flag;
        }
    }

    protected int calculateFallDamage(float fallDistance, float damageMultiplier) {
        if (this.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
            return 0;
        } else {
            float f = (float)this.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
            float f1 = fallDistance - f;
            return Mth.ceil((double)(f1 * damageMultiplier) * this.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER));
        }
    }

    protected void playBlockFallSound() {
        if (!this.isSilent()) {
            int i = Mth.floor(this.getX());
            int j = Mth.floor(this.getY() - 0.2F);
            int k = Mth.floor(this.getZ());
            BlockPos pos = new BlockPos(i, j, k);
            BlockState blockstate = this.level().getBlockState(pos);
            if (!blockstate.isAir()) {
                SoundType soundtype = blockstate.getSoundType(level(), pos, this);
                this.playSound(soundtype.getFallSound(), soundtype.getVolume() * 0.5F, soundtype.getPitch() * 0.75F);
            }
        }
    }

    @Override
    public void animateHurt(float yaw) {
        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
    }

    public int getArmorValue() {
        return Mth.floor(this.getAttributeValue(Attributes.ARMOR));
    }

    protected void hurtArmor(DamageSource damageSource, float damageAmount) {
    }

    protected void hurtHelmet(DamageSource damageSource, float damageAmount) {
    }

    protected void hurtCurrentlyUsedShield(float damageAmount) {
    }

    protected void doHurtEquipment(DamageSource damageSource, float damageAmount, EquipmentSlot... slots) {
        if (!(damageAmount <= 0.0F)) {
            int i = (int)Math.max(1.0F, damageAmount / 4.0F);

            net.neoforged.neoforge.common.CommonHooks.onArmorHurt(damageSource, slots, i, this);
            if (true) return; //Neo: Invalidates the loop. Armor damage happens in common hook.
            for (EquipmentSlot equipmentslot : slots) {
                ItemStack itemstack = this.getItemBySlot(equipmentslot);
                if (itemstack.getItem() instanceof ArmorItem && itemstack.canBeHurtBy(damageSource)) {
                    itemstack.hurtAndBreak(i, this, equipmentslot);
                }
            }
        }
    }

    /**
     * Reduces damage, depending on armor
     */
    protected float getDamageAfterArmorAbsorb(DamageSource damageSource, float damageAmount) {
        if (!damageSource.is(DamageTypeTags.BYPASSES_ARMOR)) {
            this.hurtArmor(damageSource, damageAmount);
            damageAmount = CombatRules.getDamageAfterAbsorb(
                this, damageAmount, damageSource, (float)this.getArmorValue(), (float)this.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
            );
        }

        return damageAmount;
    }

    /**
     * Reduces damage, depending on potions
     */
    protected float getDamageAfterMagicAbsorb(DamageSource damageSource, float damageAmount) {
        if (damageSource.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            return damageAmount;
        } else {
            if (this.hasEffect(MobEffects.DAMAGE_RESISTANCE) && !damageSource.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
                int i = (this.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
                int j = 25 - i;
                float f = damageAmount * (float)j;
                float f1 = damageAmount;
                damageAmount = Math.max(f / 25.0F, 0.0F);
                float f2 = f1 - damageAmount;
                if (f2 > 0.0F && f2 < 3.4028235E37F) {
                    this.damageContainers.peek().setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.MOB_EFFECTS, f2);
                    if (this instanceof ServerPlayer) {
                        ((ServerPlayer)this).awardStat(Stats.DAMAGE_RESISTED, Math.round(f2 * 10.0F));
                    } else if (damageSource.getEntity() instanceof ServerPlayer) {
                        ((ServerPlayer)damageSource.getEntity()).awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(f2 * 10.0F));
                    }
                }
            }

            if (damageAmount <= 0.0F) {
                return 0.0F;
            } else if (damageSource.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
                return damageAmount;
            } else {
                float f3;
                if (this.level() instanceof ServerLevel serverlevel) {
                    f3 = EnchantmentHelper.getDamageProtection(serverlevel, this, damageSource);
                } else {
                    f3 = 0.0F;
                }

                if (f3 > 0.0F) {
                    damageAmount = CombatRules.getDamageAfterMagicAbsorb(damageAmount, f3);
                    this.damageContainers.peek().setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ENCHANTMENTS,this.damageContainers.peek().getNewDamage() - damageAmount);
                }

                return damageAmount;
            }
        }
    }

    /**
     * Deals damage to the entity. This will take the armor of the entity into consideration before damaging the health bar.
     */
    protected void actuallyHurt(DamageSource damageSource, float damageAmount) {
        if (!this.isInvulnerableTo(damageSource)) {
            this.damageContainers.peek().setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ARMOR, this.damageContainers.peek().getNewDamage() - this.getDamageAfterArmorAbsorb(damageSource, this.damageContainers.peek().getNewDamage()));
            this.getDamageAfterMagicAbsorb(damageSource, this.damageContainers.peek().getNewDamage());
            float damage = net.neoforged.neoforge.common.CommonHooks.onLivingDamagePre(this, this.damageContainers.peek());
            this.damageContainers.peek().setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ABSORPTION, Math.min(this.getAbsorptionAmount(), damage));
            float absorbed = Math.min(damage, this.damageContainers.peek().getReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ABSORPTION));
            this.setAbsorptionAmount(Math.max(0, this.getAbsorptionAmount() - absorbed));
            float f1 = this.damageContainers.peek().getNewDamage();
            float f = absorbed;
            if (f > 0.0F && f < 3.4028235E37F && damageSource.getEntity() instanceof ServerPlayer serverplayer) {
                serverplayer.awardStat(Stats.DAMAGE_DEALT_ABSORBED, Math.round(f * 10.0F));
            }

            if (f1 != 0.0F) {
                this.getCombatTracker().recordDamage(damageSource, f1);
                this.setHealth(this.getHealth() - f1);
                this.gameEvent(GameEvent.ENTITY_DAMAGE);
                this.onDamageTaken(this.damageContainers.peek());
            }
            net.neoforged.neoforge.common.CommonHooks.onLivingDamagePost(this, this.damageContainers.peek());
        }
    }

    public CombatTracker getCombatTracker() {
        return this.combatTracker;
    }

    @Nullable
    public LivingEntity getKillCredit() {
        if (this.lastHurtByPlayer != null) {
            return this.lastHurtByPlayer;
        } else {
            return this.lastHurtByMob != null ? this.lastHurtByMob : null;
        }
    }

    public final float getMaxHealth() {
        return (float)this.getAttributeValue(Attributes.MAX_HEALTH);
    }

    public final float getMaxAbsorption() {
        return (float)this.getAttributeValue(Attributes.MAX_ABSORPTION);
    }

    public final int getArrowCount() {
        return this.entityData.get(DATA_ARROW_COUNT_ID);
    }

    /**
     * Sets the amount of arrows stuck in the entity. Used for rendering those.
     */
    public final void setArrowCount(int count) {
        this.entityData.set(DATA_ARROW_COUNT_ID, count);
    }

    public final int getStingerCount() {
        return this.entityData.get(DATA_STINGER_COUNT_ID);
    }

    public final void setStingerCount(int stingerCount) {
        this.entityData.set(DATA_STINGER_COUNT_ID, stingerCount);
    }

    public int getCurrentSwingDuration() {
        if (MobEffectUtil.hasDigSpeed(this)) {
            return 6 - (1 + MobEffectUtil.getDigSpeedAmplification(this));
        } else {
            return this.hasEffect(MobEffects.DIG_SLOWDOWN) ? 6 + (1 + this.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) * 2 : 6;
        }
    }

    public void swing(InteractionHand hand) {
        this.swing(hand, false);
    }

    public void swing(InteractionHand hand, boolean updateSelf) {
        ItemStack stack = this.getItemInHand(hand);
        if (!stack.isEmpty() && stack.onEntitySwing(this, hand)) return;
        if (!this.swinging || this.swingTime >= this.getCurrentSwingDuration() / 2 || this.swingTime < 0) {
            this.swingTime = -1;
            this.swinging = true;
            this.swingingArm = hand;
            if (this.level() instanceof ServerLevel) {
                ClientboundAnimatePacket clientboundanimatepacket = new ClientboundAnimatePacket(this, hand == InteractionHand.MAIN_HAND ? 0 : 3);
                ServerChunkCache serverchunkcache = ((ServerLevel)this.level()).getChunkSource();
                if (updateSelf) {
                    serverchunkcache.broadcastAndSend(this, clientboundanimatepacket);
                } else {
                    serverchunkcache.broadcast(this, clientboundanimatepacket);
                }
            }
        }
    }

    @Override
    public void handleDamageEvent(DamageSource damageSource) {
        this.walkAnimation.setSpeed(1.5F);
        this.invulnerableTime = 20;
        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
        SoundEvent soundevent = this.getHurtSound(damageSource);
        if (soundevent != null) {
            this.playSound(soundevent, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        }

        this.hurt(this.damageSources().generic(), 0.0F);
        this.lastDamageSource = damageSource;
        this.lastDamageStamp = this.level().getGameTime();
    }

    /**
     * Handler for {@link World#setEntityState}
     */
    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case 3:
                SoundEvent soundevent = this.getDeathSound();
                if (soundevent != null) {
                    this.playSound(soundevent, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                }

                if (!(this instanceof Player)) {
                    this.setHealth(0.0F);
                    this.die(this.damageSources().generic());
                }
                break;
            case 29:
                this.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F + this.level().random.nextFloat() * 0.4F);
                break;
            case 30:
                this.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F);
                break;
            case 46:
                int i = 128;

                for (int j = 0; j < 128; j++) {
                    double d0 = (double)j / 127.0;
                    float f = (this.random.nextFloat() - 0.5F) * 0.2F;
                    float f1 = (this.random.nextFloat() - 0.5F) * 0.2F;
                    float f2 = (this.random.nextFloat() - 0.5F) * 0.2F;
                    double d1 = Mth.lerp(d0, this.xo, this.getX()) + (this.random.nextDouble() - 0.5) * (double)this.getBbWidth() * 2.0;
                    double d2 = Mth.lerp(d0, this.yo, this.getY()) + this.random.nextDouble() * (double)this.getBbHeight();
                    double d3 = Mth.lerp(d0, this.zo, this.getZ()) + (this.random.nextDouble() - 0.5) * (double)this.getBbWidth() * 2.0;
                    this.level().addParticle(ParticleTypes.PORTAL, d1, d2, d3, (double)f, (double)f1, (double)f2);
                }
                break;
            case 47:
                this.breakItem(this.getItemBySlot(EquipmentSlot.MAINHAND));
                break;
            case 48:
                this.breakItem(this.getItemBySlot(EquipmentSlot.OFFHAND));
                break;
            case 49:
                this.breakItem(this.getItemBySlot(EquipmentSlot.HEAD));
                break;
            case 50:
                this.breakItem(this.getItemBySlot(EquipmentSlot.CHEST));
                break;
            case 51:
                this.breakItem(this.getItemBySlot(EquipmentSlot.LEGS));
                break;
            case 52:
                this.breakItem(this.getItemBySlot(EquipmentSlot.FEET));
                break;
            case 54:
                HoneyBlock.showJumpParticles(this);
                break;
            case 55:
                this.swapHandItems();
                break;
            case 60:
                this.makePoofParticles();
                break;
            case 65:
                this.breakItem(this.getItemBySlot(EquipmentSlot.BODY));
                break;
            default:
                super.handleEntityEvent(id);
        }
    }

    private void makePoofParticles() {
        for (int i = 0; i < 20; i++) {
            double d0 = this.random.nextGaussian() * 0.02;
            double d1 = this.random.nextGaussian() * 0.02;
            double d2 = this.random.nextGaussian() * 0.02;
            this.level().addParticle(ParticleTypes.POOF, this.getRandomX(1.0), this.getRandomY(), this.getRandomZ(1.0), d0, d1, d2);
        }
    }

    private void swapHandItems() {
        ItemStack itemstack = this.getItemBySlot(EquipmentSlot.OFFHAND);
        var event = net.neoforged.neoforge.common.CommonHooks.onLivingSwapHandItems(this);
        if (event.isCanceled()) return;
        this.setItemSlot(EquipmentSlot.OFFHAND, event.getItemSwappedToOffHand());
        this.setItemSlot(EquipmentSlot.MAINHAND, event.getItemSwappedToMainHand());
    }

    @Override
    protected void onBelowWorld() {
        this.hurt(this.damageSources().fellOutOfWorld(), 4.0F);
    }

    protected void updateSwingTime() {
        int i = this.getCurrentSwingDuration();
        if (this.swinging) {
            this.swingTime++;
            if (this.swingTime >= i) {
                this.swingTime = 0;
                this.swinging = false;
            }
        } else {
            this.swingTime = 0;
        }

        this.attackAnim = (float)this.swingTime / (float)i;
    }

    @Nullable
    public AttributeInstance getAttribute(Holder<Attribute> attribute) {
        return this.getAttributes().getInstance(attribute);
    }

    public double getAttributeValue(Holder<Attribute> attribute) {
        return this.getAttributes().getValue(attribute);
    }

    public double getAttributeBaseValue(Holder<Attribute> attribute) {
        return this.getAttributes().getBaseValue(attribute);
    }

    public AttributeMap getAttributes() {
        return this.attributes;
    }

    public ItemStack getMainHandItem() {
        return this.getItemBySlot(EquipmentSlot.MAINHAND);
    }

    public ItemStack getOffhandItem() {
        return this.getItemBySlot(EquipmentSlot.OFFHAND);
    }

    @Nonnull
    @Override
    public ItemStack getWeaponItem() {
        return this.getMainHandItem();
    }

    public boolean isHolding(Item item) {
        return this.isHolding(p_147200_ -> p_147200_.is(item));
    }

    public boolean isHolding(Predicate<ItemStack> predicate) {
        return predicate.test(this.getMainHandItem()) || predicate.test(this.getOffhandItem());
    }

    public ItemStack getItemInHand(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            return this.getItemBySlot(EquipmentSlot.MAINHAND);
        } else if (hand == InteractionHand.OFF_HAND) {
            return this.getItemBySlot(EquipmentSlot.OFFHAND);
        } else {
            throw new IllegalArgumentException("Invalid hand " + hand);
        }
    }

    public void setItemInHand(InteractionHand hand, ItemStack stack) {
        if (hand == InteractionHand.MAIN_HAND) {
            this.setItemSlot(EquipmentSlot.MAINHAND, stack);
        } else {
            if (hand != InteractionHand.OFF_HAND) {
                throw new IllegalArgumentException("Invalid hand " + hand);
            }

            this.setItemSlot(EquipmentSlot.OFFHAND, stack);
        }
    }

    public boolean hasItemInSlot(EquipmentSlot slot) {
        return !this.getItemBySlot(slot).isEmpty();
    }

    public boolean canUseSlot(EquipmentSlot slot) {
        return false;
    }

    public abstract Iterable<ItemStack> getArmorSlots();

    public abstract ItemStack getItemBySlot(EquipmentSlot slot);

    public abstract void setItemSlot(EquipmentSlot slot, ItemStack stack);

    public Iterable<ItemStack> getHandSlots() {
        return List.of();
    }

    public Iterable<ItemStack> getArmorAndBodyArmorSlots() {
        return this.getArmorSlots();
    }

    public Iterable<ItemStack> getAllSlots() {
        return Iterables.concat(this.getHandSlots(), this.getArmorAndBodyArmorSlots());
    }

    protected void verifyEquippedItem(ItemStack stack) {
        stack.getItem().verifyComponentsAfterLoad(stack);
    }

    public float getArmorCoverPercentage() {
        Iterable<ItemStack> iterable = this.getArmorSlots();
        int i = 0;
        int j = 0;

        for (ItemStack itemstack : iterable) {
            if (!itemstack.isEmpty()) {
                j++;
            }

            i++;
        }

        return i > 0 ? (float)j / (float)i : 0.0F;
    }

    /**
     * Set sprinting switch for Entity.
     */
    @Override
    public void setSprinting(boolean sprinting) {
        super.setSprinting(sprinting);
        AttributeInstance attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
        attributeinstance.removeModifier(SPEED_MODIFIER_SPRINTING.id());
        if (sprinting) {
            attributeinstance.addTransientModifier(SPEED_MODIFIER_SPRINTING);
        }
    }

    protected float getSoundVolume() {
        return 1.0F;
    }

    public float getVoicePitch() {
        return this.isBaby()
            ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.5F
            : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
    }

    protected boolean isImmobile() {
        return this.isDeadOrDying();
    }

    /**
     * Applies a velocity to the entities, to push them away from each other.
     */
    @Override
    public void push(Entity entity) {
        if (!this.isSleeping()) {
            super.push(entity);
        }
    }

    private void dismountVehicle(Entity vehicle) {
        Vec3 vec3;
        if (this.isRemoved()) {
            vec3 = this.position();
        } else if (!vehicle.isRemoved() && !this.level().getBlockState(vehicle.blockPosition()).is(BlockTags.PORTALS)) {
            vec3 = vehicle.getDismountLocationForPassenger(this);
        } else {
            double d0 = Math.max(this.getY(), vehicle.getY());
            vec3 = new Vec3(this.getX(), d0, this.getZ());
        }

        this.dismountTo(vec3.x, vec3.y, vec3.z);
    }

    @Override
    public boolean shouldShowName() {
        return this.isCustomNameVisible();
    }

    protected float getJumpPower() {
        return this.getJumpPower(1.0F);
    }

    protected float getJumpPower(float multiplier) {
        return (float)this.getAttributeValue(Attributes.JUMP_STRENGTH) * multiplier * this.getBlockJumpFactor() + this.getJumpBoostPower();
    }

    public float getJumpBoostPower() {
        return this.hasEffect(MobEffects.JUMP) ? 0.1F * ((float)this.getEffect(MobEffects.JUMP).getAmplifier() + 1.0F) : 0.0F;
    }

    @VisibleForTesting
    public void jumpFromGround() {
        float f = this.getJumpPower();
        if (!(f <= 1.0E-5F)) {
            Vec3 vec3 = this.getDeltaMovement();
            this.setDeltaMovement(vec3.x, (double)f, vec3.z);
            if (this.isSprinting()) {
                float f1 = this.getYRot() * (float) (Math.PI / 180.0);
                this.addDeltaMovement(new Vec3((double)(-Mth.sin(f1)) * 0.2, 0.0, (double)Mth.cos(f1) * 0.2));
            }

            this.hasImpulse = true;
            net.neoforged.neoforge.common.CommonHooks.onLivingJump(this);
        }
    }

    @Deprecated // FORGE: use sinkInFluid instead
    protected void goDownInWater() {
        this.sinkInFluid(net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value());
    }

    @Deprecated // FORGE: use jumpInFluid instead
    protected void jumpInLiquid(TagKey<Fluid> fluidTag) {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, (double)0.04F * this.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED), 0.0D));
    }

    protected float getWaterSlowDown() {
        return 0.8F;
    }

    public boolean canStandOnFluid(FluidState fluidState) {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        return this.getAttributeValue(Attributes.GRAVITY);
    }

    public void travel(Vec3 travelVector) {
        if (this.isControlledByLocalInstance()) {
            double d0 = this.getGravity();
            boolean flag = this.getDeltaMovement().y <= 0.0;
            if (flag && this.hasEffect(MobEffects.SLOW_FALLING)) {
                d0 = Math.min(d0, 0.01);
            }

            FluidState fluidstate = this.level().getFluidState(this.blockPosition());
            if ((this.isInWater() || (this.isInFluidType(fluidstate) && fluidstate.getFluidType() != net.neoforged.neoforge.common.NeoForgeMod.LAVA_TYPE.value())) && this.isAffectedByFluids() && !this.canStandOnFluid(fluidstate)) {
                if (this.isInWater() || (this.isInFluidType(fluidstate) && !this.moveInFluid(fluidstate, travelVector, d0))) {
                double d9 = this.getY();
                float f4 = this.isSprinting() ? 0.9F : this.getWaterSlowDown();
                float f5 = 0.02F;
                float f6 = (float)this.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
                if (!this.onGround()) {
                    f6 *= 0.5F;
                }

                if (f6 > 0.0F) {
                    f4 += (0.54600006F - f4) * f6;
                    f5 += (this.getSpeed() - f5) * f6;
                }

                if (this.hasEffect(MobEffects.DOLPHINS_GRACE)) {
                    f4 = 0.96F;
                }

                f5 *= (float)this.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED);
                this.moveRelative(f5, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                Vec3 vec36 = this.getDeltaMovement();
                if (this.horizontalCollision && this.onClimbable()) {
                    vec36 = new Vec3(vec36.x, 0.2, vec36.z);
                }

                this.setDeltaMovement(vec36.multiply((double)f4, 0.8F, (double)f4));
                Vec3 vec32 = this.getFluidFallingAdjustedMovement(d0, flag, this.getDeltaMovement());
                this.setDeltaMovement(vec32);
                if (this.horizontalCollision && this.isFree(vec32.x, vec32.y + 0.6F - this.getY() + d9, vec32.z)) {
                    this.setDeltaMovement(vec32.x, 0.3F, vec32.z);
                }
                }
            } else if (this.isInLava() && this.isAffectedByFluids() && !this.canStandOnFluid(fluidstate)) {
                double d8 = this.getY();
                this.moveRelative(0.02F, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                if (this.getFluidHeight(FluidTags.LAVA) <= this.getFluidJumpThreshold()) {
                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 0.8F, 0.5));
                    Vec3 vec33 = this.getFluidFallingAdjustedMovement(d0, flag, this.getDeltaMovement());
                    this.setDeltaMovement(vec33);
                } else {
                    this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
                }

                if (d0 != 0.0) {
                    this.setDeltaMovement(this.getDeltaMovement().add(0.0, -d0 / 4.0, 0.0));
                }

                Vec3 vec34 = this.getDeltaMovement();
                if (this.horizontalCollision && this.isFree(vec34.x, vec34.y + 0.6F - this.getY() + d8, vec34.z)) {
                    this.setDeltaMovement(vec34.x, 0.3F, vec34.z);
                }
            } else if (this.isFallFlying()) {
                this.checkSlowFallDistance();
                Vec3 vec3 = this.getDeltaMovement();
                Vec3 vec31 = this.getLookAngle();
                float f = this.getXRot() * (float) (Math.PI / 180.0);
                double d1 = Math.sqrt(vec31.x * vec31.x + vec31.z * vec31.z);
                double d3 = vec3.horizontalDistance();
                double d4 = vec31.length();
                double d5 = Math.cos((double)f);
                d5 = d5 * d5 * Math.min(1.0, d4 / 0.4);
                vec3 = this.getDeltaMovement().add(0.0, d0 * (-1.0 + d5 * 0.75), 0.0);
                if (vec3.y < 0.0 && d1 > 0.0) {
                    double d6 = vec3.y * -0.1 * d5;
                    vec3 = vec3.add(vec31.x * d6 / d1, d6, vec31.z * d6 / d1);
                }

                if (f < 0.0F && d1 > 0.0) {
                    double d10 = d3 * (double)(-Mth.sin(f)) * 0.04;
                    vec3 = vec3.add(-vec31.x * d10 / d1, d10 * 3.2, -vec31.z * d10 / d1);
                }

                if (d1 > 0.0) {
                    vec3 = vec3.add((vec31.x / d1 * d3 - vec3.x) * 0.1, 0.0, (vec31.z / d1 * d3 - vec3.z) * 0.1);
                }

                this.setDeltaMovement(vec3.multiply(0.99F, 0.98F, 0.99F));
                this.move(MoverType.SELF, this.getDeltaMovement());
                if (this.horizontalCollision && !this.level().isClientSide) {
                    double d11 = this.getDeltaMovement().horizontalDistance();
                    double d7 = d3 - d11;
                    float f1 = (float)(d7 * 10.0 - 3.0);
                    if (f1 > 0.0F) {
                        this.playSound(this.getFallDamageSound((int)f1), 1.0F, 1.0F);
                        this.hurt(this.damageSources().flyIntoWall(), f1);
                    }
                }

                if (this.onGround() && !this.level().isClientSide) {
                    this.setSharedFlag(7, false);
                }
            } else {
                BlockPos blockpos = this.getBlockPosBelowThatAffectsMyMovement();
                float f2 = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getFriction(level(), this.getBlockPosBelowThatAffectsMyMovement(), this);
                float f3 = this.onGround() ? f2 * 0.91F : 0.91F;
                Vec3 vec35 = this.handleRelativeFrictionAndCalculateMovement(travelVector, f2);
                double d2 = vec35.y;
                if (this.hasEffect(MobEffects.LEVITATION)) {
                    d2 += (0.05 * (double)(this.getEffect(MobEffects.LEVITATION).getAmplifier() + 1) - vec35.y) * 0.2;
                } else if (!this.level().isClientSide || this.level().hasChunkAt(blockpos)) {
                    d2 -= d0;
                } else if (this.getY() > (double)this.level().getMinBuildHeight()) {
                    d2 = -0.1;
                } else {
                    d2 = 0.0;
                }

                if (this.shouldDiscardFriction()) {
                    this.setDeltaMovement(vec35.x, d2, vec35.z);
                } else {
                    this.setDeltaMovement(vec35.x * (double)f3, this instanceof FlyingAnimal ? d2 * (double)f3 : d2 * 0.98F, vec35.z * (double)f3);
                }
            }
        }

        this.calculateEntityAnimation(this instanceof FlyingAnimal);
    }

    private void travelRidden(Player player, Vec3 travelVector) {
        Vec3 vec3 = this.getRiddenInput(player, travelVector);
        this.tickRidden(player, vec3);
        if (this.isControlledByLocalInstance()) {
            this.setSpeed(this.getRiddenSpeed(player));
            this.travel(vec3);
        } else {
            this.calculateEntityAnimation(false);
            this.setDeltaMovement(Vec3.ZERO);
            this.tryCheckInsideBlocks();
        }
    }

    protected void tickRidden(Player player, Vec3 travelVector) {
    }

    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return travelVector;
    }

    protected float getRiddenSpeed(Player player) {
        return this.getSpeed();
    }

    public void calculateEntityAnimation(boolean includeHeight) {
        float f = (float)Mth.length(this.getX() - this.xo, includeHeight ? this.getY() - this.yo : 0.0, this.getZ() - this.zo);
        this.updateWalkAnimation(f);
    }

    protected void updateWalkAnimation(float partialTick) {
        float f = Math.min(partialTick * 4.0F, 1.0F);
        this.walkAnimation.update(f, 0.4F);
    }

    public Vec3 handleRelativeFrictionAndCalculateMovement(Vec3 deltaMovement, float friction) {
        this.moveRelative(this.getFrictionInfluencedSpeed(friction), deltaMovement);
        this.setDeltaMovement(this.handleOnClimbable(this.getDeltaMovement()));
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 vec3 = this.getDeltaMovement();
        if ((this.horizontalCollision || this.jumping)
            && (this.onClimbable() || this.getInBlockState().is(Blocks.POWDER_SNOW) && PowderSnowBlock.canEntityWalkOnPowderSnow(this))) {
            vec3 = new Vec3(vec3.x, 0.2, vec3.z);
        }

        return vec3;
    }

    public Vec3 getFluidFallingAdjustedMovement(double gravity, boolean isFalling, Vec3 deltaMovement) {
        if (gravity != 0.0 && !this.isSprinting()) {
            double d0;
            if (isFalling && Math.abs(deltaMovement.y - 0.005) >= 0.003 && Math.abs(deltaMovement.y - gravity / 16.0) < 0.003) {
                d0 = -0.003;
            } else {
                d0 = deltaMovement.y - gravity / 16.0;
            }

            return new Vec3(deltaMovement.x, d0, deltaMovement.z);
        } else {
            return deltaMovement;
        }
    }

    private Vec3 handleOnClimbable(Vec3 deltaMovement) {
        if (this.onClimbable()) {
            this.resetFallDistance();
            float f = 0.15F;
            double d0 = Mth.clamp(deltaMovement.x, -0.15F, 0.15F);
            double d1 = Mth.clamp(deltaMovement.z, -0.15F, 0.15F);
            double d2 = Math.max(deltaMovement.y, -0.15F);
            if (d2 < 0.0D && !this.getInBlockState().isScaffolding(this) && this.isSuppressingSlidingDownLadder() && this instanceof Player) {
                d2 = 0.0;
            }

            deltaMovement = new Vec3(d0, d2, d1);
        }

        return deltaMovement;
    }

    private float getFrictionInfluencedSpeed(float friction) {
        return this.onGround() ? this.getSpeed() * (0.21600002F / (friction * friction * friction)) : this.getFlyingSpeed();
    }

    protected float getFlyingSpeed() {
        return this.getControllingPassenger() instanceof Player ? this.getSpeed() * 0.1F : 0.02F;
    }

    public float getSpeed() {
        return this.speed;
    }

    /**
     * Sets the movespeed used for the new AI system.
     */
    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public boolean doHurtTarget(Entity target) {
        this.setLastHurtMob(target);
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.updatingUsingItem();
        this.updateSwimAmount();
        if (!this.level().isClientSide) {
            int i = this.getArrowCount();
            if (i > 0) {
                if (this.removeArrowTime <= 0) {
                    this.removeArrowTime = 20 * (30 - i);
                }

                this.removeArrowTime--;
                if (this.removeArrowTime <= 0) {
                    this.setArrowCount(i - 1);
                }
            }

            int j = this.getStingerCount();
            if (j > 0) {
                if (this.removeStingerTime <= 0) {
                    this.removeStingerTime = 20 * (30 - j);
                }

                this.removeStingerTime--;
                if (this.removeStingerTime <= 0) {
                    this.setStingerCount(j - 1);
                }
            }

            this.detectEquipmentUpdates();
            if (this.tickCount % 20 == 0) {
                this.getCombatTracker().recheckStatus();
            }

            if (this.isSleeping() && !this.checkBedExists()) {
                this.stopSleeping();
            }
        }

        if (!this.isRemoved()) {
            this.aiStep();
        }

        double d1 = this.getX() - this.xo;
        double d0 = this.getZ() - this.zo;
        float f = (float)(d1 * d1 + d0 * d0);
        float f1 = this.yBodyRot;
        float f2 = 0.0F;
        this.oRun = this.run;
        float f3 = 0.0F;
        if (f > 0.0025000002F) {
            f3 = 1.0F;
            f2 = (float)Math.sqrt((double)f) * 3.0F;
            float f4 = (float)Mth.atan2(d0, d1) * (180.0F / (float)Math.PI) - 90.0F;
            float f5 = Mth.abs(Mth.wrapDegrees(this.getYRot()) - f4);
            if (95.0F < f5 && f5 < 265.0F) {
                f1 = f4 - 180.0F;
            } else {
                f1 = f4;
            }
        }

        if (this.attackAnim > 0.0F) {
            f1 = this.getYRot();
        }

        if (!this.onGround()) {
            f3 = 0.0F;
        }

        this.run = this.run + (f3 - this.run) * 0.3F;
        this.level().getProfiler().push("headTurn");
        f2 = this.tickHeadTurn(f1, f2);
        this.level().getProfiler().pop();
        this.level().getProfiler().push("rangeChecks");

        while (this.getYRot() - this.yRotO < -180.0F) {
            this.yRotO -= 360.0F;
        }

        while (this.getYRot() - this.yRotO >= 180.0F) {
            this.yRotO += 360.0F;
        }

        while (this.yBodyRot - this.yBodyRotO < -180.0F) {
            this.yBodyRotO -= 360.0F;
        }

        while (this.yBodyRot - this.yBodyRotO >= 180.0F) {
            this.yBodyRotO += 360.0F;
        }

        while (this.getXRot() - this.xRotO < -180.0F) {
            this.xRotO -= 360.0F;
        }

        while (this.getXRot() - this.xRotO >= 180.0F) {
            this.xRotO += 360.0F;
        }

        while (this.yHeadRot - this.yHeadRotO < -180.0F) {
            this.yHeadRotO -= 360.0F;
        }

        while (this.yHeadRot - this.yHeadRotO >= 180.0F) {
            this.yHeadRotO += 360.0F;
        }

        this.level().getProfiler().pop();
        this.animStep += f2;
        if (this.isFallFlying()) {
            this.fallFlyTicks++;
        } else {
            this.fallFlyTicks = 0;
        }

        if (this.isSleeping()) {
            this.setXRot(0.0F);
        }

        this.refreshDirtyAttributes();
        float f6 = this.getScale();
        if (f6 != this.appliedScale) {
            this.appliedScale = f6;
            this.refreshDimensions();
        }
    }

    private void detectEquipmentUpdates() {
        Map<EquipmentSlot, ItemStack> map = this.collectEquipmentChanges();
        if (map != null) {
            this.handleHandSwap(map);
            if (!map.isEmpty()) {
                this.handleEquipmentChanges(map);
            }
        }
    }

    @Nullable
    private Map<EquipmentSlot, ItemStack> collectEquipmentChanges() {
        Map<EquipmentSlot, ItemStack> map = null;

        for (EquipmentSlot equipmentslot : EquipmentSlot.values()) {
            ItemStack itemstack = switch (equipmentslot.getType()) {
                case HAND -> this.getLastHandItem(equipmentslot);
                case HUMANOID_ARMOR -> this.getLastArmorItem(equipmentslot);
                case ANIMAL_ARMOR -> this.lastBodyItemStack;
            };
            ItemStack itemstack1 = this.getItemBySlot(equipmentslot);
            if (this.equipmentHasChanged(itemstack, itemstack1)) {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent(this, equipmentslot, itemstack, itemstack1));
                if (map == null) {
                    map = Maps.newEnumMap(EquipmentSlot.class);
                }

                map.put(equipmentslot, itemstack1);
                AttributeMap attributemap = this.getAttributes();
                if (!itemstack.isEmpty()) {
                    itemstack.forEachModifier(equipmentslot, (p_344283_, p_344284_) -> {
                        AttributeInstance attributeinstance = attributemap.getInstance(p_344283_);
                        if (attributeinstance != null) {
                            attributeinstance.removeModifier(p_344284_);
                        }

                        EnchantmentHelper.stopLocationBasedEffects(itemstack, this, equipmentslot);
                    });
                }
            }
        }

        if (map != null) {
            for (Entry<EquipmentSlot, ItemStack> entry : map.entrySet()) {
                EquipmentSlot equipmentslot1 = entry.getKey();
                ItemStack itemstack2 = entry.getValue();
                if (!itemstack2.isEmpty()) {
                    itemstack2.forEachModifier(equipmentslot1, (p_352705_, p_352706_) -> {
                        AttributeInstance attributeinstance = this.attributes.getInstance(p_352705_);
                        if (attributeinstance != null) {
                            attributeinstance.removeModifier(p_352706_.id());
                            attributeinstance.addTransientModifier(p_352706_);
                        }

                        if (this.level() instanceof ServerLevel serverlevel) {
                            EnchantmentHelper.runLocationChangedEffects(serverlevel, itemstack2, this, equipmentslot1);
                        }
                    });
                }
            }
        }

        return map;
    }

    public boolean equipmentHasChanged(ItemStack oldItem, ItemStack newItem) {
        return !ItemStack.matches(newItem, oldItem);
    }

    private void handleHandSwap(Map<EquipmentSlot, ItemStack> hands) {
        ItemStack itemstack = hands.get(EquipmentSlot.MAINHAND);
        ItemStack itemstack1 = hands.get(EquipmentSlot.OFFHAND);
        if (itemstack != null
            && itemstack1 != null
            && ItemStack.matches(itemstack, this.getLastHandItem(EquipmentSlot.OFFHAND))
            && ItemStack.matches(itemstack1, this.getLastHandItem(EquipmentSlot.MAINHAND))) {
            ((ServerLevel)this.level()).getChunkSource().broadcast(this, new ClientboundEntityEventPacket(this, (byte)55));
            hands.remove(EquipmentSlot.MAINHAND);
            hands.remove(EquipmentSlot.OFFHAND);
            this.setLastHandItem(EquipmentSlot.MAINHAND, itemstack.copy());
            this.setLastHandItem(EquipmentSlot.OFFHAND, itemstack1.copy());
        }
    }

    private void handleEquipmentChanges(Map<EquipmentSlot, ItemStack> equipments) {
        List<Pair<EquipmentSlot, ItemStack>> list = Lists.newArrayListWithCapacity(equipments.size());
        equipments.forEach((p_323229_, p_323230_) -> {
            ItemStack itemstack = p_323230_.copy();
            list.add(Pair.of(p_323229_, itemstack));
            switch (p_323229_.getType()) {
                case HAND:
                    this.setLastHandItem(p_323229_, itemstack);
                    break;
                case HUMANOID_ARMOR:
                    this.setLastArmorItem(p_323229_, itemstack);
                    break;
                case ANIMAL_ARMOR:
                    this.lastBodyItemStack = itemstack;
            }
        });
        ((ServerLevel)this.level()).getChunkSource().broadcast(this, new ClientboundSetEquipmentPacket(this.getId(), list));
    }

    private ItemStack getLastArmorItem(EquipmentSlot slot) {
        return this.lastArmorItemStacks.get(slot.getIndex());
    }

    private void setLastArmorItem(EquipmentSlot slot, ItemStack stack) {
        this.lastArmorItemStacks.set(slot.getIndex(), stack);
    }

    private ItemStack getLastHandItem(EquipmentSlot slot) {
        return this.lastHandItemStacks.get(slot.getIndex());
    }

    private void setLastHandItem(EquipmentSlot slot, ItemStack stack) {
        this.lastHandItemStacks.set(slot.getIndex(), stack);
    }

    protected float tickHeadTurn(float yRot, float animStep) {
        float f = Mth.wrapDegrees(yRot - this.yBodyRot);
        this.yBodyRot += f * 0.3F;
        float f1 = Mth.wrapDegrees(this.getYRot() - this.yBodyRot);
        float f2 = this.getMaxHeadRotationRelativeToBody();
        if (Math.abs(f1) > f2) {
            this.yBodyRot = this.yBodyRot + (f1 - (float)Mth.sign((double)f1) * f2);
        }

        boolean flag = f1 < -90.0F || f1 >= 90.0F;
        if (flag) {
            animStep *= -1.0F;
        }

        return animStep;
    }

    protected float getMaxHeadRotationRelativeToBody() {
        return 50.0F;
    }

    public void aiStep() {
        if (this.noJumpDelay > 0) {
            this.noJumpDelay--;
        }

        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
        }

        if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot, this.lerpXRot);
            this.lerpSteps--;
        } else if (!this.isEffectiveAi()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        }

        if (this.lerpHeadSteps > 0) {
            this.lerpHeadRotationStep(this.lerpHeadSteps, this.lerpYHeadRot);
            this.lerpHeadSteps--;
        }

        Vec3 vec3 = this.getDeltaMovement();
        double d0 = vec3.x;
        double d1 = vec3.y;
        double d2 = vec3.z;
        if (Math.abs(vec3.x) < 0.003) {
            d0 = 0.0;
        }

        if (Math.abs(vec3.y) < 0.003) {
            d1 = 0.0;
        }

        if (Math.abs(vec3.z) < 0.003) {
            d2 = 0.0;
        }

        this.setDeltaMovement(d0, d1, d2);
        this.level().getProfiler().push("ai");
        if (this.isImmobile()) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.zza = 0.0F;
        } else if (this.isEffectiveAi()) {
            this.level().getProfiler().push("newAi");
            this.serverAiStep();
            this.level().getProfiler().pop();
        }

        this.level().getProfiler().pop();
        this.level().getProfiler().push("jump");
        if (this.jumping && this.isAffectedByFluids()) {
            double d3;
            net.neoforged.neoforge.fluids.FluidType fluidType = this.getMaxHeightFluidType();
            if (!fluidType.isAir()) d3 = this.getFluidTypeHeight(fluidType);
            else
            if (this.isInLava()) {
                d3 = this.getFluidHeight(FluidTags.LAVA);
            } else {
                d3 = this.getFluidHeight(FluidTags.WATER);
            }

            boolean flag = this.isInWater() && d3 > 0.0;
            double d4 = this.getFluidJumpThreshold();
            if (!flag || this.onGround() && !(d3 > d4)) {
                if (!this.isInLava() || this.onGround() && !(d3 > d4)) {
                    if (fluidType.isAir() || this.onGround() && !(d3 > d4)) {
                    if ((this.onGround() || flag && d3 <= d4) && this.noJumpDelay == 0) {
                        this.jumpFromGround();
                        this.noJumpDelay = 10;
                    }
                    } else this.jumpInFluid(fluidType);
                } else {
                    this.jumpInFluid(net.neoforged.neoforge.common.NeoForgeMod.LAVA_TYPE.value());
                }
            } else {
                this.jumpInFluid(net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value());
            }
        } else {
            this.noJumpDelay = 0;
        }

        this.level().getProfiler().pop();
        this.level().getProfiler().push("travel");
        this.xxa *= 0.98F;
        this.zza *= 0.98F;
        this.updateFallFlying();
        AABB aabb = this.getBoundingBox();
        Vec3 vec31 = new Vec3((double)this.xxa, (double)this.yya, (double)this.zza);
        if (this.hasEffect(MobEffects.SLOW_FALLING) || this.hasEffect(MobEffects.LEVITATION)) {
            this.resetFallDistance();
        }

        label104: {
            if (this.getControllingPassenger() instanceof Player player && this.isAlive()) {
                this.travelRidden(player, vec31);
                break label104;
            }

            this.travel(vec31);
        }

        this.level().getProfiler().pop();
        this.level().getProfiler().push("freezing");
        if (!this.level().isClientSide && !this.isDeadOrDying()) {
            int i = this.getTicksFrozen();
            if (this.isInPowderSnow && this.canFreeze()) {
                this.setTicksFrozen(Math.min(this.getTicksRequiredToFreeze(), i + 1));
            } else {
                this.setTicksFrozen(Math.max(0, i - 2));
            }
        }

        this.removeFrost();
        this.tryAddFrost();
        if (!this.level().isClientSide && this.tickCount % 40 == 0 && this.isFullyFrozen() && this.canFreeze()) {
            this.hurt(this.damageSources().freeze(), 1.0F);
        }

        this.level().getProfiler().pop();
        this.level().getProfiler().push("push");
        if (this.autoSpinAttackTicks > 0) {
            this.autoSpinAttackTicks--;
            this.checkAutoSpinAttack(aabb, this.getBoundingBox());
        }

        this.pushEntities();
        this.level().getProfiler().pop();
        if (!this.level().isClientSide && this.isSensitiveToWater() && this.isInWaterRainOrBubble()) {
            this.hurt(this.damageSources().drown(), 1.0F);
        }
    }

    public boolean isSensitiveToWater() {
        return false;
    }

    private void updateFallFlying() {
        boolean flag = this.getSharedFlag(7);
        if (flag && !this.onGround() && !this.isPassenger() && !this.hasEffect(MobEffects.LEVITATION)) {
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.CHEST);
            flag = itemstack.canElytraFly(this) && itemstack.elytraFlightTick(this, this.fallFlyTicks);
            if (false) //Neo: Moved to ElytraItem
            if (itemstack.is(Items.ELYTRA) && ElytraItem.isFlyEnabled(itemstack)) {
                flag = true;
                int i = this.fallFlyTicks + 1;
                if (!this.level().isClientSide && i % 10 == 0) {
                    int j = i / 10;
                    if (j % 2 == 0) {
                        itemstack.hurtAndBreak(1, this, EquipmentSlot.CHEST);
                    }

                    this.gameEvent(GameEvent.ELYTRA_GLIDE);
                }
            } else {
                flag = false;
            }
        } else {
            flag = false;
        }

        if (!this.level().isClientSide) {
            this.setSharedFlag(7, flag);
        }
    }

    protected void serverAiStep() {
    }

    protected void pushEntities() {
        if (this.level().isClientSide()) {
            this.level().getEntities(EntityTypeTest.forClass(Player.class), this.getBoundingBox(), EntitySelector.pushableBy(this)).forEach(this::doPush);
        } else {
            List<Entity> list = this.level().getEntities(this, this.getBoundingBox(), EntitySelector.pushableBy(this));
            if (!list.isEmpty()) {
                int i = this.level().getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
                if (i > 0 && list.size() > i - 1 && this.random.nextInt(4) == 0) {
                    int j = 0;

                    for (Entity entity : list) {
                        if (!entity.isPassenger()) {
                            j++;
                        }
                    }

                    if (j > i - 1) {
                        this.hurt(this.damageSources().cramming(), 6.0F);
                    }
                }

                for (Entity entity1 : list) {
                    this.doPush(entity1);
                }
            }
        }
    }

    protected void checkAutoSpinAttack(AABB boundingBoxBeforeSpin, AABB boundingBoxAfterSpin) {
        AABB aabb = boundingBoxBeforeSpin.minmax(boundingBoxAfterSpin);
        List<Entity> list = this.level().getEntities(this, aabb);
        if (!list.isEmpty()) {
            for (Entity entity : list) {
                if (entity instanceof LivingEntity) {
                    this.doAutoAttackOnTouch((LivingEntity)entity);
                    this.autoSpinAttackTicks = 0;
                    this.setDeltaMovement(this.getDeltaMovement().scale(-0.2));
                    break;
                }
            }
        } else if (this.horizontalCollision) {
            this.autoSpinAttackTicks = 0;
        }

        if (!this.level().isClientSide && this.autoSpinAttackTicks <= 0) {
            this.setLivingEntityFlag(4, false);
            this.autoSpinAttackDmg = 0.0F;
            this.autoSpinAttackItemStack = null;
        }
    }

    protected void doPush(Entity entity) {
        entity.push(this);
    }

    protected void doAutoAttackOnTouch(LivingEntity target) {
    }

    public boolean isAutoSpinAttack() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 4) != 0;
    }

    @Override
    public void stopRiding() {
        Entity entity = this.getVehicle();
        super.stopRiding();
        if (entity != null && entity != this.getVehicle() && !this.level().isClientSide) {
            this.dismountVehicle(entity);
        }
    }

    @Override
    public void rideTick() {
        super.rideTick();
        this.oRun = this.run;
        this.run = 0.0F;
        this.resetFallDistance();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = (double)yRot;
        this.lerpXRot = (double)xRot;
        this.lerpSteps = steps;
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : this.getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return this.lerpSteps > 0 ? (float)this.lerpXRot : this.getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return this.lerpSteps > 0 ? (float)this.lerpYRot : this.getYRot();
    }

    @Override
    public void lerpHeadTo(float yaw, int pitch) {
        this.lerpYHeadRot = (double)yaw;
        this.lerpHeadSteps = pitch;
    }

    public void setJumping(boolean jumping) {
        this.jumping = jumping;
    }

    public void onItemPickup(ItemEntity itemEntity) {
        Entity entity = itemEntity.getOwner();
        if (entity instanceof ServerPlayer) {
            CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_ENTITY.trigger((ServerPlayer)entity, itemEntity.getItem(), this);
        }
    }

    /**
     * Called when the entity picks up an item.
     */
    public void take(Entity entity, int amount) {
        if (!entity.isRemoved()
            && !this.level().isClientSide
            && (entity instanceof ItemEntity || entity instanceof AbstractArrow || entity instanceof ExperienceOrb)) {
            ((ServerLevel)this.level()).getChunkSource().broadcast(entity, new ClientboundTakeItemEntityPacket(entity.getId(), this.getId(), amount));
        }
    }

    public boolean hasLineOfSight(Entity entity) {
        if (entity.level() != this.level()) {
            return false;
        } else {
            Vec3 vec3 = new Vec3(this.getX(), this.getEyeY(), this.getZ());
            Vec3 vec31 = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
            return vec31.distanceTo(vec3) > 128.0
                ? false
                : this.level().clip(new ClipContext(vec3, vec31, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
        }
    }

    /**
     * Gets the current yaw of the entity
     */
    @Override
    public float getViewYRot(float partialTicks) {
        return partialTicks == 1.0F ? this.yHeadRot : Mth.lerp(partialTicks, this.yHeadRotO, this.yHeadRot);
    }

    /**
     * Gets the progression of the swing animation, ranges from 0.0 to 1.0.
     */
    public float getAttackAnim(float partialTick) {
        float f = this.attackAnim - this.oAttackAnim;
        if (f < 0.0F) {
            f++;
        }

        return this.oAttackAnim + f * partialTick;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return this.isAlive() && !this.isSpectator() && !this.onClimbable();
    }

    @Override
    public float getYHeadRot() {
        return this.yHeadRot;
    }

    /**
     * Sets the head's yaw rotation of the entity.
     */
    @Override
    public void setYHeadRot(float rotation) {
        this.yHeadRot = rotation;
    }

    /**
     * Set the render yaw offset
     */
    @Override
    public void setYBodyRot(float offset) {
        this.yBodyRot = offset;
    }

    @Override
    public Vec3 getRelativePortalPosition(Direction.Axis axis, BlockUtil.FoundRectangle portal) {
        return resetForwardDirectionOfRelativePortalPosition(super.getRelativePortalPosition(axis, portal));
    }

    public static Vec3 resetForwardDirectionOfRelativePortalPosition(Vec3 relativePortalPosition) {
        return new Vec3(relativePortalPosition.x, relativePortalPosition.y, 0.0);
    }

    public float getAbsorptionAmount() {
        return this.absorptionAmount;
    }

    public final void setAbsorptionAmount(float absorptionAmount) {
        this.internalSetAbsorptionAmount(Mth.clamp(absorptionAmount, 0.0F, this.getMaxAbsorption()));
    }

    protected void internalSetAbsorptionAmount(float absorptionAmount) {
        this.absorptionAmount = absorptionAmount;
    }

    public void onEnterCombat() {
    }

    public void onLeaveCombat() {
    }

    protected void updateEffectVisibility() {
        this.effectsDirty = true;
    }

    public abstract HumanoidArm getMainArm();

    public boolean isUsingItem() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 1) > 0;
    }

    public InteractionHand getUsedItemHand() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 2) > 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private void updatingUsingItem() {
        if (this.isUsingItem()) {
            ItemStack itemStack = this.getItemInHand(this.getUsedItemHand());
            if (net.neoforged.neoforge.common.CommonHooks.canContinueUsing(this.useItem, itemStack)) {
                this.useItem = itemStack;
            }
            if (itemStack == this.useItem) {
                this.updateUsingItem(this.useItem);
            } else {
                this.stopUsingItem();
            }
        }
    }

    protected void updateUsingItem(ItemStack usingItem) {
        if (!usingItem.isEmpty())
            this.useItemRemaining = net.neoforged.neoforge.event.EventHooks.onItemUseTick(this, usingItem, this.getUseItemRemainingTicks());
        if (this.getUseItemRemainingTicks() > 0)
        usingItem.onUseTick(this.level(), this, this.getUseItemRemainingTicks());
        if (this.shouldTriggerItemUseEffects()) {
            this.triggerItemUseEffects(usingItem, 5);
        }

        if (--this.useItemRemaining <= 0 && !this.level().isClientSide && !usingItem.useOnRelease()) {
            this.completeUsingItem();
        }
    }

    private boolean shouldTriggerItemUseEffects() {
        int i = this.useItem.getUseDuration(this) - this.getUseItemRemainingTicks();
        int j = (int)((float)this.useItem.getUseDuration(this) * 0.21875F);
        boolean flag = i > j;
        return flag && this.getUseItemRemainingTicks() % 4 == 0;
    }

    private void updateSwimAmount() {
        this.swimAmountO = this.swimAmount;
        if (this.isVisuallySwimming()) {
            this.swimAmount = Math.min(1.0F, this.swimAmount + 0.09F);
        } else {
            this.swimAmount = Math.max(0.0F, this.swimAmount - 0.09F);
        }
    }

    protected void setLivingEntityFlag(int key, boolean value) {
        int i = this.entityData.get(DATA_LIVING_ENTITY_FLAGS);
        if (value) {
            i |= key;
        } else {
            i &= ~key;
        }

        this.entityData.set(DATA_LIVING_ENTITY_FLAGS, (byte)i);
    }

    public void startUsingItem(InteractionHand hand) {
        ItemStack itemstack = this.getItemInHand(hand);
        if (!itemstack.isEmpty() && !this.isUsingItem()) {
            int duration = net.neoforged.neoforge.event.EventHooks.onItemUseStart(this, itemstack, hand, itemstack.getUseDuration(this));
            if (duration < 0) return; // Neo: Early return for negative values, as that indicates event cancellation.
            this.useItem = itemstack;
            this.useItemRemaining = duration;
            if (!this.level().isClientSide) {
                this.setLivingEntityFlag(1, true);
                this.setLivingEntityFlag(2, hand == InteractionHand.OFF_HAND);
                this.gameEvent(GameEvent.ITEM_INTERACT_START);
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SLEEPING_POS_ID.equals(key)) {
            if (this.level().isClientSide) {
                this.getSleepingPos().ifPresent(this::setPosToBed);
            }
        } else if (DATA_LIVING_ENTITY_FLAGS.equals(key) && this.level().isClientSide) {
            if (this.isUsingItem() && this.useItem.isEmpty()) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                if (!this.useItem.isEmpty()) {
                    this.useItemRemaining = this.useItem.getUseDuration(this);
                }
            } else if (!this.isUsingItem() && !this.useItem.isEmpty()) {
                this.useItem = ItemStack.EMPTY;
                this.useItemRemaining = 0;
            }
        }
    }

    @Override
    public void lookAt(EntityAnchorArgument.Anchor anchor, Vec3 target) {
        super.lookAt(anchor, target);
        this.yHeadRotO = this.yHeadRot;
        this.yBodyRot = this.yHeadRot;
        this.yBodyRotO = this.yBodyRot;
    }

    @Override
    public float getPreciseBodyRotation(float partialTick) {
        return Mth.lerp(partialTick, this.yBodyRotO, this.yBodyRot);
    }

    protected void triggerItemUseEffects(ItemStack stack, int amount) {
        if (!stack.isEmpty() && this.isUsingItem()) {
            if (stack.getUseAnimation() == UseAnim.DRINK) {
                this.playSound(this.getDrinkingSound(stack), 0.5F, this.level().random.nextFloat() * 0.1F + 0.9F);
            }

            if (stack.getUseAnimation() == UseAnim.EAT) {
                this.spawnItemParticles(stack, amount);
                this.playSound(
                    this.getEatingSound(stack),
                    0.5F + 0.5F * (float)this.random.nextInt(2),
                    (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F
                );
            }
        }
    }

    private void spawnItemParticles(ItemStack stack, int amount) {
        for (int i = 0; i < amount; i++) {
            Vec3 vec3 = new Vec3(((double)this.random.nextFloat() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, 0.0);
            vec3 = vec3.xRot(-this.getXRot() * (float) (Math.PI / 180.0));
            vec3 = vec3.yRot(-this.getYRot() * (float) (Math.PI / 180.0));
            double d0 = (double)(-this.random.nextFloat()) * 0.6 - 0.3;
            Vec3 vec31 = new Vec3(((double)this.random.nextFloat() - 0.5) * 0.3, d0, 0.6);
            vec31 = vec31.xRot(-this.getXRot() * (float) (Math.PI / 180.0));
            vec31 = vec31.yRot(-this.getYRot() * (float) (Math.PI / 180.0));
            vec31 = vec31.add(this.getX(), this.getEyeY(), this.getZ());
            this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, stack), vec31.x, vec31.y, vec31.z, vec3.x, vec3.y + 0.05, vec3.z);
        }
    }

    protected void completeUsingItem() {
        if (!this.level().isClientSide || this.isUsingItem()) {
            InteractionHand interactionhand = this.getUsedItemHand();
            if (!this.useItem.equals(this.getItemInHand(interactionhand))) {
                this.releaseUsingItem();
            } else {
                if (!this.useItem.isEmpty() && this.isUsingItem()) {
                    this.triggerItemUseEffects(this.useItem, 16);
                    ItemStack copy = this.useItem.copy();
                    ItemStack itemstack = net.neoforged.neoforge.event.EventHooks.onItemUseFinish(this, copy, getUseItemRemainingTicks(), this.useItem.finishUsingItem(this.level(), this));
                    if (itemstack != this.useItem) {
                        this.setItemInHand(interactionhand, itemstack);
                    }

                    this.stopUsingItem();
                }
            }
        }
    }

    public ItemStack getUseItem() {
        return this.useItem;
    }

    public int getUseItemRemainingTicks() {
        return this.useItemRemaining;
    }

    public int getTicksUsingItem() {
        return this.isUsingItem() ? this.useItem.getUseDuration(this) - this.getUseItemRemainingTicks() : 0;
    }

    public void releaseUsingItem() {
        if (!this.useItem.isEmpty()) {
            if (!net.neoforged.neoforge.event.EventHooks.onUseItemStop(this, useItem, this.getUseItemRemainingTicks())) {
                ItemStack copy = this instanceof Player ? useItem.copy() : null;
            this.useItem.releaseUsing(this.level(), this, this.getUseItemRemainingTicks());
              if (copy != null && useItem.isEmpty()) net.neoforged.neoforge.event.EventHooks.onPlayerDestroyItem((Player)this, copy, getUsedItemHand());
            }
            if (this.useItem.useOnRelease()) {
                this.updatingUsingItem();
            }
        }

        this.stopUsingItem();
    }

    public void stopUsingItem() {
        if (this.isUsingItem() && !this.useItem.isEmpty()) this.useItem.onStopUsing(this, useItemRemaining);
        if (!this.level().isClientSide) {
            boolean flag = this.isUsingItem();
            this.setLivingEntityFlag(1, false);
            if (flag) {
                this.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
            }
        }

        this.useItem = ItemStack.EMPTY;
        this.useItemRemaining = 0;
    }

    public boolean isBlocking() {
        if (this.isUsingItem() && !this.useItem.isEmpty()) {
            Item item = this.useItem.getItem();
            return !this.useItem.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHIELD_BLOCK) ? false : item.getUseDuration(this.useItem, this) - this.useItemRemaining >= 5;
        } else {
            return false;
        }
    }

    public boolean isSuppressingSlidingDownLadder() {
        return this.isShiftKeyDown();
    }

    public boolean isFallFlying() {
        return this.getSharedFlag(7);
    }

    @Override
    public boolean isVisuallySwimming() {
        return super.isVisuallySwimming() || !this.isFallFlying() && this.hasPose(Pose.FALL_FLYING);
    }

    public int getFallFlyingTicks() {
        return this.fallFlyTicks;
    }

    public boolean randomTeleport(double x, double y, double z, boolean broadcastTeleport) {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();
        double d3 = y;
        boolean flag = false;
        BlockPos blockpos = BlockPos.containing(x, y, z);
        Level level = this.level();
        if (level.hasChunkAt(blockpos)) {
            boolean flag1 = false;

            while (!flag1 && blockpos.getY() > level.getMinBuildHeight()) {
                BlockPos blockpos1 = blockpos.below();
                BlockState blockstate = level.getBlockState(blockpos1);
                if (blockstate.blocksMotion()) {
                    flag1 = true;
                } else {
                    d3--;
                    blockpos = blockpos1;
                }
            }

            if (flag1) {
                this.teleportTo(x, d3, z);
                if (level.noCollision(this) && !level.containsAnyLiquid(this.getBoundingBox())) {
                    flag = true;
                }
            }
        }

        if (!flag) {
            this.teleportTo(d0, d1, d2);
            return false;
        } else {
            if (broadcastTeleport) {
                level.broadcastEntityEvent(this, (byte)46);
            }

            if (this instanceof PathfinderMob pathfindermob) {
                pathfindermob.getNavigation().stop();
            }

            return true;
        }
    }

    public boolean isAffectedByPotions() {
        return !this.isDeadOrDying();
    }

    public boolean attackable() {
        return true;
    }

    /**
     * Called when a record starts or stops playing. Used to make parrots start or stop partying.
     */
    public void setRecordPlayingNearby(BlockPos jukebox, boolean partyParrot) {
    }

    public boolean canTakeItem(ItemStack stack) {
        return false;
    }

    @Override
    public final EntityDimensions getDimensions(Pose pose) {
        return pose == Pose.SLEEPING ? SLEEPING_DIMENSIONS : this.getDefaultDimensions(pose).scale(this.getScale());
    }

    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return this.getType().getDimensions().scale(this.getAgeScale());
    }

    public ImmutableList<Pose> getDismountPoses() {
        return ImmutableList.of(Pose.STANDING);
    }

    public AABB getLocalBoundsForPose(Pose pose) {
        EntityDimensions entitydimensions = this.getDimensions(pose);
        return new AABB(
            (double)(-entitydimensions.width() / 2.0F),
            0.0,
            (double)(-entitydimensions.width() / 2.0F),
            (double)(entitydimensions.width() / 2.0F),
            (double)entitydimensions.height(),
            (double)(entitydimensions.width() / 2.0F)
        );
    }

    protected boolean wouldNotSuffocateAtTargetPose(Pose pose) {
        AABB aabb = this.getDimensions(pose).makeBoundingBox(this.position());
        return this.level().noBlockCollision(this, aabb);
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return super.canUsePortal(allowPassengers) && !this.isSleeping();
    }

    public Optional<BlockPos> getSleepingPos() {
        return this.entityData.get(SLEEPING_POS_ID);
    }

    public void setSleepingPos(BlockPos pos) {
        this.entityData.set(SLEEPING_POS_ID, Optional.of(pos));
    }

    public void clearSleepingPos() {
        this.entityData.set(SLEEPING_POS_ID, Optional.empty());
    }

    public boolean isSleeping() {
        return this.getSleepingPos().isPresent();
    }

    public void startSleeping(BlockPos pos) {
        if (this.isPassenger()) {
            this.stopRiding();
        }

        BlockState blockstate = this.level().getBlockState(pos);
        if (blockstate.isBed(level(), pos, this)) {
            blockstate.setBedOccupied(level(), pos, this, true);
        }

        this.setPose(Pose.SLEEPING);
        this.setPosToBed(pos);
        this.setSleepingPos(pos);
        this.setDeltaMovement(Vec3.ZERO);
        this.hasImpulse = true;
    }

    /**
     * Sets entity position to a supplied BlockPos plus a little offset
     */
    private void setPosToBed(BlockPos pos) {
        this.setPos((double)pos.getX() + 0.5, (double)pos.getY() + 0.6875, (double)pos.getZ() + 0.5);
    }

    private boolean checkBedExists() {
        // Neo: Overwrite the vanilla instanceof BedBlock check with isBed and fire the CanContinueSleepingEvent.
        boolean hasBed = this.getSleepingPos().map(pos -> this.level().getBlockState(pos).isBed(this.level(), pos, this)).orElse(false);
        return net.neoforged.neoforge.event.EventHooks.canEntityContinueSleeping(this, hasBed ? null : Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
    }

    public void stopSleeping() {
        this.getSleepingPos().filter(this.level()::hasChunkAt).ifPresent(p_261435_ -> {
            BlockState blockstate = this.level().getBlockState(p_261435_);
            if (blockstate.isBed(level(), p_261435_, this)) {
                Direction direction = blockstate.getValue(BedBlock.FACING);
                blockstate.setBedOccupied(level(), p_261435_, this, false);
                Vec3 vec31 = BedBlock.findStandUpPosition(this.getType(), this.level(), p_261435_, direction, this.getYRot()).orElseGet(() -> {
                    BlockPos blockpos = p_261435_.above();
                    return new Vec3((double)blockpos.getX() + 0.5, (double)blockpos.getY() + 0.1, (double)blockpos.getZ() + 0.5);
                });
                Vec3 vec32 = Vec3.atBottomCenterOf(p_261435_).subtract(vec31).normalize();
                float f = (float)Mth.wrapDegrees(Mth.atan2(vec32.z, vec32.x) * 180.0F / (float)Math.PI - 90.0);
                this.setPos(vec31.x, vec31.y, vec31.z);
                this.setYRot(f);
                this.setXRot(0.0F);
            }
        });
        Vec3 vec3 = this.position();
        this.setPose(Pose.STANDING);
        this.setPos(vec3.x, vec3.y, vec3.z);
        this.clearSleepingPos();
    }

    @Nullable
    public Direction getBedOrientation() {
        BlockPos blockpos = this.getSleepingPos().orElse(null);
        if (blockpos == null) return Direction.UP;
        BlockState state = this.level().getBlockState(blockpos);
        return !state.isBed(level(), blockpos, this) ? Direction.UP : state.getBedDirection(level(), blockpos);
    }

    @Override
    public boolean isInWall() {
        return !this.isSleeping() && super.isInWall();
    }

    /**
     * Gets an item stack available to this entity to be loaded into the provided weapon, or an empty item stack if no such item stack is available.
     */
    public ItemStack getProjectile(ItemStack weaponStack) {
        return net.neoforged.neoforge.common.CommonHooks.getProjectile(this, weaponStack, ItemStack.EMPTY);
    }

    public final ItemStack eat(Level level, ItemStack food) {
        FoodProperties foodproperties = food.getFoodProperties(this);
        return foodproperties != null ? this.eat(level, food, foodproperties) : food;
    }

    public ItemStack eat(Level level, ItemStack food, FoodProperties foodProperties) {
        level.playSound(
            null,
            this.getX(),
            this.getY(),
            this.getZ(),
            this.getEatingSound(food),
            SoundSource.NEUTRAL,
            1.0F,
            1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.4F
        );
        this.addEatEffect(foodProperties);
        food.consume(1, this);
        this.gameEvent(GameEvent.EAT);
        return food;
    }

    private void addEatEffect(FoodProperties foodProperties) {
        if (!this.level().isClientSide()) {
            for (FoodProperties.PossibleEffect foodproperties$possibleeffect : foodProperties.effects()) {
                if (this.random.nextFloat() < foodproperties$possibleeffect.probability()) {
                    this.addEffect(foodproperties$possibleeffect.effect());
                }
            }
        }
    }

    private static byte entityEventForEquipmentBreak(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> 47;
            case OFFHAND -> 48;
            case HEAD -> 49;
            case CHEST -> 50;
            case FEET -> 52;
            case LEGS -> 51;
            case BODY -> 65;
        };
    }

    public void onEquippedItemBroken(Item item, EquipmentSlot slot) {
        this.level().broadcastEntityEvent(this, entityEventForEquipmentBreak(slot));
    }

    public static EquipmentSlot getSlotForHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    /**
     * Neo: Removes all potion effects that have the given {@link net.neoforged.neoforge.common.EffectCure} in their set of cures
     * @param cure the EffectCure being used
     */
    public boolean removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCure cure) {
        if (this.level().isClientSide)
            return false;
        boolean ret = false;
        Iterator<MobEffectInstance> itr = this.activeEffects.values().iterator();
        while (itr.hasNext()) {
            MobEffectInstance effect = itr.next();
            if (effect.getCures().contains(cure) && !net.neoforged.neoforge.event.EventHooks.onEffectRemoved(this, effect, cure)) {
                this.onEffectRemoved(effect);
                itr.remove();
                ret = true;
                this.effectsDirty = true;
            }
        }
        return ret;
    }

    /**
     * Neo: Returns true if the entity's rider (EntityPlayer) should face forward when mounted.
     * currently only used in vanilla code by pigs.
     *
     * @param player The player who is riding the entity.
     * @return If the player should orient the same direction as this entity.
     */
    public boolean shouldRiderFaceForward(Player player) {
        return this instanceof net.minecraft.world.entity.animal.Pig;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        if (this.getItemBySlot(EquipmentSlot.HEAD).is(Items.DRAGON_HEAD)) {
            float f = 0.5F;
            return this.getBoundingBox().inflate(0.5, 0.5, 0.5);
        } else {
            return super.getBoundingBoxForCulling();
        }
    }

    public EquipmentSlot getEquipmentSlotForItem(ItemStack stack) {
        final EquipmentSlot slot = stack.getEquipmentSlot();
        if (slot != null) return slot; // FORGE: Allow modders to set a non-default equipment slot for a stack; e.g. a non-armor chestplate-slot item
        Equipable equipable = Equipable.get(stack);
        if (equipable != null) {
            EquipmentSlot equipmentslot = equipable.getEquipmentSlot();
            if (this.canUseSlot(equipmentslot)) {
                return equipmentslot;
            }
        }

        return EquipmentSlot.MAINHAND;
    }

    private static SlotAccess createEquipmentSlotAccess(LivingEntity entity, EquipmentSlot slot) {
        return slot != EquipmentSlot.HEAD && slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND
            ? SlotAccess.forEquipmentSlot(entity, slot, p_348156_ -> p_348156_.isEmpty() || entity.getEquipmentSlotForItem(p_348156_) == slot)
            : SlotAccess.forEquipmentSlot(entity, slot);
    }

    @Nullable
    private static EquipmentSlot getEquipmentSlot(int index) {
        if (index == 100 + EquipmentSlot.HEAD.getIndex()) {
            return EquipmentSlot.HEAD;
        } else if (index == 100 + EquipmentSlot.CHEST.getIndex()) {
            return EquipmentSlot.CHEST;
        } else if (index == 100 + EquipmentSlot.LEGS.getIndex()) {
            return EquipmentSlot.LEGS;
        } else if (index == 100 + EquipmentSlot.FEET.getIndex()) {
            return EquipmentSlot.FEET;
        } else if (index == 98) {
            return EquipmentSlot.MAINHAND;
        } else if (index == 99) {
            return EquipmentSlot.OFFHAND;
        } else {
            return index == 105 ? EquipmentSlot.BODY : null;
        }
    }

    @Override
    public SlotAccess getSlot(int slot) {
        EquipmentSlot equipmentslot = getEquipmentSlot(slot);
        return equipmentslot != null ? createEquipmentSlotAccess(this, equipmentslot) : super.getSlot(slot);
    }

    @Override
    public boolean canFreeze() {
        if (this.isSpectator()) {
            return false;
        } else {
            boolean flag = !this.getItemBySlot(EquipmentSlot.HEAD).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
                && !this.getItemBySlot(EquipmentSlot.CHEST).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
                && !this.getItemBySlot(EquipmentSlot.LEGS).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
                && !this.getItemBySlot(EquipmentSlot.FEET).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
                && !this.getItemBySlot(EquipmentSlot.BODY).is(ItemTags.FREEZE_IMMUNE_WEARABLES);
            return flag && super.canFreeze();
        }
    }

    @Override
    public boolean isCurrentlyGlowing() {
        return !this.level().isClientSide() && this.hasEffect(MobEffects.GLOWING) || super.isCurrentlyGlowing();
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return this.yBodyRot;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        double d0 = packet.getX();
        double d1 = packet.getY();
        double d2 = packet.getZ();
        float f = packet.getYRot();
        float f1 = packet.getXRot();
        this.syncPacketPositionCodec(d0, d1, d2);
        this.yBodyRot = packet.getYHeadRot();
        this.yHeadRot = packet.getYHeadRot();
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.setId(packet.getId());
        this.setUUID(packet.getUUID());
        this.absMoveTo(d0, d1, d2, f, f1);
        this.setDeltaMovement(packet.getXa(), packet.getYa(), packet.getZa());
    }

    public boolean canDisableShield() {
        return this.getMainHandItem().canDisableShield(this.useItem, this, this); // Neo: Switch to using the item to determine if disables shield instead of hardcoded Axe check
    }

    @Override
    public float maxUpStep() {
        float f = (float)this.getAttributeValue(Attributes.STEP_HEIGHT);
        return this.getControllingPassenger() instanceof Player ? Math.max(f, 1.0F) : f;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity entity) {
        return this.position().add(this.getPassengerAttachmentPoint(entity, this.getDimensions(this.getPose()), this.getScale() * this.getAgeScale()));
    }

    protected void lerpHeadRotationStep(int lerpHeadSteps, double lerpYHeadRot) {
        this.yHeadRot = (float)Mth.rotLerp(1.0 / (double)lerpHeadSteps, (double)this.yHeadRot, lerpYHeadRot);
    }

    @Override
    public void igniteForTicks(int ticks) {
        super.igniteForTicks(Mth.ceil((double)ticks * this.getAttributeValue(Attributes.BURNING_TIME)));
    }

    public boolean hasInfiniteMaterials() {
        return false;
    }

    /**
     * Returns whether this Entity is invulnerable to the given DamageSource.
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (super.isInvulnerableTo(source)) {
            return true;
        } else {
            if (this.level() instanceof ServerLevel serverlevel && EnchantmentHelper.isImmuneToDamage(serverlevel, this, source)) {
                return true;
            }

            return false;
        }
    }

    public static record Fallsounds(SoundEvent small, SoundEvent big) {
    }
}
