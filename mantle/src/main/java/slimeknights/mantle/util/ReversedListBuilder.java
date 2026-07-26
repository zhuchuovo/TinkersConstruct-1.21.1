package slimeknights.mantle.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builder for creating a list by adding the last elements for the final list first
 */
public class ReversedListBuilder<E> {
  /** Store the list as a list of lists as that makes reversing easier */
  private final List<E> unpacked = new ArrayList<>();

  /**
   * Adds the given data to the builder. Typically its best to do collections here, you can merge them in the final building.
   * @param data  Data to add
   */
  public void add(E data) {
    unpacked.add(data);
  }

  /** Gets the number of elements in the builder */
  public int size() {
    return unpacked.size();
  }

  /** Builds the list into the given consumer */
  public void build(Consumer<E> consumer) {
    for (int i = unpacked.size() - 1; i >= 0; i--) {
      consumer.accept(unpacked.get(i));
    }
  }

  /** Gets the number of elements after flattening the builder */
  public static int size(ReversedListBuilder<? extends Collection<?>> builder) {
    return builder.unpacked.stream().mapToInt(Collection::size).sum();
  }

  /** Builds a list out of a collection builder */
  public static <E> List<E> buildList(ReversedListBuilder<? extends Collection<E>> builder) {
    int size = size(builder);
    if (size == 0) {
      return List.of();
    }
    List<E> listBuilder = new ArrayList<>(size);
    builder.build(listBuilder::addAll);
    return List.copyOf(listBuilder);
  }
}
