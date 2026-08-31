package net.minecraft.client.resources.model;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Transformation;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ModelBakery {
    public static final Material FIRE_0 = new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.withDefaultNamespace("block/fire_0"));
    public static final Material FIRE_1 = new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.withDefaultNamespace("block/fire_1"));
    public static final Material LAVA_FLOW = new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.withDefaultNamespace("block/lava_flow"));
    public static final Material WATER_FLOW = new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.withDefaultNamespace("block/water_flow"));
    public static final Material WATER_OVERLAY = new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.withDefaultNamespace("block/water_overlay"));
    public static final Material BANNER_BASE = new Material(Sheets.BANNER_SHEET, ResourceLocation.withDefaultNamespace("entity/banner_base"));
    public static final Material SHIELD_BASE = new Material(Sheets.SHIELD_SHEET, ResourceLocation.withDefaultNamespace("entity/shield_base"));
    public static final Material NO_PATTERN_SHIELD = new Material(Sheets.SHIELD_SHEET, ResourceLocation.withDefaultNamespace("entity/shield_base_nopattern"));
    public static final int DESTROY_STAGE_COUNT = 10;
    public static final List<ResourceLocation> DESTROY_STAGES = IntStream.range(0, 10)
        .mapToObj(p_349912_ -> ResourceLocation.withDefaultNamespace("block/destroy_stage_" + p_349912_))
        .collect(Collectors.toList());
    public static final List<ResourceLocation> BREAKING_LOCATIONS = DESTROY_STAGES.stream()
        .map(p_349910_ -> p_349910_.withPath(p_349911_ -> "textures/" + p_349911_ + ".png"))
        .collect(Collectors.toList());
    public static final List<RenderType> DESTROY_TYPES = BREAKING_LOCATIONS.stream().map(RenderType::crumbling).collect(Collectors.toList());
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BUILTIN_SLASH = "builtin/";
    private static final String BUILTIN_SLASH_GENERATED = "builtin/generated";
    private static final String BUILTIN_BLOCK_ENTITY = "builtin/entity";
    private static final String MISSING_MODEL_NAME = "missing";
    public static final ResourceLocation MISSING_MODEL_LOCATION = ResourceLocation.withDefaultNamespace("builtin/missing");
    public static final ModelResourceLocation MISSING_MODEL_VARIANT = new ModelResourceLocation(MISSING_MODEL_LOCATION, "missing");
    public static final FileToIdConverter MODEL_LISTER = FileToIdConverter.json("models");
    @VisibleForTesting
    public static final String MISSING_MODEL_MESH = ("{    'textures': {       'particle': '"
            + MissingTextureAtlasSprite.getLocation().getPath()
            + "',       'missingno': '"
            + MissingTextureAtlasSprite.getLocation().getPath()
            + "'    },    'elements': [         {  'from': [ 0, 0, 0 ],            'to': [ 16, 16, 16 ],            'faces': {                'down':  { 'uv': [ 0, 0, 16, 16 ], 'cullface': 'down',  'texture': '#missingno' },                'up':    { 'uv': [ 0, 0, 16, 16 ], 'cullface': 'up',    'texture': '#missingno' },                'north': { 'uv': [ 0, 0, 16, 16 ], 'cullface': 'north', 'texture': '#missingno' },                'south': { 'uv': [ 0, 0, 16, 16 ], 'cullface': 'south', 'texture': '#missingno' },                'west':  { 'uv': [ 0, 0, 16, 16 ], 'cullface': 'west',  'texture': '#missingno' },                'east':  { 'uv': [ 0, 0, 16, 16 ], 'cullface': 'east',  'texture': '#missingno' }            }        }    ]}")
        .replace('\'', '"');
    private static final Map<String, String> BUILTIN_MODELS = Map.of("missing", MISSING_MODEL_MESH);
    public static final BlockModel GENERATION_MARKER = Util.make(
        BlockModel.fromString("{\"gui_light\": \"front\"}"), p_119359_ -> p_119359_.name = "generation marker"
    );
    public static final BlockModel BLOCK_ENTITY_MARKER = Util.make(
        BlockModel.fromString("{\"gui_light\": \"side\"}"), p_119297_ -> p_119297_.name = "block entity marker"
    );
    static final ItemModelGenerator ITEM_MODEL_GENERATOR = new ItemModelGenerator();
    private final Map<ResourceLocation, BlockModel> modelResources;
    private final Set<ResourceLocation> loadingStack = new HashSet<>();
    private final Map<ResourceLocation, UnbakedModel> unbakedCache = new HashMap<>();
    final Map<ModelBakery.BakedCacheKey, BakedModel> bakedCache = new HashMap<>();
    private final Map<ModelResourceLocation, UnbakedModel> topLevelModels = new HashMap<>();
    private final Map<ModelResourceLocation, BakedModel> bakedTopLevelModels = new HashMap<>();
    private final UnbakedModel missingModel;
    private final Object2IntMap<BlockState> modelGroups;

    public ModelBakery(
        BlockColors blockColors,
        ProfilerFiller profilerFiller,
        Map<ResourceLocation, BlockModel> modelResources,
        Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStateResources
    ) {
        this.modelResources = modelResources;
        profilerFiller.push("missing_model");

        try {
            this.missingModel = this.loadBlockModel(MISSING_MODEL_LOCATION);
            this.registerModel(MISSING_MODEL_VARIANT, this.missingModel);
        } catch (IOException ioexception) {
            LOGGER.error("Error loading missing model, should never happen :(", (Throwable)ioexception);
            throw new RuntimeException(ioexception);
        }

        BlockStateModelLoader blockstatemodelloader = new BlockStateModelLoader(
            blockStateResources, profilerFiller, this.missingModel, blockColors, this::registerModelAndLoadDependencies
        );
        blockstatemodelloader.loadAllBlockStates();
        this.modelGroups = blockstatemodelloader.getModelGroups();
        profilerFiller.popPush("items");

        for (ResourceLocation resourcelocation : BuiltInRegistries.ITEM.keySet()) {
            this.loadItemModelAndDependencies(resourcelocation);
        }

        profilerFiller.popPush("special");
        this.loadSpecialItemModelAndDependencies(ItemRenderer.TRIDENT_IN_HAND_MODEL);
        this.loadSpecialItemModelAndDependencies(ItemRenderer.SPYGLASS_IN_HAND_MODEL);
        Set<ModelResourceLocation> additionalModels = new HashSet<>();
        net.neoforged.neoforge.client.ClientHooks.onRegisterAdditionalModels(additionalModels);
        for (ModelResourceLocation rl : additionalModels) {
            UnbakedModel unbakedmodel = this.getModel(rl.id());
            this.registerModelAndLoadDependencies(rl, unbakedmodel);
        }
        this.topLevelModels.values().forEach(p_247954_ -> p_247954_.resolveParents(this::getModel));
        profilerFiller.pop();
    }

    public void bakeModels(ModelBakery.TextureGetter textureGetter) {
        this.topLevelModels.forEach((p_351687_, p_351688_) -> {
            BakedModel bakedmodel = null;

            try {
                bakedmodel = new ModelBakery.ModelBakerImpl(textureGetter, p_351687_).bakeUncached(p_351688_, BlockModelRotation.X0_Y0);
            } catch (Exception exception) {
                LOGGER.warn("Unable to bake model: '{}': {}", p_351687_, exception);
            }

            if (bakedmodel != null) {
                this.bakedTopLevelModels.put(p_351687_, bakedmodel);
            }
        });
    }

    UnbakedModel getModel(ResourceLocation modelLocation) {
        if (this.unbakedCache.containsKey(modelLocation)) {
            return this.unbakedCache.get(modelLocation);
        } else if (this.loadingStack.contains(modelLocation)) {
            throw new IllegalStateException("Circular reference while loading " + modelLocation);
        } else {
            this.loadingStack.add(modelLocation);

            while (!this.loadingStack.isEmpty()) {
                ResourceLocation resourcelocation = this.loadingStack.iterator().next();

                try {
                    if (!this.unbakedCache.containsKey(resourcelocation)) {
                        UnbakedModel unbakedmodel = this.loadBlockModel(resourcelocation);
                        this.unbakedCache.put(resourcelocation, unbakedmodel);
                        this.loadingStack.addAll(unbakedmodel.getDependencies());
                    }
                } catch (Exception exception) {
                    LOGGER.warn("Unable to load model: '{}' referenced from: {}: {}", resourcelocation, modelLocation, exception);
                    this.unbakedCache.put(resourcelocation, this.missingModel);
                } finally {
                    this.loadingStack.remove(resourcelocation);
                }
            }

            return this.unbakedCache.getOrDefault(modelLocation, this.missingModel);
        }
    }

    private void loadItemModelAndDependencies(ResourceLocation modelLocation) {
        ModelResourceLocation modelresourcelocation = ModelResourceLocation.inventory(modelLocation);
        ResourceLocation resourcelocation = modelLocation.withPrefix("item/");
        UnbakedModel unbakedmodel = this.getModel(resourcelocation);
        this.registerModelAndLoadDependencies(modelresourcelocation, unbakedmodel);
    }

    private void loadSpecialItemModelAndDependencies(ModelResourceLocation modelLocation) {
        ResourceLocation resourcelocation = modelLocation.id().withPrefix("item/");
        UnbakedModel unbakedmodel = this.getModel(resourcelocation);
        this.registerModelAndLoadDependencies(modelLocation, unbakedmodel);
    }

    private void registerModelAndLoadDependencies(ModelResourceLocation modelLocation, UnbakedModel model) {
        for (ResourceLocation resourcelocation : model.getDependencies()) {
            this.getModel(resourcelocation);
        }

        this.registerModel(modelLocation, model);
    }

    private void registerModel(ModelResourceLocation modelLocation, UnbakedModel model) {
        this.topLevelModels.put(modelLocation, model);
    }

    private BlockModel loadBlockModel(ResourceLocation location) throws IOException {
        String s = location.getPath();
        if ("builtin/generated".equals(s)) {
            return GENERATION_MARKER;
        } else if ("builtin/entity".equals(s)) {
            return BLOCK_ENTITY_MARKER;
        } else if (s.startsWith("builtin/")) {
            String s1 = s.substring("builtin/".length());
            String s2 = BUILTIN_MODELS.get(s1);
            if (s2 == null) {
                throw new FileNotFoundException(location.toString());
            } else {
                Reader reader = new StringReader(s2);
                BlockModel blockmodel1 = BlockModel.fromStream(reader);
                blockmodel1.name = location.toString();
                return blockmodel1;
            }
        } else {
            ResourceLocation resourcelocation = MODEL_LISTER.idToFile(location);
            BlockModel blockmodel = this.modelResources.get(resourcelocation);
            if (blockmodel == null) {
                throw new FileNotFoundException(resourcelocation.toString());
            } else {
                blockmodel.name = location.toString();
                return blockmodel;
            }
        }
    }

    public Map<ModelResourceLocation, BakedModel> getBakedTopLevelModels() {
        return this.bakedTopLevelModels;
    }

    public Object2IntMap<BlockState> getModelGroups() {
        return this.modelGroups;
    }

    @OnlyIn(Dist.CLIENT)
    static record BakedCacheKey(ResourceLocation id, Transformation transformation, boolean isUvLocked) {
    }

    @OnlyIn(Dist.CLIENT)
    class ModelBakerImpl implements ModelBaker {
        private final Function<Material, TextureAtlasSprite> modelTextureGetter;

        ModelBakerImpl(ModelBakery.TextureGetter textureGetter, ModelResourceLocation modelLocation) {
            this.modelTextureGetter = p_351691_ -> textureGetter.get(modelLocation, p_351691_);
        }

        @Override
        public UnbakedModel getModel(ResourceLocation location) {
            return ModelBakery.this.getModel(location);
        }

        @Override
        @Nullable
        public UnbakedModel getTopLevelModel(ModelResourceLocation location) {
            return topLevelModels.get(location);
        }

        @Override
        public Function<Material, TextureAtlasSprite> getModelTextureGetter() {
            return this.modelTextureGetter;
        }

        @Override
        public BakedModel bake(ResourceLocation location, ModelState transform) {
            return bake(location, transform, this.modelTextureGetter);
        }

        @Override
        public BakedModel bake(ResourceLocation location, ModelState transform, Function<Material, TextureAtlasSprite> sprites) {
            ModelBakery.BakedCacheKey modelbakery$bakedcachekey = new ModelBakery.BakedCacheKey(location, transform.getRotation(), transform.isUvLocked());
            BakedModel bakedmodel = ModelBakery.this.bakedCache.get(modelbakery$bakedcachekey);
            if (bakedmodel != null) {
                return bakedmodel;
            } else {
                UnbakedModel unbakedmodel = this.getModel(location);
                BakedModel bakedmodel1 = this.bakeUncached(unbakedmodel, transform, sprites);
                ModelBakery.this.bakedCache.put(modelbakery$bakedcachekey, bakedmodel1);
                return bakedmodel1;
            }
        }

        @Nullable
        BakedModel bakeUncached(UnbakedModel model, ModelState state) {
            return bakeUncached(model, state, this.modelTextureGetter);
        }

        @Override
        @Nullable
        public BakedModel bakeUncached(UnbakedModel model, ModelState state, Function<Material, TextureAtlasSprite> sprites) {
            if (model instanceof BlockModel blockmodel && blockmodel.getRootModel() == ModelBakery.GENERATION_MARKER) {
                return ModelBakery.ITEM_MODEL_GENERATOR
                    .generateBlockModel(sprites, blockmodel)
                    .bake(this, blockmodel, sprites, state, false);
            }

            return model.bake(this, sprites, state);
        }
    }

    @FunctionalInterface
    @OnlyIn(Dist.CLIENT)
    public interface TextureGetter {
        TextureAtlasSprite get(ModelResourceLocation modelLocation, Material material);
    }
}
