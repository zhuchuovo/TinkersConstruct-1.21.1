package net.minecraft.server.level;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class ServerPlayerGameMode {
    private static final Logger LOGGER = LogUtils.getLogger();
    protected ServerLevel level;
    protected final ServerPlayer player;
    private GameType gameModeForPlayer = GameType.DEFAULT_MODE;
    @Nullable
    private GameType previousGameModeForPlayer;
    private boolean isDestroyingBlock;
    private int destroyProgressStart;
    private BlockPos destroyPos = BlockPos.ZERO;
    private int gameTicks;
    private boolean hasDelayedDestroy;
    private BlockPos delayedDestroyPos = BlockPos.ZERO;
    private int delayedTickStart;
    private int lastSentState = -1;

    public ServerPlayerGameMode(ServerPlayer player) {
        this.player = player;
        this.level = player.serverLevel();
    }

    public boolean changeGameModeForPlayer(GameType gameModeForPlayer) {
        if (gameModeForPlayer == this.gameModeForPlayer) {
            return false;
        } else {
            this.setGameModeForPlayer(gameModeForPlayer, this.previousGameModeForPlayer);
            this.player.onUpdateAbilities();
            this.player
                .server
                .getPlayerList()
                .broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, this.player));
            this.level.updateSleepingPlayerList();
            if (gameModeForPlayer == GameType.CREATIVE) {
                this.player.resetCurrentImpulseContext();
            }

            return true;
        }
    }

    protected void setGameModeForPlayer(GameType gameModeForPlayer, @Nullable GameType previousGameModeForPlayer) {
        this.previousGameModeForPlayer = previousGameModeForPlayer;
        this.gameModeForPlayer = gameModeForPlayer;
        // Neo: preserve flying state, removed on tick if Attribute or ability no longer applies
        boolean wasFlying = this.player.getAbilities().flying;
        gameModeForPlayer.updatePlayerAbilities(this.player.getAbilities());
        this.player.getAbilities().flying = wasFlying || this.player.getAbilities().flying;
    }

    public GameType getGameModeForPlayer() {
        return this.gameModeForPlayer;
    }

    @Nullable
    public GameType getPreviousGameModeForPlayer() {
        return this.previousGameModeForPlayer;
    }

    public boolean isSurvival() {
        return this.gameModeForPlayer.isSurvival();
    }

    public boolean isCreative() {
        return this.gameModeForPlayer.isCreative();
    }

    public void tick() {
        this.gameTicks++;
        if (this.hasDelayedDestroy) {
            BlockState blockstate = this.level.getBlockState(this.delayedDestroyPos);
            if (blockstate.isAir()) {
                this.hasDelayedDestroy = false;
            } else {
                float f = this.incrementDestroyProgress(blockstate, this.delayedDestroyPos, this.delayedTickStart);
                if (f >= 1.0F) {
                    this.hasDelayedDestroy = false;
                    this.destroyBlock(this.delayedDestroyPos);
                }
            }
        } else if (this.isDestroyingBlock) {
            BlockState blockstate1 = this.level.getBlockState(this.destroyPos);
            if (blockstate1.isAir()) {
                this.level.destroyBlockProgress(this.player.getId(), this.destroyPos, -1);
                this.lastSentState = -1;
                this.isDestroyingBlock = false;
            } else {
                this.incrementDestroyProgress(blockstate1, this.destroyPos, this.destroyProgressStart);
            }
        }
    }

    private float incrementDestroyProgress(BlockState state, BlockPos pos, int startTick) {
        int i = this.gameTicks - startTick;
        float f = state.getDestroyProgress(this.player, this.player.level(), pos) * (float)(i + 1);
        int j = (int)(f * 10.0F);
        if (j != this.lastSentState) {
            this.level.destroyBlockProgress(this.player.getId(), pos, j);
            this.lastSentState = j;
        }

        return f;
    }

    private void debugLogging(BlockPos pos, boolean terminate, int sequence, String message) {
    }

    public void handleBlockBreakAction(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction face, int maxBuildHeight, int sequence) {
        net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event = net.neoforged.neoforge.common.CommonHooks.onLeftClickBlock(player, pos, face, action);
        if (event.isCanceled()) {
            return;
        }
        if (!this.player.canInteractWithBlock(pos, 1.0)) {
            this.debugLogging(pos, false, sequence, "too far");
        } else if (pos.getY() >= maxBuildHeight) {
            this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
            this.debugLogging(pos, false, sequence, "too high");
        } else {
            if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                if (!this.level.mayInteract(this.player, pos)) {
                    this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
                    this.debugLogging(pos, false, sequence, "may not interact");
                    return;
                }

                if (this.isCreative()) {
                    this.destroyAndAck(pos, sequence, "creative destroy");
                    return;
                }

                if (this.player.blockActionRestricted(this.level, pos, this.gameModeForPlayer)) {
                    this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
                    this.debugLogging(pos, false, sequence, "block action restricted");
                    return;
                }

                this.destroyProgressStart = this.gameTicks;
                float f = 1.0F;
                BlockState blockstate = this.level.getBlockState(pos);
                if (!blockstate.isAir()) {
                    EnchantmentHelper.onHitBlock(
                        this.level,
                        this.player.getMainHandItem(),
                        this.player,
                        this.player,
                        EquipmentSlot.MAINHAND,
                        Vec3.atCenterOf(pos),
                        blockstate,
                        p_348149_ -> this.player.onEquippedItemBroken(p_348149_, EquipmentSlot.MAINHAND)
                    );
                    if (event.getUseBlock() != net.neoforged.neoforge.common.util.TriState.FALSE)
                    blockstate.attack(this.level, pos, this.player);
                    f = blockstate.getDestroyProgress(this.player, this.player.level(), pos);
                }

                if (!blockstate.isAir() && f >= 1.0F) {
                    this.destroyAndAck(pos, sequence, "insta mine");
                } else {
                    if (this.isDestroyingBlock) {
                        this.player.connection.send(new ClientboundBlockUpdatePacket(this.destroyPos, this.level.getBlockState(this.destroyPos)));
                        this.debugLogging(pos, false, sequence, "abort destroying since another started (client insta mine, server disagreed)");
                    }

                    this.isDestroyingBlock = true;
                    this.destroyPos = pos.immutable();
                    int i = (int)(f * 10.0F);
                    this.level.destroyBlockProgress(this.player.getId(), pos, i);
                    this.debugLogging(pos, true, sequence, "actual start of destroying");
                    this.lastSentState = i;
                }
            } else if (action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
                if (pos.equals(this.destroyPos)) {
                    int j = this.gameTicks - this.destroyProgressStart;
                    BlockState blockstate1 = this.level.getBlockState(pos);
                    if (!blockstate1.isAir()) {
                        float f1 = blockstate1.getDestroyProgress(this.player, this.player.level(), pos) * (float)(j + 1);
                        if (f1 >= 0.7F) {
                            this.isDestroyingBlock = false;
                            this.level.destroyBlockProgress(this.player.getId(), pos, -1);
                            this.destroyAndAck(pos, sequence, "destroyed");
                            return;
                        }

                        if (!this.hasDelayedDestroy) {
                            this.isDestroyingBlock = false;
                            this.hasDelayedDestroy = true;
                            this.delayedDestroyPos = pos;
                            this.delayedTickStart = this.destroyProgressStart;
                        }
                    }
                }

                this.debugLogging(pos, true, sequence, "stopped destroying");
            } else if (action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
                this.isDestroyingBlock = false;
                if (!Objects.equals(this.destroyPos, pos)) {
                    LOGGER.warn("Mismatch in destroy block pos: {} {}", this.destroyPos, pos);
                    this.level.destroyBlockProgress(this.player.getId(), this.destroyPos, -1);
                    this.debugLogging(pos, true, sequence, "aborted mismatched destroying");
                }

                this.level.destroyBlockProgress(this.player.getId(), pos, -1);
                this.debugLogging(pos, true, sequence, "aborted destroying");
            }
        }
    }

    public void destroyAndAck(BlockPos pos, int sequence, String message) {
        if (this.destroyBlock(pos)) {
            this.debugLogging(pos, true, sequence, message);
        } else {
            this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
            this.debugLogging(pos, false, sequence, message);
        }
    }

    /**
     * Attempts to harvest a block
     */
    public boolean destroyBlock(BlockPos pos) {
        BlockState blockstate1 = this.level.getBlockState(pos);
        var event = net.neoforged.neoforge.common.CommonHooks.fireBlockBreak(level, gameModeForPlayer, player, pos, blockstate1);
        if (event.isCanceled()) {
            return false;
        } else {
            BlockEntity blockentity = this.level.getBlockEntity(pos);
            Block block = blockstate1.getBlock();
            if (block instanceof GameMasterBlock && !this.player.canUseGameMasterBlocks()) {
                this.level.sendBlockUpdated(pos, blockstate1, blockstate1, 3);
                return false;
            } else if (this.player.blockActionRestricted(this.level, pos, this.gameModeForPlayer)) {
                return false;
            } else {
                BlockState blockstate = block.playerWillDestroy(this.level, pos, blockstate1, this.player);

                if (this.isCreative()) {
                    removeBlock(pos, blockstate, false);
                    return true;
                } else {
                    ItemStack itemstack = this.player.getMainHandItem();
                    ItemStack itemstack1 = itemstack.copy();
                    boolean flag1 = blockstate.canHarvestBlock(this.level, pos, this.player); // previously player.hasCorrectToolForDrops(blockstate)
                    itemstack.mineBlock(this.level, blockstate, pos, this.player);
                    boolean flag = removeBlock(pos, blockstate, flag1);

                    if (flag1 && flag) {
                        block.playerDestroy(this.level, this.player, pos, blockstate, blockentity, itemstack1);
                    }

                    // Neo: Fire the PlayerDestroyItemEvent if the tool was broken at any point during the break process
                    if (itemstack.isEmpty() && !itemstack1.isEmpty()) {
                        net.neoforged.neoforge.event.EventHooks.onPlayerDestroyItem(this.player, itemstack1, InteractionHand.MAIN_HAND);
                    }

                    return true;
                }
            }
        }
    }

    /**
     * Patched-in method that handles actual removal of blocks for {@link #destroyBlock(BlockPos)}.
     *
     * @param pos The block pos of the destroyed block
     * @param state The state of the destroyed block
     * @param canHarvest If the player breaking the block can harvest the drops of the block
     * @return If the block was removed, as reported by {@link BlockState#onDestroyedByPlayer}.
     */
    private boolean removeBlock(BlockPos pos, BlockState state, boolean canHarvest) {
        boolean removed = state.onDestroyedByPlayer(this.level, pos, this.player, canHarvest, this.level.getFluidState(pos));
        if (removed)
            state.getBlock().destroy(this.level, pos, state);
        return removed;
    }

    public InteractionResult useItem(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand) {
        if (this.gameModeForPlayer == GameType.SPECTATOR) {
            return InteractionResult.PASS;
        } else if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResult.PASS;
        } else {
            InteractionResult cancelResult = net.neoforged.neoforge.common.CommonHooks.onItemRightClick(player, hand);
            if (cancelResult != null) return cancelResult;
            int i = stack.getCount();
            int j = stack.getDamageValue();
            InteractionResultHolder<ItemStack> interactionresultholder = stack.use(level, player, hand);
            ItemStack itemstack = interactionresultholder.getObject();
            if (itemstack == stack && itemstack.getCount() == i && itemstack.getUseDuration(player) <= 0 && itemstack.getDamageValue() == j) {
                return interactionresultholder.getResult();
            } else if (interactionresultholder.getResult() == InteractionResult.FAIL && itemstack.getUseDuration(player) > 0 && !player.isUsingItem()) {
                return interactionresultholder.getResult();
            } else {
                if (stack != itemstack) {
                    player.setItemInHand(hand, itemstack);
                }

                if (itemstack.isEmpty()) {
                    player.setItemInHand(hand, ItemStack.EMPTY);
                }

                if (!player.isUsingItem()) {
                    player.inventoryMenu.sendAllDataToRemote();
                }

                return interactionresultholder.getResult();
            }
        }
    }

    public InteractionResult useItemOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult) {
        BlockPos blockpos = hitResult.getBlockPos();
        BlockState blockstate = level.getBlockState(blockpos);
        if (!blockstate.getBlock().isEnabled(level.enabledFeatures())) {
            return InteractionResult.FAIL;
        }
        net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event = net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(player, hand, blockpos, hitResult);
        if (event.isCanceled()) return event.getCancellationResult();
        if (this.gameModeForPlayer == GameType.SPECTATOR) {
            MenuProvider menuprovider = blockstate.getMenuProvider(level, blockpos);
            if (menuprovider != null) {
                player.openMenu(menuprovider);
                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.PASS;
            }
        } else {
            UseOnContext useoncontext = new UseOnContext(player, hand, hitResult);
            if (event.getUseItem() != net.neoforged.neoforge.common.util.TriState.FALSE) {
                InteractionResult result = stack.onItemUseFirst(useoncontext);
                if (result != InteractionResult.PASS) return result;
            }
            boolean flag = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
            boolean flag1 = (player.isSecondaryUseActive() && flag) && !(player.getMainHandItem().doesSneakBypassUse(level, blockpos, player) && player.getOffhandItem().doesSneakBypassUse(level, blockpos, player));
            ItemStack itemstack = stack.copy();
            if (event.getUseBlock().isTrue() || (event.getUseBlock().isDefault() && !flag1)) {
                ItemInteractionResult iteminteractionresult = blockstate.useItemOn(player.getItemInHand(hand), level, player, hand, hitResult);
                if (iteminteractionresult.consumesAction()) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, blockpos, itemstack);
                    return iteminteractionresult.result();
                }

                if (iteminteractionresult == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && hand == InteractionHand.MAIN_HAND) {
                    InteractionResult interactionresult = blockstate.useWithoutItem(level, player, hitResult);
                    if (interactionresult.consumesAction()) {
                        CriteriaTriggers.DEFAULT_BLOCK_USE.trigger(player, blockpos);
                        return interactionresult;
                    }
                }
            }

            if (event.getUseItem().isTrue() || (!stack.isEmpty() && !player.getCooldowns().isOnCooldown(stack.getItem()))) {
                if (event.getUseItem().isFalse()) return InteractionResult.PASS;
                InteractionResult interactionresult1;
                if (this.isCreative()) {
                    int i = stack.getCount();
                    interactionresult1 = stack.useOn(useoncontext);
                    stack.setCount(i);
                } else {
                    interactionresult1 = stack.useOn(useoncontext);
                }

                if (interactionresult1.consumesAction()) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, blockpos, itemstack);
                }

                return interactionresult1;
            } else {
                return InteractionResult.PASS;
            }
        }
    }

    /**
     * Sets the world instance.
     */
    public void setLevel(ServerLevel serverLevel) {
        this.level = serverLevel;
    }
}
