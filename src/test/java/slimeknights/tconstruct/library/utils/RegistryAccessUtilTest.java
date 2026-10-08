package slimeknights.tconstruct.library.utils;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.client.SafeClient;
import slimeknights.tconstruct.library.tools.capability.inventory.InventoryModule;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class RegistryAccessUtilTest extends BaseMcTest {
  @Test
  void integratedServerAndClientUseTheirOwnEnchantmentHolders() {
    RegistryAccess.Frozen serverAccess = registries();
    RegistryAccess.Frozen clientAccess = registries();
    MinecraftServer server = mock(MinecraftServer.class);
    when(server.registryAccess()).thenReturn(serverAccess);
    try (var sides = mockStatic(EffectiveSide.class);
         var servers = mockStatic(ServerLifecycleHooks.class);
         var client = mockStatic(SafeClient.class)) {
      servers.when(ServerLifecycleHooks::getCurrentServer).thenReturn(server);
      client.when(SafeClient::getRegistryAccess).thenReturn(clientAccess);
      for (LogicalSide side : LogicalSide.values()) {
        sides.when(EffectiveSide::get).thenReturn(side);
        RegistryAccess access = side.isServer() ? serverAccess : clientAccess;
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.enchant(access.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING), 1);

        assertThat(RegistryAccessUtil.get()).isSameAs(access);
        CompoundTag encoded = InventoryModule.writeStack(stack, 0, new CompoundTag());
        ItemStack decoded = ItemStack.parseOptional(RegistryAccessUtil.get(), encoded);
        assertThat(ItemStack.isSameItemSameComponents(stack, decoded)).isTrue();
      }
    }
  }

  @Test
  void clientConnectionRegistriesAreNotCachedAcrossTransitionsOrDisconnects() {
    RegistryAccess first = registries();
    RegistryAccess second = registries();
    try (var sides = mockStatic(EffectiveSide.class);
         var client = mockStatic(SafeClient.class)) {
      sides.when(EffectiveSide::get).thenReturn(LogicalSide.CLIENT);
      // Dimension changes reuse the connection; reconnecting may use completely different holders.
      client.when(SafeClient::getRegistryAccess).thenReturn(first, first, second, null);
      assertThat(RegistryAccessUtil.get()).isSameAs(first);
      assertThat(RegistryAccessUtil.get()).isSameAs(first);
      assertThat(RegistryAccessUtil.get()).isSameAs(second);
      assertThat(RegistryAccessUtil.get()).isSameAs(RegistryAccessUtil.BUILTIN);
    }
  }

  @Test
  void serverWithoutWorldNeverFallsBackToClientRegistries() {
    try (var sides = mockStatic(EffectiveSide.class);
         var servers = mockStatic(ServerLifecycleHooks.class);
         var client = mockStatic(SafeClient.class)) {
      sides.when(EffectiveSide::get).thenReturn(LogicalSide.SERVER);
      servers.when(ServerLifecycleHooks::getCurrentServer).thenReturn(null);
      assertThat(RegistryAccessUtil.get()).isSameAs(RegistryAccessUtil.BUILTIN);
      client.verifyNoInteractions();
    }
  }

  private static RegistryAccess.Frozen registries() {
    MappedRegistry<Enchantment> enchantments = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
    enchantments.register(Enchantments.UNBREAKING, mock(Enchantment.class), RegistrationInfo.BUILT_IN);
    return new RegistryAccess.ImmutableRegistryAccess(Stream.concat(
      BuiltInRegistries.REGISTRY.stream(), Stream.of(enchantments)).toList()).freeze();
  }
}
