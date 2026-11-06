package cn.blockforge.generated.arcanesingularity.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 星门符文环启动后的转子参数。
 *
 * <p>和浑天仪一样，这里不是把门框藏起来再画一圈线，而是把蓝图里 {@code stargate_frame}
 * 的每一个方块各自当成一个带碰撞箱的刚体构件：每个构件记着自己在门平面里的方位角与半径，
 * 门打开时整圈像车轮一样绕门心自转，方块本身的模型跟着转，激光/粒子/碰撞都跟着一起走。</p>
 */
public final class StargateRingAssembly {
    /** 门环圆心在 local 方块坐标里的 y（结构里整圈门框落在 y=2..18、圆心 y=10）。 */
    public static final double CENTER_LOCAL_Y = 10.0D;
    /**
     * 传送膜（也就是整张门面特效）的半径，相对门心。
     *
     * <p>渲染（{@code StargateFx}）、粒子（控制器方块的 {@code animateTick}）和传送判定
     * （{@code teleportEntities}）都读这一个值，所以「看得见的门面」和「能传过去的范围」
     * 永远是同一块地方，不会出现站在门里却传不过去、或者门外面也被传走的情况。</p>
     */
    public static final double PORTAL_RADIUS = 6.30D;
    /** 门心在 local 坐标里的精确高度（方块中心）：圆心那一层加半格。 */
    public static final double PORTAL_CENTER_Y = CENTER_LOCAL_Y + 0.5D;
    /** 刚开门时每 tick 转过的角度：车轮慢慢起转。 */
    public static final float SPIN_START_DEG_PER_TICK = 0.10F;
    /** 加速完成后的每 tick 转角：约 2.2 秒一圈。 */
    public static final float SPIN_MAX_DEG_PER_TICK = 9.0F;
    /** 加速时长（tick），约 8 秒。 */
    public static final int SPIN_RAMP_TICKS = 160;
    /** 关门刹车的最短时长（tick）。刹车时长按剩余角度动态计算，保证停在 360 的整数倍。 */
    public static final int MIN_RELEASE_LENGTH = 24;
    /** 门环关闭时用于「刹车回位」的最长动画长度（tick）。 */
    public static final int MAX_RELEASE_LENGTH = 110;
    /** 兼容旧引用。 */
    public static final float SPIN_DEG_PER_TICK = SPIN_MAX_DEG_PER_TICK;
    /** 门环构件的碰撞箱尺寸。 */
    public static final float PART_WIDTH = 1.35F;
    public static final float PART_HEIGHT = 1.35F;

    /**
     * 门环上的一个方块构件。
     *
     * @param offset   蓝图里的 local 方块偏移（相对控制器，按 NORTH 方向定义）
     * @param angleDeg 原始方位角（度，0 指向 local +X）
     * @param radius   原始半径
     * @param index    在该环中的序号
     */
    public record RingPart(BlockPos offset, double angleDeg, double radius, int index) {
    }

    private static volatile List<RingPart> ringParts;

    private StargateRingAssembly() {
    }

    /** 门环构件列表，懒加载以避开注册表初始化顺序。 */
    public static List<RingPart> ringParts() {
        List<RingPart> cached = ringParts;
        if (cached == null) {
            synchronized (StargateRingAssembly.class) {
                cached = ringParts;
                if (cached == null) {
                    cached = buildRingParts();
                    ringParts = cached;
                }
            }
        }
        return cached;
    }

    private static List<RingPart> buildRingParts() {
        List<RingPart> result = new ArrayList<>();
        int index = 0;
        for (ArcaneStructure.Part part : ArcaneStructure.parts(StructureKind.STARGATE, Direction.NORTH)) {
            if (part.type() != ArcaneStructure.PartType.GATE_FRAME) {
                continue;
            }
            BlockPos offset = part.offset();
            double dx = offset.getX();
            double dy = offset.getY() - CENTER_LOCAL_Y;
            result.add(new RingPart(offset, Math.toDegrees(Math.atan2(dy, dx)), Math.hypot(dx, dy), index++));
        }
        return List.copyOf(result);
    }

