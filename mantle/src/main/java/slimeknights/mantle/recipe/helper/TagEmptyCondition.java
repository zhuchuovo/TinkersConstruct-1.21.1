package slimeknights.mantle.recipe.helper;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/** @deprecated use {@link slimeknights.mantle.recipe.condition.TagEmptyCondition} */
@Deprecated(forRemoval = true)
public class TagEmptyCondition<T> extends slimeknights.mantle.recipe.condition.TagEmptyCondition<T> {
  public TagEmptyCondition(TagKey<T> tag) {
    super(tag);
  }

  public TagEmptyCondition(ResourceKey<? extends Registry<T>> registry, ResourceLocation name) {
    super(registry, name);
  }
}
