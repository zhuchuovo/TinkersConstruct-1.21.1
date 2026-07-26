package slimeknights.mantle.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import slimeknights.mantle.network.PacketContext;

import java.util.function.Supplier;

/**
 * Packet interface to add common methods for registration
 */
public interface ISimplePacket {
  /**
   * Encodes a packet for the buffer
   * @param buf  Buffer instance
   */
  void encode(FriendlyByteBuf buf);

  /**
   * Handles receiving the packet
   * @param context  Packet context
   */
  void handle(Supplier<PacketContext> context);
}
