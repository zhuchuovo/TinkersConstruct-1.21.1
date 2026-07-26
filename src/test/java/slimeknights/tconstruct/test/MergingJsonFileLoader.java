package slimeknights.tconstruct.test;

import com.google.gson.JsonElement;
import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import slimeknights.mantle.data.listener.MergingJsonDataLoader;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;

import static org.mockito.Mockito.mock;

/** Test helper that mocks multiple data packs for a merging JSON loader. */
public class MergingJsonFileLoader<B> extends JsonFileLoader {
  private final MergingJsonDataLoader<B> dataLoader;

  public MergingJsonFileLoader(MergingJsonDataLoader<B> dataLoader) {
    super(readField(dataLoader, "gson", Gson.class), readField(dataLoader, "folder", String.class));
    this.dataLoader = dataLoader;
  }

  private static <T> T readField(Object instance, String name, Class<T> type) {
    try {
      Field field = MergingJsonDataLoader.class.getDeclaredField(name);
      field.setAccessible(true);
      return type.cast(field.get(instance));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Unable to access merging loader field " + name, e);
    }
  }

  @SuppressWarnings("unchecked")
  private B createBuilder(ResourceLocation id) {
    Function<ResourceLocation,B> constructor = (Function<ResourceLocation,B>)readField(dataLoader, "builderConstructor", Function.class);
    return constructor.apply(id);
  }

  private void invoke(String name, Object... arguments) {
    Method method = Arrays.stream(dataLoader.getClass().getDeclaredMethods())
      .filter(candidate -> candidate.getName().equals(name) && candidate.getParameterCount() == arguments.length && !candidate.isBridge())
      .findFirst().orElseThrow(() -> new IllegalStateException("Missing merging loader method " + name));
    try {
      method.setAccessible(true);
      method.invoke(dataLoader, arguments);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Unable to invoke merging loader method " + name, e);
    }
  }

  public void loadAndParseFiles(@Nullable String mergeFolder, ResourceLocation... files) {
    Map<ResourceLocation,B> parsedMap = new HashMap<>();
    for (Entry<ResourceLocation,JsonElement> entry : loadFilesAsSplashlist(files).entrySet()) {
      ResourceLocation id = entry.getKey();
      invoke("parse", parsedMap.computeIfAbsent(id, this::createBuilder), id, entry.getValue());
    }
    if (mergeFolder != null) {
      JsonFileLoader fakeSecondDataPack = new JsonFileLoader(readField(dataLoader, "gson", Gson.class), readField(dataLoader, "folder", String.class) + "/" + mergeFolder);
      for (Entry<ResourceLocation,JsonElement> entry : fakeSecondDataPack.loadFilesAsSplashlist(files).entrySet()) {
        ResourceLocation id = entry.getKey();
        invoke("parse", parsedMap.computeIfAbsent(id, this::createBuilder), id, entry.getValue());
      }
    }
    invoke("finishLoad", parsedMap, mock(ResourceManager.class));
  }
}
