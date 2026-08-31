package net.minecraft.client.resources.model;

import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record ModelResourceLocation(ResourceLocation id, String variant) {
    public static final String INVENTORY_VARIANT = "inventory";
    public static final String STANDALONE_VARIANT = "standalone";

    public ModelResourceLocation(ResourceLocation id, String variant) {
        variant = lowercaseVariant(variant);
        this.id = id;
        this.variant = variant;
    }

    public static ModelResourceLocation vanilla(String path, String variant) {
        return new ModelResourceLocation(ResourceLocation.withDefaultNamespace(path), variant);
    }

    public static ModelResourceLocation inventory(ResourceLocation id) {
        return new ModelResourceLocation(id, "inventory");
    }

    /**
     * Construct a {@code ModelResourceLocation} for use in the {@link net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional}
     * to load a model at the given path directly instead of going through blockstates or item model auto-prefixing.
     */
    public static ModelResourceLocation standalone(ResourceLocation id) {
        return new ModelResourceLocation(id, STANDALONE_VARIANT);
    }

    private static String lowercaseVariant(String variant) {
        return variant.toLowerCase(Locale.ROOT);
    }

    public String getVariant() {
        return this.variant;
    }

    @Override
    public String toString() {
        return this.id + "#" + this.variant;
    }
}
