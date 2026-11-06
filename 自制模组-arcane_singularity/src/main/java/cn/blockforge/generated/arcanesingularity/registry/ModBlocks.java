package cn.blockforge.generated.arcanesingularity.registry;

import cn.blockforge.generated.arcanesingularity.ArcaneSingularity;
import cn.blockforge.generated.arcanesingularity.block.ArcanePartBlock;
import cn.blockforge.generated.arcanesingularity.block.ArcaneRingBlock;
import cn.blockforge.generated.arcanesingularity.block.ArcaneProperties;
import cn.blockforge.generated.arcanesingularity.block.EnergyCableBlock;
import cn.blockforge.generated.arcanesingularity.block.EnergyPortBlock;
import cn.blockforge.generated.arcanesingularity.block.LaserInjectorBlock;
import cn.blockforge.generated.arcanesingularity.block.ProjectionGhostBlock;
import cn.blockforge.generated.arcanesingularity.block.SingularityControllerBlock;
import cn.blockforge.generated.arcanesingularity.block.StargateControllerBlock;
import cn.blockforge.generated.arcanesingularity.block.TimeBarrelBlock;
import cn.blockforge.generated.arcanesingularity.block.TimeBeaconControllerBlock;
import cn.blockforge.generated.arcanesingularity.block.WorldCoreApexBlock;
import cn.blockforge.generated.arcanesingularity.block.WorldCoreCableBlock;
import cn.blockforge.generated.arcanesingularity.block.WorldCoreControllerBlock;
import cn.blockforge.generated.arcanesingularity.block.WorldCoreEmitterBlock;
import cn.blockforge.generated.arcanesingularity.block.WorldCoreInterfaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ArcaneSingularity.MOD_ID);

    public static final RegistryObject<Block> RUNE_BRICKS = BLOCKS.register("rune_bricks",
            () -> new ArcanePartBlock(stoneProperties(MapColor.DEEPSLATE)));
    public static final RegistryObject<Block> CHISELED_RUNE_BRICKS = BLOCKS.register("chiseled_rune_bricks",
            () -> new ArcanePartBlock(stoneProperties(MapColor.DEEPSLATE)));
    public static final RegistryObject<Block> ARCANE_CRYSTAL_BLOCK = BLOCKS.register("arcane_crystal_block",
            () -> new ArcaneRingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0F, 600.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    // 实体化（active=true）时方块是隐形的，光也要一起熄掉，否则原地会留一团没来由的光
                    .lightLevel(state -> state.getValue(ArcaneProperties.ACTIVE) ? 0 : 7)));
    public static final RegistryObject<Block> RUNE_PILLAR = BLOCKS.register("rune_pillar",
            () -> new ArcanePartBlock(stoneProperties(MapColor.GOLD).lightLevel(state -> 3)));
    public static final RegistryObject<Block> ARMILLARY_RING = BLOCKS.register("armillary_ring",
            () -> new ArcaneRingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(4.5F, 900.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ArcaneProperties.ACTIVE) ? 0
                            : state.getValue(ArcaneProperties.FORMED) ? 10 : 4)));
    /** 激光注入器：成型后换成空模型、由渲染器画成悬浮在顶环中央的注入器。 */
    public static final RegistryObject<Block> LASER_INJECTOR = BLOCKS.register("laser_injector",
            () -> new LaserInjectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(5.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ArcaneProperties.FORMED) ? 14 : 7)));
    public static final RegistryObject<Block> LASER_STABILIZER = BLOCKS.register("laser_stabilizer",
            () -> new ArcaneRingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(5.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ArcaneProperties.ACTIVE) ? 0
                            : state.getValue(ArcaneProperties.FORMED) ? 13 : 6)));
    public static final RegistryObject<Block> ENERGY_CABLE = BLOCKS.register("energy_cable",
            () -> new EnergyCableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5F, 300.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ArcaneProperties.OVERLOADED) ? 9 : 3)));
    public static final RegistryObject<Block> ENERGY_OUTPUT_PORT = BLOCKS.register("energy_output_port",
            () -> new EnergyPortBlock(portProperties(), true));
    public static final RegistryObject<Block> ENERGY_INPUT_PORT = BLOCKS.register("energy_input_port",
            () -> new EnergyPortBlock(portProperties(), false));
    public static final RegistryObject<Block> SINGULARITY_CONTROLLER = BLOCKS.register("singularity_controller",
            () -> new SingularityControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(9.0F, 1400.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ArcaneProperties.ACTIVE) ? 15
                            : state.getValue(ArcaneProperties.FORMED) ? 8 : 3)));
    public static final RegistryObject<Block> STARGATE_FRAME = BLOCKS.register("stargate_frame",
            () -> new ArcaneRingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(7.0F, 1400.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE_BRICKS)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ArcaneProperties.ACTIVE) ? 0
                            : state.getValue(ArcaneProperties.FORMED) ? 9 : 2)));
    public static final RegistryObject<Block> STARGATE_CONTROLLER = BLOCKS.register("stargate_controller",
            () -> new StargateControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(8.0F, 1400.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ArcaneProperties.ACTIVE) ? 15
                            : state.getValue(ArcaneProperties.FORMED) ? 7 : 2)));
    /** 时间信标控制器：朝天激光发射塔的塔基。 */
    public static final RegistryObject<Block> TIME_BEACON_CONTROLLER = BLOCKS.register("time_beacon_controller",
            () -> new TimeBeaconControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(9.0F, 1600.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ArcaneProperties.ACTIVE) ? 15
                            : state.getValue(ArcaneProperties.FORMED) ? 9 : 2)));
    /** 能量汇聚环：开机后脱离底座、绕炮管由慢到快旋转。 */
    public static final RegistryObject<Block> CHRONO_RING = BLOCKS.register("chrono_ring",
            () -> new ArcaneRingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(5.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ArcaneProperties.ACTIVE) ? 0
                            : state.getValue(ArcaneProperties.FORMED) ? 12 : 4)));
    /** 时间信标的中央炮管。 */
    public static final RegistryObject<Block> TIME_BARREL = BLOCKS.register("time_barrel",
            () -> new TimeBarrelBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(6.0F, 1400.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ArcaneProperties.FORMED) ? 11 : 4)));
    /** 世界之核控制器：仪式场正中央那台机器，也是命令输入框的宿主。 */
    public static final RegistryObject<Block> WORLD_CORE_CONTROLLER = BLOCKS.register("world_core_controller",
            () -> new WorldCoreControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(10.0F, 1800.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ArcaneProperties.ACTIVE) ? 15
                            : state.getValue(ArcaneProperties.FORMED) ? 10 : 3)));
    /** 世界之核的专属接口：立在地基上，用来接三台控制器的专属管道。 */
    public static final RegistryObject<Block> WORLD_CORE_INTERFACE = BLOCKS.register("world_core_interface",
            () -> new WorldCoreInterfaceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(8.0F, 1500.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ArcaneProperties.ACTIVE) ? 13
                            : state.getValue(ArcaneProperties.FORMED) ? 7 : 2)));
    /** 世界之核专属管道：只连专属接口与三种控制器，和符文导管互不相通。 */
    public static final RegistryObject<Block> WORLD_CORE_CABLE = BLOCKS.register("world_core_cable",
            () -> new WorldCoreCableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_MAGENTA)
                    .strength(1.8F, 300.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 4)));
    /** 接口上方的专属激光器（三台一组，射向天空同一点）。 */
    public static final RegistryObject<Block> WORLD_CORE_EMITTER = BLOCKS.register("world_core_emitter",
            () -> new WorldCoreEmitterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_MAGENTA)
                    .strength(6.0F, 1400.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ArcaneProperties.FORMED) ? 13 : 5)));
    /** 控制器正上方那台更高的专属激光器：材质不同、模型更高。 */
    public static final RegistryObject<Block> WORLD_CORE_APEX = BLOCKS.register("world_core_apex",
            () -> new WorldCoreApexBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(7.0F, 1600.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(ArcaneProperties.FORMED) ? 15 : 6)));
    /** 投影幻影：不可见、无碰撞、不可破坏，只用于让准星和 Jade 能读到目标构件信息。 */
    public static final RegistryObject<Block> PROJECTION_GHOST = BLOCKS.register("projection_ghost",
            () -> new ProjectionGhostBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .replaceable()
                    .noParticlesOnBreak()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.AMETHYST)));
    public static final RegistryObject<Block> ARCANE_LAMP = BLOCKS.register("arcane_lamp",
            () -> new ArcanePartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(3.0F, 300.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 14)));

    private ModBlocks() {
    }

    private static BlockBehaviour.Properties stoneProperties(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(5.0F, 1200.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE);
    }

    private static BlockBehaviour.Properties portProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.GOLD)
                .strength(5.0F, 1000.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .noOcclusion()
                .lightLevel(state -> state.getValue(ArcaneProperties.ACTIVE) ? 13 : 5);
    }
}
