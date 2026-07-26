package slimeknights.tconstruct.library.tools.item.armor;

import lombok.Getter;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import slimeknights.mantle.registration.object.IdAwareObject;

import java.util.List;
import java.util.Map;

/** Wrapper around the immutable 1.21 armor-material value used by modifiable armor. */
@Getter
public class DummyArmorMaterial implements IdAwareObject {
  private final ResourceLocation id;
  private final SoundEvent equipSound;
  private final Holder<ArmorMaterial> armorMaterial;

  public DummyArmorMaterial(ResourceLocation id, SoundEvent equipSound) {
    this.id = id;
    this.equipSound = equipSound;
    this.armorMaterial = Holder.direct(new ArmorMaterial(
      Map.of(), 0, Holder.direct(equipSound), () -> Ingredient.EMPTY,
      List.of(new ArmorMaterial.Layer(id)), 0, 0));
  }
}
