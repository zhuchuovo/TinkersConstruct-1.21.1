package slimeknights.tconstruct.library.utils;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import slimeknights.tconstruct.library.client.SafeClient;

/** Registry access for legacy item capability hooks which have no world parameter. */
public final class RegistryAccessUtil {
  private RegistryAccessUtil() {}

  /** Fallback provider with built-in registries only, used while no level is loaded */
  public static final HolderLookup.Provider BUILTIN = HolderLookup.Provider.create(
    BuiltInRegistries.REGISTRY.stream().map(Registry::asLookup));

  /**
   * Resolves the current logical side's registries without caching world load/unload events.
   * Callers with a world (including asynchronous callers) should pass its registry access directly.
   */
  public static HolderLookup.Provider get() {
    if (EffectiveSide.get().isServer()) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      return server != null ? server.registryAccess() : BUILTIN;
    }
    HolderLookup.Provider provider = SafeClient.getRegistryAccess();
    return provider != null ? provider : BUILTIN;
  }
}
