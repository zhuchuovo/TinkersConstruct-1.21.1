package slimeknights.mantle.data.predicate.damage;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.record.RecordLoadable;

/** Predicate matching a single damage type */
public record DamageTypePredicate(ResourceKey<DamageType> type) implements DamageSourcePredicate {
  public static final RecordLoadable<DamageTypePredicate> LOADER = RecordLoadable.create(
    Loadables.DAMAGE_TYPE_KEY.requiredField("name", DamageTypePredicate::type),
    DamageTypePredicate::new);

  @Override
  public boolean matches(DamageSource input) {
    return input.is(type);
  }

  @Override
  public RecordLoadable<DamageTypePredicate> getLoader() {
    return LOADER;
  }
}
