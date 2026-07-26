package slimeknights.mantle;

import net.minecraft.Util;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import slimeknights.mantle.block.entity.MantleHangingSignBlockEntity;
import slimeknights.mantle.block.entity.MantleSignBlockEntity;
import slimeknights.mantle.block.entity.InventoryBlockEntity;
import slimeknights.mantle.block.InventoryBlock;
import slimeknights.mantle.client.ClientEvents;
import slimeknights.mantle.command.MantleCommand;
import slimeknights.mantle.command.argument.ResourceOrTagKeyArgument;
import slimeknights.mantle.config.Config;
import slimeknights.mantle.data.predicate.block.BlockPredicate;
import slimeknights.mantle.data.predicate.block.BlockPropertiesPredicate;
import slimeknights.mantle.data.predicate.damage.DamageSourcePredicate;
import slimeknights.mantle.data.predicate.damage.DamageTypePredicate;
import slimeknights.mantle.data.predicate.damage.SourceAttackerPredicate;
import slimeknights.mantle.data.predicate.damage.SourceMessagePredicate;
import slimeknights.mantle.data.predicate.entity.BlockAtEntityPredicate;
import slimeknights.mantle.data.predicate.entity.HasEnchantmentEntityPredicate;
import slimeknights.mantle.data.predicate.entity.HasMobEffectPredicate;
import slimeknights.mantle.data.predicate.entity.LivingEntityPredicate;
import slimeknights.mantle.data.predicate.entity.MobTypePredicate;
import slimeknights.mantle.data.predicate.fluid.FluidPredicate;
import slimeknights.mantle.data.predicate.fluid.FluidTypePredicate;
import slimeknights.mantle.data.predicate.item.ItemPredicate;
import slimeknights.mantle.datagen.MantleTags;
import slimeknights.mantle.fluid.transfer.EmptyFluidContainerTransfer;
import slimeknights.mantle.fluid.transfer.EmptyFluidWithNBTTransfer;
import slimeknights.mantle.fluid.transfer.EmptyPotionTransfer;
import slimeknights.mantle.fluid.transfer.FillFluidContainerTransfer;
import slimeknights.mantle.fluid.transfer.FillFluidWithNBTTransfer;
import slimeknights.mantle.fluid.transfer.FluidContainerTransferManager;
import slimeknights.mantle.item.LecternBookItem;
import slimeknights.mantle.item.ContainerFoodItem.FluidContainerFoodItem;
import slimeknights.mantle.loot.LootTableInjector;
import slimeknights.mantle.loot.MantleLoot;
import slimeknights.mantle.network.MantleNetwork;
import slimeknights.mantle.recipe.MantleRecipes;
import slimeknights.mantle.recipe.condition.TagCombinationCondition;
import slimeknights.mantle.recipe.condition.TagEmptyCondition;
import slimeknights.mantle.recipe.condition.TagFilledCondition;
import slimeknights.mantle.recipe.helper.TagPreference;
import slimeknights.mantle.recipe.ingredient.FluidContainerIngredient;
import slimeknights.mantle.recipe.ingredient.PotionDisplayIngredient;
import slimeknights.mantle.recipe.ingredient.PotionIngredient;
import slimeknights.mantle.registration.RegistrationHelper;
import slimeknights.mantle.registration.MantleRegistrations;
import slimeknights.mantle.registration.adapter.BlockEntityTypeRegistryAdapter;
import slimeknights.mantle.util.OffhandCooldownTracker;

import java.util.Objects;
import java.util.Set;

/**
 * Mantle
 *
 * Central mod object for Mantle
 *
 * @author Sunstrike <sun@sunstrike.io>
 */
@Mod(Mantle.modId)
public class Mantle {
  public static final String modId = "mantle";
  public static final Logger logger = LogManager.getLogger("Mantle");
  /** Namespace for common tags, used for easier migration to the future "c" standard */
  public static final String COMMON = "c";

  /* Instance of this mod, used for grabbing prototype fields */
  public static Mantle instance;

