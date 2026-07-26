package slimeknights.tconstruct.fluids.fluids;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import slimeknights.tconstruct.library.utils.PotionUtils;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import slimeknights.mantle.fluid.texture.ClientTextureFluidType;
import slimeknights.mantle.recipe.helper.FluidOutput;
import slimeknights.tconstruct.fluids.TinkerFluids;

import java.util.function.Consumer;

public class PotionFluidType extends FluidType {
  public PotionFluidType(Properties properties) {
    super(properties);
  }

  @Override
  public String getDescriptionId(FluidStack stack) {
    return Potion.getName(PotionUtils.getPotionContents(stack).potion(), "item.minecraft.potion.effect.");
  }

  @Override
  public ItemStack getBucket(FluidStack fluidStack) {
    ItemStack itemStack = new ItemStack(fluidStack.getFluid().getBucket());
    PotionUtils.copyPotion(fluidStack, itemStack);
    return itemStack;
  }

  @Override
  public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
    consumer.accept(new ClientTextureFluidType(this) {
      /**
       * Gets the color, based on {@link PotionUtils#getColor(ItemStack)}
       * @param stack  Fluid stack instance
       * @return  Color for the fluid
       */
      @Override
      public int getTintColor(FluidStack stack) {
        PotionContents contents = PotionUtils.getPotionContents(stack);
        if (contents.equals(PotionContents.EMPTY)) {
          return getTintColor();
        }
        return contents.getColor() | 0xFF000000;
      }
    });
  }

  /** Resolves a built-in potion key to its holder. */
  private static Holder<Potion> potionHolder(ResourceKey<Potion> potion) {
    return BuiltInRegistries.POTION.getHolderOrThrow(potion);
  }

  /** Creates a fluid stack for the given potion */
  public static FluidStack potionFluid(ResourceKey<Potion> potion, int size) {
    return potionFluid(potionHolder(potion), size);
  }

  /** Creates a fluid stack for the given potion. */
  public static FluidStack potionFluid(Holder<Potion> potion, int size) {
    return PotionUtils.setPotion(new FluidStack(TinkerFluids.potion.get(), size), potion);
  }

  /** Creates a fluid stack for the given potion */
  @SuppressWarnings("deprecation")  // forge registries have nullable keys, like why would you want that?
  public static FluidStack potionFluid(Potion potion, int size) {
    return potionFluid(BuiltInRegistries.POTION.wrapAsHolder(potion), size);
  }

  /** Creates a fluid output for the given potion */
  @SuppressWarnings("deprecation")  // forge registries have nullable keys, like why would you want that?
  public static FluidOutput potionResult(Holder<Potion> potion, int size) {
    return FluidOutput.fromStack(potionFluid(potion, size));
  }

  /** Creates a fluid output for the given potion. */
  public static FluidOutput potionResult(Potion potion, int size) {
    return potionResult(BuiltInRegistries.POTION.wrapAsHolder(potion), size);
  }

  /** Creates a potion bucket for the given potion */
  public static ItemStack potionBucket(ResourceKey<Potion> potion) {
    return potionBucket(potionHolder(potion));
  }

  /** Creates a potion bucket for the given potion. */
  public static ItemStack potionBucket(Holder<Potion> potion) {
    return PotionUtils.setPotion(new ItemStack(TinkerFluids.potion), potion);
  }

  /** Creates a potion bucket for the given potion */
  @SuppressWarnings("deprecation")  // forge registries have nullable keys, like why would you want that?
  public static ItemStack potionBucket(Potion potion) {
    return potionBucket(BuiltInRegistries.POTION.wrapAsHolder(potion));
  }
}
