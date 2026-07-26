package slimeknights.tconstruct.library.utils;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Compatibility helpers for the pre-1.21 condition array keys used by Tinkers data. */
public final class ConditionUtil {
  private ConditionUtil() {}

  public static boolean matches(JsonObject json, String key, ICondition.IContext context) {
    if (!json.has(key)) {
      return true;
    }
    return ICondition.LIST_CODEC.parse(JsonOps.INSTANCE, json.get(key))
      .getOrThrow(com.google.gson.JsonParseException::new).stream().allMatch(condition -> condition.test(context));
  }
}
