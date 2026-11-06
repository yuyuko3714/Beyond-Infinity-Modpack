package cn.blockforge.generated.arcanesingularity.structure;

import cn.blockforge.generated.arcanesingularity.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 两套多方块结构的确定性蓝图。所有坐标均相对控制器方块。
 *
 * <p>结构不是从外部文件读取的，因此服务端、客户端和投影渲染共用同一份定义。</p>
 */
public final class ArcaneStructure {
    public enum PartType {
        CONTROLLER, BODY, RING, LASER, PORT, GATE_FRAME, PILLAR, CRYSTAL, LAMP, FOUNDATION,
        /** 时间信标炮管周围的能量汇聚环（启动后脱离底座、越转越快）。 */
        CHRONO,
        /** 时间信标的中央炮管。 */
        BARREL,
        /** 世界之核的三个专属接口（立在地基上，不埋进地基）。 */
        INTERFACE,
        /** 接口上方的专属激光器。 */
        EMITTER,
        /** 控制器正上方那台更高的专属激光器。 */
        APEX
    }

    public record Part(BlockPos offset, Block block, PartType type) {
    }

    private static final int SINGULARITY_RADIUS = 13;
    /** 时间信标底座圆盘的半径（整块实心圆盘，不再是几圈散环）。 */
    private static final int BEACON_RADIUS = 10;
    /** 时间信标地基半径：底座下面再铺一整张圆盘。 */
    private static final int BEACON_FOUNDATION_RADIUS = 13;
    /**
     * 炮塔中轴相对控制器的偏移：控制器站在底座内圈，离矮墙两格；
     * 塔顶平台半径 7，比控制器离轴距离 8 还小，所以控制器头顶永远没有东西压着。
     */
    public static final double BEACON_AXIS_X = 8.0D;
    public static final double BEACON_AXIS_Y = 0.0D;
    public static final double BEACON_AXIS_Z = 0.0D;
    /** 主炮管顶面（相对塔轴）：第一段激光从这里射向顶部时序炮管。 */
    public static final double BEACON_MUZZLE_Y = 19.0D;
    /** 顶部两格时序炮管的底面：第一段激光打到这里。 */
    public static final double BEACON_TOP_GAP_Y = 20.0D;
    /** 顶部时序炮管的顶面：二次聚能之后，第二段激光从这里射向天空。 */
    public static final double BEACON_TOP_MUZZLE_Y = 22.0D;
    /** 大能量汇聚环的方块中心高度（方块 y + 0.5）。 */
    public static final double BEACON_RING_A_Y = 8.5D;
    public static final double BEACON_RING_B_Y = 13.5D;
    /** 最顶上一层小型聚能环的高度。 */
    public static final double BEACON_TOP_RING_Y = 22.5D;
    /** 世界之核的地基半径。 */
    private static final int CORE_RADIUS = 13;
    private static final int CORE_FOUNDATION_RADIUS = 14;
    /** 黑洞发生器顶部那座悬空的激光注入器（相对控制器）：悬在顶环正中央，成型后一直上下浮动。 */
    private static final BlockPos SINGULARITY_INJECTOR = new BlockPos(0, 18, 0);
    /**
     * 世界之核的中轴：正好是控制器所在的那一列。
     *
     * <p>控制器的家在底座正中心，主激光器就架在它正上方 7 格（不是偏到旁边），
     * 所以魔法阵、三光汇聚点、对射点全部落在底座中轴上，和「整个基座中心是控制器」一致。</p>
     */
    public static final int WORLD_CORE_AXIS_X = 0;
    /** 世界之核三个接口相对控制器的位置：立在底座表面，三向 120° 排开。 */
    private static final List<BlockPos> WORLD_CORE_INTERFACES = List.of(
            new BlockPos(0, 0, 10),
            new BlockPos(-9, 0, -5),
            new BlockPos(9, 0, -5));
    /** 接口上方的专属激光器顶面（接口 y=0、激光器 y=1，模型 16 像素高 → 顶面 2）。 */
    public static final double WORLD_CORE_EMITTER_MUZZLE_Y = 2.0D;
    /** 控制器正上方那台更高的激光器顶面（激光器 y=8，模型 16 像素高 → 顶面 9）。 */
    public static final double WORLD_CORE_APEX_MUZZLE_Y = 9.0D;
    /** 三道光在天空汇聚的那一点（相对控制器）。 */
    public static final double WORLD_CORE_GATHER_Y = 40.0D;
    /** 魔法阵中心与顶部激光器对射、交汇的那一点。 */
    public static final double WORLD_CORE_FOCUS_Y = 22.0D;
    /** 炮口上方两格悬浮的时序炮管位置（跟着塔轴走，悬浮、上下振动）。 */
    private static final List<BlockPos> BEACON_TOP_BARRELS = List.of(
            new BlockPos((int) BEACON_AXIS_X, 20, 0), new BlockPos((int) BEACON_AXIS_X, 21, 0));
    /** 最顶上一层那四块奥术晶体的位置（与小型聚能环反向旋转）。 */
    private static final List<BlockPos> BEACON_TOP_CRYSTALS = List.of(
            new BlockPos((int) BEACON_AXIS_X + 2, 23, 0), new BlockPos((int) BEACON_AXIS_X - 2, 23, 0),
            new BlockPos((int) BEACON_AXIS_X, 23, 2), new BlockPos((int) BEACON_AXIS_X, 23, -2));
    private static final List<Part> SINGULARITY = buildSingularity();
    private static final List<Part> TIMEBEACON = buildTimeBeacon();
    private static final List<Part> WORLD_CORE = buildWorldCore();
    private static final Map<Direction, List<Part>> GATES = buildGates();
    /** 完整地基：把结构脚下铺满，摆到坡地上也不用先挖平。这两份只用于搭建/投影，不参与成型校验。 */
    private static final List<Part> SINGULARITY_FOUNDATION = buildSingularityFoundation();
    private static final List<Part> TIMEBEACON_FOUNDATION = buildTimeBeaconFoundation();
    private static final List<Part> WORLD_CORE_FOUNDATION = buildWorldCoreFoundation();
    private static final Map<Direction, List<Part>> GATE_FOUNDATIONS = buildGateFoundations();
    /** 启动后脱离底座、跟着转子转的那圈浑天仪环。 */
    private static final List<BlockPos> ROTOR_RING = collectOffsets(SINGULARITY, PartType.RING, 8);
    /** 四方的四台激光稳定器（顶部注入器也属于 LASER，但高度不同）。 */
    private static final List<BlockPos> LASER_STABILIZERS = collectOffsets(SINGULARITY, PartType.LASER, 8);
    /** 最顶上那圈浑天仪环（连同四个水晶冠饰）：启动后一边上下震动一边由慢到快转。 */
    private static final List<Part> TOP_RING = buildTopRing();
    /** 中环（y=8）四个转角上的奥术晶体块：启动后实体化、先向外扩一段，再跟着整圈环一起转。 */
    private static final List<BlockPos> MID_CRYSTALS = collectOffsets(SINGULARITY, PartType.CRYSTAL, 8);

