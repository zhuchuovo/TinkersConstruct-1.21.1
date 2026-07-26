package slimeknights.mantle.data.loadable.mapping;

import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.field.LoadableField;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/** Loadable of list of elements */
public class ListLoadable<T> extends CollectionLoadable<T,List<T>> {
  public ListLoadable(Loadable<T> base, int minSize) {
    super(base, minSize);
  }

  @Override
  protected List<T> build(Collection<T> builder) {
    return List.copyOf(builder);
  }

  @Override
  public <P> LoadableField<List<T>,P> emptyField(String key, boolean serializeEmpty, Function<P,List<T>> getter) {
    return defaultField(key, List.of(), serializeEmpty, getter);
  }
}
