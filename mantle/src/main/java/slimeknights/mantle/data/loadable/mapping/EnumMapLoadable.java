package slimeknights.mantle.data.loadable.mapping;

import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.primitive.EnumLoadable;
import slimeknights.mantle.data.loadable.primitive.StringLoadable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Loadable for a map type with enum keys. Allows using the more efficient enum map on parsing
 * @param <K>  Key type
 * @param <V>  Value type
 */
public class EnumMapLoadable<K extends Enum<K>,V> extends MapLoadable<K,V> {
  private final Class<K> enumClass;
  public EnumMapLoadable(Class<K> enumClass, StringLoadable<K> keyLoadable, Loadable<V> valueLoadable, int minSize) {
    super(keyLoadable, valueLoadable, minSize);
    this.enumClass = enumClass;
  }

  public EnumMapLoadable(EnumLoadable<K> keyLoadable, Loadable<V> valueLoadable, int minSize) {
    this(keyLoadable.enumClass(), keyLoadable, valueLoadable, minSize);
  }

  @Override
  protected Map<K,V> createBuilder(int size) {
    return new EnumMap<>(enumClass);
  }

  @Override
  protected Map<K,V> build(Map<K,V> builder) {
    return Collections.unmodifiableMap(builder);
  }
}
