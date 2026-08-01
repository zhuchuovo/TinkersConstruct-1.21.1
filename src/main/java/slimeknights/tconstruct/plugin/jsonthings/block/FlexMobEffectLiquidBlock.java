package slimeknights.tconstruct.plugin.jsonthings.block;

import dev.gigaherz.jsonthings.things.blocks.FlexLiquidBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.Map;
import java.util.function.Supplier;

/** Json Things version of {@link slimeknights.tconstruct.fluids.block.BurningLiquidBlock} */
public class FlexMobEffectLiquidBlock extends FlexLiquidBlock {
  private final Supplier<MobEffectInstance> effect;
  private final FlowingFluid fluid;

  public FlexMobEffectLiquidBlock(Properties properties, Map<Property<?>,Comparable<?>> propertyDefaultValues, Supplier<FlowingFluid> fluidSupplier, Supplier<MobEffectInstance> effect) {
    this(properties, propertyDefaultValues, fluidSupplier.get(), effect);
  }

  private FlexMobEffectLiquidBlock(Properties properties, Map<Property<?>,Comparable<?>> propertyDefaultValues, FlowingFluid fluid, Supplier<MobEffectInstance> effect) {
    super(properties, propertyDefaultValues, fluid);
    this.fluid = fluid;
    this.effect = effect;
  }

  @Override
  public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
    if (entity.getFluidTypeHeight(fluid.getFluidType()) > 0 && entity instanceof LivingEntity living) {
      living.addEffect(effect.get());
    }
  }
}