  /* Proxies for sides, used for graphics processing */
  public Mantle(IEventBus bus, ModContainer container) {
    container.registerConfig(Type.CLIENT, Config.CLIENT_SPEC);
    container.registerConfig(Type.SERVER, Config.SERVER_SPEC);

    FluidContainerTransferManager.INSTANCE.init();
    MantleTags.init();

    instance = this;
    MantleNetwork.registerPackets();
    bus.addListener(EventPriority.NORMAL, false, FMLCommonSetupEvent.class, this::commonSetup);
    bus.addListener(EventPriority.NORMAL, false, RegisterCapabilitiesEvent.class, this::registerCapabilities);
    bus.addListener(EventPriority.NORMAL, false, RegisterPayloadHandlersEvent.class, MantleNetwork::registerPayloads);
    bus.addListener(EventPriority.NORMAL, false, RegisterEvent.class, this::register);
    MantleRecipes.init(bus);
    NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerInteractEvent.RightClickBlock.class, LecternBookItem::interactWithBlock);

    if (FMLEnvironment.dist == Dist.CLIENT) {
      ClientEvents.onConstruct();
    }
  }

  private void registerCapabilities(RegisterCapabilitiesEvent event) {
    OffhandCooldownTracker.register(event);
    Block[] inventoryBlocks = BuiltInRegistries.BLOCK.stream().filter(InventoryBlock.class::isInstance).toArray(Block[]::new);
    if (inventoryBlocks.length > 0) {
      event.registerBlock(Capabilities.ItemHandler.BLOCK,
        (level, pos, state, blockEntity, side) -> blockEntity instanceof InventoryBlockEntity inventory ? inventory.getItemHandler() : null,
        inventoryBlocks);
    }
    Item[] fluidFoods = BuiltInRegistries.ITEM.stream().filter(FluidContainerFoodItem.class::isInstance).toArray(Item[]::new);
    if (fluidFoods.length > 0) {
      event.registerItem(Capabilities.FluidHandler.ITEM,
        (stack, unused) -> ((FluidContainerFoodItem)stack.getItem()).createFluidHandler(stack), fluidFoods);
    }
  }

  private void commonSetup(final FMLCommonSetupEvent event) {
    MantleCommand.init();
    OffhandCooldownTracker.init();
    TagPreference.init();
    LootTableInjector.init();
  }

  private void register(RegisterEvent event) {
    ResourceKey<?> key = event.getRegistryKey();
    if (key == NeoForgeRegistries.Keys.CONDITION_CODECS) {
      event.register(NeoForgeRegistries.Keys.CONDITION_CODECS, TagEmptyCondition.ID, () -> TagEmptyCondition.CODEC);
      event.register(NeoForgeRegistries.Keys.CONDITION_CODECS, TagFilledCondition.ID, () -> TagFilledCondition.CODEC);
      event.register(NeoForgeRegistries.Keys.CONDITION_CODECS, TagCombinationCondition.ID, () -> TagCombinationCondition.CODEC);
    }
    else if (key == NeoForgeRegistries.Keys.INGREDIENT_TYPES) {
      event.register(NeoForgeRegistries.Keys.INGREDIENT_TYPES, FluidContainerIngredient.ID, () -> FluidContainerIngredient.TYPE);
      event.register(NeoForgeRegistries.Keys.INGREDIENT_TYPES, getResource("potion"), () -> PotionIngredient.SERIALIZER.type());
      event.register(NeoForgeRegistries.Keys.INGREDIENT_TYPES, getResource("potion_display"), () -> PotionDisplayIngredient.SERIALIZER.type());
    }
    else if (key == Registries.RECIPE_SERIALIZER) {
      // fluid container transfer
      FluidContainerTransferManager.TRANSFER_LOADERS.registerDeserializer(EmptyFluidContainerTransfer.ID, EmptyFluidContainerTransfer.DESERIALIZER);
      FluidContainerTransferManager.TRANSFER_LOADERS.registerDeserializer(FillFluidContainerTransfer.ID, FillFluidContainerTransfer.DESERIALIZER);
      FluidContainerTransferManager.TRANSFER_LOADERS.registerDeserializer(EmptyFluidWithNBTTransfer.ID, EmptyFluidWithNBTTransfer.DESERIALIZER);
      FluidContainerTransferManager.TRANSFER_LOADERS.registerDeserializer(FillFluidWithNBTTransfer.ID, FillFluidWithNBTTransfer.DESERIALIZER);
      FluidContainerTransferManager.TRANSFER_LOADERS.registerDeserializer(EmptyPotionTransfer.ID, EmptyPotionTransfer.DESERIALIZER);

      // predicates
      {
        // block predicates
        BlockPredicate.LOADER.register(getResource("requires_tool"), BlockPredicate.REQUIRES_TOOL.getLoader());
        BlockPredicate.LOADER.register(getResource("blocks_motion"), BlockPredicate.BLOCKS_MOTION.getLoader());
        BlockPredicate.LOADER.register(getResource("can_be_replaced"), BlockPredicate.CAN_BE_REPLACED.getLoader());
        BlockPredicate.LOADER.register(getResource("block_properties"), BlockPropertiesPredicate.LOADER);

        // item predicates
        ItemPredicate.LOADER.register(getResource("has_container"), ItemPredicate.HAS_CONTAINER.getLoader());
        ItemPredicate.LOADER.register(getResource("may_have_transfer"), ItemPredicate.MAY_HAVE_TRANSFER.getLoader());

        // fluid predicates
        FluidPredicate.LOADER.register(getResource("fluid_type"), FluidTypePredicate.LOADER);
        FluidPredicate.LOADER.register(getResource("is_source"), FluidPredicate.SOURCE.getLoader());
        FluidPredicate.LOADER.register(getResource("has_bucket"), FluidPredicate.HAS_BUCKET.getLoader());
        FluidPredicate.LOADER.register(getResource("lighter_than_air"), FluidPredicate.LIGHTER_THAN_AIR.getLoader());

        // entity predicates
        // simple
        LivingEntityPredicate.LOADER.register(getResource("fire_immune"), LivingEntityPredicate.FIRE_IMMUNE.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("can_freeze"), LivingEntityPredicate.CAN_FREEZE.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("water_sensitive"), LivingEntityPredicate.WATER_SENSITIVE.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("on_fire"), LivingEntityPredicate.ON_FIRE.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("is_freezing"), LivingEntityPredicate.IS_FREEZING.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("is_in_powdered_snow"), LivingEntityPredicate.IS_IN_POWDERED_SNOW.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("on_ground"), LivingEntityPredicate.ON_GROUND.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("crouching"), LivingEntityPredicate.CROUCHING.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("sprinting"), LivingEntityPredicate.SPRINTING.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("blocking"), LivingEntityPredicate.BLOCKING.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("elytra_flying"), LivingEntityPredicate.ELYTRA_FLYING.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("has_effect"), HasMobEffectPredicate.LOADER);
        LivingEntityPredicate.LOADER.register(getResource("block_at_entity"), BlockAtEntityPredicate.LOADER);
        LivingEntityPredicate.LOADER.register(getResource("eyes_in_water"), LivingEntityPredicate.EYES_IN_WATER.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("feet_in_water"), LivingEntityPredicate.FEET_IN_WATER.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("underwater"), LivingEntityPredicate.UNDERWATER.getLoader());
        LivingEntityPredicate.LOADER.register(getResource("raining_at"), LivingEntityPredicate.RAINING.getLoader());
        // property
        LivingEntityPredicate.LOADER.register(getResource("mob_type"), MobTypePredicate.LOADER);
        LivingEntityPredicate.LOADER.register(getResource("has_enchantment"), HasEnchantmentEntityPredicate.LOADER);
        // register mob types
        MobTypePredicate.MOB_TYPES.register(ResourceLocation.parse("undefined"), MobTypePredicate.UNDEFINED);
        MobTypePredicate.MOB_TYPES.register(ResourceLocation.parse("undead"), MobTypePredicate.UNDEAD);
        MobTypePredicate.MOB_TYPES.register(ResourceLocation.parse("arthropod"), MobTypePredicate.ARTHROPOD);
        MobTypePredicate.MOB_TYPES.register(ResourceLocation.parse("illager"), MobTypePredicate.ILLAGER);
        MobTypePredicate.MOB_TYPES.register(ResourceLocation.parse("water"), MobTypePredicate.WATER);

        // damage predicates
        // simple
        DamageSourcePredicate.LOADER.register(getResource("has_entity"), DamageSourcePredicate.HAS_ENTITY.getLoader());
        DamageSourcePredicate.LOADER.register(getResource("is_indirect"), DamageSourcePredicate.IS_INDIRECT.getLoader());
        DamageSourcePredicate.LOADER.register(getResource("can_protect"), DamageSourcePredicate.CAN_PROTECT.getLoader());
        // fields
        DamageSourcePredicate.LOADER.register(getResource("damage_type"), DamageTypePredicate.LOADER);
        DamageSourcePredicate.LOADER.register(getResource("message"), SourceMessagePredicate.LOADER);
        DamageSourcePredicate.LOADER.register(getResource("attacker"), SourceAttackerPredicate.LOADER);
      }
    }
    else if (key == Registries.BLOCK_ENTITY_TYPE) {
      BlockEntityTypeRegistryAdapter adapter = new BlockEntityTypeRegistryAdapter(Objects.requireNonNull(event.getRegistry(Registries.BLOCK_ENTITY_TYPE)));
      Set<Block> signs = MantleSignBlockEntity.buildSignBlocks();
      if (!signs.isEmpty()) {
        MantleRegistrations.SIGN = adapter.register(MantleSignBlockEntity::new, signs, "sign");
      }
      signs = MantleHangingSignBlockEntity.buildSignBlocks();
      if (!signs.isEmpty()) {
        MantleRegistrations.HANGING_SIGN = adapter.register(MantleHangingSignBlockEntity::new, signs, "hanging_sign");
      }
    }
    else if (key == Registries.COMMAND_ARGUMENT_TYPE) {
      ResourceOrTagKeyArgument.Info<?> info = new ResourceOrTagKeyArgument.Info<>();
      Registry.register(BuiltInRegistries.COMMAND_ARGUMENT_TYPE, getResource("resource_or_tag_key"), info);
      ArgumentTypeInfos.registerByClass(RegistrationHelper.genericArgumentType(ResourceOrTagKeyArgument.class), info);
    }
    else {
      MantleLoot.registerGlobalLootModifiers(event);
    }
  }

  /**
   * Gets a resource location for Mantle
   * @param name  Name
   * @return  Resource location instance
   */
  public static ResourceLocation getResource(String name) {
    return ResourceLocation.fromNamespaceAndPath(modId, name);
  }

  /**
   * Gets a resource location for the common namespace, which is "forge" for 1.20 and "c" for 1.21.
   * @param name  Name
   * @return  Resource location instance
   */
  public static ResourceLocation commonResource(String name) {
    return ResourceLocation.fromNamespaceAndPath(COMMON, name);
  }

  /**
   * Makes a translation key for the given name
   * @param base  Base name, such as "block" or "gui"
   * @param name  Object name
   * @return  Translation key
   */
  public static String makeDescriptionId(String base, String name) {
    return Util.makeDescriptionId(base, getResource(name));
  }

  /**
   * Makes a translation text component for the given name
   * @param base  Base name, such as "block" or "gui"
   * @param name  Object name
   * @return  Translation key
   */
  public static MutableComponent makeComponent(String base, String name) {
    return Component.translatable(makeDescriptionId(base, name));
  }

  /**
   * Makes a translation text component for the given name
   * @param base  Base name, such as "block" or "gui"
   * @param name  Object name
   * @param args  Additional arguments to format strings
   * @return  Translation key
   */
  public static MutableComponent makeComponent(String base, String name, Object... args) {
    return Component.translatable(makeDescriptionId(base, name), args);
  }
}
