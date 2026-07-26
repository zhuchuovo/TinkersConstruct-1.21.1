package slimeknights.mantle.registration.object;

import java.util.List;
import java.util.function.Consumer;

/** Shared class for objects containing multiple other objects */
public interface MultiObject<T> {
  /** {@return list of objects in this multiobject} */
  List<T> values();

  /** Runs the consumer on each element in the object */
  void forEach(Consumer<? super T> consumer);
}
