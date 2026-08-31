package net.minecraft.client.resources.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ModelManager implements PreparableReloadListener, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, ResourceLocation> VANILLA_ATLASES = Map.of(
        Sheets.BANNER_SHEET,
        ResourceLocation.withDefaultNamespace("banner_patterns"),
        Sheets.BED_SHEET,
        ResourceLocation.withDefaultNamespace("beds"),
        Sheets.CHEST_SHEET,
        ResourceLocation.withDefaultNamespace("chests"),
        Sheets.SHIELD_SHEET,
        ResourceLocation.withDefaultNamespace("shield_patterns"),
        Sheets.SIGN_SHEET,
        ResourceLocation.withDefaultNamespace("signs"),
        Sheets.SHULKER_SHEET,
        ResourceLocation.withDefaultNamespace("shulker_boxes"),
        Sheets.ARMOR_TRIMS_SHEET,
        ResourceLocation.withDefaultNamespace("armor_trims"),
        Sheets.DECORATED_POT_SHEET,
        ResourceLocation.withDefaultNamespace("decorated_pot"),
        TextureAtlas.LOCATION_BLOCKS,
        ResourceLocation.withDefaultNamespace("blocks")
    );
    private Map<ModelResourceLocation, BakedModel> bakedRegistry = new java.util.HashMap<>();
    private final AtlasSet atlases;
    private final BlockModelShaper blockModelShaper;
    private final BlockColors blockColors;
    private int maxMipmapLevels;
    private BakedModel missingModel;
    private Object2IntMap<BlockState> modelGroups;
    private ModelBakery modelBakery;

    public ModelManager(TextureManager textureManager, BlockColors blockColors, int maxMipmapLevels) {
        this.blockColors = blockColors;
        this.maxMipmapLevels = maxMipmapLevels;
        this.blockModelShaper = new BlockModelShaper(this);
        Map<ResourceLocation, ResourceLocation> VANILLA_ATLASES = net.neoforged.neoforge.client.ClientHooks.gatherMaterialAtlases(ModelManager.VANILLA_ATLASES);
        this.atlases = new AtlasSet(VANILLA_ATLASES, textureManager);
    }

    public BakedModel getModel(ModelResourceLocation modelLocation) {
        return this.bakedRegistry.getOrDefault(modelLocation, this.missingModel);
    }

    public BakedModel getMissingModel() {
        return this.missingModel;
    }

    public BlockModelShaper getBlockModelShaper() {
        return this.blockModelShaper;
    }

    @Override
    public final CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier preparationBarrier,
        ResourceManager resourceManager,
        ProfilerFiller preparationsProfiler,
        ProfilerFiller reloadProfiler,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        preparationsProfiler.startTick();
        net.neoforged.neoforge.client.model.geometry.GeometryLoaderManager.init();
        CompletableFuture<Map<ResourceLocation, BlockModel>> completablefuture = loadBlockModels(resourceManager, backgroundExecutor);
        CompletableFuture<Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>>> completablefuture1 = loadBlockStates(resourceManager, backgroundExecutor);
        CompletableFuture<ModelBakery> completablefuture2 = completablefuture.thenCombineAsync(
            completablefuture1,
            (p_251201_, p_251281_) -> new ModelBakery(
                    this.blockColors,
                    preparationsProfiler,
                    (Map<ResourceLocation, BlockModel>)p_251201_,
                    (Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>>)p_251281_
                ),
            backgroundExecutor
        );
        Map<ResourceLocation, CompletableFuture<AtlasSet.StitchResult>> map = this.atlases.scheduleLoad(resourceManager, this.maxMipmapLevels, backgroundExecutor);
        return CompletableFuture.allOf(Stream.concat(map.values().stream(), Stream.of(completablefuture2)).toArray(CompletableFuture[]::new))
            .thenApplyAsync(
                p_248624_ -> this.loadModels(
                        preparationsProfiler,
                        map.entrySet().stream().collect(Collectors.toMap(Entry::getKey, p_248988_ -> p_248988_.getValue().join())),
                        completablefuture2.join()
                    ),
                backgroundExecutor
            )
            .thenCompose(p_252255_ -> p_252255_.readyForUpload.thenApply(p_251581_ -> (ModelManager.ReloadState)p_252255_))
            .thenCompose(preparationBarrier::wait)
            .thenAcceptAsync(p_252252_ -> this.apply(p_252252_, reloadProfiler), gameExecutor);
    }

    private static CompletableFuture<Map<ResourceLocation, BlockModel>> loadBlockModels(ResourceManager resourceManager, Executor executor) {
        return CompletableFuture.<Map<ResourceLocation, Resource>>supplyAsync(() -> ModelBakery.MODEL_LISTER.listMatchingResources(resourceManager), executor)
            .thenCompose(
                p_250597_ -> {
                    List<CompletableFuture<Pair<ResourceLocation, BlockModel>>> list = new ArrayList<>(p_250597_.size());

                    for (Entry<ResourceLocation, Resource> entry : p_250597_.entrySet()) {
                        list.add(CompletableFuture.supplyAsync(() -> {
                            try {
                                Pair pair;
                                try (Reader reader = entry.getValue().openAsReader()) {
                                    pair = Pair.of(entry.getKey(), BlockModel.fromStream(reader));
                                }

                                return pair;
                            } catch (Exception exception) {
                                LOGGER.error("Failed to load model {}", entry.getKey(), exception);
                                return null;
                            }
                        }, executor));
                    }

                    return Util.sequence(list)
                        .thenApply(
                            p_250813_ -> p_250813_.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond))
                        );
                }
            );
    }

    private static CompletableFuture<Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>>> loadBlockStates(
        ResourceManager resourceManager, Executor executor
    ) {
        return CompletableFuture.<Map<ResourceLocation, List<Resource>>>supplyAsync(
                () -> BlockStateModelLoader.BLOCKSTATE_LISTER.listMatchingResourceStacks(resourceManager), executor
            )
            .thenCompose(
                p_250744_ -> {
                    List<CompletableFuture<Pair<ResourceLocation, List<BlockStateModelLoader.LoadedJson>>>> list = new ArrayList<>(p_250744_.size());

                    for (Entry<ResourceLocation, List<Resource>> entry : p_250744_.entrySet()) {
                        list.add(CompletableFuture.supplyAsync(() -> {
                            List<Resource> list1 = entry.getValue();
                            List<BlockStateModelLoader.LoadedJson> list2 = new ArrayList<>(list1.size());

                            for (Resource resource : list1) {
                                try (Reader reader = resource.openAsReader()) {
                                    JsonObject jsonobject = GsonHelper.parse(reader);
                                    list2.add(new BlockStateModelLoader.LoadedJson(resource.sourcePackId(), jsonobject));
                                } catch (Exception exception) {
                                    LOGGER.error("Failed to load blockstate {} from pack {}", entry.getKey(), resource.sourcePackId(), exception);
                                }
                            }

                            return Pair.of(entry.getKey(), list2);
                        }, executor));
                    }

                    return Util.sequence(list)
                        .thenApply(
                            p_248966_ -> p_248966_.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond))
                        );
                }
            );
    }

    private ModelManager.ReloadState loadModels(ProfilerFiller profilerFiller, Map<ResourceLocation, AtlasSet.StitchResult> atlasPreparations, ModelBakery modelBakery) {
        profilerFiller.push("load");
        profilerFiller.popPush("baking");
        Multimap<ModelResourceLocation, Material> multimap = HashMultimap.create();
        modelBakery.bakeModels((p_352403_, p_251262_) -> {
            AtlasSet.StitchResult atlasset$stitchresult = atlasPreparations.get(p_251262_.atlasLocation());
            TextureAtlasSprite textureatlassprite = atlasset$stitchresult.getSprite(p_251262_.texture());
            if (textureatlassprite != null) {
                return textureatlassprite;
            } else {
                multimap.put(p_352403_, p_251262_);
                return atlasset$stitchresult.missing();
            }
        });
        multimap.asMap()
            .forEach(
                (p_352087_, p_252017_) -> LOGGER.warn(
                        "Missing textures in model {}:\n{}",
                        p_352087_,
                        p_252017_.stream()
                            .sorted(Material.COMPARATOR)
                            .map(p_339314_ -> "    " + p_339314_.atlasLocation() + ":" + p_339314_.texture())
                            .collect(Collectors.joining("\n"))
                    )
            );
        profilerFiller.popPush("forge_modify_baking_result");
        net.neoforged.neoforge.client.ClientHooks.onModifyBakingResult(modelBakery.getBakedTopLevelModels(), atlasPreparations, modelBakery);
        profilerFiller.popPush("dispatch");
        Map<ModelResourceLocation, BakedModel> map = modelBakery.getBakedTopLevelModels();
        BakedModel bakedmodel = map.get(ModelBakery.MISSING_MODEL_VARIANT);
        Map<BlockState, BakedModel> map1 = new IdentityHashMap<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            block.getStateDefinition().getPossibleStates().forEach(p_250633_ -> {
                ResourceLocation resourcelocation = p_250633_.getBlock().builtInRegistryHolder().key().location();
                BakedModel bakedmodel1 = map.getOrDefault(BlockModelShaper.stateToModelLocation(resourcelocation, p_250633_), bakedmodel);
                map1.put(p_250633_, bakedmodel1);
            });
        }

        CompletableFuture<Void> completablefuture = CompletableFuture.allOf(
            atlasPreparations.values().stream().map(AtlasSet.StitchResult::readyForUpload).toArray(CompletableFuture[]::new)
        );
        profilerFiller.pop();
        profilerFiller.endTick();
        return new ModelManager.ReloadState(modelBakery, bakedmodel, map1, atlasPreparations, completablefuture);
    }

    private void apply(ModelManager.ReloadState reloadState, ProfilerFiller profiler) {
        profiler.startTick();
        profiler.push("upload");
        reloadState.atlasPreparations.values().forEach(AtlasSet.StitchResult::upload);
        ModelBakery modelbakery = reloadState.modelBakery;
        this.bakedRegistry = modelbakery.getBakedTopLevelModels();
        this.modelGroups = modelbakery.getModelGroups();
        this.missingModel = reloadState.missingModel;
        this.modelBakery = modelbakery;
        net.neoforged.neoforge.client.ClientHooks.onModelBake(this, this.bakedRegistry, modelbakery);
        profiler.popPush("cache");
        this.blockModelShaper.replaceCache(reloadState.modelCache);
        profiler.pop();
        profiler.endTick();
    }

    public boolean requiresRender(BlockState oldState, BlockState newState) {
        if (oldState == newState) {
            return false;
        } else {
            int i = this.modelGroups.getInt(oldState);
            if (i != -1) {
                int j = this.modelGroups.getInt(newState);
                if (i == j) {
                    FluidState fluidstate = oldState.getFluidState();
                    FluidState fluidstate1 = newState.getFluidState();
                    return fluidstate != fluidstate1;
                }
            }

            return true;
        }
    }

    public TextureAtlas getAtlas(ResourceLocation location) {
        if (this.atlases == null) throw new RuntimeException("getAtlasTexture called too early!");
        return this.atlases.getAtlas(location);
    }

    @Override
    public void close() {
        this.atlases.close();
    }

    public void updateMaxMipLevel(int level) {
        this.maxMipmapLevels = level;
    }

    public ModelBakery getModelBakery() {
        return com.google.common.base.Preconditions.checkNotNull(modelBakery, "Attempted to query model bakery before it has been initialized.");
    }

    @OnlyIn(Dist.CLIENT)
    static record ReloadState(
        ModelBakery modelBakery,
        BakedModel missingModel,
        Map<BlockState, BakedModel> modelCache,
        Map<ResourceLocation, AtlasSet.StitchResult> atlasPreparations,
        CompletableFuture<Void> readyForUpload
    ) {
    }
}
