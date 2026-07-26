package slimeknights.tconstruct.world.worldgen.trees;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import slimeknights.tconstruct.world.TinkerStructures;
import slimeknights.tconstruct.world.block.FoliageType;

import java.util.Optional;

/** Creates the data-driven tree grower used by each slime foliage family. */
public final class SlimeTree {
  private SlimeTree() {}

  public static TreeGrower create(FoliageType foliageType) {
    ResourceKey<ConfiguredFeature<?,?>> tree = switch (foliageType) {
      case EARTH -> TinkerStructures.earthSlimeTree;
      case SKY -> TinkerStructures.skySlimeTree;
      case ENDER -> TinkerStructures.enderSlimeTree;
      case BLOOD -> TinkerStructures.bloodSlimeFungus;
      case ICHOR -> TinkerStructures.ichorSlimeFungus;
    };
    if (foliageType == FoliageType.ENDER) {
      return new TreeGrower("tconstruct:ender_slime", 0.85f, Optional.empty(), Optional.empty(), Optional.of(tree),
                            Optional.of(TinkerStructures.enderSlimeTreeTall), Optional.empty(), Optional.empty());
    }
    return new TreeGrower("tconstruct:" + foliageType.getSerializedName() + "_slime", Optional.empty(), Optional.of(tree), Optional.empty());
  }
}
