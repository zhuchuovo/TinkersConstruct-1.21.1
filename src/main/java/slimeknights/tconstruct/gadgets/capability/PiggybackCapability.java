package slimeknights.tconstruct.gadgets.capability;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.EntityCapability;
import slimeknights.tconstruct.TConstruct;

import java.util.Map;
import java.util.WeakHashMap;

/** Capability logic */
public class PiggybackCapability {
  public static final EntityCapability<PiggybackHandler, Void> PIGGYBACK =
    EntityCapability.createVoid(TConstruct.getResource("piggyback"), PiggybackHandler.class);
  private static final Map<Player,PiggybackHandler> HANDLERS = new WeakHashMap<>();

  private PiggybackCapability() {}

  /** Registers this capability */
  public static void register() {
    TConstruct.getModBus().addListener(PiggybackCapability::onRegisterCapabilities);
  }

  /** Registers the capability with the event bus */
  private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
    event.registerEntity(PIGGYBACK, EntityType.PLAYER,
      (player, ignored) -> HANDLERS.computeIfAbsent(player, PiggybackHandler::new));
  }
}
