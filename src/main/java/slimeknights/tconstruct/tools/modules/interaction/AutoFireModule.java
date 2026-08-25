package slimeknights.tconstruct.tools.modules.interaction;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.loadable.record.SingletonLoader;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.UsingToolModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableCrossbowItem;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableLauncherItem;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.List;

/**
 * Module making crossbows automatically load and fire as soon as they are fully drawn,
 * then immediately start drawing again. Reproduces the Tinkers' 2 crossbow behavior of
 * holding right click to continuously load and shoot.
 */
public enum AutoFireModule implements ModifierModule, UsingToolModifierHook {
  INSTANCE;

  private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider.<AutoFireModule>defaultHooks(ModifierHooks.TOOL_USING);
  public static final RecordLoadable<AutoFireModule> LOADER = new SingletonLoader<>(INSTANCE);

  @Override
  public RecordLoadable<AutoFireModule> getLoader() {
    return LOADER;
  }

  @Override
  public List<ModuleHook<?>> getDefaultHooks() {
    return DEFAULT_HOOKS;
  }

  @Override
  public void onUsingTick(IToolStackView tool, ModifierEntry modifier, LivingEntity entity, int useDuration, int timeLeft, ModifierEntry activeModifier) {
    // only applies to drawing a crossbow directly, not to modifier interactions such as blocking
    if (activeModifier != ModifierEntry.EMPTY || !(tool.getItem() instanceof ModifiableCrossbowItem)) {
      return;
    }
    ModDataNBT persistentData = tool.getPersistentData();
    int drawtime = persistentData.getInt(GeneralInteractionModifierHook.KEY_DRAWTIME);
    // act on the exact tick the crossbow is fully drawn; never touch an already loaded crossbow
    if (drawtime <= 0 || useDuration - timeLeft != drawtime || persistentData.contains(ModifiableCrossbowItem.KEY_CROSSBOW_AMMO)) {
      return;
    }
    InteractionHand hand = entity.getUsedItemHand();
    if (!entity.level().isClientSide) {
      // load and immediately fire, then start the next draw if we had ammo
      if (ModifiableCrossbowItem.loadAndFire(tool, entity, hand)) {
        restartDrawing(entity, hand);
      } else {
        // out of ammo, end the cycle
        entity.stopUsingItem();
      }
    } else {
      // client keeps the draw animation cycling, the server handles firing
      restartDrawing(entity, hand);
    }
  }

  /** Restarts the draw so the crossbow keeps cycling while the use button is held */
  private static void restartDrawing(LivingEntity entity, InteractionHand hand) {
    entity.stopUsingItem();
    // refetch the tool as firing replaced its data components
    IToolStackView tool = ToolStack.from(entity.getItemInHand(hand));
    GeneralInteractionModifierHook.startDrawing(tool, entity, 1);
    tool.getPersistentData().putBoolean(ModifiableLauncherItem.KEY_DRAWBACK_AMMO, true);
    entity.startUsingItem(hand);
  }
}
