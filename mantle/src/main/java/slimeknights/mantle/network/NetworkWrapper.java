package slimeknights.mantle.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.network.packet.ISimplePacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Small packet wrapper preserving Mantle's packet API on NeoForge's payload system.
 * Each logical channel uses one client-bound and one server-bound envelope payload;
 * the original packet codecs are dispatched by a stable integer id inside it.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class NetworkWrapper {
  private final String version;
  private final CustomPacketPayload.Type<Envelope> clientboundType;
  private final CustomPacketPayload.Type<Envelope> serverboundType;
  private final List<Registration<?>> registrations = new ArrayList<>();
  private final Map<Class<?>, Registration<?>> registrationsByClass = new HashMap<>();

  @Deprecated
  public NetworkWrapper(ResourceLocation channelName) {
    this(channelName, "1");
  }

  public NetworkWrapper(ResourceLocation channelName, String version) {
    this.version = version;
    this.clientboundType = new CustomPacketPayload.Type<>(channelName.withSuffix("_clientbound"));
    this.serverboundType = new CustomPacketPayload.Type<>(channelName.withSuffix("_serverbound"));
  }

  public <MSG extends ISimplePacket> void registerPacket(Class<MSG> clazz, Function<FriendlyByteBuf, MSG> decoder, @Nullable NetworkDirection direction) {
    registerPacket(clazz, ISimplePacket::encode, decoder, ISimplePacket::handle, direction);
  }

  public <MSG> void registerPacket(Class<MSG> clazz, BiConsumer<MSG, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, MSG> decoder,
                                  BiConsumer<MSG, Supplier<PacketContext>> consumer, @Nullable NetworkDirection direction) {
    registerPacketNoLogger(clazz, wrapLogger(clazz, encoder), wrapLogger(clazz, decoder), consumer, direction);
  }

  public <MSG> void registerPacketNoLogger(Class<MSG> clazz, BiConsumer<MSG, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, MSG> decoder,
                                          BiConsumer<MSG, Supplier<PacketContext>> consumer, @Nullable NetworkDirection direction) {
    if (registrationsByClass.containsKey(clazz)) {
      throw new IllegalArgumentException("Packet class registered twice: " + clazz.getName());
    }
    Registration<MSG> registration = new Registration<>(registrations.size(), clazz, encoder, decoder, consumer, direction);
    registrations.add(registration);
    registrationsByClass.put(clazz, registration);
  }

  /** Registers this channel's two envelope payloads during NeoForge's payload event. */
  public void registerPayloads(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar registrar = event.registrar(version);
    if (registrations.stream().anyMatch(registration -> registration.direction != NetworkDirection.PLAY_TO_SERVER)) {
      registrar.playToClient(clientboundType, codec(clientboundType, NetworkDirection.PLAY_TO_CLIENT), this::handle);
    }
    if (registrations.stream().anyMatch(registration -> registration.direction != NetworkDirection.PLAY_TO_CLIENT)) {
      registrar.playToServer(serverboundType, codec(serverboundType, NetworkDirection.PLAY_TO_SERVER), this::handle);
    }
  }

  private StreamCodec<RegistryFriendlyByteBuf, Envelope> codec(CustomPacketPayload.Type<Envelope> type, NetworkDirection direction) {
    return new StreamCodec<>() {
      @Override
      public Envelope decode(RegistryFriendlyByteBuf buffer) {
        int packetId = buffer.readVarInt();
        Registration<?> registration = getRegistration(packetId, direction);
        return new Envelope(type, packetId, registration.decode(buffer));
      }

      @Override
      public void encode(RegistryFriendlyByteBuf buffer, Envelope envelope) {
        Registration<?> registration = getRegistration(envelope.packetId, direction);
        buffer.writeVarInt(envelope.packetId);
        registration.encode(envelope.message, buffer);
      }
    };
  }

  private Registration<?> getRegistration(int packetId, NetworkDirection direction) {
    if (packetId < 0 || packetId >= registrations.size()) {
      throw new IllegalArgumentException("Unknown packet id " + packetId);
    }
    Registration<?> registration = registrations.get(packetId);
    if (registration.direction != null && registration.direction != direction) {
      throw new IllegalArgumentException("Packet " + registration.type.getName() + " received in the wrong direction");
    }
    return registration;
  }

  private void handle(Envelope envelope, IPayloadContext context) {
    Registration<?> registration = registrations.get(envelope.packetId);
    registration.handle(envelope.message, () -> new PacketContext(context));
  }

  private Envelope payload(Object message, NetworkDirection direction) {
    Registration<?> registration = registrationsByClass.get(message.getClass());
    if (registration == null) {
      throw new IllegalArgumentException("Unregistered packet class: " + message.getClass().getName());
    }
    if (registration.direction != null && registration.direction != direction) {
      throw new IllegalArgumentException("Packet " + message.getClass().getName() + " cannot be sent in " + direction);
    }
    return new Envelope(direction == NetworkDirection.PLAY_TO_CLIENT ? clientboundType : serverboundType, registration.id, message);
  }

  private static <MSG> BiConsumer<MSG, FriendlyByteBuf> wrapLogger(Class<MSG> clazz, BiConsumer<MSG, FriendlyByteBuf> encoder) {
    return (message, buffer) -> {
      try {
        encoder.accept(message, buffer);
      } catch (Exception e) {
        Mantle.logger.error("Exception while encoding packet of class {}", clazz.getName(), e);
        throw e;
      }
    };
  }

  private static <MSG> Function<FriendlyByteBuf,MSG> wrapLogger(Class<MSG> clazz, Function<FriendlyByteBuf,MSG> decoder) {
    return buffer -> {
      try {
        return decoder.apply(buffer);
      } catch (Exception e) {
        Mantle.logger.error("Exception while decoding packet of class {}", clazz.getName(), e);
        throw e;
      }
    };
  }

  public void sendToServer(Object message) {
    PacketDistributor.sendToServer(payload(message, NetworkDirection.PLAY_TO_SERVER));
  }

  public void sendVanillaPacket(Packet<?> packet, Entity player) {
    if (player instanceof ServerPlayer serverPlayer) {
      serverPlayer.connection.send(packet);
    }
  }

  public void sendTo(Object message, Player player) {
    if (player instanceof ServerPlayer serverPlayer) {
      sendTo(message, serverPlayer);
    }
  }

  public void sendTo(Object message, ServerPlayer player) {
    if (!(player instanceof FakePlayer)) {
      PacketDistributor.sendToPlayer(player, payload(message, NetworkDirection.PLAY_TO_CLIENT));
    }
  }

  public void sendToClientsAround(Object message, ServerLevel level, BlockPos position) {
    PacketDistributor.sendToPlayersTrackingChunk(level, level.getChunkAt(position).getPos(), payload(message, NetworkDirection.PLAY_TO_CLIENT));
  }

  public void sendToTrackingAndSelf(Object message, Entity entity) {
    PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload(message, NetworkDirection.PLAY_TO_CLIENT));
  }

  public void sendToTracking(Object message, Entity entity) {
    PacketDistributor.sendToPlayersTrackingEntity(entity, payload(message, NetworkDirection.PLAY_TO_CLIENT));
  }

  private record Envelope(CustomPacketPayload.Type<Envelope> type, int packetId, Object message) implements CustomPacketPayload {}

  private record Registration<MSG>(int id, Class<MSG> type, BiConsumer<MSG, FriendlyByteBuf> encoder,
                                   Function<FriendlyByteBuf, MSG> decoder,
                                   BiConsumer<MSG, Supplier<PacketContext>> consumer,
                                   @Nullable NetworkDirection direction) {
    private Object decode(FriendlyByteBuf buffer) {
      return decoder.apply(buffer);
    }

    @SuppressWarnings("unchecked")
    private void encode(Object message, FriendlyByteBuf buffer) {
      encoder.accept((MSG) message, buffer);
    }

    @SuppressWarnings("unchecked")
    private void handle(Object message, Supplier<PacketContext> context) {
      consumer.accept((MSG) message, context);
    }
  }
}