    private ArcaneStructure() {
    }

    public static List<Part> parts(StructureKind kind, Direction facing) {
        if (kind == StructureKind.SINGULARITY) {
            return SINGULARITY;
        }
        if (kind == StructureKind.TIMEBEACON) {
            return TIMEBEACON;
        }
        if (kind == StructureKind.WORLD_CORE) {
            return WORLD_CORE;
        }
        return GATES.getOrDefault(facing, GATES.get(Direction.NORTH));
    }

    /**
     * 搭建/投影用的完整清单：地基在前，主体在后。
     *
     * <p>成型校验（{@link #parts}）故意不要求地基——旧存档里已经成型的结构不会因为缺少地基而崩掉，
     * 但新搭建时成型终端会顺带把整块地基铺好。</p>
     */
    public static List<Part> buildParts(StructureKind kind, Direction facing) {
        List<Part> foundation = switch (kind) {
            case SINGULARITY -> SINGULARITY_FOUNDATION;
            case TIMEBEACON -> TIMEBEACON_FOUNDATION;
            case WORLD_CORE -> WORLD_CORE_FOUNDATION;
            default -> GATE_FOUNDATIONS.getOrDefault(facing, GATE_FOUNDATIONS.get(Direction.NORTH));
        };
        List<Part> parts = parts(kind, facing);
        List<Part> all = new ArrayList<>(foundation.size() + parts.size());
        all.addAll(foundation);
        all.addAll(parts);
        return List.copyOf(all);
    }

    public static Block controller(StructureKind kind) {
        return switch (kind) {
            case SINGULARITY -> ModBlocks.SINGULARITY_CONTROLLER.get();
            case TIMEBEACON -> ModBlocks.TIME_BEACON_CONTROLLER.get();
            case WORLD_CORE -> ModBlocks.WORLD_CORE_CONTROLLER.get();
            default -> ModBlocks.STARGATE_CONTROLLER.get();
        };
    }

    public static int footprint(StructureKind kind) {
        return switch (kind) {
            case SINGULARITY -> SINGULARITY_RADIUS * 2 + 1;
            case TIMEBEACON -> BEACON_FOUNDATION_RADIUS * 2 + 1;
            case WORLD_CORE -> CORE_FOUNDATION_RADIUS * 2 + 1;
            default -> 19;
        };
    }

