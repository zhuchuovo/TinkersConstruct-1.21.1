package slimeknights.tconstruct.library.json.predicate.tool;

import com.mojang.serialization.Codec;
import lombok.RequiredArgsConstructor;
import net.minecraft.advancements.critereon.ItemSubPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.data.loadable.LoadableCodec;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags.Items;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/** Variant of ItemPredicate for matching Tinker tools using {@link ToolStackItemPredicate} */
@RequiredArgsConstructor(staticName = "ofTool")
public class ToolStackItemPredicate implements ItemSubPredicate {
  public static final ResourceLocation ID = TConstruct.getResource("tool_stack");
  public static final Codec<ToolStackItemPredicate> CODEC = new LoadableCodec<>(ToolStackPredicate.LOADER)
    .xmap(ToolStackItemPredicate::new, value -> value.predicate);
  public static final ItemSubPredicate.Type<ToolStackItemPredicate> TYPE = new ItemSubPredicate.Type<>(CODEC);

  private final IJsonPredicate<IToolStackView> predicate;

  public static ToolStackItemPredicate ofContext(IJsonPredicate<IToolContext> predicate) {
    return new ToolStackItemPredicate(ToolStackPredicate.context(predicate));
  }

  @Override
  public boolean matches(ItemStack stack) {
    // tag check is important to prevent accidently modifying the NBT of non-tools
    return stack.is(Items.MODIFIABLE) && predicate.matches(ToolStack.from(stack));
  }
}
