package slimeknights.mantle.registration;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Source-compatible single-parameter view over NeoForge's two-parameter
 * {@link DeferredHolder}. Mantle historically exposed Forge's
 * {@code RegistryObject<T>} throughout its registration helpers; keeping that
 * surface avoids leaking the registry base type through every SlimeKnights API.
 * Registration and holder binding are still performed entirely by NeoForge.
 */
public final class RegistryObject<T> implements Supplier<T> {
  private final DeferredHolder<?,?> holder;

  private RegistryObject(DeferredHolder<?,?> holder) {
    this.holder = holder;
  }

  public static <R,T extends R> RegistryObject<T> of(DeferredHolder<R,T> holder) {
    return new RegistryObject<>(holder);
  }

  @Override
  @SuppressWarnings("unchecked")
  public T get() {
    return (T) holder.get();
  }

  public ResourceLocation getId() {
    return holder.getId();
  }

  public boolean isPresent() {
    return holder.isBound();
  }

  public Optional<T> asOptional() {
    return isPresent() ? Optional.of(get()) : Optional.empty();
  }

  public void ifPresent(Consumer<? super T> consumer) {
    asOptional().ifPresent(consumer);
  }

  public <U> Supplier<U> lazyMap(java.util.function.Function<? super T,? extends U> mapper) {
    return () -> mapper.apply(get());
  }

  public DeferredHolder<?,?> asHolder() {
    return holder;
  }

  /** Gets this deferred object as the registry holder required by 1.21 APIs. */
  @SuppressWarnings("unchecked")
  public Holder<T> getHolder() {
    return (Holder<T>) holder;
  }

  /** Gets this holder viewed as a registry base type, for covariant registry entries. */
  @SuppressWarnings("unchecked")
  public <R> Holder<R> getHolderAs() {
    return (Holder<R>) holder;
  }
}