    /**
     * 时间信标：朝天的高能激光发射塔。
     *
     * <p>整座塔以炮管中轴（控制器东侧 {@link #BEACON_AXIS_X} 格）为圆心旋转对称：</p>
     * <ul>
     *     <li>地基（半径 13 的符文圆盘，在底座下面一层）→ 半径 10 的底座圆盘（外圈雕纹砖收边）；</li>
     *     <li>底座边缘起一圈矮墙，顶面就是塔基的走道高度；</li>
     *     <li>四根承重柱的柱脚直接落在底座圆盘上，从 y=0 顶到 y=18 的塔顶平台；</li>
     *     <li>中央 3×3 炮管坐在塔基底座上，从 y=1 砌到 y=18（顶面 19）；</li>
     *     <li>控制器站在底座内圈的台纹上，距中轴 8 格。塔顶平台半径只有 7，
     *         所以控制器四个侧面与头顶全是空气，从任意一面都能直接右键；</li>
     *     <li>炮管外的两圈大能量汇聚环（y=8、y=13）与塔顶的小环、时序炮管、奥术晶体
     *         都是会转/会振动的特效构件，按规则允许脱离方块悬浮。</li>
     * </ul>
     */
    private static List<Part> buildTimeBeacon() {
        Map<BlockPos, Part> map = new LinkedHashMap<>();
        final int ax = (int) BEACON_AXIS_X;
        final int floor = -1;

        // ① 底座圆盘：整块实心平台，外圈一圈雕纹砖收边。
        discAtCenter(map, ax, BEACON_RADIUS, floor, ModBlocks.RUNE_BRICKS.get(), PartType.BODY);
        ringAtCenter(map, ax, BEACON_RADIUS, floor, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);

        // ② 地面纹样：嵌在底座同一层里换材质，不会浮空。
        ringAtCenter(map, ax, 7, floor, ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.BODY);
        ringAtCenter(map, ax, 4, floor, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);

        // ③ 外圈矮墙：立在底座边缘，和周围的塔基走道齐平。
        ringAtCenter(map, ax, BEACON_RADIUS, 0, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);

        // ④ 塔基底座 + 中央炮管：底座落在圆盘上，炮管从 y=1 砌到 y=18。
        box(map, ax - 1, 0, -1, ax + 1, 0, 1, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
        box(map, ax - 1, 1, -1, ax + 1, 18, 1, ModBlocks.TIME_BARREL.get(), PartType.BARREL);

        // ⑤ 四根承重柱：柱脚踩在圆盘上，一路顶到塔顶平台。
        for (int[] offset : new int[][]{{6, 0}, {-6, 0}, {0, 6}, {0, -6}}) {
            add(map, ax + offset[0], floor, offset[1], ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
            for (int y = 0; y <= 18; y++) {
                add(map, ax + offset[0], y, offset[1], ModBlocks.RUNE_PILLAR.get(), PartType.PILLAR);
            }
        }

        // ⑥ 塔顶平台：半径 7 的圆盘，中心留 3×3 炮口。
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                double distance = Math.hypot(x, z);
                if (distance > 7.5D) {
                    continue;
                }
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    continue;
                }
                add(map, ax + x, 19, z, distance > 6.5D
                        ? ModBlocks.CHISELED_RUNE_BRICKS.get()
                        : ModBlocks.RUNE_BRICKS.get(), PartType.BODY);
            }
        }

        // ⑦ 顶部两格时序炮管：悬浮在炮口上方，一直上下振动（特效构件，允许悬浮）。
        for (BlockPos barrel : BEACON_TOP_BARRELS) {
            add(map, barrel.getX(), barrel.getY(), barrel.getZ(),
                    ModBlocks.TIME_BARREL.get(), PartType.BARREL);
        }

        // ⑧ 最顶上一层小型聚能环 + 环上方四块奥术晶体（都是会转的特效构件）。
        ringAtCenter(map, ax, 2, 22, ModBlocks.CHRONO_RING.get(), PartType.CHRONO);
        for (BlockPos crystal : BEACON_TOP_CRYSTALS) {
            add(map, crystal.getX(), crystal.getY(), crystal.getZ(),
                    ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.CRYSTAL);
        }

        // ⑨ 两圈大能量汇聚环：半径 3 与 4，紧贴 3×3 炮管外围，和六格外的承重柱互不相交。
        ringAtCenter(map, ax, 3, 8, ModBlocks.CHRONO_RING.get(), PartType.CHRONO);
        ringAtCenter(map, ax, 4, 13, ModBlocks.CHRONO_RING.get(), PartType.CHRONO);

