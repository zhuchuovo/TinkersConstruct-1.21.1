package slimeknights.mantle.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/** Minimal context surface used by legacy Mantle/Tinkers packet handlers. */
public final class PacketContext {
  private final IPayloadContext context;

  PacketContext(IPayloadContext context) {
    this.context = context;
  }

  @Nullable
  public ServerPlayer getSender() {
    return context.player() instanceof ServerPlayer player ? player : null;
  }

  public CompletableFuture<Void> enqueueWork(Runnable task) {
    return context.enqueueWork(task);
  }

  /** NeoForge payload handlers are considered handled once the handler returns. */
  public void setPacketHandled(boolean handled) {}
}
