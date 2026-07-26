package slimeknights.tconstruct.gadgets.capability;

import lombok.RequiredArgsConstructor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import slimeknights.tconstruct.common.network.TinkerNetwork;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Capability instance
 * <p>
 * Does not serialize as the world saves the entities already, they just dismounted on logout
 */
@RequiredArgsConstructor
public class PiggybackHandler {

  /** Player holding this capability */
  @Nullable
  private final Player riddenPlayer;
  /** Last found list of passengers, used in serialization and syncing */
  private List<Entity> lastPassengers;

  /**
   * Updates the passengers on the back
   */
  public void updatePassengers() {
    if (this.riddenPlayer != null) {
      // tell the player itself if his riders changed serverside
      if (!this.riddenPlayer.getPassengers().equals(this.lastPassengers)) {
        if (this.riddenPlayer instanceof ServerPlayer) {
          TinkerNetwork.getInstance().sendVanillaPacket(this.riddenPlayer, new ClientboundSetPassengersPacket(this.riddenPlayer));
        }
      }
      this.lastPassengers = this.riddenPlayer.getPassengers();
    }
  }
}
