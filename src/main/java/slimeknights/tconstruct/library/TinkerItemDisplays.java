package slimeknights.tconstruct.library;

import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

/** Custom transform types used for tinkers item rendering */
public class TinkerItemDisplays {
  private TinkerItemDisplays() {}

  public static void init() {}

  /** Used by the melter and smeltery for display of items its melting */
  public static final EnumProxy<ItemDisplayContext> MELTER = create("melter", null);
  /** Used by the part builder, crafting station, tinkers station, and tinker anvil */
  public static final EnumProxy<ItemDisplayContext> TABLE = create("table", null);
  /** Used by the casting table for item rendering */
  public static final EnumProxy<ItemDisplayContext> CASTING_TABLE = create("casting_table", "FIXED");
  /** Used by the casting basin for item rendering */
  public static final EnumProxy<ItemDisplayContext> CASTING_BASIN = create("casting_basin", null);
  /** Used by the fluid cannon for display of the item in front */
  public static final EnumProxy<ItemDisplayContext> FLUID_CANNON = create("fluid_cannon", "FIXED");
  /** Used by throwing to allow adjusting the tool position */
  public static final EnumProxy<ItemDisplayContext> THROWN = create("thrown", "FIXED");

  /** Creates a transform type */
  private static EnumProxy<ItemDisplayContext> create(String name, String fallback) {
    return new EnumProxy<>(ItemDisplayContext.class, -1, "tconstruct:" + name, fallback);
  }
}
