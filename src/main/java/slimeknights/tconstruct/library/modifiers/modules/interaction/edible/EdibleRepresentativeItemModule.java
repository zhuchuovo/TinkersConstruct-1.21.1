package slimeknights.tconstruct.library.modifiers.modules.interaction.edible;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.Vec3;
import slimeknights.mantle.data.loadable.common.ItemStackLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.interaction.EdibleEffectHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.UsingToolModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition.ConditionalModule;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;

import java.util.List;

/**
 * Module adding a representative item to tool eating. Affects the particles shown and the foods reported to Diet.
 * @param representativeItem  Stack used for mods like Diet to know what we ate.
 */
public record EdibleRepresentativeItemModule(ItemStack representativeItem, ModifierCondition<IToolStackView> condition) implements ModifierModule, UsingToolModifierHook, EdibleEffectHook, ConditionalModule<IToolStackView> {
  private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider.<EdibleRepresentativeItemModule>defaultHooks(ModifierHooks.TOOL_USING, ModifierHooks.EDIBLE_EFFECT);
  public static final RecordLoadable<EdibleRepresentativeItemModule> LOADER = RecordLoadable.create(
    ItemStackLoadable.REQUIRED_ITEM_NBT.requiredField("representative_item", EdibleRepresentativeItemModule::representativeItem),
    ModifierCondition.TOOL_FIELD, EdibleRepresentativeItemModule::new);

  public EdibleRepresentativeItemModule(ItemLike representativeItem) {
    this(new ItemStack(representativeItem), ModifierCondition.ANY_TOOL);
  }

  @Override
  public RecordLoadable<EdibleRepresentativeItemModule> getLoader() {
    return LOADER;
  }

  @Override
  public List<ModuleHook<?>> getDefaultHooks() {
    return DEFAULT_HOOKS;
  }

  @Override
  public void onToolEaten(IToolStackView tool, ModifierEntry modifier, Player player, EquipmentSlot eatenSlot, int hunger, float saturation, List<ItemStack> representativeItems) {
    if (!tool.isBroken() && condition.matches(tool, modifier)) {
      representativeItems.add(representativeItem);
    }
  }

  /** Plays effects for eating */
  private static void eatEffects(LivingEntity entity, ItemStack representativeItem, int amount) {
    RandomSource random = entity.getRandom();
    for (int i = 0; i < amount; i++) {
      Vec3 velocity = new Vec3((random.nextDouble() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, 0.0)
        .xRot(-entity.getXRot() * ((float)Math.PI / 180.0F))
        .yRot(-entity.getYRot() * ((float)Math.PI / 180.0F));
      Vec3 position = new Vec3((random.nextDouble() - 0.5) * 0.3, -random.nextDouble() * 0.6 - 0.3, 0.6)
        .xRot(-entity.getXRot() * ((float)Math.PI / 180.0F))
        .yRot(-entity.getYRot() * ((float)Math.PI / 180.0F))
        .add(entity.getX(), entity.getEyeY(), entity.getZ());
      entity.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, representativeItem), position.x, position.y, position.z, velocity.x, velocity.y + 0.05, velocity.z);
    }
    entity.playSound(SoundEvents.GENERIC_EAT, 0.5f + 0.5f * random.nextInt(2), (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f);
  }

  @Override
  public void onUsingTick(IToolStackView tool, ModifierEntry modifier, LivingEntity entity, int useDuration, int timeLeft, ModifierEntry activeModifier) {
    if (!tool.isBroken() && condition.matches(tool, modifier)) {
      StatsNBT stats = tool.getStats();
      if (stats.getInt(EdibleModule.HUNGER) > 0) {
        int useTime = useDuration - timeLeft;

        // if we reached the end, finish drinking; don't have to release the current use
        int duration = stats.getInt(EdibleModule.EAT_DURATION);
        if (useTime == duration) {
          if (entity instanceof Player player && player.canEat(false)) {
            eatEffects(entity, representativeItem, 16);
          }
        }
        // if we have not finished drinking, and we can drink, play effects
        else if (useTime < duration && useTime % 4 == 0 && entity instanceof Player player && player.canEat(false)) {
          eatEffects(entity, representativeItem, 5);
        }
      }
    }
  }

  @Override
  public void beforeReleaseUsing(IToolStackView tool, ModifierEntry modifier, LivingEntity entity, int useDuration, int timeLeft, ModifierEntry activeModifier) {
    if (!tool.isBroken() && condition.matches(tool, modifier) && entity instanceof Player player && player.canEat(false)) {
      StatsNBT stats = tool.getStats();
      if (stats.getInt(EdibleModule.HUNGER) > 0 && useDuration - timeLeft == stats.getInt(EdibleModule.EAT_DURATION)) {
        eatEffects(entity, representativeItem, 5);
      }
    }
  }
}