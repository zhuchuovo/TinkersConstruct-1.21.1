package slimeknights.tconstruct.library.utils;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Lookup providers usable by codecs that only reference static/built-in registries. */
public final class RegistryAccessUtil {
  private RegistryAccessUtil() {}

  public static final HolderLookup.Provider BUILTIN = HolderLookup.Provider.create(
    BuiltInRegistries.REGISTRY.stream().map(Registry::asLookup));
}
