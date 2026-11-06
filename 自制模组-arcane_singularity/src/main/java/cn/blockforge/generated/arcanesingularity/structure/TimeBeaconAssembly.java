package cn.blockforge.generated.arcanesingularity.structure;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间信标里那两圈能量汇聚环的转动参数。
 *
 * <p>和浑天仪转子同一条思路：环不是抽象的圆，而是蓝图里每一个汇聚环方块各自作为一个构件，
 * 绕炮管中轴旋转。启动瞬间角度为 0，构件严格落在原来的方块位置上；之后整圈由慢到快转起来，
 * 渲染器画的方块、带碰撞的实体、以及环绕辉光，用的都是同一个角度。</p>
 *
 * <p>转速不是线性的：从几乎静止的 {@link #SPIN_START_DEG_PER_TICK} 平滑加速，在
 * {@link #RAMP_TICKS} tick（约 40 秒）后达到 {@link #SPIN_MAX_DEG_PER_TICK} 并保持。
 * 整个充能过程正好一分钟（{@code TimeBeaconControllerBlockEntity#WARMUP_LENGTH}），
 * 也就是说后半分钟环已经在满速空转、炮口开始聚能。</p>
 */
public final class TimeBeaconAssembly {
    /** 从起步转速加速到满速所需的 tick 数。 */
    public static final int RAMP_TICKS = 800;
    /** 刚启动时每 tick 转过的角度：看起来几乎不动，玩家能看清环「脱开底座」。 */
    public static final float SPIN_START_DEG_PER_TICK = 0.05F;
    /** 满速时每 tick 转过的角度：约 2.5 秒一圈。 */
    public static final float SPIN_MAX_DEG_PER_TICK = 9.0F;
    /** 下环的转速比例（正转）。 */
    public static final double RING_A_SCALE = 1.0D;
    /** 上环的转速比例（反向，比下环略慢）：两层环对着转才像一台机器。 */
    public static final double RING_B_SCALE = -0.82D;
    /** 最顶上那圈小型聚能环：正转，比大环慢一些，看起来是二次聚能。 */
    public static final double TOP_RING_SCALE = 0.46D;
    /** 四块奥术晶体：与小型聚能环反向旋转。 */
    public static final double TOP_CRYSTAL_SCALE = -0.78D;
    /** 顶部两格时序炮管上下浮动的振幅（格）。 */
    public static final double TOP_BARREL_BOB_AMPLITUDE = 0.16D;
    /** 顶部两格时序炮管上下浮动的频率（弧度/tick）。 */
    public static final double TOP_BARREL_BOB_SPEED = 0.07D;
    /**
     * 两根时序炮管合起来的中心高度（local y=20 与 21 两格 → 整体中心 21）。
     *
     * <p>摆动、倾斜全部绕这一点做，两根炮管就是一个刚体：一起浮、一起晃，
     * 不会出现「上面那格往上、下面那格往下」各震各的。</p>
     */
    public static final double TOP_BARREL_PIVOT_Y = 21.0D;

    /** 顶部时序炮管整体的上下浮动偏移（格）：渲染器与激光特效共用，光柱始终贴着炮口。 */
    public static double topBarrelBob(double time) {
        return TOP_BARREL_BOB_AMPLITUDE * Math.sin(time * TOP_BARREL_BOB_SPEED);
    }

    /**
     * 一个汇聚环构件。
     *
     * @param offset   蓝图里相对控制器的偏移（原始方块位置）
     * @param angleDeg 原始方位角（度，0 指向 +X）
     * @param radius   原始半径
     * @param ring     属于哪一圈（0 = 下环 y=7，1 = 上环 y=12）
     */
    public record RingPart(BlockPos offset, double angleDeg, double radius, int ring) {
        public double y() {
            return offset.getY() + 0.5D;
        }
    }

    private static volatile List<RingPart> ringA;
    private static volatile List<RingPart> ringB;
    private static volatile List<RingPart> ringC;
    private static volatile List<RingPart> all;

    private TimeBeaconAssembly() {
    }

    public static List<RingPart> ringParts(int index) {
        return switch (index) {
            case 0 -> lowerRing();
            case 1 -> upperRing();
            default -> topRing();
        };
    }

    /** 最顶上一层小型聚能环（不自带碰撞实体，只由渲染器画）。 */
    public static List<RingPart> topRing() {
        List<RingPart> cached = ringC;
        if (cached == null) {
            synchronized (TimeBeaconAssembly.class) {
                cached = ringC;
                if (cached == null) {
                    cached = buildRing(2);
                    ringC = cached;
                }
            }
        }
        return cached;
    }

    /** 每一圈各自的转速比例。 */
    public static double scaleOf(int index) {
        return switch (index) {
            case 0 -> RING_A_SCALE;
            case 1 -> RING_B_SCALE;
            default -> TOP_RING_SCALE;
        };
    }

    public static List<RingPart> lowerRing() {
        List<RingPart> cached = ringA;
        if (cached == null) {
            synchronized (TimeBeaconAssembly.class) {
                cached = ringA;
                if (cached == null) {
                    cached = buildRing(0);
                    ringA = cached;
                }
            }
        }
        return cached;
    }

    public static List<RingPart> upperRing() {
        List<RingPart> cached = ringB;
        if (cached == null) {
            synchronized (TimeBeaconAssembly.class) {
                cached = ringB;
                if (cached == null) {
                    cached = buildRing(1);
                    ringB = cached;
                }
            }
        }
        return cached;
    }

    /** 所有会转的构件：下环在前、上环在后。碰撞实体按这个顺序编号。 */
    public static List<RingPart> allParts() {
        List<RingPart> cached = all;
        if (cached == null) {
            synchronized (TimeBeaconAssembly.class) {
                cached = all;
                if (cached == null) {
                    List<RingPart> merged = new ArrayList<>(lowerRing());
                    merged.addAll(upperRing());
                    cached = List.copyOf(merged);
                    all = cached;
                }
            }
        }
        return cached;
    }

    private static List<RingPart> buildRing(int index) {
        List<RingPart> result = new ArrayList<>();
        for (BlockPos offset : ArcaneStructure.beaconRingOffsets(index)) {
            // 半径与方位角都以炮塔中轴为准（控制器站在炮塔旁边，不在轴上）
            double dx = offset.getX() - ArcaneStructure.BEACON_AXIS_X;
            double dz = offset.getZ() - ArcaneStructure.BEACON_AXIS_Z;
            result.add(new RingPart(offset, normalize(Math.toDegrees(Math.atan2(dz, dx))),
                    Math.hypot(dx, dz), index));
        }
        return List.copyOf(result);
    }

    public static double angleOf(BlockPos offset) {
        return normalize(Math.toDegrees(Math.atan2(offset.getZ(), offset.getX())));
    }

    public static double radiusOf(BlockPos offset) {
        return Math.hypot(offset.getX(), offset.getZ());
    }

    /** 某一圈当前的角度（度）= 累计积分角 × 该圈的转速比例。 */
    public static double ringAngleDeg(int index, double ticksSinceStart) {
        return normalize(spinAngleRawDeg(ticksSinceStart) * scaleOf(index));
    }

    /**
     * 累计转过的「原始角度」（度，不取模）：对速度曲线做积分。
     *
     * <p>渲染器是拿这个再乘各圈比例的，所以加速、减速过程中角度是连续的，不会跳。</p>
     */
    public static double spinAngleRawDeg(double ticksSinceStart) {
        double ticks = Math.max(0.0D, ticksSinceStart);
        double delta = SPIN_MAX_DEG_PER_TICK - SPIN_START_DEG_PER_TICK;
        if (ticks <= RAMP_TICKS) {
            double u = ticks / RAMP_TICKS;
            // ∫ smoothstep(u) du = u³ - u⁴/2
            return SPIN_START_DEG_PER_TICK * ticks
                    + delta * RAMP_TICKS * (u * u * u - 0.5D * u * u * u * u);
        }
        double rampAngle = SPIN_START_DEG_PER_TICK * RAMP_TICKS + delta * RAMP_TICKS * 0.5D;
        return rampAngle + SPIN_MAX_DEG_PER_TICK * (ticks - RAMP_TICKS);
    }

    /** 当前转速占满速的多少（0..1）：环的颜色、辉光强度都跟着它变化。 */
    public static float spinRamp01(double ticksSinceStart) {
        double u = Math.max(0.0D, Math.min(ticksSinceStart / RAMP_TICKS, 1.0D));
        return (float) (u * u * (3.0D - 2.0D * u));
    }

    public static double normalize(double angle) {
        double wrapped = angle % 360.0D;
        return wrapped < 0.0D ? wrapped + 360.0D : wrapped;
    }
}
