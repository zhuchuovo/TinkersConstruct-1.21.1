package slimeknights.tconstruct.test;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;

public class BaseMcTest {

  @SuppressWarnings({"ResultOfMethodCallIgnored", "unused"})
  @BeforeAll
  static synchronized void setUpRegistries() {
    try {
      SharedConstants.getCurrentVersion();
    } catch (IllegalStateException ignored) {
      SharedConstants.setVersion(TestWorldVersion.INSTANCE);
    }
    Bootstrap.bootStrap();
  }

  /** Kept for source compatibility; 1.21 vanilla tiers have a fixed ordering. */
  public static void setupTierSorting() {
  }

  /** Creates a test buffer with access to the built-in registries, as required by 1.21 item and ingredient codecs. */
  protected static RegistryFriendlyByteBuf registryBuffer() {
    return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
  }
}
