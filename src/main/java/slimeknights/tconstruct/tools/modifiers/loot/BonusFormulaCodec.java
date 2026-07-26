package slimeknights.tconstruct.tools.modifiers.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount.BinomialWithBonusCount;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount.Formula;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount.OreDrops;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount.UniformBonusCount;

import java.util.Map;

/** Codec for vanilla apply-bonus formulas, whose dispatch codec is private in vanilla. */
final class BonusFormulaCodec {
  private static final FormulaType BINOMIAL = new FormulaType(ResourceLocation.withDefaultNamespace("binomial_with_bonus_count"), BinomialWithBonusCount.CODEC);
  private static final FormulaType ORE = new FormulaType(ResourceLocation.withDefaultNamespace("ore_drops"), OreDrops.CODEC);
  private static final FormulaType UNIFORM = new FormulaType(ResourceLocation.withDefaultNamespace("uniform_bonus_count"), UniformBonusCount.CODEC);
  private static final Map<ResourceLocation,FormulaType> TYPES = Map.of(BINOMIAL.id(), BINOMIAL, ORE.id(), ORE, UNIFORM.id(), UNIFORM);
  private static final Codec<FormulaType> TYPE_CODEC = ResourceLocation.CODEC.comapFlatMap(
    id -> {
      FormulaType type = TYPES.get(id);
      return type == null ? DataResult.error(() -> "Unknown bonus formula: " + id) : DataResult.success(type);
    }, FormulaType::id);

  static final MapCodec<Formula> CODEC = ExtraCodecs.dispatchOptionalValue(
    "formula", "parameters", TYPE_CODEC, BonusFormulaCodec::type, FormulaType::codec);

  private BonusFormulaCodec() {}

  private static FormulaType type(Formula formula) {
    if (formula instanceof BinomialWithBonusCount) return BINOMIAL;
    if (formula instanceof OreDrops) return ORE;
    if (formula instanceof UniformBonusCount) return UNIFORM;
    throw new IllegalArgumentException("Unsupported bonus formula: " + formula.getClass().getName());
  }

  private record FormulaType(ResourceLocation id, Codec<? extends Formula> codec) {}
}
