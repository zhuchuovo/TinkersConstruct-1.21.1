package slimeknights.mantle.data.loadable.mapping;

import com.google.gson.JsonElement;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.primitive.EnumLoadable;
import slimeknights.mantle.util.typed.TypedMap;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Loadable of set of enum elements, provides some additional optimizations */
public class EnumSetLoadable<T extends Enum<T>> extends SetLoadable<T> {
  private final Class<T> enumClass;
  public EnumSetLoadable(Class<T> enumClass, Loadable<T> base, int minSize) {
    super(base, minSize);
    this.enumClass = enumClass;
  }

  public EnumSetLoadable(EnumLoadable<T> base, int minSize) {
    this(base.enumClass(), base, minSize);
  }

  @Override
  protected Collection<T> createBuilder(int size) {
    return EnumSet.noneOf(enumClass);
  }

  @Override
  protected Set<T> build(Collection<T> builder) {
    // cast safe outside convertCompact
    return Collections.unmodifiableSet((Set<T>)builder);
  }

  @Override
  public Set<T> convertCompact(JsonElement element, String key, TypedMap context) {
    // base impl uses List.of rather than createBuilder, so we need an override
    return build(EnumSet.of(base.convert(element, key, context)));
  }
}
