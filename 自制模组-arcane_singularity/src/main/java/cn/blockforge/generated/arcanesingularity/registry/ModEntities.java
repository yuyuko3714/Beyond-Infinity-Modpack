package cn.blockforge.generated.arcanesingularity.registry;

import cn.blockforge.generated.arcanesingularity.ArcaneSingularity;
import cn.blockforge.generated.arcanesingularity.entity.ArmillaryPartEntity;
import cn.blockforge.generated.arcanesingularity.entity.StargateRingPartEntity;
import cn.blockforge.generated.arcanesingularity.entity.TimeBeaconRingPartEntity;
import cn.blockforge.generated.arcanesingularity.entity.WorldCoreCoreEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ArcaneSingularity.MOD_ID);

    /** 浑天仪转子上实体化的环方块与激光稳定器（只有碰撞，外观由控制器渲染）。 */
    public static final RegistryObject<EntityType<ArmillaryPartEntity>> ARMILLARY_PART = ENTITIES.register(
            "armillary_part", () -> EntityType.Builder
                    .of(ArmillaryPartEntity::new, MobCategory.MISC)
                    .sized(1.7F, 1.4F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(null));

    /** 星门符文环上实体化的门框方块（像车轮一样绕门心自转）。 */
    public static final RegistryObject<EntityType<StargateRingPartEntity>> STARGATE_RING_PART = ENTITIES.register(
            "stargate_ring_part", () -> EntityType.Builder
                    .of(StargateRingPartEntity::new, MobCategory.MISC)
                    .sized(1.35F, 1.35F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(null));

    /** 时间信标能量汇聚环上实体化的环方块（绕炮管中轴由慢到快旋转）。 */
    public static final RegistryObject<EntityType<TimeBeaconRingPartEntity>> TIME_BEACON_RING_PART =
            ENTITIES.register("time_beacon_ring_part", () -> EntityType.Builder
                    .of(TimeBeaconRingPartEntity::new, MobCategory.MISC)
                    .sized(TimeBeaconRingPartEntity.PART_WIDTH, TimeBeaconRingPartEntity.PART_HEIGHT)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(null));

    /** 世界之核的核心：悬在中轴上、带碰撞箱的命令方块模型（外观由实体渲染器画）。 */
    public static final RegistryObject<EntityType<WorldCoreCoreEntity>> WORLD_CORE_CORE = ENTITIES.register(
            "world_core_core", () -> EntityType.Builder
                    .of(WorldCoreCoreEntity::new, MobCategory.MISC)
                    .sized(WorldCoreCoreEntity.CORE_WIDTH, WorldCoreCoreEntity.CORE_HEIGHT)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .build(null));

    private ModEntities() {
    }
}
