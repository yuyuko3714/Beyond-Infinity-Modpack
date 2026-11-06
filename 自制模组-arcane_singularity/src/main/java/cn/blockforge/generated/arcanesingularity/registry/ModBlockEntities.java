package cn.blockforge.generated.arcanesingularity.registry;

import cn.blockforge.generated.arcanesingularity.ArcaneSingularity;
import cn.blockforge.generated.arcanesingularity.blockentity.EnergyCableBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.EnergyPortBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.ProjectionGhostBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.SingularityControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.StargateControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.TimeBeaconControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.WorldCoreControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.WorldCoreInterfaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ArcaneSingularity.MOD_ID);

    public static final RegistryObject<BlockEntityType<SingularityControllerBlockEntity>> SINGULARITY_CONTROLLER =
            BLOCK_ENTITIES.register("singularity_controller",
                    () -> BlockEntityType.Builder.of(SingularityControllerBlockEntity::new,
                            ModBlocks.SINGULARITY_CONTROLLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<StargateControllerBlockEntity>> STARGATE_CONTROLLER =
            BLOCK_ENTITIES.register("stargate_controller",
                    () -> BlockEntityType.Builder.of(StargateControllerBlockEntity::new,
                            ModBlocks.STARGATE_CONTROLLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<TimeBeaconControllerBlockEntity>> TIME_BEACON_CONTROLLER =
            BLOCK_ENTITIES.register("time_beacon_controller",
                    () -> BlockEntityType.Builder.of(TimeBeaconControllerBlockEntity::new,
                            ModBlocks.TIME_BEACON_CONTROLLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<WorldCoreControllerBlockEntity>> WORLD_CORE_CONTROLLER =
            BLOCK_ENTITIES.register("world_core_controller",
                    () -> BlockEntityType.Builder.of(WorldCoreControllerBlockEntity::new,
                            ModBlocks.WORLD_CORE_CONTROLLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<WorldCoreInterfaceBlockEntity>> WORLD_CORE_INTERFACE =
            BLOCK_ENTITIES.register("world_core_interface",
                    () -> BlockEntityType.Builder.of(WorldCoreInterfaceBlockEntity::new,
                            ModBlocks.WORLD_CORE_INTERFACE.get()).build(null));
    public static final RegistryObject<BlockEntityType<EnergyCableBlockEntity>> ENERGY_CABLE =
            BLOCK_ENTITIES.register("energy_cable",
                    () -> BlockEntityType.Builder.of(EnergyCableBlockEntity::new,
                            ModBlocks.ENERGY_CABLE.get()).build(null));
    public static final RegistryObject<BlockEntityType<EnergyPortBlockEntity>> ENERGY_PORT =
            BLOCK_ENTITIES.register("energy_port",
                    () -> BlockEntityType.Builder.of(EnergyPortBlockEntity::new,
                            ModBlocks.ENERGY_OUTPUT_PORT.get(), ModBlocks.ENERGY_INPUT_PORT.get()).build(null));

    public static final RegistryObject<BlockEntityType<ProjectionGhostBlockEntity>> PROJECTION_GHOST =
            BLOCK_ENTITIES.register("projection_ghost",
                    () -> BlockEntityType.Builder.of(ProjectionGhostBlockEntity::new,
                            ModBlocks.PROJECTION_GHOST.get()).build(null));

    private ModBlockEntities() {
    }
}
