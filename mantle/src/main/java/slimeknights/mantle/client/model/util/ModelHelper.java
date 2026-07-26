package slimeknights.mantle.client.model.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Utilities to help in custom models
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ModelHelper {
  private static final Map<Block,ResourceLocation> TEXTURE_NAME_CACHE = new ConcurrentHashMap<>();
  /** Listener instance to clear cache */
  public static final ResourceManagerReloadListener LISTENER = manager -> TEXTURE_NAME_CACHE.clear();

  /**
   * Gets the texture name for a block from the model manager
   * @param block  Block to fetch
   * @return Texture name for the block
   */
  @SuppressWarnings("deprecation")
  private static ResourceLocation getParticleTextureInternal(Block block) {
    TextureAtlasSprite particle = Minecraft.getInstance().getModelManager().getBlockModelShaper().getBlockModel(block.defaultBlockState()).getParticleIcon();
    //noinspection ConstantConditions  dumb mods returning null particle icons
    if (particle != null) {
      return particle.contents().name();
    }
    return MissingTextureAtlasSprite.getLocation();
  }

  /**
   * Gets the name of a particle texture for a block, using the cached value if present
   * @param block Block to fetch
   * @return Texture name for the block
   */
  public static ResourceLocation getParticleTexture(Block block) {
    return TEXTURE_NAME_CACHE.computeIfAbsent(block, ModelHelper::getParticleTextureInternal);
  }

  /* JSON */

  /**
   * Converts a JSON float array to the specified object
   * @param json    JSON object
   * @param name    Name of the array in the object to fetch
   * @param size    Expected array size
   * @param mapper  Functon to map from the array to the output type
   * @param <T> Output type
   * @return  Vector3f of data
   * @throws JsonParseException  If there is no array or the length is wrong
   * @deprecated use {@link slimeknights.mantle.data.loadable.array.FloatArrayLoadable}
   */
  @Deprecated(forRemoval = true)
  public static <T> T arrayToObject(JsonObject json, String name, int size, Function<float[], T> mapper) {
    JsonArray array = GsonHelper.getAsJsonArray(json, name);
    if (array.size() != size) {
      throw new JsonParseException("Expected " + size + " " + name + " values, found: " + array.size());
    }
    float[] vec = new float[size];
    for(int i = 0; i < size; ++i) {
      vec[i] = GsonHelper.convertToFloat(array.get(i), name + "[" + i + "]");
    }
    return mapper.apply(vec);
  }

  /**
   * Converts a JSON array with 3 elements into a Vector3f
   * @param json  JSON object
   * @param name  Name of the array in the object to fetch
   * @return  Vector3f of data
   * @throws JsonParseException  If there is no array or the length is wrong
   * @deprecated use {@link slimeknights.mantle.data.loadable.common.Vector3fLoadable}
   */
  @Deprecated(forRemoval = true)
  public static Vector3f arrayToVector(JsonObject json, String name) {
    return arrayToObject(json, name, 3, arr -> new Vector3f(arr[0], arr[1], arr[2]));
  }

  /** @deprecated use {@link slimeknights.mantle.data.loadable.common.Vector3fLoadable} */
  @Deprecated(forRemoval = true)
  public static JsonArray vectorToJson(Vector3f vector) {
    JsonArray array = new JsonArray();
    array.add(vector.x());
    array.add(vector.y());
    array.add(vector.z());
    return array;
  }

  /** Validates the given rotation is in 90 degree increments */
  public static boolean checkRotation(float rotation) {
    return rotation >= 0 && rotation % 90 == 0 && rotation <= 270;
  }

  /**
   * Gets a rotation from JSON
   * @param json  JSON parent
   * @return  Integer of 0, 90, 180, or 270
   */
  public static int getRotation(JsonObject json, String key) {
    int i = GsonHelper.getAsInt(json, key, 0);
    if (checkRotation(i)) {
      return i;
    } else {
      throw new JsonParseException("Invalid '" + key + "' " + i + " found, only 0/90/180/270 allowed");
    }
  }
}
