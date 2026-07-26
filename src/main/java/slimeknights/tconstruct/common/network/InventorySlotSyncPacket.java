package slimeknights.tconstruct.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import slimeknights.mantle.network.PacketContext;
import slimeknights.mantle.network.packet.IThreadsafePacket;

public class InventorySlotSyncPacket implements IThreadsafePacket {

  public final ItemStack itemStack;
  public final int slot;
  public final BlockPos pos;

  public InventorySlotSyncPacket(ItemStack itemStack, int slot, BlockPos pos) {
    this.itemStack = itemStack;
    this.slot = slot;
    this.pos = pos;
  }

  public InventorySlotSyncPacket(FriendlyByteBuf buffer) {
    this.itemStack = ItemStack.OPTIONAL_STREAM_CODEC.decode((RegistryFriendlyByteBuf)buffer);
    this.slot = buffer.readShort();
    this.pos = buffer.readBlockPos();
  }

  @Override
  public void encode(FriendlyByteBuf packetBuffer) {
    ItemStack.OPTIONAL_STREAM_CODEC.encode((RegistryFriendlyByteBuf)packetBuffer, this.itemStack);
    packetBuffer.writeShort(this.slot);
    packetBuffer.writeBlockPos(this.pos);
  }

  @Override
  public void handleThreadsafe(PacketContext context) {
    HandleClient.handle(this);
  }

  /** Safely runs client side only code in a method only called on client */
  private static class HandleClient {
    private static void handle(InventorySlotSyncPacket packet) {
      Level world = Minecraft.getInstance().level;
      if (world != null) {
        BlockEntity te = world.getBlockEntity(packet.pos);
        if (te != null) {
          var cap = world.getCapability(Capabilities.ItemHandler.BLOCK, packet.pos, null);
          if (cap instanceof IItemHandlerModifiable modifiable) {
              modifiable.setStackInSlot(packet.slot, packet.itemStack);
              //noinspection ConstantConditions
              Minecraft.getInstance().levelRenderer.blockChanged(null, packet.pos, null, null, 0);
          }
        }
      }
    }
  }
}
