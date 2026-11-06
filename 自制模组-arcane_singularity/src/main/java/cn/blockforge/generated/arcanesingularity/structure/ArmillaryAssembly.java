package cn.blockforge.generated.arcanesingularity.structure;

import cn.blockforge.generated.arcanesingularity.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * 浑天仪启动后的转子参数。渲染器与碰撞实体共用同一份常量，保证「看到的」和「撞到的」是同一圈环。
 *
 * <p>转子不是抽象的一圈圆环，而是结构蓝图里 y=8 那一层的每一个方块（每块浑天仪环方块、
 * 四台激光稳定器）各自作为一个构件绕圆平面中轴旋转：每个构件记着自己在蓝图里的方位角与半径，
 * 启动瞬间 {@code rotorAngle = 0}，正好与方块原位重合；之后整层一起转，方块本身的模型跟着转，
 * 碰撞箱也一起转。因此看到的、撞到的、以及稳定器射出的激光，都是同一个刚体。</p>
 */
public final class ArmillaryAssembly {
    /** 黑洞球心 / 转子环平面相对控制器的高度。 */
    public static final double CENTER_Y = 8.5D;
    public static final float ROTOR_RADIUS = 8.0F;
    public static final int PODS = 4;
    /** 刚启动时每 tick 转过的角度：几乎是在「挪」，让玩家看清整层方块开始动了。 */
    public static final float SPIN_START_DEG_PER_TICK = 0.06F;
    /** 加速完成后的每 tick 转角：约 2.5 秒一圈，转起来像飞轮。 */
    public static final float SPIN_MAX_DEG_PER_TICK = 8.0F;
    /** 从起始转速平滑加速到最高转速所需的 tick 数（约 8.5 秒）。 */
    public static final int SPIN_RAMP_TICKS = 170;
    /** 兼容旧引用：最高转速。 */
    public static final float SPIN_DEG_PER_TICK = SPIN_MAX_DEG_PER_TICK;
    /** 三只装饰倾斜环的半径与倾角。 */
    public static final float[] TILT_RADII = {5.6F, 7.0F, 8.2F};
    public static final float[] TILT_ANGLES = {25.0F, 55.0F, 80.0F};
    /** 转子构件碰撞箱：比方块略宽，让一圈环在转动时没有能钻过去的缝。 */
    public static final float PART_WIDTH = 1.7F;
    public static final float PART_HEIGHT = 1.4F;
    /** 最顶那圈环的方块中心相对控制器的高度。 */
    public static final double TOP_RING_Y = 18.5D;
    /** 顶部激光注入器的方块中心高度（local y=18 那一格，方块中心 18.5）。 */
    public static final double INJECTOR_Y = 18.5D;
    /** 注入器成型后悬浮的振幅（格）与频率（弧度/tick）：缓慢上下浮，像被能量托着。 */
    public static final double INJECTOR_HOVER_AMPLITUDE = 0.17D;
    public static final double INJECTOR_HOVER_SPEED = 0.045D;
    /** 注入器成型后额外的轻微横向漂移（格）。 */
    public static final double INJECTOR_DRIFT_AMPLITUDE = 0.045D;

    /**
     * 顶部注入器的悬浮偏移（格）：渲染器画的注入器方块、以及它向下射出的注入光束
     * 都读这一个值，所以光柱的起点永远贴着注入器，不会脱开。
     *
     * <p>只在「已成型」时才有意义——成型前方块老老实实待在原位，这个偏移由调用方忽略。</p>
     */
    public static double injectorHover(double time) {
        if (!Double.isFinite(time)) {
            return 0.0D;
        }
        return INJECTOR_HOVER_AMPLITUDE * Math.sin(time * INJECTOR_HOVER_SPEED);
    }

    /** 注入器的横向漂移（格，X/Z 用不同的相位，看起来是慢慢画小圈）。 */
    public static double injectorDriftX(double time) {
        return INJECTOR_DRIFT_AMPLITUDE * Math.sin(time * 0.031D);
    }

    public static double injectorDriftZ(double time) {
        return INJECTOR_DRIFT_AMPLITUDE * Math.cos(time * 0.026D);
    }

    /** 注入器炮口（方块中心）相对控制器的高度，含悬浮偏移。 */
    public static double injectorY(double time) {
        return INJECTOR_Y + injectorHover(time);
    }

    /**
     * 顶环的转速相对转子主环的比例：负值表示<b>与中环反向</b>旋转，像真正的浑天仪那样两层环对着转。
     * 绝对值决定快慢，稍慢一点看起来像套在一起的两层。
     */
    public static final float TOP_SPIN_SCALE = -0.72F;
    /** 顶环上下震动的幅度（格）与频率。 */
    public static final double TOP_BOB_AMPLITUDE = 0.30D;
    public static final double TOP_BOB_SPEED = 0.62D;

