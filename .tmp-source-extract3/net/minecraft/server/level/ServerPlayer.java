package net.minecraft.server.level;

import com.google.common.net.InetAddresses;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ComplexItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ServerItemCooldowns;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.slf4j.Logger;

public class ServerPlayer extends Player {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_XZ = 32;
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_Y = 10;
    private static final int FLY_STAT_RECORDING_SPEED = 25;
    public static final double INTERACTION_DISTANCE_VERIFICATION_BUFFER = 1.0;
    private static final AttributeModifier CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER = new AttributeModifier(
        ResourceLocation.withDefaultNamespace("creative_mode_block_range"), 0.5, AttributeModifier.Operation.ADD_VALUE
    );
    private static final AttributeModifier CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER = new AttributeModifier(
        ResourceLocation.withDefaultNamespace("creative_mode_entity_range"), 2.0, AttributeModifier.Operation.ADD_VALUE
    );
    public ServerGamePacketListenerImpl connection;
    public final MinecraftServer server;
    public final ServerPlayerGameMode gameMode;
    private final PlayerAdvancements advancements;
    private final ServerStatsCounter stats;
    /**
     * the total health of the player, includes actual health and absorption health. Updated every tick.
     */
    private float lastRecordedHealthAndAbsorption = Float.MIN_VALUE;
    private int lastRecordedFoodLevel = Integer.MIN_VALUE;
    private int lastRecordedAirLevel = Integer.MIN_VALUE;
    private int lastRecordedArmor = Integer.MIN_VALUE;
    private int lastRecordedLevel = Integer.MIN_VALUE;
    private int lastRecordedExperience = Integer.MIN_VALUE;
    private float lastSentHealth = -1.0E8F;
    private int lastSentFood = -99999999;
    private boolean lastFoodSaturationZero = true;
    private int lastSentExp = -99999999;
    private int spawnInvulnerableTime = 60;
    private ChatVisiblity chatVisibility = ChatVisiblity.FULL;
    private boolean canChatColor = true;
    private long lastActionTime = Util.getMillis();
    /**
     * The entity the player is currently spectating through.
     */
    @Nullable
    private Entity camera;
    private boolean isChangingDimension;
    public boolean seenCredits;
    private final ServerRecipeBook recipeBook = new ServerRecipeBook();
    @Nullable
    private Vec3 levitationStartPos;
    private int levitationStartTime;
    private boolean disconnected;
    private int requestedViewDistance = 2;
    private String language = "en_us";
    @Nullable
    private Vec3 startingToFallPosition;
    @Nullable
    private Vec3 enteredNetherPosition;
    @Nullable
    private Vec3 enteredLavaOnVehiclePosition;
    /**
     * Player section position as last updated by TicketManager, used by ChunkManager
     */
    private SectionPos lastSectionPos = SectionPos.of(0, 0, 0);
    private ChunkTrackingView chunkTrackingView = ChunkTrackingView.EMPTY;
    private ResourceKey<Level> respawnDimension = Level.OVERWORLD;
    @Nullable
    private BlockPos respawnPosition;
    private boolean respawnForced;
    private float respawnAngle;
    private final TextFilter textFilter;
    private boolean textFilteringEnabled;
    private boolean allowsListing;
    private boolean spawnExtraParticlesOnFall;
    private WardenSpawnTracker wardenSpawnTracker = new WardenSpawnTracker(0, 0, 0);
    @Nullable
    private BlockPos raidOmenPosition;
    private Vec3 lastKnownClientMovement = Vec3.ZERO;
    private final ContainerSynchronizer containerSynchronizer = new ContainerSynchronizer() {
        @Override
        public void sendInitialData(AbstractContainerMenu container, NonNullList<ItemStack> items, ItemStack carriedItem, int[] initialData) {
            ServerPlayer.this.connection
                .send(new ClientboundContainerSetContentPacket(container.containerId, container.incrementStateId(), items, carriedItem));

            for (int i = 0; i < initialData.length; i++) {
                this.broadcastDataValue(container, i, initialData[i]);
            }
        }

        @Override
        public void sendSlotChange(AbstractContainerMenu container, int slot, ItemStack itemStack) {
            ServerPlayer.this.connection.send(new ClientboundContainerSetSlotPacket(container.containerId, container.incrementStateId(), slot, itemStack));
        }

        @Override
        public void sendCarriedChange(AbstractContainerMenu containerMenu, ItemStack stack) {
            ServerPlayer.this.connection.send(new ClientboundContainerSetSlotPacket(-1, containerMenu.incrementStateId(), -1, stack));
        }

        @Override
        public void sendDataChange(AbstractContainerMenu container, int id, int value) {
            this.broadcastDataValue(container, id, value);
        }

        private void broadcastDataValue(AbstractContainerMenu container, int id, int value) {
            if (ServerPlayer.this.connection.hasChannel(net.neoforged.neoforge.network.payload.AdvancedContainerSetDataPayload.TYPE)) {
                ServerPlayer.this.connection.send(new net.neoforged.neoforge.network.payload.AdvancedContainerSetDataPayload((byte) container.containerId, (short) id, value));
                return;
            }
            ServerPlayer.this.connection.send(new ClientboundContainerSetDataPacket(container.containerId, id, value));
        }
    };
    private final ContainerListener containerListener = new ContainerListener() {
        @Override
        public void slotChanged(AbstractContainerMenu p_143466_, int p_143467_, ItemStack p_143468_) {
            Slot slot = p_143466_.getSlot(p_143467_);
            if (!(slot instanceof ResultSlot)) {
                if (slot.container == ServerPlayer.this.getInventory()) {
                    CriteriaTriggers.INVENTORY_CHANGED.trigger(ServerPlayer.this, ServerPlayer.this.getInventory(), p_143468_);
                }
            }
        }

        @Override
        public void dataChanged(AbstractContainerMenu p_143462_, int p_143463_, int p_143464_) {
        }
    };
    @Nullable
    private RemoteChatSession chatSession;
    @Nullable
    public final Object object;
    private int containerCounter;
    public boolean wonGame;

    public ServerPlayer(MinecraftServer server, ServerLevel level, GameProfile gameProfile, ClientInformation clientInformation) {
        super(level, level.getSharedSpawnPos(), level.getSharedSpawnAngle(), gameProfile);
        this.textFilter = server.createTextFilterForPlayer(this);
        this.gameMode = server.createGameModeForPlayer(this);
        this.server = server;
        this.stats = server.getPlayerList().getPlayerStats(this);
        this.advancements = server.getPlayerList().getPlayerAdvancements(this);
        this.moveTo(this.adjustSpawnLocation(level, level.getSharedSpawnPos()).getBottomCenter(), 0.0F, 0.0F);
        this.updateOptions(clientInformation);
        this.object = null;
    }

