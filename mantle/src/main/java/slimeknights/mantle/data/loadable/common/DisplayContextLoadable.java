package slimeknights.mantle.data.loadable.common;

import com.google.gson.JsonSyntaxException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.mapping.EnumMapLoadable;
import slimeknights.mantle.data.loadable.primitive.ResourceLocationLoadable;
import slimeknights.mantle.util.typed.TypedMap;

import java.util.Map;

/** Special loadable for display contexts due to the Forge weirdness in {@link ItemDisplayContext} */
public enum DisplayContextLoadable implements ResourceLocationLoadable<ItemDisplayContext> {
  INSTANCE;

  @Override
  public ItemDisplayContext fromKey(ResourceLocation name, String key, TypedMap context) {
    for (ItemDisplayContext value : ItemDisplayContext.values()) {
      if (ResourceLocation.parse(value.getSerializedName()).equals(name)) {
        return value;
      }
    }
    throw new JsonSyntaxException("Unable to parse " + key + " as the ItemDisplayContext registry does not contain ID " + name);
  }

  @Override
  public ResourceLocation getKey(ItemDisplayContext object) {
    return ResourceLocation.parse(object.getSerializedName());
  }

  @Override
  public ItemDisplayContext decode(FriendlyByteBuf buffer, TypedMap context) {
    return ItemDisplayContext.BY_ID.apply(buffer.readVarInt());
  }

  @Override
  public void encode(FriendlyByteBuf buffer, ItemDisplayContext value) {
    buffer.writeVarInt(value.getId());
  }

  @Override
  public <V> Loadable<Map<ItemDisplayContext,V>> mapWithValues(Loadable<V> valueLoadable, int minSize) {
    return new EnumMapLoadable<>(ItemDisplayContext.class, this, valueLoadable, minSize);
  }
}