    /**
     * 中环四块奥术晶体启动后向外扩出的距离（格）。
     *
     * <p>晶体原位在半径 8.49 的转角上，外扩 1.3 后半径约 9.79：既明显跑到整圈环外面，
     * 又离四根对角承重柱（中心半径 11.31、方块边缘 10.81）留了余量，绕圈转过去时不会插进柱子里。</p>
     */
    public static final double CRYSTAL_OUTWARD = 1.3D;
    /** 开机时晶体从原位推到最远所需 tick：这段时间转子几乎还没转起来，玩家先看清「脱开底座」。 */
    public static final int CRYSTAL_OUTWARD_TICKS = 34;
    /** 停机进度过了这个点，晶体才开始往里收；前面的时间是留给转速刹车的。 */
    public static final double CRYSTAL_RETRACT_START = 0.45D;

    /**
     * 转子上的一个构件。
     *
     * @param offset   蓝图里相对控制器的方块偏移（即原始位置）
     * @param angleDeg 原始方位角（度，0 指向 +X）
     * @param radius   原始半径（取整格后的真实半径，启动瞬间能严丝合缝地贴回方块位置）
     * @param pod      是否为激光稳定器
     * @param podIndex 稳定器编号（0..3），环方块为 -1
     * @param top      是否属于最顶那圈上下震动的环
     * @param crystal  是否为中环那四块会先外扩再绕圆平面转的奥术晶体
     * @param block    该构件的方块（渲染时用它的已成型模型）
     */
    public record RotorPart(BlockPos offset, double angleDeg, double radius, boolean pod, int podIndex,
                            boolean top, boolean crystal, Block block) {
    }

    private static volatile List<RotorPart> rotorParts;
    private static volatile List<RotorPart> pods;
    private static volatile List<RotorPart> topParts;
    private static volatile List<RotorPart> crystalParts;
    private static volatile List<RotorPart> allParts;

    private ArmillaryAssembly() {
    }

    /** 转子构件列表：先是整圈环方块，再是四台稳定器。懒加载，避开注册表初始化顺序。 */
    public static List<RotorPart> rotorParts() {
        List<RotorPart> cached = rotorParts;
        if (cached == null) {
            synchronized (ArmillaryAssembly.class) {
                cached = rotorParts;
                if (cached == null) {
                    cached = buildRotorParts();
                    rotorParts = cached;
                }
            }
        }
        return cached;
    }

    /** 最顶上那圈环（含水晶冠饰）。 */
    public static List<RotorPart> topParts() {
        List<RotorPart> cached = topParts;
        if (cached == null) {
            synchronized (ArmillaryAssembly.class) {
                cached = topParts;
                if (cached == null) {
                    cached = buildTopParts();
                    topParts = cached;
                }
            }
        }
        return cached;
    }

    /** 全部会跟着转的构件：主转子 + 四块外扩的晶体 + 顶环。碰撞实体按这个顺序编号。 */
    public static List<RotorPart> allParts() {
        List<RotorPart> cached = allParts;
        if (cached == null) {
            synchronized (ArmillaryAssembly.class) {
                cached = allParts;
                if (cached == null) {
                    List<RotorPart> merged = new ArrayList<>(rotorParts());
                    merged.addAll(crystalParts());
                    merged.addAll(topParts());
                    cached = List.copyOf(merged);
                    allParts = cached;
                }
            }
        }
        return cached;
    }

    /** 中环那四块奥术晶体构件：启动后脱离方块形态，先外扩一段再绕圆平面转。 */
    public static List<RotorPart> crystalParts() {
        List<RotorPart> cached = crystalParts;
        if (cached == null) {
            synchronized (ArmillaryAssembly.class) {
                cached = crystalParts;
                if (cached == null) {
                    cached = buildCrystalParts();
                    crystalParts = cached;
                }
            }
        }
        return cached;
    }

    /** 四台激光稳定器构件（按 pod 编号排序）。 */
    public static List<RotorPart> pods() {
        List<RotorPart> cached = pods;
        if (cached == null) {
            List<RotorPart> result = new ArrayList<>(PODS);
            for (RotorPart part : rotorParts()) {
                if (part.pod()) {
                    result.add(part);
                }
            }
            result.sort((a, b) -> Integer.compare(a.podIndex(), b.podIndex()));
            cached = List.copyOf(result);
            pods = cached;
        }
        return cached;
    }