    @Override
    public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos pos) {
        AABB aabb = this.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);
        BlockPos blockpos = pos;
        if (level.dimensionType().hasSkyLight() && level.getServer().getWorldData().getGameType() != GameType.ADVENTURE) {
            int i = Math.max(0, this.server.getSpawnRadius(level));
            int j = Mth.floor(level.getWorldBorder().getDistanceToBorder((double)pos.getX(), (double)pos.getZ()));
            if (j < i) {
                i = j;
            }

            if (j <= 1) {
                i = 1;
            }

            long k = (long)(i * 2 + 1);
            long l = k * k;
            int i1 = l > 2147483647L ? Integer.MAX_VALUE : (int)l;
            int j1 = this.getCoprime(i1);
            int k1 = RandomSource.create().nextInt(i1);

            for (int l1 = 0; l1 < i1; l1++) {
                int i2 = (k1 + j1 * l1) % i1;
                int j2 = i2 % (i * 2 + 1);
                int k2 = i2 / (i * 2 + 1);
                blockpos = PlayerRespawnLogic.getOverworldRespawnPos(level, pos.getX() + j2 - i, pos.getZ() + k2 - i);
                if (blockpos != null && level.noCollision(this, aabb.move(blockpos.getBottomCenter()))) {
                    return blockpos;
                }
            }

            blockpos = pos;
        }

        while (!level.noCollision(this, aabb.move(blockpos.getBottomCenter())) && blockpos.getY() < level.getMaxBuildHeight() - 1) {
            blockpos = blockpos.above();
        }

        while (level.noCollision(this, aabb.move(blockpos.below().getBottomCenter())) && blockpos.getY() > level.getMinBuildHeight() + 1) {
            blockpos = blockpos.below();
        }

        return blockpos;
    }

    private int getCoprime(int spawnArea) {
        return spawnArea <= 16 ? spawnArea - 1 : 17;
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("warden_spawn_tracker", 10)) {
            WardenSpawnTracker.CODEC
                .parse(new Dynamic<>(NbtOps.INSTANCE, compound.get("warden_spawn_tracker")))
                .resultOrPartial(LOGGER::error)
                .ifPresent(p_248205_ -> this.wardenSpawnTracker = p_248205_);
        }

        if (compound.contains("enteredNetherPosition", 10)) {
            CompoundTag compoundtag = compound.getCompound("enteredNetherPosition");
            this.enteredNetherPosition = new Vec3(compoundtag.getDouble("x"), compoundtag.getDouble("y"), compoundtag.getDouble("z"));
        }

        this.seenCredits = compound.getBoolean("seenCredits");
        if (compound.contains("recipeBook", 10)) {
            this.recipeBook.fromNbt(compound.getCompound("recipeBook"), this.server.getRecipeManager());
        }

        if (this.isSleeping()) {
            this.stopSleeping();
        }

        if (compound.contains("SpawnX", 99) && compound.contains("SpawnY", 99) && compound.contains("SpawnZ", 99)) {
            this.respawnPosition = new BlockPos(compound.getInt("SpawnX"), compound.getInt("SpawnY"), compound.getInt("SpawnZ"));
            this.respawnForced = compound.getBoolean("SpawnForced");
            this.respawnAngle = compound.getFloat("SpawnAngle");
            if (compound.contains("SpawnDimension")) {
                this.respawnDimension = Level.RESOURCE_KEY_CODEC
                    .parse(NbtOps.INSTANCE, compound.get("SpawnDimension"))
                    .resultOrPartial(LOGGER::error)
                    .orElse(Level.OVERWORLD);
            }
        }

        this.spawnExtraParticlesOnFall = compound.getBoolean("spawn_extra_particles_on_fall");
        Tag tag = compound.get("raid_omen_position");
        if (tag != null) {
            BlockPos.CODEC.parse(NbtOps.INSTANCE, tag).resultOrPartial(LOGGER::error).ifPresent(p_337547_ -> this.raidOmenPosition = p_337547_);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        WardenSpawnTracker.CODEC
            .encodeStart(NbtOps.INSTANCE, this.wardenSpawnTracker)
            .resultOrPartial(LOGGER::error)
            .ifPresent(p_9134_ -> compound.put("warden_spawn_tracker", p_9134_));
        this.storeGameTypes(compound);
        compound.putBoolean("seenCredits", this.seenCredits);
        if (this.enteredNetherPosition != null) {
            CompoundTag compoundtag = new CompoundTag();
            compoundtag.putDouble("x", this.enteredNetherPosition.x);
            compoundtag.putDouble("y", this.enteredNetherPosition.y);
            compoundtag.putDouble("z", this.enteredNetherPosition.z);
            compound.put("enteredNetherPosition", compoundtag);
        }

        Entity entity1 = this.getRootVehicle();
        Entity entity = this.getVehicle();
        if (entity != null && entity1 != this && entity1.hasExactlyOnePlayerPassenger()) {
            CompoundTag compoundtag1 = new CompoundTag();
            CompoundTag compoundtag2 = new CompoundTag();
            entity1.save(compoundtag2);
            compoundtag1.putUUID("Attach", entity.getUUID());
            compoundtag1.put("Entity", compoundtag2);
            compound.put("RootVehicle", compoundtag1);
        }

        compound.put("recipeBook", this.recipeBook.toNbt());
        compound.putString("Dimension", this.level().dimension().location().toString());
        if (this.respawnPosition != null) {
            compound.putInt("SpawnX", this.respawnPosition.getX());
            compound.putInt("SpawnY", this.respawnPosition.getY());
            compound.putInt("SpawnZ", this.respawnPosition.getZ());
            compound.putBoolean("SpawnForced", this.respawnForced);
            compound.putFloat("SpawnAngle", this.respawnAngle);
            ResourceLocation.CODEC
                .encodeStart(NbtOps.INSTANCE, this.respawnDimension.location())
                .resultOrPartial(LOGGER::error)
                .ifPresent(p_248207_ -> compound.put("SpawnDimension", p_248207_));
        }

        compound.putBoolean("spawn_extra_particles_on_fall", this.spawnExtraParticlesOnFall);
        if (this.raidOmenPosition != null) {
            BlockPos.CODEC
                .encodeStart(NbtOps.INSTANCE, this.raidOmenPosition)
                .resultOrPartial(LOGGER::error)
                .ifPresent(p_337549_ -> compound.put("raid_omen_position", p_337549_));
        }
    }

    public void setExperiencePoints(int experiencePoints) {
        float f = (float)this.getXpNeededForNextLevel();
        float f1 = (f - 1.0F) / f;
        this.experienceProgress = Mth.clamp((float)experiencePoints / f, 0.0F, f1);
        this.lastSentExp = -1;
    }

    public void setExperienceLevels(int level) {
        this.experienceLevel = level;
        this.lastSentExp = -1;
    }

    /**
     * Add experience levels to this player.
     */
    @Override
    public void giveExperienceLevels(int levels) {
        super.giveExperienceLevels(levels);
        this.lastSentExp = -1;
    }

    @Override
    public void onEnchantmentPerformed(ItemStack enchantedItem, int cost) {
        super.onEnchantmentPerformed(enchantedItem, cost);
        this.lastSentExp = -1;
    }

    private void initMenu(AbstractContainerMenu menu) {
        menu.addSlotListener(this.containerListener);
        menu.setSynchronizer(this.containerSynchronizer);
    }

    public void initInventoryMenu() {
        this.initMenu(this.inventoryMenu);
    }

    @Override
    public void onEnterCombat() {
        super.onEnterCombat();
        this.connection.send(ClientboundPlayerCombatEnterPacket.INSTANCE);
    }

    @Override
    public void onLeaveCombat() {
        super.onLeaveCombat();
        this.connection.send(new ClientboundPlayerCombatEndPacket(this.getCombatTracker()));
    }

    @Override
    public void onInsideBlock(BlockState state) {
        CriteriaTriggers.ENTER_BLOCK.trigger(this, state);
    }

    @Override
    protected ItemCooldowns createItemCooldowns() {
        return new ServerItemCooldowns(this);
    }

    @Override
    public void tick() {
        this.gameMode.tick();
        this.wardenSpawnTracker.tick();
        this.spawnInvulnerableTime--;
        if (this.invulnerableTime > 0) {
            this.invulnerableTime--;
        }

        this.containerMenu.broadcastChanges();
        if (!this.level().isClientSide && !this.containerMenu.stillValid(this)) {
            this.closeContainer();
            this.containerMenu = this.inventoryMenu;
        }

        Entity entity = this.getCamera();
        if (entity != this) {
            if (entity.isAlive()) {
                this.absMoveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                this.serverLevel().getChunkSource().move(this);
                if (this.wantsToStopRiding()) {
                    this.setCamera(this);
                }
            } else {
                this.setCamera(this);
            }
        }

        CriteriaTriggers.TICK.trigger(this);
        if (this.levitationStartPos != null) {
            CriteriaTriggers.LEVITATION.trigger(this, this.levitationStartPos, this.tickCount - this.levitationStartTime);
        }

        this.trackStartFallingPosition();
        this.trackEnteredOrExitedLavaOnVehicle();
        this.updatePlayerAttributes();
        this.advancements.flushDirty(this);
    }

    private void updatePlayerAttributes() {
        AttributeInstance attributeinstance = this.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (attributeinstance != null) {
            if (this.isCreative()) {
                attributeinstance.addOrUpdateTransientModifier(CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER);
            } else {
                attributeinstance.removeModifier(CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER);
            }
        }

        AttributeInstance attributeinstance1 = this.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (attributeinstance1 != null) {
            if (this.isCreative()) {
                attributeinstance1.addOrUpdateTransientModifier(CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER);
            } else {
                attributeinstance1.removeModifier(CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER);
            }
        }
    }

    public void doTick() {
        try {
            if (!this.isSpectator() || !this.touchingUnloadedChunk()) {
                super.tick();
            }

            for (int i = 0; i < this.getInventory().getContainerSize(); i++) {
                ItemStack itemstack = this.getInventory().getItem(i);
                if (itemstack.getItem().isComplex()) {
                    Packet<?> packet = ((ComplexItem)itemstack.getItem()).getUpdatePacket(itemstack, this.level(), this);
                    if (packet != null) {
                        this.connection.send(packet);
                    }
                }
            }

            if (this.getHealth() != this.lastSentHealth
                || this.lastSentFood != this.foodData.getFoodLevel()
                || this.foodData.getSaturationLevel() == 0.0F != this.lastFoodSaturationZero) {
                this.connection.send(new ClientboundSetHealthPacket(this.getHealth(), this.foodData.getFoodLevel(), this.foodData.getSaturationLevel()));
                this.lastSentHealth = this.getHealth();
                this.lastSentFood = this.foodData.getFoodLevel();
                this.lastFoodSaturationZero = this.foodData.getSaturationLevel() == 0.0F;
            }

            if (this.getHealth() + this.getAbsorptionAmount() != this.lastRecordedHealthAndAbsorption) {
                this.lastRecordedHealthAndAbsorption = this.getHealth() + this.getAbsorptionAmount();
                this.updateScoreForCriteria(ObjectiveCriteria.HEALTH, Mth.ceil(this.lastRecordedHealthAndAbsorption));
            }

            if (this.foodData.getFoodLevel() != this.lastRecordedFoodLevel) {
                this.lastRecordedFoodLevel = this.foodData.getFoodLevel();
                this.updateScoreForCriteria(ObjectiveCriteria.FOOD, Mth.ceil((float)this.lastRecordedFoodLevel));
            }

            if (this.getAirSupply() != this.lastRecordedAirLevel) {
                this.lastRecordedAirLevel = this.getAirSupply();
                this.updateScoreForCriteria(ObjectiveCriteria.AIR, Mth.ceil((float)this.lastRecordedAirLevel));
            }

            if (this.getArmorValue() != this.lastRecordedArmor) {
                this.lastRecordedArmor = this.getArmorValue();
                this.updateScoreForCriteria(ObjectiveCriteria.ARMOR, Mth.ceil((float)this.lastRecordedArmor));
            }

            if (this.totalExperience != this.lastRecordedExperience) {
                this.lastRecordedExperience = this.totalExperience;
                this.updateScoreForCriteria(ObjectiveCriteria.EXPERIENCE, Mth.ceil((float)this.lastRecordedExperience));
            }

            if (this.experienceLevel != this.lastRecordedLevel) {
                this.lastRecordedLevel = this.experienceLevel;
                this.updateScoreForCriteria(ObjectiveCriteria.LEVEL, Mth.ceil((float)this.lastRecordedLevel));
            }

            if (this.totalExperience != this.lastSentExp) {
                this.lastSentExp = this.totalExperience;
                this.connection.send(new ClientboundSetExperiencePacket(this.experienceProgress, this.totalExperience, this.experienceLevel));
            }

            if (this.getAbilities().flying && !this.mayFly()) {
                this.getAbilities().flying = false;
                this.onUpdateAbilities();
            }

            if (this.tickCount % 20 == 0) {
                CriteriaTriggers.LOCATION.trigger(this);
            }
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking player");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Player being ticked");
            this.fillCrashReportCategory(crashreportcategory);
            throw new ReportedException(crashreport);
        }
    }

    @Override
    public void resetFallDistance() {
        if (this.getHealth() > 0.0F && this.startingToFallPosition != null) {
            CriteriaTriggers.FALL_FROM_HEIGHT.trigger(this, this.startingToFallPosition);
        }

        this.startingToFallPosition = null;
        super.resetFallDistance();
    }

    public void trackStartFallingPosition() {
        if (this.fallDistance > 0.0F && this.startingToFallPosition == null) {
            this.startingToFallPosition = this.position();
            if (this.currentImpulseImpactPos != null && this.currentImpulseImpactPos.y <= this.startingToFallPosition.y) {
                CriteriaTriggers.FALL_AFTER_EXPLOSION.trigger(this, this.currentImpulseImpactPos, this.currentExplosionCause);
            }
        }
    }

    public void trackEnteredOrExitedLavaOnVehicle() {
        if (this.getVehicle() != null && this.getVehicle().isInLava()) {
            if (this.enteredLavaOnVehiclePosition == null) {
                this.enteredLavaOnVehiclePosition = this.position();
            } else {
                CriteriaTriggers.RIDE_ENTITY_IN_LAVA_TRIGGER.trigger(this, this.enteredLavaOnVehiclePosition);
            }
        }

        if (this.enteredLavaOnVehiclePosition != null && (this.getVehicle() == null || !this.getVehicle().isInLava())) {
            this.enteredLavaOnVehiclePosition = null;
        }
    }

    private void updateScoreForCriteria(ObjectiveCriteria criteria, int points) {
        this.getScoreboard().forAllObjectives(criteria, this, p_313589_ -> p_313589_.set(points));
    }

    /**
     * Called when the mob's health reaches 0.
     */
    @Override
    public void die(DamageSource cause) {
        this.gameEvent(GameEvent.ENTITY_DIE);
        if (net.neoforged.neoforge.common.CommonHooks.onLivingDeath(this, cause)) return;
        boolean flag = this.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES);
        if (flag) {
            Component component = this.getCombatTracker().getDeathMessage();
            this.connection
                .send(
                    new ClientboundPlayerCombatKillPacket(this.getId(), component),
                    PacketSendListener.exceptionallySend(
                        () -> {
                            int i = 256;
                            String s = component.getString(256);
                            Component component1 = Component.translatable(
                                "death.attack.message_too_long", Component.literal(s).withStyle(ChatFormatting.YELLOW)
                            );
                            Component component2 = Component.translatable("death.attack.even_more_magic", this.getDisplayName())
                                .withStyle(p_143420_ -> p_143420_.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, component1)));
                            return new ClientboundPlayerCombatKillPacket(this.getId(), component2);
                        }
                    )
                );
            Team team = this.getTeam();
            if (team == null || team.getDeathMessageVisibility() == Team.Visibility.ALWAYS) {
                this.server.getPlayerList().broadcastSystemMessage(component, false);
            } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
                this.server.getPlayerList().broadcastSystemToTeam(this, component);
            } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OWN_TEAM) {
                this.server.getPlayerList().broadcastSystemToAllExceptTeam(this, component);
            }
        } else {
            this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), CommonComponents.EMPTY));
        }

        this.removeEntitiesOnShoulder();
        if (this.level().getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
            this.tellNeutralMobsThatIDied();
        }

        if (!this.isSpectator()) {
            this.dropAllDeathLoot(this.serverLevel(), cause);
        }

        this.getScoreboard().forAllObjectives(ObjectiveCriteria.DEATH_COUNT, this, ScoreAccess::increment);
        LivingEntity livingentity = this.getKillCredit();
        if (livingentity != null) {
            this.awardStat(Stats.ENTITY_KILLED_BY.get(livingentity.getType()));
            livingentity.awardKillScore(this, this.deathScore, cause);
            this.createWitherRose(livingentity);
        }

        this.level().broadcastEntityEvent(this, (byte)3);
        this.awardStat(Stats.DEATHS);
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        this.clearFire();
        this.setTicksFrozen(0);
        this.setSharedFlagOnFire(false);
        this.getCombatTracker().recheckStatus();
        this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
    }

    private void tellNeutralMobsThatIDied() {
        AABB aabb = new AABB(this.blockPosition()).inflate(32.0, 10.0, 32.0);
        this.level()
            .getEntitiesOfClass(Mob.class, aabb, EntitySelector.NO_SPECTATORS)
            .stream()
            .filter(p_9188_ -> p_9188_ instanceof NeutralMob)
            .forEach(p_9057_ -> ((NeutralMob)p_9057_).playerDied(this));
    }

    @Override
    public void awardKillScore(Entity killed, int scoreValue, DamageSource damageSource) {
        if (killed != this) {
            super.awardKillScore(killed, scoreValue, damageSource);
            this.increaseScore(scoreValue);
            this.getScoreboard().forAllObjectives(ObjectiveCriteria.KILL_COUNT_ALL, this, ScoreAccess::increment);
            if (killed instanceof Player) {
                this.awardStat(Stats.PLAYER_KILLS);
                this.getScoreboard().forAllObjectives(ObjectiveCriteria.KILL_COUNT_PLAYERS, this, ScoreAccess::increment);
            } else {
                this.awardStat(Stats.MOB_KILLS);
            }

            this.handleTeamKill(this, killed, ObjectiveCriteria.TEAM_KILL);
            this.handleTeamKill(killed, this, ObjectiveCriteria.KILLED_BY_TEAM);
            CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(this, killed, damageSource);
        }
    }

    private void handleTeamKill(ScoreHolder scoreHolder, ScoreHolder teamMember, ObjectiveCriteria[] crtieria) {
        PlayerTeam playerteam = this.getScoreboard().getPlayersTeam(teamMember.getScoreboardName());
        if (playerteam != null) {
            int i = playerteam.getColor().getId();
            if (i >= 0 && i < crtieria.length) {
                this.getScoreboard().forAllObjectives(crtieria[i], scoreHolder, ScoreAccess::increment);
            }
        }
    }

    /**
     * Called when the entity is attacked.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            boolean flag = this.server.isDedicatedServer() && this.isPvpAllowed() && source.is(DamageTypeTags.IS_FALL);
            if (!flag && this.spawnInvulnerableTime > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return false;
            } else {
                Entity entity = source.getEntity();
                if (entity instanceof Player player && !this.canHarmPlayer(player)) {
                    return false;
                }

                if (entity instanceof AbstractArrow abstractarrow && abstractarrow.getOwner() instanceof Player player1 && !this.canHarmPlayer(player1)) {
                    return false;
                }

                return super.hurt(source, amount);
            }
        }
    }

    @Override
    public boolean canHarmPlayer(Player other) {
        return !this.isPvpAllowed() ? false : super.canHarmPlayer(other);
    }

    private boolean isPvpAllowed() {
        return this.server.isPvpAllowed();
    }

    public DimensionTransition findRespawnPositionAndUseSpawnBlock(boolean keepInventory, DimensionTransition.PostDimensionTransition postDimensionTransition) {
        BlockPos blockpos = this.getRespawnPosition();
        float f = this.getRespawnAngle();
        boolean flag = this.isRespawnForced();
        ServerLevel serverlevel = this.server.getLevel(this.getRespawnDimension());
        if (serverlevel != null && blockpos != null) {
            Optional<ServerPlayer.RespawnPosAngle> optional = findRespawnAndUseSpawnBlock(serverlevel, blockpos, f, flag, keepInventory);
            if (optional.isPresent()) {
                ServerPlayer.RespawnPosAngle serverplayer$respawnposangle = optional.get();
                return new DimensionTransition(
                    serverlevel, serverplayer$respawnposangle.position(), Vec3.ZERO, serverplayer$respawnposangle.yaw(), 0.0F, postDimensionTransition
                );
            } else {
                return DimensionTransition.missingRespawnBlock(this.server.overworld(), this, postDimensionTransition);
            }
        } else {
            return new DimensionTransition(this.server.overworld(), this, postDimensionTransition);
        }
    }

    private static Optional<ServerPlayer.RespawnPosAngle> findRespawnAndUseSpawnBlock(
        ServerLevel level, BlockPos pos, float angle, boolean forced, boolean keepInventory
    ) {
        BlockState blockstate = level.getBlockState(pos);
        Block block = blockstate.getBlock();
        if (block instanceof RespawnAnchorBlock
            && (forced || blockstate.getValue(RespawnAnchorBlock.CHARGE) > 0)
            && RespawnAnchorBlock.canSetSpawn(level)) {
            Optional<Vec3> optional = RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, level, pos);
            if (!forced && !keepInventory && optional.isPresent()) {
                level.setBlock(
                    pos, blockstate.setValue(RespawnAnchorBlock.CHARGE, Integer.valueOf(blockstate.getValue(RespawnAnchorBlock.CHARGE) - 1)), 3
                );
            }

            return optional.map(p_348139_ -> ServerPlayer.RespawnPosAngle.of(p_348139_, pos));
        } else if (block instanceof BedBlock && BedBlock.canSetSpawn(level)) {
            return BedBlock.findStandUpPosition(EntityType.PLAYER, level, pos, blockstate.getValue(BedBlock.FACING), angle)
                .map(p_348148_ -> ServerPlayer.RespawnPosAngle.of(p_348148_, pos));
        } else if (!forced) {
            return blockstate.getRespawnPosition(EntityType.PLAYER, level, pos, angle);
        } else {
            boolean flag = block.isPossibleToRespawnInThis(blockstate);
            BlockState blockstate1 = level.getBlockState(pos.above());
            boolean flag1 = blockstate1.getBlock().isPossibleToRespawnInThis(blockstate1);
            return flag && flag1
                ? Optional.of(
                    new ServerPlayer.RespawnPosAngle(
                        new Vec3((double)pos.getX() + 0.5, (double)pos.getY() + 0.1, (double)pos.getZ() + 0.5), angle
                    )
                )
                : Optional.empty();
        }
    }

    public void showEndCredits() {
        this.unRide();
        this.serverLevel().removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
        if (!this.wonGame) {
            this.wonGame = true;
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 0.0F));
            this.seenCredits = true;
        }
    }

    @Nullable
    @Override
    public Entity changeDimension(DimensionTransition transition) {
        if (!net.neoforged.neoforge.common.CommonHooks.onTravelToDimension(this, transition.newLevel().dimension())) return null;
        if (this.isRemoved()) {
            return null;
        } else {
            if (transition.missingRespawnBlock()) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
            }

            ServerLevel serverlevel = transition.newLevel();
            ServerLevel serverlevel1 = this.serverLevel();
            ResourceKey<Level> resourcekey = serverlevel1.dimension();
            if (serverlevel.dimension() == resourcekey) {
                this.connection.teleport(transition.pos().x, transition.pos().y, transition.pos().z, transition.yRot(), transition.xRot());
                this.connection.resetPosition();
                transition.postDimensionTransition().onTransition(this);
                return this;
            } else {
                this.isChangingDimension = true;
                LevelData leveldata = serverlevel.getLevelData();
                this.connection.send(new ClientboundRespawnPacket(this.createCommonSpawnInfo(serverlevel), (byte)3));
                this.connection.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
                PlayerList playerlist = this.server.getPlayerList();
                playerlist.sendPlayerPermissionLevel(this);
                serverlevel1.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
                this.revive();
                serverlevel1.getProfiler().push("moving");
                if (resourcekey == Level.OVERWORLD && serverlevel.dimension() == Level.NETHER) {
                    this.enteredNetherPosition = this.position();
                }

                serverlevel1.getProfiler().pop();
                serverlevel1.getProfiler().push("placing");
                this.setServerLevel(serverlevel);
                this.connection.teleport(transition.pos().x, transition.pos().y, transition.pos().z, transition.yRot(), transition.xRot());
                this.connection.resetPosition();
                serverlevel.addDuringTeleport(this);
                serverlevel1.getProfiler().pop();
                this.triggerDimensionChangeTriggers(serverlevel1);
                this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
                playerlist.sendLevelInfo(this, serverlevel);
                playerlist.sendAllPlayerInfo(this);
                playerlist.sendActivePlayerEffects(this);
                // TODO 1.21: Play custom teleport sound
                transition.postDimensionTransition().onTransition(this);
                this.lastSentExp = -1;
                this.lastSentHealth = -1.0F;
                this.lastSentFood = -1;
                // Neo: On the client all attachments are lost on dimension change and need to be re-sent
                net.neoforged.neoforge.attachment.AttachmentSync.syncInitialPlayerAttachments(this);
                net.neoforged.neoforge.event.EventHooks.firePlayerChangedDimensionEvent(this, resourcekey, transition.newLevel().dimension());
                return this;
            }
        }
    }

    private void triggerDimensionChangeTriggers(ServerLevel level) {
        ResourceKey<Level> resourcekey = level.dimension();
        ResourceKey<Level> resourcekey1 = this.level().dimension();
        CriteriaTriggers.CHANGED_DIMENSION.trigger(this, resourcekey, resourcekey1);
        if (resourcekey == Level.NETHER && resourcekey1 == Level.OVERWORLD && this.enteredNetherPosition != null) {
            CriteriaTriggers.NETHER_TRAVEL.trigger(this, this.enteredNetherPosition);
        }

        if (resourcekey1 != Level.NETHER) {
            this.enteredNetherPosition = null;
        }
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        if (player.isSpectator()) {
            return this.getCamera() == this;
        } else {
            return this.isSpectator() ? false : super.broadcastToPlayer(player);
        }
    }

    /**
     * Called when the entity picks up an item.
     */
    @Override
    public void take(Entity entity, int quantity) {
        super.take(entity, quantity);
        this.containerMenu.broadcastChanges();
    }

    @Override
    public Either<Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos at) {
        // Neo: Encapsulate the vanilla check logic to supply to the CanPlayerSleepEvent
        var vanillaResult = ((java.util.function.Supplier<Either<BedSleepingProblem, Unit>>) () -> {
        // Guard against modded beds that may not have the FACING property.
        // We just return success (Unit) here. Modders will need to implement conditions in the CanPlayerSleepEvent
        if (!this.level().getBlockState(at).hasProperty(HorizontalDirectionalBlock.FACING)) {
            return Either.right(Unit.INSTANCE);
        }

        // Start vanilla code
        Direction direction = this.level().getBlockState(at).getValue(HorizontalDirectionalBlock.FACING);
        if (this.isSleeping() || !this.isAlive()) {
            return Either.left(Player.BedSleepingProblem.OTHER_PROBLEM);
        } else if (!this.level().dimensionType().natural()) {
            return Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
        } else if (!this.bedInRange(at, direction)) {
            return Either.left(Player.BedSleepingProblem.TOO_FAR_AWAY);
        } else if (this.bedBlocked(at, direction)) {
            return Either.left(Player.BedSleepingProblem.OBSTRUCTED);
        } else {
            this.setRespawnPosition(this.level().dimension(), at, this.getYRot(), false, true);
            if (this.level().isDay()) {
                return Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_NOW);
            } else {
                if (!this.isCreative()) {
                    double d0 = 8.0;
                    double d1 = 5.0;
                    Vec3 vec3 = Vec3.atBottomCenterOf(at);
                    List<Monster> list = this.level()
                        .getEntitiesOfClass(
                            Monster.class,
                            new AABB(vec3.x() - 8.0, vec3.y() - 5.0, vec3.z() - 8.0, vec3.x() + 8.0, vec3.y() + 5.0, vec3.z() + 8.0),
                            p_9062_ -> p_9062_.isPreventingPlayerRest(this)
                        );
                    if (!list.isEmpty()) {
                        return Either.left(Player.BedSleepingProblem.NOT_SAFE);
                    }
                }
        // End vanilla code
            }
        }
        return Either.right(Unit.INSTANCE);
        }).get();

        // Fire the event. Return the error if one exists after the event, otherwise use the vanilla logic to start sleeping.
        vanillaResult = net.neoforged.neoforge.event.EventHooks.canPlayerStartSleeping(this, at, vanillaResult);
        if (vanillaResult.left().isPresent()) {
            return vanillaResult;
        }

        {
            {
                // Start vanilla code
                Either<Player.BedSleepingProblem, Unit> either = super.startSleepInBed(at).ifRight(p_9029_ -> {
                    this.awardStat(Stats.SLEEP_IN_BED);
                    CriteriaTriggers.SLEPT_IN_BED.trigger(this);
                });
                if (!this.serverLevel().canSleepThroughNights()) {
                    this.displayClientMessage(Component.translatable("sleep.not_possible"), true);
                }

                ((ServerLevel)this.level()).updateSleepingPlayerList();
                return either;
            }
        }
    }

    @Override
    public void startSleeping(BlockPos pos) {
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        super.startSleeping(pos);
    }

    private boolean bedInRange(BlockPos pos, Direction direction) {
        if (direction == null) return false;
        return this.isReachableBedBlock(pos) || this.isReachableBedBlock(pos.relative(direction.getOpposite()));
    }

    private boolean isReachableBedBlock(BlockPos pos) {
        Vec3 vec3 = Vec3.atBottomCenterOf(pos);
        return Math.abs(this.getX() - vec3.x()) <= 3.0 && Math.abs(this.getY() - vec3.y()) <= 2.0 && Math.abs(this.getZ() - vec3.z()) <= 3.0;
    }

    private boolean bedBlocked(BlockPos pos, Direction direction) {
        BlockPos blockpos = pos.above();
        return !this.freeAt(blockpos) || !this.freeAt(blockpos.relative(direction.getOpposite()));
    }

    @Override
    public void stopSleepInBed(boolean wakeImmediately, boolean updateLevelForSleepingPlayers) {
        if (this.isSleeping()) {
            this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(this, 2));
        }

        super.stopSleepInBed(wakeImmediately, updateLevelForSleepingPlayers);
        if (this.connection != null) {
            this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        }
    }

    @Override
    public void dismountTo(double x, double y, double z) {
        this.removeVehicle();
        this.setPos(x, y, z);
    }

    /**
     * Returns whether this Entity is invulnerable to the given DamageSource.
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) || this.isChangingDimension();
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected void onChangedBlock(ServerLevel level, BlockPos pos) {
        if (!this.isSpectator()) {
            super.onChangedBlock(level, pos);
        }
    }

    public void doCheckFallDamage(double movementX, double movementY, double movementZ, boolean onGround) {
        if (!this.touchingUnloadedChunk()) {
            this.checkSupportingBlock(onGround, new Vec3(movementX, movementY, movementZ));
            BlockPos blockpos = this.getOnPosLegacy();
            BlockState blockstate = this.level().getBlockState(blockpos);
            if (this.spawnExtraParticlesOnFall && onGround && this.fallDistance > 0.0F) {
                Vec3 vec3 = blockpos.getCenter().add(0.0, 0.5, 0.0);
                int i = (int)Mth.clamp(50.0F * this.fallDistance, 0.0F, 200.0F);
                this.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockstate), vec3.x, vec3.y, vec3.z, i, 0.3F, 0.3F, 0.3F, 0.15F);
                this.spawnExtraParticlesOnFall = false;
            }

            super.checkFallDamage(movementY, onGround, blockstate, blockpos);
        }
    }

    @Override
    public void onExplosionHit(@Nullable Entity entity) {
        super.onExplosionHit(entity);
        this.currentImpulseImpactPos = this.position();
        this.currentExplosionCause = entity;
        this.setIgnoreFallDamageFromCurrentImpulse(entity != null && entity.getType() == EntityType.WIND_CHARGE);
    }

    @Override
    protected void pushEntities() {
        if (this.level().tickRateManager().runsNormally()) {
            super.pushEntities();
        }
    }

    @Override
    public void openTextEdit(SignBlockEntity signEntity, boolean isFrontText) {
        this.connection.send(new ClientboundBlockUpdatePacket(this.level(), signEntity.getBlockPos()));
        this.connection.send(new ClientboundOpenSignEditorPacket(signEntity.getBlockPos(), isFrontText));
    }

    private void nextContainerCounter() {
        this.containerCounter = this.containerCounter % 100 + 1;
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider menu) {
        return openMenu(menu, (java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf>) null);
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider menu, @Nullable java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> extraDataWriter) {
        if (menu == null) {
            return OptionalInt.empty();
        } else {
            if (this.containerMenu != this.inventoryMenu) {
                if (menu.shouldTriggerClientSideContainerClosingOnOpen())
                this.closeContainer();
                else
                    this.doCloseContainer();
            }

            this.nextContainerCounter();
            AbstractContainerMenu abstractcontainermenu = menu.createMenu(this.containerCounter, this.getInventory(), this);
            if (abstractcontainermenu == null) {
                if (this.isSpectator()) {
                    this.displayClientMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED), true);
                }

                return OptionalInt.empty();
            } else {
                // Neo: Support sending additional arbitrary data to menu factories on the client-side
                var extraData = net.neoforged.neoforge.common.util.FriendlyByteBufUtil.writeCustomData(
                        buffer -> {
                            menu.writeClientSideData(abstractcontainermenu, buffer);
                            if (extraDataWriter != null) {
                                extraDataWriter.accept(buffer);
                            }
                        },
                        registryAccess()
                );
                if (extraData.length != 0) {
                    this.connection.send(
                        new net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload(
                            abstractcontainermenu.containerId,
                            abstractcontainermenu.getType(),
                            menu.getDisplayName(),
                            extraData
                        )
                    );
                } else {
                this.connection
                    .send(new ClientboundOpenScreenPacket(abstractcontainermenu.containerId, abstractcontainermenu.getType(), menu.getDisplayName()));
                }
                this.initMenu(abstractcontainermenu);
                this.containerMenu = abstractcontainermenu;
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open(this, this.containerMenu));
                return OptionalInt.of(this.containerCounter);
            }
        }
    }

    @Override
    public void sendMerchantOffers(int containerId, MerchantOffers offers, int level, int xp, boolean showProgress, boolean canRestock) {
        this.connection.send(new ClientboundMerchantOffersPacket(containerId, offers, level, xp, showProgress, canRestock));
    }

    @Override
    public void openHorseInventory(AbstractHorse horse, Container inventory) {
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer();
        }

        this.nextContainerCounter();
        int i = horse.getInventoryColumns();
        this.connection.send(new ClientboundHorseScreenOpenPacket(this.containerCounter, i, horse.getId()));
        this.containerMenu = new HorseInventoryMenu(this.containerCounter, this.getInventory(), inventory, horse, i);
        this.initMenu(this.containerMenu);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open(this, this.containerMenu));
    }

    @Override
    public void openItemGui(ItemStack stack, InteractionHand hand) {
        if (stack.is(Items.WRITTEN_BOOK)) {
            if (WrittenBookItem.resolveBookComponents(stack, this.createCommandSourceStack(), this)) {
                this.containerMenu.broadcastChanges();
            }

            this.connection.send(new ClientboundOpenBookPacket(hand));
        }
    }

    @Override
    public void openCommandBlock(CommandBlockEntity commandBlock) {
        this.connection.send(ClientboundBlockEntityDataPacket.create(commandBlock, BlockEntity::saveCustomOnly));
    }

    @Override
    public void closeContainer() {
        this.connection.send(new ClientboundContainerClosePacket(this.containerMenu.containerId));
        this.doCloseContainer();
    }

    @Override
    public void doCloseContainer() {
        this.containerMenu.removed(this);
        this.inventoryMenu.transferState(this.containerMenu);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Close(this, this.containerMenu));
        this.containerMenu = this.inventoryMenu;
    }

    public void setPlayerInput(float strafe, float forward, boolean jumping, boolean sneaking) {
        if (this.isPassenger()) {
            if (strafe >= -1.0F && strafe <= 1.0F) {
                this.xxa = strafe;
            }

            if (forward >= -1.0F && forward <= 1.0F) {
                this.zza = forward;
            }

            this.jumping = jumping;
            this.setShiftKeyDown(sneaking);
        }
    }

    @Override
    public void travel(Vec3 travelVector) {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();
        super.travel(travelVector);
        this.checkMovementStatistics(this.getX() - d0, this.getY() - d1, this.getZ() - d2);
    }

    @Override
    public void rideTick() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();
        super.rideTick();
        this.checkRidingStatistics(this.getX() - d0, this.getY() - d1, this.getZ() - d2);
    }

    public void checkMovementStatistics(double dx, double dy, double dz) {
        if (!this.isPassenger() && !didNotMove(dx, dy, dz)) {
            if (this.isSwimming()) {
                int i = Math.round((float)Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);
                if (i > 0) {
                    this.awardStat(Stats.SWIM_ONE_CM, i);
                    this.causeFoodExhaustion(0.01F * (float)i * 0.01F);
                }
            } else if (this.isEyeInFluid(FluidTags.WATER)) {
                int j = Math.round((float)Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);
                if (j > 0) {
                    this.awardStat(Stats.WALK_UNDER_WATER_ONE_CM, j);
                    this.causeFoodExhaustion(0.01F * (float)j * 0.01F);
                }
            } else if (this.isInWater()) {
                int k = Math.round((float)Math.sqrt(dx * dx + dz * dz) * 100.0F);
                if (k > 0) {
                    this.awardStat(Stats.WALK_ON_WATER_ONE_CM, k);
                    this.causeFoodExhaustion(0.01F * (float)k * 0.01F);
                }
            } else if (this.onClimbable()) {
                if (dy > 0.0) {
                    this.awardStat(Stats.CLIMB_ONE_CM, (int)Math.round(dy * 100.0));
                }
            } else if (this.onGround()) {
                int l = Math.round((float)Math.sqrt(dx * dx + dz * dz) * 100.0F);
                if (l > 0) {
                    if (this.isSprinting()) {
                        this.awardStat(Stats.SPRINT_ONE_CM, l);
                        this.causeFoodExhaustion(0.1F * (float)l * 0.01F);
                    } else if (this.isCrouching()) {
                        this.awardStat(Stats.CROUCH_ONE_CM, l);
                        this.causeFoodExhaustion(0.0F * (float)l * 0.01F);
                    } else {
                        this.awardStat(Stats.WALK_ONE_CM, l);
                        this.causeFoodExhaustion(0.0F * (float)l * 0.01F);
                    }
                }
            } else if (this.isFallFlying()) {
                int i1 = Math.round((float)Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);
                this.awardStat(Stats.AVIATE_ONE_CM, i1);
            } else {
                int j1 = Math.round((float)Math.sqrt(dx * dx + dz * dz) * 100.0F);
                if (j1 > 25) {
                    this.awardStat(Stats.FLY_ONE_CM, j1);
                }
            }
        }
    }

    public void checkRidingStatistics(double dx, double dy, double dz) {
        if (this.isPassenger() && !didNotMove(dx, dy, dz)) {
            int i = Math.round((float)Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);
            Entity entity = this.getVehicle();
            if (entity instanceof AbstractMinecart) {
                this.awardStat(Stats.MINECART_ONE_CM, i);
            } else if (entity instanceof Boat) {
                this.awardStat(Stats.BOAT_ONE_CM, i);
            } else if (entity instanceof Pig) {
                this.awardStat(Stats.PIG_ONE_CM, i);
            } else if (entity instanceof AbstractHorse) {
                this.awardStat(Stats.HORSE_ONE_CM, i);
            } else if (entity instanceof Strider) {
                this.awardStat(Stats.STRIDER_ONE_CM, i);
            }
        }
    }

    private static boolean didNotMove(double dx, double dy, double dz) {
        return dx == 0.0 && dy == 0.0 && dz == 0.0;
    }

    /**
     * Adds a value to a statistic field.
     */
    @Override
    public void awardStat(Stat<?> stat, int amount) {
        this.stats.increment(this, stat, amount);
        this.getScoreboard().forAllObjectives(stat, this, p_313587_ -> p_313587_.add(amount));
    }

    @Override
    public void resetStat(Stat<?> stat) {
        this.stats.setValue(this, stat, 0);
        this.getScoreboard().forAllObjectives(stat, this, ScoreAccess::reset);
    }

    @Override
    public int awardRecipes(Collection<RecipeHolder<?>> recipes) {
        return this.recipeBook.addRecipes(recipes, this);
    }

    @Override
    public void triggerRecipeCrafted(RecipeHolder<?> recipe, List<ItemStack> items) {
        CriteriaTriggers.RECIPE_CRAFTED.trigger(this, recipe.id(), items);
    }

    @Override
    public void awardRecipesByKey(List<ResourceLocation> recipes) {
        List<RecipeHolder<?>> list = recipes.stream()
            .flatMap(p_311549_ -> this.server.getRecipeManager().byKey(p_311549_).stream())
            .collect(Collectors.toList());
        this.awardRecipes(list);
    }

    @Override
    public int resetRecipes(Collection<RecipeHolder<?>> recipes) {
        return this.recipeBook.removeRecipes(recipes, this);
    }

    @Override
    public void giveExperiencePoints(int xpPoints) {
        super.giveExperiencePoints(xpPoints);
        this.lastSentExp = -1;
    }

    public void disconnect() {
        this.disconnected = true;
        this.ejectPassengers();
        if (this.isSleeping()) {
            this.stopSleepInBed(true, false);
        }
    }

    public boolean hasDisconnected() {
        return this.disconnected;
    }

    public void resetSentInfo() {
        this.lastSentHealth = -1.0E8F;
    }

    @Override
    public void displayClientMessage(Component chatComponent, boolean actionBar) {
        this.sendSystemMessage(chatComponent, actionBar);
    }

    @Override
    protected void completeUsingItem() {
        if (!this.useItem.isEmpty() && this.isUsingItem()) {
            this.connection.send(new ClientboundEntityEventPacket(this, (byte)9));
            super.completeUsingItem();
        }
    }

    @Override
    public void lookAt(EntityAnchorArgument.Anchor anchor, Vec3 target) {
        super.lookAt(anchor, target);
        this.connection.send(new ClientboundPlayerLookAtPacket(anchor, target.x, target.y, target.z));
    }

    public void lookAt(EntityAnchorArgument.Anchor fromAnchor, Entity entity, EntityAnchorArgument.Anchor toAnchor) {
        Vec3 vec3 = toAnchor.apply(entity);
        super.lookAt(fromAnchor, vec3);
        this.connection.send(new ClientboundPlayerLookAtPacket(fromAnchor, entity, toAnchor));
    }

    public void restoreFrom(ServerPlayer that, boolean keepEverything) {
        this.wardenSpawnTracker = that.wardenSpawnTracker;
        this.chatSession = that.chatSession;
        this.gameMode.setGameModeForPlayer(that.gameMode.getGameModeForPlayer(), that.gameMode.getPreviousGameModeForPlayer());
        this.onUpdateAbilities();
        this.getAttributes().assignBaseValues(that.getAttributes());
        this.setHealth(this.getMaxHealth());
        if (keepEverything) {
            this.getInventory().replaceWith(that.getInventory());
            this.setHealth(that.getHealth());
            this.foodData = that.foodData;

            for (MobEffectInstance mobeffectinstance : that.getActiveEffects()) {
                this.addEffect(new MobEffectInstance(mobeffectinstance));
            }

            this.experienceLevel = that.experienceLevel;
            this.totalExperience = that.totalExperience;
            this.experienceProgress = that.experienceProgress;
            this.setScore(that.getScore());
            this.portalProcess = that.portalProcess;
        } else if (this.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || that.isSpectator()) {
            this.getInventory().replaceWith(that.getInventory());
            this.experienceLevel = that.experienceLevel;
            this.totalExperience = that.totalExperience;
            this.experienceProgress = that.experienceProgress;
            this.setScore(that.getScore());
        }

        this.enchantmentSeed = that.enchantmentSeed;
        this.enderChestInventory = that.enderChestInventory;
        this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, that.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION));
        this.lastSentExp = -1;
        this.lastSentHealth = -1.0F;
        this.lastSentFood = -1;
        this.recipeBook.copyOverData(that.recipeBook);
        this.seenCredits = that.seenCredits;
        this.enteredNetherPosition = that.enteredNetherPosition;
        this.chunkTrackingView = that.chunkTrackingView;
        this.setShoulderEntityLeft(that.getShoulderEntityLeft());
        this.setShoulderEntityRight(that.getShoulderEntityRight());
        this.setLastDeathLocation(that.getLastDeathLocation());

        //Copy over a section of the Entity Data from the old player.
        //Allows mods to specify data that persists after players respawn.
        CompoundTag old = that.getPersistentData();
        if (old.contains(PERSISTED_NBT_TAG))
             getPersistentData().put(PERSISTED_NBT_TAG, old.get(PERSISTED_NBT_TAG));
        net.neoforged.neoforge.event.EventHooks.onPlayerClone(this, that, !keepEverything);
        this.tabListHeader = that.tabListHeader;
        this.tabListFooter = that.tabListFooter;
    }

    @Override
    protected void onEffectAdded(MobEffectInstance effectInstance, @Nullable Entity entity) {
        super.onEffectAdded(effectInstance, entity);
        this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effectInstance, true));
        if (effectInstance.is(MobEffects.LEVITATION)) {
            this.levitationStartTime = this.tickCount;
            this.levitationStartPos = this.position();
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, entity);
    }

    @Override
    protected void onEffectUpdated(MobEffectInstance effectInstance, boolean forced, @Nullable Entity entity) {
        super.onEffectUpdated(effectInstance, forced, entity);
        this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effectInstance, false));
        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, entity);
    }

    @Override
    protected void onEffectRemoved(MobEffectInstance effect) {
        super.onEffectRemoved(effect);
        this.connection.send(new ClientboundRemoveMobEffectPacket(this.getId(), effect.getEffect()));
        if (effect.is(MobEffects.LEVITATION)) {
            this.levitationStartPos = null;
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, null);
    }

    /**
     * Sets the position of the entity and updates the 'last' variables
     */
    @Override
    public void teleportTo(double x, double y, double z) {
        this.connection.teleport(x, y, z, this.getYRot(), this.getXRot(), RelativeMovement.ROTATION);
    }

    @Override
    public void teleportRelative(double dx, double dy, double dz) {
        this.connection
            .teleport(this.getX() + dx, this.getY() + dy, this.getZ() + dz, this.getYRot(), this.getXRot(), RelativeMovement.ALL);
    }

    @Override
    public boolean teleportTo(
        ServerLevel level, double x, double y, double z, Set<RelativeMovement> relativeMovements, float yRot, float xRot
    ) {
        ChunkPos chunkpos = new ChunkPos(BlockPos.containing(x, y, z));
        level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkpos, 1, this.getId());
        this.stopRiding();
        if (this.isSleeping()) {
            this.stopSleepInBed(true, true);
        }

        if (level == this.level()) {
            this.connection.teleport(x, y, z, yRot, xRot, relativeMovements);
        } else {
            this.teleportTo(level, x, y, z, yRot, xRot);
        }

        this.setYHeadRot(yRot);
        return true;
    }

    @Override
    public void moveTo(double x, double y, double z) {
        super.moveTo(x, y, z);
        this.connection.resetPosition();
    }

    /**
     * Called when the entity is dealt a critical hit.
     */
    @Override
    public void crit(Entity entityHit) {
        this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(entityHit, 4));
    }

    /**
     * Called when the entity hit is dealt extra melee damage due to an enchantment.
     */
    @Override
    public void magicCrit(Entity entityHit) {
        this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(entityHit, 5));
    }

    @Override
    public void onUpdateAbilities() {
        if (this.connection != null) {
            this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
            this.updateInvisibilityStatus();
        }
    }

    public ServerLevel serverLevel() {
        return (ServerLevel)this.level();
    }

    public boolean setGameMode(GameType gameMode) {
        boolean flag = this.isSpectator();
        gameMode = net.neoforged.neoforge.common.CommonHooks.onChangeGameType(this, this.gameMode.getGameModeForPlayer(), gameMode);
        if (gameMode == null) return false;
        if (!this.gameMode.changeGameModeForPlayer(gameMode)) {
            return false;
        } else {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, (float)gameMode.getId()));
            if (gameMode == GameType.SPECTATOR) {
                this.removeEntitiesOnShoulder();
                this.stopRiding();
                EnchantmentHelper.stopLocationBasedEffects(this);
            } else {
                this.setCamera(this);
                if (flag) {
                    EnchantmentHelper.runLocationChangedEffects(this.serverLevel(), this);
                }
            }

            this.onUpdateAbilities();
            this.updateEffectVisibility();
            return true;
        }
    }

    @Override
    public boolean isSpectator() {
        return this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
    }

    @Override
    public boolean isCreative() {
        return this.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    @Override
    public void sendSystemMessage(Component component) {
        this.sendSystemMessage(component, false);
    }

    public void sendSystemMessage(Component p_component, boolean bypassHiddenChat) {
        if (this.acceptsSystemMessages(bypassHiddenChat)) {
            this.connection
                .send(
                    new ClientboundSystemChatPacket(p_component, bypassHiddenChat),
                    PacketSendListener.exceptionallySend(
                        () -> {
                            if (this.acceptsSystemMessages(false)) {
                                int i = 256;
                                String s = p_component.getString(256);
                                Component component = Component.literal(s).withStyle(ChatFormatting.YELLOW);
                                return new ClientboundSystemChatPacket(
                                    Component.translatable("multiplayer.message_not_delivered", component).withStyle(ChatFormatting.RED), false
                                );
                            } else {
                                return null;
                            }
                        }
                    )
                );
        }
    }

    public void sendChatMessage(OutgoingChatMessage message, boolean filtered, ChatType.Bound boundType) {
        if (this.acceptsChatMessages()) {
            message.sendToPlayer(this, filtered, boundType);
        }
    }

    public String getIpAddress() {
        return this.connection.getRemoteAddress() instanceof InetSocketAddress inetsocketaddress
            ? InetAddresses.toAddrString(inetsocketaddress.getAddress())
            : "<unknown>";
    }

    public void updateOptions(ClientInformation clientInformation) {
        this.language = clientInformation.language();
        this.requestedViewDistance = clientInformation.viewDistance();
        this.chatVisibility = clientInformation.chatVisibility();
        this.canChatColor = clientInformation.chatColors();
        this.textFilteringEnabled = clientInformation.textFilteringEnabled();
        this.allowsListing = clientInformation.allowsListing();
        this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte)clientInformation.modelCustomisation());
        this.getEntityData().set(DATA_PLAYER_MAIN_HAND, (byte)clientInformation.mainHand().getId());
    }

    public ClientInformation clientInformation() {
        int i = this.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION);
        HumanoidArm humanoidarm = HumanoidArm.BY_ID.apply(this.getEntityData().get(DATA_PLAYER_MAIN_HAND));
        return new ClientInformation(
            this.language, this.requestedViewDistance, this.chatVisibility, this.canChatColor, i, humanoidarm, this.textFilteringEnabled, this.allowsListing
        );
    }

    public boolean canChatInColor() {
        return this.canChatColor;
    }

    public ChatVisiblity getChatVisibility() {
        return this.chatVisibility;
    }

    private boolean acceptsSystemMessages(boolean bypassHiddenChat) {
        return this.chatVisibility == ChatVisiblity.HIDDEN ? bypassHiddenChat : true;
    }

    private boolean acceptsChatMessages() {
        return this.chatVisibility == ChatVisiblity.FULL;
    }

    public int requestedViewDistance() {
        return this.requestedViewDistance;
    }

    public void sendServerStatus(ServerStatus serverStatus) {
        this.connection.send(new ClientboundServerDataPacket(serverStatus.description(), serverStatus.favicon().map(ServerStatus.Favicon::iconBytes)));
    }

    @Override
    protected int getPermissionLevel() {
        return this.server.getProfilePermissions(this.getGameProfile());
    }

    public void resetLastActionTime() {
        this.lastActionTime = Util.getMillis();
    }

    public ServerStatsCounter getStats() {
        return this.stats;
    }

    public ServerRecipeBook getRecipeBook() {
        return this.recipeBook;
    }

    @Override
    protected void updateInvisibilityStatus() {
        if (this.isSpectator()) {
            this.removeEffectParticles();
            this.setInvisible(true);
        } else {
            super.updateInvisibilityStatus();
        }
    }

    public Entity getCamera() {
        return (Entity)(this.camera == null ? this : this.camera);
    }

    public void setCamera(@Nullable Entity entityToSpectate) {
        Entity entity = this.getCamera();
        this.camera = (Entity)(entityToSpectate == null ? this : entityToSpectate);
        while (this.camera instanceof net.neoforged.neoforge.entity.PartEntity<?> partEntity) this.camera = partEntity.getParent(); // Neo: fix MC-46486
        if (entity != this.camera) {
            if (this.camera.level() instanceof ServerLevel serverlevel) {
                this.teleportTo(serverlevel, this.camera.getX(), this.camera.getY(), this.camera.getZ(), Set.of(), this.getYRot(), this.getXRot());
            }

            if (entityToSpectate != null) {
                this.serverLevel().getChunkSource().move(this);
            }

            this.connection.send(new ClientboundSetCameraPacket(this.camera));
            this.connection.resetPosition();
        }
    }

    @Override
    protected void processPortalCooldown() {
        if (!this.isChangingDimension) {
            super.processPortalCooldown();
        }
    }

    /**
     * Attacks for the player the targeted entity with the currently equipped item.  The equipped item has hitEntity called on it. Args: targetEntity
     */
    @Override
    public void attack(Entity targetEntity) {
        if (this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            this.setCamera(targetEntity);
        } else {
            super.attack(targetEntity);
        }
    }

    public long getLastActionTime() {
        return this.lastActionTime;
    }

    @Nullable
    public Component getTabListDisplayName() {
        if (!this.hasTabListName) {
            this.tabListDisplayName = net.neoforged.neoforge.event.EventHooks.getPlayerTabListDisplayName(this);
            this.hasTabListName = true;
        }
        return this.tabListDisplayName;
    }

    @Override
    public void swing(InteractionHand hand) {
        super.swing(hand);
        this.resetAttackStrengthTicker();
    }

    public boolean isChangingDimension() {
        return this.isChangingDimension;
    }

    public void hasChangedDimension() {
        this.isChangingDimension = false;
    }

    public PlayerAdvancements getAdvancements() {
        return this.advancements;
    }

    public void teleportTo(ServerLevel newLevel, double x, double y, double z, float yaw, float pitch) {
        this.setCamera(this);
        this.stopRiding();
        if (newLevel == this.level()) {
            this.connection.teleport(x, y, z, yaw, pitch);
        } else {
            this.changeDimension(
                new DimensionTransition(newLevel, new Vec3(x, y, z), Vec3.ZERO, yaw, pitch, DimensionTransition.DO_NOTHING)
            );
        }
    }

    @Nullable
    public BlockPos getRespawnPosition() {
        return this.respawnPosition;
    }

    public float getRespawnAngle() {
        return this.respawnAngle;
    }

    public ResourceKey<Level> getRespawnDimension() {
        return this.respawnDimension;
    }

    public boolean isRespawnForced() {
        return this.respawnForced;
    }

    public void copyRespawnPosition(ServerPlayer player) {
        this.setRespawnPosition(
            player.getRespawnDimension(), player.getRespawnPosition(), player.getRespawnAngle(), player.isRespawnForced(), false
        );
    }

    public void setRespawnPosition(ResourceKey<Level> dimension, @Nullable BlockPos position, float angle, boolean forced, boolean sendMessage) {
        if (net.neoforged.neoforge.event.EventHooks.onPlayerSpawnSet(this, position == null ? Level.OVERWORLD : dimension, position, forced)) return;
        if (position != null) {
            boolean flag = position.equals(this.respawnPosition) && dimension.equals(this.respawnDimension);
            if (sendMessage && !flag) {
                this.sendSystemMessage(Component.translatable("block.minecraft.set_spawn"));
            }

            this.respawnPosition = position;
            this.respawnDimension = dimension;
            this.respawnAngle = angle;
            this.respawnForced = forced;
        } else {
            this.respawnPosition = null;
            this.respawnDimension = Level.OVERWORLD;
            this.respawnAngle = 0.0F;
            this.respawnForced = false;
        }
    }

    public SectionPos getLastSectionPos() {
        return this.lastSectionPos;
    }

    public void setLastSectionPos(SectionPos sectionPos) {
        this.lastSectionPos = sectionPos;
    }

    public ChunkTrackingView getChunkTrackingView() {
        return this.chunkTrackingView;
    }

    public void setChunkTrackingView(ChunkTrackingView chunkTrackingView) {
        this.chunkTrackingView = chunkTrackingView;
    }

    @Override
    public void playNotifySound(SoundEvent sound, SoundSource source, float volume, float pitch) {
        this.connection
            .send(
                new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                    source,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    volume,
                    pitch,
                    this.random.nextLong()
                )
            );
    }

    /**
     * Creates and drops the provided item. Depending on the dropAround, it will drop the item around the player, instead of dropping the item from where the player is pointing at. Likewise, if includeThrowerName is true, the dropped item entity will have the thrower set as the player.
     */
    @Override
    public ItemEntity drop(ItemStack droppedItem, boolean dropAround, boolean traceItem) {
        ItemEntity itementity = super.drop(droppedItem, dropAround, traceItem);
        if (itementity == null) {
            return null;
        } else {
            if (captureDrops() != null) captureDrops().add(itementity);
            else
            this.level().addFreshEntity(itementity);
            ItemStack itemstack = itementity.getItem();
            if (traceItem) {
                if (!itemstack.isEmpty()) {
                    this.awardStat(Stats.ITEM_DROPPED.get(itemstack.getItem()), droppedItem.getCount());
                }

                this.awardStat(Stats.DROP);
            }

            return itementity;
        }
    }

    /**
     * Returns the language last reported by the player as their local language.
     * Defaults to en_us if the value is unknown.
     */
    public String getLanguage() {
        return this.language;
    }

    private Component tabListHeader = Component.empty();
    private Component tabListFooter = Component.empty();

    public Component getTabListHeader() {
         return this.tabListHeader;
    }

    /**
     * Set the tab list header while preserving the footer.
     *
     * @param header the new header, or {@link Component#empty()} to clear
     */
    public void setTabListHeader(final Component header) {
         this.setTabListHeaderFooter(header, this.tabListFooter);
    }

    public Component getTabListFooter() {
         return this.tabListFooter;
    }

    /**
     * Set the tab list footer while preserving the header.
     *
     * @param footer the new footer, or {@link Component#empty()} to clear
     */
    public void setTabListFooter(final Component footer) {
         this.setTabListHeaderFooter(this.tabListHeader, footer);
    }

    /**
     * Set the tab list header and footer at once.
     *
     * @param header the new header, or {@link Component#empty()} to clear
     * @param footer the new footer, or {@link Component#empty()} to clear
     */
    public void setTabListHeaderFooter(final Component header, final Component footer) {
         if (java.util.Objects.equals(header, this.tabListHeader)
              && java.util.Objects.equals(footer, this.tabListFooter)) {
              return;
         }

         this.tabListHeader = java.util.Objects.requireNonNull(header, "header");
         this.tabListFooter = java.util.Objects.requireNonNull(footer, "footer");

         this.connection.send(new net.minecraft.network.protocol.game.ClientboundTabListPacket(header, footer));
    }

    // We need this as tablistDisplayname may be null even if the event was fired.
    private boolean hasTabListName = false;
    private Component tabListDisplayName = null;
    /**
     * Force the name displayed in the tab list to refresh, by firing {@link net.neoforged.neoforge.event.entity.player.PlayerEvent.TabListNameFormat}.
     */
    public void refreshTabListName() {
        Component oldName = this.tabListDisplayName;
        this.tabListDisplayName = net.neoforged.neoforge.event.EventHooks.getPlayerTabListDisplayName(this);
        if (!java.util.Objects.equals(oldName, this.tabListDisplayName)) {
            this.getServer().getPlayerList().broadcastAll(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, this));
        }
    }

    public TextFilter getTextFilter() {
        return this.textFilter;
    }

    public void setServerLevel(ServerLevel level) {
        this.setLevel(level);
        this.gameMode.setLevel(level);
    }

    @Nullable
    private static GameType readPlayerMode(@Nullable CompoundTag tag, String key) {
        return tag != null && tag.contains(key, 99) ? GameType.byId(tag.getInt(key)) : null;
    }

    private GameType calculateGameModeForNewPlayer(@Nullable GameType gameType) {
        GameType gametype = this.server.getForcedGameType();
        if (gametype != null) {
            return gametype;
        } else {
            return gameType != null ? gameType : this.server.getDefaultGameType();
        }
    }

    public void loadGameTypes(@Nullable CompoundTag tag) {
        this.gameMode
            .setGameModeForPlayer(
                this.calculateGameModeForNewPlayer(readPlayerMode(tag, "playerGameType")), readPlayerMode(tag, "previousPlayerGameType")
            );
    }

    private void storeGameTypes(CompoundTag tag) {
        tag.putInt("playerGameType", this.gameMode.getGameModeForPlayer().getId());
        GameType gametype = this.gameMode.getPreviousGameModeForPlayer();
        if (gametype != null) {
            tag.putInt("previousPlayerGameType", gametype.getId());
        }
    }

    @Override
    public boolean isTextFilteringEnabled() {
        return this.textFilteringEnabled;
    }

    public boolean shouldFilterMessageTo(ServerPlayer player) {
        return player == this ? false : this.textFilteringEnabled || player.textFilteringEnabled;
    }

    @Override
    public boolean mayInteract(Level level, BlockPos pos) {
        return super.mayInteract(level, pos) && level.mayInteract(this, pos);
    }

    @Override
    protected void updateUsingItem(ItemStack usingItem) {
        CriteriaTriggers.USING_ITEM.trigger(this, usingItem);
        super.updateUsingItem(usingItem);
    }

    /**
     * @param dropStack Whether to drop the entire stack of items. If {@code false},
     *                  drops a single item.
     */
    public boolean drop(boolean dropStack) {
        Inventory inventory = this.getInventory();
        ItemStack selected = inventory.getSelected();
        if (selected.isEmpty() || !selected.onDroppedByPlayer(this)) return false;
        if (isUsingItem() && getUsedItemHand() == InteractionHand.MAIN_HAND && (dropStack || selected.getCount() == 1)) stopUsingItem(); // Forge: fix MC-231097 on the serverside
        ItemStack itemstack = inventory.removeFromSelected(dropStack);
        this.containerMenu.findSlot(inventory, inventory.selected).ifPresent(p_287377_ -> this.containerMenu.setRemoteSlot(p_287377_, inventory.getSelected()));
        return net.neoforged.neoforge.common.CommonHooks.onPlayerTossEvent(this, itemstack, true) != null;
    }

    public boolean allowsListing() {
        return this.allowsListing;
    }

    @Override
    public Optional<WardenSpawnTracker> getWardenSpawnTracker() {
        return Optional.of(this.wardenSpawnTracker);
    }

    public void setSpawnExtraParticlesOnFall(boolean spawnExtraParticlesOnFall) {
        this.spawnExtraParticlesOnFall = spawnExtraParticlesOnFall;
    }

    @Override
    public void onItemPickup(ItemEntity itemEntity) {
        super.onItemPickup(itemEntity);
        Entity entity = itemEntity.getOwner();
        if (entity != null) {
            CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_PLAYER.trigger(this, itemEntity.getItem(), entity);
        }
    }

    public void setChatSession(RemoteChatSession chatSession) {
        this.chatSession = chatSession;
    }

    @Nullable
    public RemoteChatSession getChatSession() {
        return this.chatSession != null && this.chatSession.hasExpired() ? null : this.chatSession;
    }

    @Override
    public void indicateDamage(double xDistance, double zDistance) {
        this.hurtDir = (float)(Mth.atan2(zDistance, xDistance) * 180.0F / (float)Math.PI - (double)this.getYRot());
        this.connection.send(new ClientboundHurtAnimationPacket(this));
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        if (super.startRiding(vehicle, force)) {
            this.setKnownMovement(Vec3.ZERO);
            vehicle.positionRider(this);
            this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
            if (vehicle instanceof LivingEntity livingentity) {
                this.server.getPlayerList().sendActiveEffects(livingentity, this.connection);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void stopRiding() {
        Entity entity = this.getVehicle();
        super.stopRiding();
        if (entity instanceof LivingEntity livingentity) {
            for (MobEffectInstance mobeffectinstance : livingentity.getActiveEffects()) {
                this.connection.send(new ClientboundRemoveMobEffectPacket(entity.getId(), mobeffectinstance.getEffect()));
            }
        }
    }

    public CommonPlayerSpawnInfo createCommonSpawnInfo(ServerLevel level) {
        return new CommonPlayerSpawnInfo(
            level.dimensionTypeRegistration(),
            level.dimension(),
            BiomeManager.obfuscateSeed(level.getSeed()),
            this.gameMode.getGameModeForPlayer(),
            this.gameMode.getPreviousGameModeForPlayer(),
            level.isDebug(),
            level.isFlat(),
            this.getLastDeathLocation(),
            this.getPortalCooldown()
        );
    }

    public void setRaidOmenPosition(BlockPos raidOmenPosition) {
        this.raidOmenPosition = raidOmenPosition;
    }

    public void clearRaidOmenPosition() {
        this.raidOmenPosition = null;
    }

    @Nullable
    public BlockPos getRaidOmenPosition() {
        return this.raidOmenPosition;
    }

    @Override
    public Vec3 getKnownMovement() {
        Entity entity = this.getVehicle();
        return entity != null && entity.getControllingPassenger() != this ? entity.getKnownMovement() : this.lastKnownClientMovement;
    }

    public void setKnownMovement(Vec3 knownMovement) {
        this.lastKnownClientMovement = knownMovement;
    }

    @Override
    protected float getEnchantedDamage(Entity entity, float damage, DamageSource damageSource) {
        return EnchantmentHelper.modifyDamage(this.serverLevel(), this.getWeaponItem(), entity, damageSource, damage);
    }

    @Override
    public void onEquippedItemBroken(Item item, EquipmentSlot slot) {
        super.onEquippedItemBroken(item, slot);
        this.awardStat(Stats.ITEM_BROKEN.get(item));
    }

    public static record RespawnPosAngle(Vec3 position, float yaw) {
        public static ServerPlayer.RespawnPosAngle of(Vec3 position, BlockPos towardsPos) {
            return new ServerPlayer.RespawnPosAngle(position, calculateLookAtYaw(position, towardsPos));
        }

        private static float calculateLookAtYaw(Vec3 position, BlockPos towardsPos) {
            Vec3 vec3 = Vec3.atBottomCenterOf(towardsPos).subtract(position).normalize();
            return (float)Mth.wrapDegrees(Mth.atan2(vec3.z, vec3.x) * 180.0F / (float)Math.PI - 90.0);
        }
    }
}
