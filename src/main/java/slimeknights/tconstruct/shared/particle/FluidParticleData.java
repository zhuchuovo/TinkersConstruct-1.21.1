package slimeknights.tconstruct.shared.particle;

import com.mojang.serialization.MapCodec;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;

/** Particle data for a fluid particle */
@RequiredArgsConstructor
public class FluidParticleData implements ParticleOptions {
  @Getter
  private final ParticleType<FluidParticleData> type;
  @Getter
  private final FluidStack fluid;

  /** Particle type for a fluid particle */
  public static class Type extends ParticleType<FluidParticleData> {
    public Type() {
      super(false);
    }

    @Override
    public MapCodec<FluidParticleData> codec() {
      return FluidStack.CODEC.xmap(fluid -> new FluidParticleData(this, fluid), data -> data.fluid).fieldOf("fluid");
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, FluidParticleData> streamCodec() {
      return FluidStack.STREAM_CODEC.map(fluid -> new FluidParticleData(this, fluid), data -> data.fluid);
    }
  }
}
