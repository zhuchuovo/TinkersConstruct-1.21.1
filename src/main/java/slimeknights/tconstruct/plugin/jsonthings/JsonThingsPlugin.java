package slimeknights.tconstruct.plugin.jsonthings;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import slimeknights.tconstruct.library.utils.HarvestTiers;

/** This plugin is referenced in the main class, so it may not directly access JSON Things classes. It may access classes that access them however */
public class JsonThingsPlugin {
  /** Called by mod constructor to register JsonThings things */
  public static void onConstruct() {
    FlexBlockTypes.init();
    FlexItemTypes.init();
    HarvestTiers.registerTierResolver(JsonThingsTierRegistry::byId, JsonThingsTierRegistry::getId);

    if (FMLEnvironment.dist == Dist.CLIENT) {
      PluginClient.init();
    }
  }
}
