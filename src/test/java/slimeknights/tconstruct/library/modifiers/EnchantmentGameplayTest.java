package slimeknights.tconstruct.library.modifiers;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.impl.ComposableModifier;
import slimeknights.tconstruct.library.modifiers.modules.build.EnchantmentModule;
import slimeknights.tconstruct.library.tools.context.ToolHarvestContext;
import slimeknights.tconstruct.library.tools.helper.ToolHarvestLogic;
import slimeknights.tconstruct.library.tools.item.ToolItemTest;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/** Verifies dynamic tool enchantments are visible to vanilla gameplay and loot APIs. */
class EnchantmentGameplayTest extends ToolItemTest {
  private static final ModifierId TEST_FORTUNE = new ModifierId("test", "gameplay_fortune");
  private static final ResourceKey<Enchantment> FORTUNE_KEY = ResourceKey.create(
    Registries.ENCHANTMENT, ResourceLocation.withDefaultNamespace("fortune"));
  private static final Holder.Reference<Enchantment> FORTUNE = Holder.Reference.createStandAlone(
    new HolderOwner<>() {}, FORTUNE_KEY);

  @BeforeAll
  static synchronized void registerFortuneModifier() {
    if (!ModifierManager.INSTANCE.staticModifiers.containsKey(TEST_FORTUNE)) {
      Modifier modifier = ComposableModifier.builder()
        .addModule(EnchantmentModule.builder(FORTUNE_KEY).constant())
        .build();
      modifier.setId(TEST_FORTUNE);
      ModifierManager.INSTANCE.staticModifiers.put(TEST_FORTUNE, modifier);
    }
  }

  private void addFortune(int level) {
    ToolStack.from(testItemStack).addModifier(TEST_FORTUNE, level);
  }

  @Test
  void fortuneIsVisibleToGameplayQueriesAndCopies() {
    addFortune(3);

    assertThat(testItemStack.getEnchantmentLevel(FORTUNE)).isEqualTo(3);
    assertThat(EnchantmentHelper.getItemEnchantmentLevel(FORTUNE, testItemStack)).isEqualTo(3);
    assertThat(EnchantmentHelper.getItemEnchantmentLevel(FORTUNE, testItemStack.copy())).isEqualTo(3);
  }

  @Test
  void vanillaOreBonusReadsDynamicFortune() {
    addFortune(3);
    LootItemFunction function = ApplyBonusCount.addOreBonusCount(FORTUNE).build();
    LootContext context = mock(LootContext.class);
    when(context.getParamOrNull(LootContextParams.TOOL)).thenReturn(testItemStack);

    int largestDrop = 1;
    for (int seed = 0; seed < 100; seed++) {
      when(context.getRandom()).thenReturn(RandomSource.create(seed));
      ItemStack drops = function.apply(new ItemStack(Items.DIAMOND), context);
      largestDrop = Math.max(largestDrop, drops.getCount());
    }

    assertThat(largestDrop).isGreaterThan(1);
  }

  @Test
  @SuppressWarnings("unchecked")
  void harvestStack_materializesFortuneWithoutMutatingHeldTool() {
    addFortune(3);
    RegistryAccess registryAccess = mock(RegistryAccess.class);
    HolderLookup.RegistryLookup<Enchantment> enchantments = mock(HolderLookup.RegistryLookup.class);
    when(registryAccess.lookupOrThrow(Registries.ENCHANTMENT)).thenReturn(enchantments);
    when(enchantments.getOrThrow(FORTUNE_KEY)).thenReturn(FORTUNE);
    ServerLevel level = mock(ServerLevel.class);
    when(level.registryAccess()).thenReturn(registryAccess);
    LivingEntity living = mock(LivingEntity.class);
    when(living.getItemBySlot(any(EquipmentSlot.class))).thenReturn(ItemStack.EMPTY);
    ToolHarvestContext context = mock(ToolHarvestContext.class);
    when(context.getLiving()).thenReturn(living);
    when(context.getPlayer()).thenReturn(null);
    when(context.getWorld()).thenReturn(level);

    ItemStack harvestStack = ToolHarvestLogic.createHarvestStack(testItemStack, ToolStack.from(testItemStack), context);
    assertThat(harvestStack.getTagEnchantments().getLevel(FORTUNE)).isEqualTo(3);
    assertThat(testItemStack.getTagEnchantments().getLevel(FORTUNE)).isZero();

    ToolStack.from(harvestStack).setDamage(1);
    assertThat(testItemStack.getDamageValue()).isZero();
  }
}