        // ⑩ 控制器：底座内圈一块 3×3 台纹上，四面与头顶全是空气。
        box(map, -1, floor, -1, 1, floor, 1, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
        add(map, 0, 0, 0, ModBlocks.TIME_BEACON_CONTROLLER.get(), PartType.CONTROLLER);

        // ⑪ 四盏奥术灯：各自踩在底座上，不是悬空的。
        for (int[] offset : new int[][]{{5, 5}, {5, -5}, {-5, 5}, {-5, -5}}) {
            add(map, ax + offset[0], floor, offset[1],
                    ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
            add(map, ax + offset[0], 0, offset[1], ModBlocks.ARCANE_LAMP.get(), PartType.LAMP);
        }

        // ⑫ 四个能量接入端口：和灯一样立在底座表面上（不再嵌进底座里），
        //    抬高后空出来的那一层地板用雕纹符文砖补平。
        for (int[] offset : new int[][]{{6, 6}, {6, -6}, {-6, 6}, {-6, -6}}) {
            add(map, ax + offset[0], floor, offset[1],
                    ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
            add(map, ax + offset[0], 0, offset[1],
                    ModBlocks.ENERGY_INPUT_PORT.get(), PartType.PORT);
        }

        return List.copyOf(map.values());
    }

    /** 时间信标的完整地基：半径 13 的符文圆盘，铺在底座下面一层（y=-2）。 */
    private static List<Part> buildTimeBeaconFoundation() {
        Map<BlockPos, Part> map = new LinkedHashMap<>();
        discAtCenter(map, (int) BEACON_AXIS_X, BEACON_FOUNDATION_RADIUS, -2,
                ModBlocks.RUNE_BRICKS.get(), PartType.FOUNDATION);
        // 主体构件优先：地基只补空位，绝不覆盖控制器、端口、灯这些真正的构件。
        for (Part part : TIMEBEACON) {
            map.put(part.offset(), part);
        }
        return collectFoundation(map);
    }

    /** 顶部两格悬浮时序炮管的位置（相对控制器），成型后由渲染器接管。 */
    public static List<BlockPos> beaconTopBarrelOffsets() {
        return BEACON_TOP_BARRELS;
    }

    /** 黑洞发生器顶部那座激光注入器的位置（相对控制器）：成型后脱离方块形态、悬在顶环中央浮动。 */
    public static BlockPos singularityInjectorOffset() {
        return SINGULARITY_INJECTOR;
    }

    /** 顶部四块奥术晶体的位置（相对控制器），启动后与小型聚能环反向旋转。 */
    public static List<BlockPos> beaconTopCrystalOffsets() {
        return BEACON_TOP_CRYSTALS;
    }

    /** 三圈能量汇聚环（0、1 为大环，2 为最顶上那圈小型环）。 */
    public static List<BlockPos> beaconRingOffsets(int ringIndex) {
        return switch (ringIndex) {
            case 0 -> collectOffsets(TIMEBEACON, PartType.CHRONO, 8);
            case 1 -> collectOffsets(TIMEBEACON, PartType.CHRONO, 13);
            default -> collectOffsets(TIMEBEACON, PartType.CHRONO, 22);
        };
    }

    // ------------------------------------------------------------------
    // 世界之核
    // ------------------------------------------------------------------

    /**
     * 世界之核：一座完整的符文圆盘底座 + 正中央的控制器 + 三个 120° 排开的专属接口。
     *
     * <p>整座仪式场以控制器为轴旋转对称，每一块构件都踩在底座圆盘上：</p>
     * <ul>
     *     <li>半径 13 的实心底座圆盘（外圈雕纹砖收边、外圈矮墙一圈，地基再铺一层半径 14）；</li>
     *     <li>控制器就在底座正中心：脚下是一块 3×3 台纹，四个侧面与头顶全是空气；</li>
     *     <li>主激光器架在控制器正上方 8 格，由四根柱子 + 一圈顶环 + 底座托着，不是偏到旁边；</li>
     *     <li>三个专属接口立在底座表面上（y=0），每个接口上方压一台专属激光器；</li>
     *     <li>四角四根符文柱从底座一路砌到 9 格，柱顶各一块奥术晶体；</li>
     *     <li>控制器周围是一圈只有一格高的晶体短柱，围出祭坛又不挡视线、不盖顶；</li>
     *     <li>四个能量接入端口与四盏奥术灯都立在底座表面上，抬高后空出的地板补成雕纹符文砖。</li>
     * </ul>
     */
    private static List<Part> buildWorldCore() {
        Map<BlockPos, Part> map = new LinkedHashMap<>();
        final int floor = -1;

        // ① 底座圆盘：整块实心平台，外圈一圈雕纹砖收边。
        discAtCenter(map, 0, CORE_RADIUS, floor, ModBlocks.RUNE_BRICKS.get(), PartType.BODY);
        ringAtCenter(map, 0, CORE_RADIUS, floor, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);

        // ② 地面纹样：三圈嵌进底座的环，换材质不换层，所以不会浮空。
        ringAtCenter(map, 0, 10, floor, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
        ringAtCenter(map, 0, 7, floor, ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.BODY);
        ringAtCenter(map, 0, 4, floor, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);

        // ③ 外圈矮墙：底座边缘一圈。
        ringAtCenter(map, 0, CORE_RADIUS, 0, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);

        // ④ 控制器：底座正中心的 3×3 台纹上，四面与头顶全空。
        box(map, -1, floor, -1, 1, floor, 1, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
        add(map, 0, 0, 0, ModBlocks.WORLD_CORE_CONTROLLER.get(), PartType.CONTROLLER);

        // ⑤ 主激光器支架：四根柱子 + 顶环 + 十字辐条，主激光器架在控制器正上方。
        for (int[] offset : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            for (int y = 0; y <= 6; y++) {
                add(map, offset[0], y, offset[1], ModBlocks.RUNE_PILLAR.get(), PartType.PILLAR);
            }
        }
        ringAtCenter(map, 0, 2, 7, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
        for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            add(map, offset[0], 7, offset[1], ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
        }
        add(map, 0, 7, 0, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
        add(map, 0, 8, 0, ModBlocks.WORLD_CORE_APEX.get(), PartType.APEX);

        // ⑥ 控制器周围的一圈低位符文柱：只有一格高，柱顶一块奥术晶体，
        //    像一圈栏杆把祭坛围出来，但一点也挡不住控制器（不悬空、不盖顶）。
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0D * i / 8.0D;
            int px = (int) Math.round(Math.cos(angle) * 4.0D);
            int pz = (int) Math.round(Math.sin(angle) * 4.0D);
            add(map, px, 0, pz, ModBlocks.RUNE_PILLAR.get(), PartType.PILLAR);
            add(map, px, 1, pz, ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.CRYSTAL);
        }

        // ⑦ 四角符文柱：柱脚踩在底座上，柱顶一块奥术晶体。
        for (int[] corner : new int[][]{{9, 9}, {9, -9}, {-9, 9}, {-9, -9}}) {
            for (int y = 0; y <= 9; y++) {
                add(map, corner[0], y, corner[1], ModBlocks.RUNE_PILLAR.get(), PartType.PILLAR);
            }
            add(map, corner[0], 10, corner[1], ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.CRYSTAL);
        }

        // ⑧ 三个专属接口：立在底座表面上（不是埋进地基），接口上方各一台专属激光器。
        for (BlockPos interfacePos : WORLD_CORE_INTERFACES) {
            add(map, interfacePos.getX(), interfacePos.getY(), interfacePos.getZ(),
                    ModBlocks.WORLD_CORE_INTERFACE.get(), PartType.INTERFACE);
            add(map, interfacePos.getX(), interfacePos.getY() + 1, interfacePos.getZ(),
                    ModBlocks.WORLD_CORE_EMITTER.get(), PartType.EMITTER);
            add(map, interfacePos.getX(), floor, interfacePos.getZ(),
                    ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
        }

        // ⑨ 四盏奥术灯与四个能量接入端口：灯和端口都立在底座表面上（不再嵌进底座里），
        //    抬高后空出来的那一层地板用雕纹符文砖补平。
        for (int[] offset : new int[][]{{5, 5}, {5, -5}, {-5, 5}, {-5, -5}}) {
            add(map, offset[0], floor, offset[1], ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
            add(map, offset[0], 0, offset[1], ModBlocks.ARCANE_LAMP.get(), PartType.LAMP);
        }
        for (int[] offset : new int[][]{{8, 0}, {-8, 0}, {0, 8}, {0, -8}}) {
            add(map, offset[0], floor, offset[1], ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
            add(map, offset[0], 0, offset[1], ModBlocks.ENERGY_INPUT_PORT.get(), PartType.PORT);
        }

        return List.copyOf(map.values());
    }

    /** 世界之核的完整地基：半径 14 的符文圆盘，铺在底座下面一层（y=-2）。 */
    private static List<Part> buildWorldCoreFoundation() {
        Map<BlockPos, Part> map = new LinkedHashMap<>();
        discAtCenter(map, 0, CORE_FOUNDATION_RADIUS, -2, ModBlocks.RUNE_BRICKS.get(), PartType.FOUNDATION);
        for (Part part : WORLD_CORE) {
            map.put(part.offset(), part);
        }
        return collectFoundation(map);
    }

    /** 世界之核的三个专属接口（相对控制器）。 */
    public static List<BlockPos> worldCoreInterfaceOffsets() {
        return WORLD_CORE_INTERFACES;
    }

    /** 某个接口上方的专属激光器相对控制器的位置。 */
    public static BlockPos worldCoreEmitterOffset(int index) {
        BlockPos interfacePos = WORLD_CORE_INTERFACES.get(Math.floorMod(index, WORLD_CORE_INTERFACES.size()));
        return interfacePos.above();
    }

    /** 控制器正上方那台更高的专属激光器（相对控制器）。 */
    public static BlockPos worldCoreApexOffset() {
        return new BlockPos(WORLD_CORE_AXIS_X, 8, 0);
    }

    /**
     * 浑天仪黑洞发生器：一整张符文圆盘上立着四根角柱，中间的奇点摇篮完全露在外面。
     *
     * <p>以前摇篮正上方压着「底部注入器 + 奥术晶体」，把它堵得死死的；现在注入器只保留
     * 最顶上那一座（悬在顶环圆心，属于允许悬浮的特效构件），摇篮头顶一路空到 18 格，
     * 从上方、四面都能直接右键。</p>
     */
    private static List<Part> buildSingularity() {
        Map<BlockPos, Part> map = new LinkedHashMap<>();
        final int floor = -1;

        // ① 完整的地面圆盘 + 外圈矮墙：不再是一圈圈悬空的装饰环。
        discAtCenter(map, 0, SINGULARITY_RADIUS, floor, ModBlocks.RUNE_BRICKS.get(), PartType.BODY);
        ringAtCenter(map, 0, 9, floor, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
        ringAtCenter(map, 0, 4, floor, ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.BODY);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0D * i / 8.0D;
            int x = (int) Math.round(Math.cos(angle) * SINGULARITY_RADIUS);
            int z = (int) Math.round(Math.sin(angle) * SINGULARITY_RADIUS);
            add(map, x, floor, z, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
        }
        ringAtCenter(map, 0, SINGULARITY_RADIUS, 0, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);

        // ② 摇篮：底座正中的 3×3 台纹上，四面与头顶全空。
        box(map, -1, floor, -1, 1, floor, 1, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);
        add(map, 0, 0, 0, ModBlocks.SINGULARITY_CONTROLLER.get(), PartType.CONTROLLER);

        // ③ 四根外层承重柱：柱脚踩在圆盘上，从 y=0 一路砌到 y=17，柱顶一块奥术晶体。
        for (int[] corner : new int[][]{{8, 8}, {8, -8}, {-8, 8}, {-8, -8}}) {
            for (int y = 0; y <= 17; y++) {
                add(map, corner[0], y, corner[1], ModBlocks.RUNE_PILLAR.get(), PartType.PILLAR);
            }
            add(map, corner[0], 18, corner[1], ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.CRYSTAL);
        }

        // ④ 中层浑天仪环架与四个稳定激光器（开机后整层作为刚体绕中轴旋转，允许悬浮）。
        ringAtCenter(map, 0, 8, 8, ModBlocks.ARMILLARY_RING.get(), PartType.RING);
        add(map, 0, 8, 8, ModBlocks.LASER_STABILIZER.get(), PartType.LASER);
        add(map, 0, 8, -8, ModBlocks.LASER_STABILIZER.get(), PartType.LASER);
        add(map, 8, 8, 0, ModBlocks.LASER_STABILIZER.get(), PartType.LASER);
        add(map, -8, 8, 0, ModBlocks.LASER_STABILIZER.get(), PartType.LASER);
        add(map, 6, 8, 6, ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.CRYSTAL);
        add(map, 6, 8, -6, ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.CRYSTAL);
        add(map, -6, 8, 6, ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.CRYSTAL);
        add(map, -6, 8, -6, ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.CRYSTAL);

        // ⑤ 顶部注入器：悬在顶环正中央，向黑洞注入能量（成型后脱离方块形态、一直悬浮上下浮动）。
        add(map, SINGULARITY_INJECTOR.getX(), SINGULARITY_INJECTOR.getY(), SINGULARITY_INJECTOR.getZ(),
                ModBlocks.LASER_INJECTOR.get(), PartType.LASER);

        // ⑥ 顶部闭合环：架在四根角柱顶端的高度，开机后与中环反向旋转。
        ringAtCenter(map, 0, 8, 18, ModBlocks.ARMILLARY_RING.get(), PartType.RING);
        add(map, 8, 18, 0, ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.CRYSTAL);
        add(map, -8, 18, 0, ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.CRYSTAL);
        add(map, 0, 18, 8, ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.CRYSTAL);
        add(map, 0, 18, -8, ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.CRYSTAL);

        // ⑦ 四个能量输出端口与四盏奥术灯：都踩在地面上，不再悬在半空。
        for (int[] offset : new int[][]{{3, 3}, {3, -3}, {-3, 3}, {-3, -3}}) {
            add(map, offset[0], 0, offset[1], ModBlocks.ENERGY_OUTPUT_PORT.get(), PartType.PORT);
        }
        for (int[] offset : new int[][]{{6, 6}, {6, -6}, {-6, 6}, {-6, -6}}) {
            add(map, offset[0], 0, offset[1], ModBlocks.ARCANE_LAMP.get(), PartType.LAMP);
        }

        return List.copyOf(map.values());
    }

    private static Map<Direction, List<Part>> buildGates() {
        Map<Direction, List<Part>> result = new LinkedHashMap<>();
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            result.put(facing, buildGate(facing));
        }
        return Map.copyOf(result);
    }

    /**
     * 星门：底座平台（local y=-1）上立起两根门柱与一圈竖直门环，控制器站在平台正中的台纹上。
     *
     * <p>和另外三座结构统一了「地面」约定：地面铺在控制器<b>脚下一层</b>（local y=-1），
     * 所有站在地面上的构件都从 local y=0 起。这样控制器不会再像以前那样和地面齐平、
     * 半个方块埋在地里，抬高后空出来的地板由脚下的 3×3 台纹补平。</p>
     *
     * <p>门环圆心也抬到 local y=10（比原来的 y=9 高一格），所以门心、特效与可传送范围
     * 一起上移，三者仍然共用 {@link StargateRingAssembly#CENTER_LOCAL_Y} 这一组常量。</p>
     */
    private static List<Part> buildGate(Direction facing) {
        Map<BlockPos, Part> map = new LinkedHashMap<>();
        final int floor = -1;
        add(map, 0, 0, 0, ModBlocks.STARGATE_CONTROLLER.get(), PartType.CONTROLLER);
        // 控制器脚下补一块 3×3 台纹：控制器抬高后空出来的地板就在这里
        box(map, -1, floor, -1, 1, floor, 1, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY);

        ringLocal(map, 6, floor, ModBlocks.CHISELED_RUNE_BRICKS.get(), PartType.BODY, facing);
        for (int i = 3; i < 6; i++) {
            addLocal(map, i, floor, 0, ModBlocks.RUNE_BRICKS.get(), PartType.BODY, facing);
            addLocal(map, -i, floor, 0, ModBlocks.RUNE_BRICKS.get(), PartType.BODY, facing);
            addLocal(map, 0, floor, i, ModBlocks.RUNE_BRICKS.get(), PartType.BODY, facing);
            addLocal(map, 0, floor, -i, ModBlocks.RUNE_BRICKS.get(), PartType.BODY, facing);
        }

        // 门环：圆心 local y=10、半径 7.5 → 整圈门框落在 y=2..18、x=-8..8 之内。
        // 循环边界要把圆的最外圈也算进去（|dist - 7.5| < 0.62 在 x=±8、y=10±8 处仍然成立），
        // 否则正上、正下、正左、正右会各缺一格，整圈看起来像断开的。
        for (int x = -8; x <= 8; x++) {
            for (int y = 2; y <= 18; y++) {
                double dx = x;
                double dy = y - 10.0D;
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (Math.abs(distance - 7.5D) < 0.62D) {
                    addLocal(map, x, y, 0, ModBlocks.STARGATE_FRAME.get(), PartType.GATE_FRAME, facing);
                }
            }
        }

        // 两根门柱：柱脚踩在地面上（local y=0），柱顶一块奥术晶体
        for (int y = 0; y <= 4; y++) {
            addLocal(map, 9, y, 0, ModBlocks.RUNE_PILLAR.get(), PartType.PILLAR, facing);
            addLocal(map, -9, y, 0, ModBlocks.RUNE_PILLAR.get(), PartType.PILLAR, facing);
        }
        addLocal(map, 9, 5, 0, ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.CRYSTAL, facing);
        addLocal(map, -9, 5, 0, ModBlocks.ARCANE_CRYSTAL_BLOCK.get(), PartType.CRYSTAL, facing);

        // 门柱与门框弧之间的底梁：同样躺在地面上，门框不再是「飘」在两根柱子中间
        for (int i = 3; i <= 8; i++) {
            addLocal(map, i, 0, 0, ModBlocks.RUNE_BRICKS.get(), PartType.BODY, facing);
            addLocal(map, -i, 0, 0, ModBlocks.RUNE_BRICKS.get(), PartType.BODY, facing);
        }

        addLocal(map, 0, 0, 2, ModBlocks.ENERGY_INPUT_PORT.get(), PartType.PORT, facing);
        addLocal(map, 0, 0, -2, ModBlocks.ENERGY_INPUT_PORT.get(), PartType.PORT, facing);
        addLocal(map, 6, 0, 6, ModBlocks.ARCANE_LAMP.get(), PartType.LAMP, facing);
        addLocal(map, -6, 0, 6, ModBlocks.ARCANE_LAMP.get(), PartType.LAMP, facing);
        addLocal(map, 6, 0, -6, ModBlocks.ARCANE_LAMP.get(), PartType.LAMP, facing);
        addLocal(map, -6, 0, -6, ModBlocks.ARCANE_LAMP.get(), PartType.LAMP, facing);

        return List.copyOf(map.values());
    }

    /**
     * 浑天仪的完整地基：半径 13 的符文圆盘，铺在地面下面一层。
     *
     * <p>摇篮就坐在这一层上面的地面圆盘正中：地基不再从摇篮底下挖空，
     * 而是整块托住，摇篮从头到尾没有被埋的可能。</p>
     */
    private static List<Part> buildSingularityFoundation() {
        Map<BlockPos, Part> map = new LinkedHashMap<>();
        discAtCenter(map, 0, SINGULARITY_RADIUS, -2, ModBlocks.RUNE_BRICKS.get(), PartType.FOUNDATION);
        // 主体构件优先：地基只补空位，绝不覆盖控制器、端口、灯这些真正的构件。
        for (Part part : SINGULARITY) {
            map.put(part.offset(), part);
        }
        return collectFoundation(map);
    }

    private static Map<Direction, List<Part>> buildGateFoundations() {
        Map<Direction, List<Part>> result = new LinkedHashMap<>();
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            result.put(facing, buildGateFoundation(facing));
        }
        return Map.copyOf(result);
    }

    /**
     * 星门的完整地基：门前的圆台，铺在控制器脚下那一层（local y=-1）。
     *
     * <p>半径盖到 ±9 的两根门柱，柱子不会再悬空；整层台面就是门柱、底梁、端口、奥术灯站的地方。</p>
     */
    private static List<Part> buildGateFoundation(Direction facing) {
        Map<BlockPos, Part> map = new LinkedHashMap<>();
        int radius = 10;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.hypot(x, z) > radius + 0.5D) {
                    continue;
                }
                addLocal(map, x, -1, z, ModBlocks.RUNE_BRICKS.get(), PartType.FOUNDATION, facing);
            }
        }
        for (Part part : buildGate(facing)) {
            map.put(part.offset(), part);
        }
        return collectFoundation(map);
    }

    private static List<Part> collectFoundation(Map<BlockPos, Part> map) {
        List<Part> result = new ArrayList<>();
        for (Part part : map.values()) {
            if (part.type() == PartType.FOUNDATION) {
                result.add(part);
            }
        }
        return List.copyOf(result);
    }

    private static void ring(Map<BlockPos, Part> map, int radius, int y, Block block, PartType type) {        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (Math.abs(distance - radius) < 0.55D) {
                    add(map, x, y, z, block, type);
                }
            }
        }
    }

    private static void ringAtCenter(Map<BlockPos, Part> map, int centerX, int radius, int y,
                                     Block block, PartType type) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (Math.abs(distance - radius) < 0.55D) {
                    add(map, centerX + x, y, z, block, type);
                }
            }
        }
    }

    /** 一整块实心圆盘（不是环）：底座和地基都用它，保证「底座完整、地基完整」。 */
    private static void discAtCenter(Map<BlockPos, Part> map, int centerX, int radius, int y,
                                     Block block, PartType type) {
        int limit = radius + 1;
        for (int x = -limit; x <= limit; x++) {
            for (int z = -limit; z <= limit; z++) {
                if (Math.hypot(x, z) > radius + 0.5D) {
                    continue;
                }
                add(map, centerX + x, y, z, block, type);
            }
        }
    }

    /** 实心长方体填充：炮管、台座、塔基底座这些规整构件用它一次铺好。 */
    private static void box(Map<BlockPos, Part> map, int x0, int y0, int z0, int x1, int y1, int z1,
                            Block block, PartType type) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    add(map, x, y, z, block, type);
                }
            }
        }
    }

    private static void ringAtY(Map<BlockPos, Part> map, int y, int radius, Block block, PartType type) {
        ring(map, radius, y, block, type);
    }

    private static void ringLocal(Map<BlockPos, Part> map, int radius, int y, Block block,
                                  PartType type, Direction facing) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (Math.abs(distance - radius) < 0.55D) {
                    addLocal(map, x, y, z, block, type, facing);
                }
            }
        }
    }

    private static void add(Map<BlockPos, Part> map, int x, int y, int z, Block block, PartType type) {
        BlockPos pos = new BlockPos(x, y, z);
        map.put(pos, new Part(pos, block, type));
    }

    private static void addLocal(Map<BlockPos, Part> map, int x, int y, int z, Block block,
                                 PartType type, Direction facing) {
        BlockPos rotated = rotateLocal(x, y, z, facing);
        add(map, rotated.getX(), rotated.getY(), rotated.getZ(), block, type);
    }

    public static BlockPos rotateLocal(int x, int y, int z, Direction facing) {
        return switch (facing) {
            case EAST -> new BlockPos(-z, y, x);
            case WEST -> new BlockPos(z, y, -x);
            case SOUTH -> new BlockPos(-x, y, -z);
            default -> new BlockPos(x, y, z);
        };
    }

    /** 转子上的环方块偏移（相对控制器）。 */
    public static List<BlockPos> rotorRingOffsets() {
        return ROTOR_RING;
    }

    /** 四台激光稳定器的偏移（相对控制器）。 */
    public static List<BlockPos> stabilizerOffsets() {
        return LASER_STABILIZERS;
    }

    /** 中环四块奥术晶体的偏移（相对控制器），启动后会实体化并跟着转子转。 */
    public static List<BlockPos> rotorCrystalOffsets() {
        return MID_CRYSTALS;
    }

    /** 顶部那圈浑天仪环的构件（含四个水晶冠饰，它们和环一起转，否则会插进转动的环里）。 */
    public static List<Part> topRingParts() {
        return TOP_RING;
    }

    private static List<Part> buildTopRing() {
        List<Part> result = new ArrayList<>();
        for (Part part : SINGULARITY) {
            if (part.offset().getY() != 18) {
                continue;
            }
            boolean ring = part.type() == PartType.RING;
            boolean crown = part.type() == PartType.CRYSTAL
                    && Math.abs(Math.hypot(part.offset().getX(), part.offset().getZ()) - 8.0D) < 0.6D;
            if (ring || crown) {
                result.add(part);
            }
        }
        return List.copyOf(result);
    }

    private static List<BlockPos> collectOffsets(List<Part> parts, PartType type, int y) {
        List<BlockPos> result = new ArrayList<>();
        for (Part part : parts) {
            if (part.type() == type && part.offset().getY() == y) {
                result.add(part.offset());
            }
        }
        return List.copyOf(result);
    }

    public static List<BlockPos> offsetsOf(StructureKind kind, Direction facing, PartType type) {
        List<BlockPos> result = new ArrayList<>();
        for (Part part : parts(kind, facing)) {
            if (part.type() == type) {
                result.add(part.offset());
            }
        }
        return result;
    }
}