    private static List<RotorPart> buildRotorParts() {
        List<RotorPart> result = new ArrayList<>();
        for (BlockPos offset : ArcaneStructure.rotorRingOffsets()) {
            result.add(new RotorPart(offset, angleOf(offset), radiusOf(offset), false, -1, false, false,
                    ModBlocks.ARMILLARY_RING.get()));
        }
        int podIndex = 0;
        for (BlockPos offset : ArcaneStructure.stabilizerOffsets()) {
            result.add(new RotorPart(offset, angleOf(offset), radiusOf(offset), true, podIndex++, false, false,
                    ModBlocks.LASER_STABILIZER.get()));
        }
        return List.copyOf(result);
    }

    /** 中环四个转角上的奥术晶体：方位角与半径都取自蓝图，启动瞬间正好落在原方块位置上。 */
    private static List<RotorPart> buildCrystalParts() {
        List<RotorPart> result = new ArrayList<>();
        for (BlockPos offset : ArcaneStructure.rotorCrystalOffsets()) {
            result.add(new RotorPart(offset, angleOf(offset), radiusOf(offset), false, -1, false, true,
                    ModBlocks.ARCANE_CRYSTAL_BLOCK.get()));
        }
        return List.copyOf(result);
    }

    private static List<RotorPart> buildTopParts() {
        List<RotorPart> result = new ArrayList<>();
        for (ArcaneStructure.Part part : ArcaneStructure.topRingParts()) {
            BlockPos offset = part.offset();
            result.add(new RotorPart(offset, angleOf(offset), radiusOf(offset), false, -1, true, false,
                    part.block()));
        }
        return List.copyOf(result);
    }

    public static double angleOf(BlockPos offset) {
        return normalize(Math.toDegrees(Math.atan2(offset.getZ(), offset.getX())));
    }

    public static double radiusOf(BlockPos offset) {
        return Math.hypot(offset.getX(), offset.getZ());
    }

    /**
     * 启动阶段的瞬时转速（度/tick）：从 {@link #SPIN_START_DEG_PER_TICK} 平滑加速，
     * 在 {@link #SPIN_RAMP_TICKS} tick 后达到 {@link #SPIN_MAX_DEG_PER_TICK} 并保持。
     */
    public static float spinSpeedDegPerTick(double ticksSinceStart) {
        double u = Math.max(0.0D, Math.min(ticksSinceStart / SPIN_RAMP_TICKS, 1.0D));
        double eased = u * u * (3.0D - 2.0D * u);
        return (float) (SPIN_START_DEG_PER_TICK + (SPIN_MAX_DEG_PER_TICK - SPIN_START_DEG_PER_TICK) * eased);
    }

    /**
     * 启动阶段累计转过的角度（度）。这是对上面那条速度曲线做积分得到的闭式表达，
     * 客户端渲染与服务端碰撞实体都调用它，所以「看到的」和「撞到的」永远是同一个角度，
     * 也不会因为停机重开而累积误差。
     */
    public static double spinAngleDeg(double ticksSinceStart) {
        return normalize(spinAngleRawDeg(ticksSinceStart));
    }

    /** 同一角度但不取模：要按比例缩放转速时用这个，否则每次转过 360 都会跳一下。 */
    public static double spinAngleRawDeg(double ticksSinceStart) {
        double ticks = Math.max(0.0D, ticksSinceStart);
        double delta = SPIN_MAX_DEG_PER_TICK - SPIN_START_DEG_PER_TICK;
        if (ticks <= SPIN_RAMP_TICKS) {
            double u = ticks / SPIN_RAMP_TICKS;
            return SPIN_START_DEG_PER_TICK * ticks
                    + delta * SPIN_RAMP_TICKS * (u * u * u - 0.5D * u * u * u * u);
        }
        double rampAngle = SPIN_START_DEG_PER_TICK * SPIN_RAMP_TICKS + delta * SPIN_RAMP_TICKS * 0.5D;
        return rampAngle + SPIN_MAX_DEG_PER_TICK * (ticks - SPIN_RAMP_TICKS);
    }

    /** 转子角度（度）。{@code startTick} 是启动时刻，启动瞬间所有构件正好落在原始方块位置上。 */
    public static double rotorAngleDeg(double time, long startTick) {
        return spinAngleDeg(Math.max(0.0D, time - startTick));
    }

    /** 顶环角度（度）：与转子同一条加速曲线，速度稍慢，启动瞬间同样正好落在原位。 */
    public static double topRingAngleDeg(double time, long startTick) {
        return normalize(spinAngleRawDeg(Math.max(0.0D, time - startTick)) * TOP_SPIN_SCALE);
    }