    /** 开门时的瞬时转速（度/tick）：由慢到快，{@link #SPIN_RAMP_TICKS} tick 后到最高速。 */
    public static float spinSpeedDegPerTick(double ticksSinceStart) {
        double u = Math.max(0.0D, Math.min(ticksSinceStart / SPIN_RAMP_TICKS, 1.0D));
        double eased = u * u * (3.0D - 2.0D * u);
        return (float) (SPIN_START_DEG_PER_TICK + (SPIN_MAX_DEG_PER_TICK - SPIN_START_DEG_PER_TICK) * eased);
    }

    /** 开门阶段累计转角（度），速度曲线的闭式积分，客户端与服务端共用。 */
    public static double spinAngleDeg(double ticksSinceStart) {
        double ticks = Math.max(0.0D, ticksSinceStart);
        double delta = SPIN_MAX_DEG_PER_TICK - SPIN_START_DEG_PER_TICK;
        if (ticks <= SPIN_RAMP_TICKS) {
            double u = ticks / SPIN_RAMP_TICKS;
            return ArmillaryAssembly.normalize(SPIN_START_DEG_PER_TICK * ticks
                    + delta * SPIN_RAMP_TICKS * (u * u * u - 0.5D * u * u * u * u));
        }
        double rampAngle = SPIN_START_DEG_PER_TICK * SPIN_RAMP_TICKS + delta * SPIN_RAMP_TICKS * 0.5D;
        return ArmillaryAssembly.normalize(rampAngle + SPIN_MAX_DEG_PER_TICK * (ticks - SPIN_RAMP_TICKS));
    }

    /** 关门刹车时长（tick）：由当前角度决定，快转时刹车更久，总能把车轮稳稳停回原位。 */
    public static int releaseTicks(double startAngle) {
        double start = ArmillaryAssembly.normalize(startAngle);
        double remaining = 360.0D - start;
        if (remaining >= 359.5D) {
            return MIN_RELEASE_LENGTH;
        }
        double ticks = 2.0D * remaining / SPIN_MAX_DEG_PER_TICK;
        return (int) Math.max(MIN_RELEASE_LENGTH, Math.min(MAX_RELEASE_LENGTH, ticks));
    }

    /** 整个门环当前的角度（度）：开门时由慢到快转，关门时由快到慢刹回 360 的整数倍。 */
    public static double ringAngleDeg(double time, long openStamp, long closeStamp, boolean open) {
        if (openStamp <= 0L) {
            return 0.0D;
        }
        if (open) {
            return spinAngleDeg(time - openStamp);
        }
        if (closeStamp <= 0L) {
            return 0.0D;
        }
        double start = spinAngleDeg(closeStamp - openStamp);
        int length = releaseTicks(start);
        double progress = Mth.clamp((time - closeStamp) / length, 0.0D, 1.0D);
        return ArmillaryAssembly.brakeAngleDeg(start, progress);
    }

    /** 门环从 {@code closeStamp} 起还需要多少 tick 才能刹回原位。 */
    public static int releaseTicksAt(long openStamp, long closeStamp) {
        if (openStamp <= 0L || closeStamp <= 0L) {
            return MIN_RELEASE_LENGTH;
        }
        return releaseTicks(spinAngleDeg(closeStamp - openStamp));
    }

    /** local 坐标旋转到世界偏移（与 {@link ArcaneStructure#rotateLocal} 同一套映射，允许小数）。 */
    public static Vec3 rotate(double x, double y, double z, Direction facing) {
        return switch (facing) {
            case EAST -> new Vec3(-z, y, x);
            case WEST -> new Vec3(z, y, -x);
            case SOUTH -> new Vec3(-x, y, -z);
            default -> new Vec3(x, y, z);
        };
    }

    /** 某个门环构件在转子角 {@code ringAngle} 时的世界中心点。 */
    public static Vec3 partCenter(BlockPos owner, Direction facing, RingPart part, double ringAngle) {
        double radians = Math.toRadians(part.angleDeg() + ringAngle);
        double localX = Math.cos(radians) * part.radius();
        double localY = Math.sin(radians) * part.radius();
        Vec3 center = rotate(0.5D, PORTAL_CENTER_Y, 0.5D, facing);
        Vec3 offset = rotate(localX, localY, 0.0D, facing);
        return new Vec3(owner.getX() + center.x + offset.x,
                owner.getY() + center.y + offset.y,
                owner.getZ() + center.z + offset.z);
    }
}
