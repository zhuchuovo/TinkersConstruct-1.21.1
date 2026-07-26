package slimeknights.mantle.data.predicate.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.tags.EntityTypeTags;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.registry.NamedComponentRegistry;

/** Predicate matching a specific mob type */
import java.util.function.Predicate;

public record MobTypePredicate(Predicate<LivingEntity> type) implements LivingEntityPredicate {
  public static final Predicate<LivingEntity> UNDEAD = entity -> entity.getType().is(EntityTypeTags.UNDEAD);
  public static final Predicate<LivingEntity> ARTHROPOD = entity -> entity.getType().is(EntityTypeTags.ARTHROPOD);
  public static final Predicate<LivingEntity> ILLAGER = entity -> entity.getType().is(EntityTypeTags.ILLAGER);
  public static final Predicate<LivingEntity> WATER = entity -> entity.getType().is(EntityTypeTags.AQUATIC);
  public static final Predicate<LivingEntity> UNDEFINED = entity -> !UNDEAD.test(entity) && !ARTHROPOD.test(entity) && !ILLAGER.test(entity) && !WATER.test(entity);
  /**
   * Registry of mob types, to allow addons to register types
   * TODO: support registering via IMC
   */
  public static final NamedComponentRegistry<Predicate<LivingEntity>> MOB_TYPES = new NamedComponentRegistry<>("Unknown mob type");
  /** Loader for a mob type predicate */
  public static RecordLoadable<MobTypePredicate> LOADER = RecordLoadable.create(MOB_TYPES.requiredField("mobs", MobTypePredicate::type), MobTypePredicate::new);

  @Override
  public boolean matches(LivingEntity input) {
    return type.test(input);
  }

  @Override
  public RecordLoadable<? extends LivingEntityPredicate> getLoader() {
    return LOADER;
  }
}