    /**
     * 顶环的上下震动偏移（格）：启动后振幅随转速一起涨起来，正弦上下抖。
     * 启动瞬间为 0，所以方块正好落在原位；停机时渲染器会把这个值乘上 (1 - 停机进度) 收回去。
     */
    public static double topRingBob(double time, long startTick) {
        double ticks = Math.max(0.0D, time - startTick);
        double ramp = Math.min(ticks / SPIN_RAMP_TICKS, 1.0D);
        return TOP_BOB_AMPLITUDE * ramp * Math.sin(ticks * TOP_BOB_SPEED);
    }

    /**
     * 中环奥术晶体向外扩出的距离（格）。
     *
     * <p>启动瞬间为 0，正好落在原方块位置上；随后用平滑曲线（两端导数为 0，也就是起步和到位都
     * 不会「弹一下」）在 {@link #CRYSTAL_OUTWARD_TICKS} tick 内推到 {@link #CRYSTAL_OUTWARD}。
     * 这段时间转子才刚从 0.06°/tick 起步，所以看起来就是「先脱开底座向外飘出去，再开始跟着转」。</p>
     */
    public static double crystalOutward(double ticksSinceStart) {
        return CRYSTAL_OUTWARD * smoothstep(ticksSinceStart / CRYSTAL_OUTWARD_TICKS);
    }

    /**
     * 停机时晶体外扩距离：旋转刹车的前半程还留在最外圈，过了 {@link #CRYSTAL_RETRACT_START}
     * 再同步往回收，进度到 1 时正好是 0——也就是方块恢复的那一帧，晶体刚好落回原位。
     */
    public static double crystalOutwardOnBrake(double progress) {
        double p = clamp01(progress);
        if (p <= CRYSTAL_RETRACT_START) {
            return CRYSTAL_OUTWARD;
        }
        double u = (p - CRYSTAL_RETRACT_START) / (1.0D - CRYSTAL_RETRACT_START);
        return CRYSTAL_OUTWARD * (1.0D - smoothstep(u));
    }

    /** 两端导数为 0 的平滑插值（0..1）。 */
    public static double smoothstep(double u) {
        double x = clamp01(u);
        return x * x * (3.0D - 2.0D * x);
    }

    public static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return value < 0.0D ? 0.0D : (value > 1.0D ? 1.0D : value);
    }

    /**
     * 停机「刹车」角度：从当前角度用二次缓出转到下一个 360 的整数倍，
     * 起始角速度最大、越转越慢，最后稳稳停在原位，方块就在这一刻恢复，看不出跳动。
     */
    public static double brakeAngleDeg(double startAngle, double progress) {
        double p = Math.max(0.0D, Math.min(progress, 1.0D));
        double start = normalize(startAngle);
        double remaining = 360.0D - start;
        // 已经停在原位（角度正好 0）时不要再空转一整圈。
        if (remaining >= 359.5D) {
            remaining = 0.0D;
        }
        return start + remaining * (2.0D * p - p * p);
    }

    /**
     * 反向刹车的角度：从当前角度用二次缓出<b>往回</b>转到 0（也就是 360 的整数倍）。
     * 顶环与中环反向转，所以它停机时也得反着刹，否则会在停机瞬间掉头，看起来就是「抽搐」。
     */
    public static double brakeAngleBackDeg(double startAngle, double progress) {
        double p = Math.max(0.0D, Math.min(progress, 1.0D));
        double start = normalize(startAngle);
        return start * (1.0D - (2.0D * p - p * p));
    }

    /**
     * 对「不取模的积分角」做同样的刹车：从 {@code startRaw} 平滑减到下一个 360 的整数倍。
     * 装饰环、视界外壳这类按比例缩放进角度的东西必须用这个，否则缩放后取模会跳一大格。
     */
    public static double brakeRawAngleDeg(double startRaw, double progress) {
        if (!Double.isFinite(startRaw)) {
            return 0.0D;
        }
        double p = Math.max(0.0D, Math.min(progress, 1.0D));
        double target = Math.ceil(startRaw / 360.0D) * 360.0D;
        double remaining = target - startRaw;
        if (remaining < 0.5D) {
            remaining = 0.0D;
        }
        return startRaw + remaining * (2.0D * p - p * p);
    }

    public static double normalize(double angle) {
        double wrapped = angle % 360.0D;
        return wrapped < 0.0D ? wrapped + 360.0D : wrapped;
    }

    /** 第 {@code podIndex} 台稳定器当前的方位角。 */
    public static double podAngleDeg(double rotorAngleDeg, int podIndex) {
        for (RotorPart part : rotorParts()) {
            if (part.pod() && part.podIndex() == podIndex) {
                return normalize(part.angleDeg() + rotorAngleDeg);
            }
        }
        return normalize(rotorAngleDeg + podIndex * (360.0D / PODS));
    }
}
