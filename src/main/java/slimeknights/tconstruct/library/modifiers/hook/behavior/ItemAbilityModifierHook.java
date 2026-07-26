package slimeknights.tconstruct.library.modifiers.hook.behavior;

import net.neoforged.neoforge.common.ItemAbility;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import java.util.Collection;

/**
 * Hook that checks if the tool can perform the given action
 */
public interface ItemAbilityModifierHook {
  /**
   * Checks if the tool can perform the given tool action. If any modifier returns true, the action is assumed to be present
   * @param tool        Tool to check, will never be broken
   * @param modifier    Modifier level
   * @param toolAction  Action to check
   * @return  True if the tool can perform the action.
   */
  boolean canPerformAction(IToolStackView tool, ModifierEntry modifier, ItemAbility toolAction);

  /** Merger that returns true if any of the nested modules returns true */
  record AnyMerger(Collection<ItemAbilityModifierHook> modules) implements ItemAbilityModifierHook {
    @Override
    public boolean canPerformAction(IToolStackView tool, ModifierEntry modifier, ItemAbility toolAction) {
      for (ItemAbilityModifierHook module : modules) {
        if (module.canPerformAction(tool, modifier, toolAction)) {
          return true;
        }
      }
      return false;
    }
  }
}
