package cn.blockforge.generated.arcanesingularity.structure;

import net.minecraft.util.Mth;

/**
 * 世界之核的启动 / 关机节奏。
 *
 * <p>整段仪式一分钟（{@link #WARMUP_LENGTH}），按顺序分五幕，中间不再有雷暴：</p>
 * <ol>
 *     <li><b>充能</b>（0~{@link #GATHER_TICK}）：三个接口上的专属激光器各自聚起一团光球；</li>
 *     <li><b>三光汇聚</b>（{@link #GATHER_TICK} 起）：三道光同时射出，打在天空的同一点上；</li>
 *     <li><b>魔法阵展开</b>（{@link #GLYPH_OPEN_TICK} 起）：以汇聚点为圆心铺开一张巨大的魔法阵，
 *         随后<b>由快到慢</b>旋转；</li>
 *     <li><b>对射</b>（{@link #CONVERGE_TICK} 起）：魔法阵中心向下、顶部激光器向上，两束光在半空汇聚；</li>
 *     <li><b>世界之核显形</b>（{@link #CORE_BURST_TICK} 起）：汇聚点炸开一圈冲击波，一个巨大的命令方块
 *         从能量点里爆发出来，像末地水晶一样旋转，随后稳定下来，命令输入框解锁。</li>
 * </ol>
 * <p>关机反过来走：命令方块先塌缩、光柱回收、魔法阵停转淡出。</p>
 */
public final class WorldCoreAssembly {
    /** 开机全过程：一分钟。 */
    public static final int WARMUP_LENGTH = 1200;
    /** 关机动画长度：让所有特效都能收得干干净净。 */
    public static final int SHUTDOWN_LENGTH = 160;

    /** 三台接口激光器充能结束、同时发射的时刻。 */
    public static final int GATHER_TICK = 260;
    /** 三道激光打到汇聚点、魔法阵开始展开的时刻。 */
    public static final int GLYPH_OPEN_TICK = 340;
    /** 魔法阵完全展开的时刻，之后开始由快到慢旋转。 */
    public static final int GLYPH_OPEN_END = 460;
    /** 魔法阵中心与顶部激光器开始对射的时刻。 */
    public static final int CONVERGE_TICK = 780;
    /** 命令方块世界之核从对撞点爆发出来的时刻。 */
    public static final int CORE_BURST_TICK = 980;

    /** 魔法阵刚开始转的时候每 tick 的角速度：转得很快，一眼就能看出「激活了」。 */
    public static final double GLYPH_FAST_DEG_PER_TICK = 7.0D;
    /** 稳定下来之后每 tick 的角速度：慢到能看清符文。 */
    public static final double GLYPH_SLOW_DEG_PER_TICK = 0.32D;
    /** 角速度从快衰减到慢的时间常数（tick）。 */
    public static final double GLYPH_DECAY_TICKS = 240.0D;

    /** 魔法阵完全展开后的半径（格）。 */
    public static final double GLYPH_RADIUS = 17.0D;

    private WorldCoreAssembly() {
    }

    /** 接口激光器的充能进度 0..1。 */
    public static float charge01(double ticks) {
        return (float) clamp01(ticks / (double) GATHER_TICK);
    }

    /** 三道光伸向汇聚点的进度 0..1（发射瞬间从 0 迅速涨满）。 */
    public static float gather01(double ticks) {
        return (float) clamp01((ticks - GATHER_TICK) / 70.0D);
    }

    /** 魔法阵展开进度 0..1。 */
    public static float glyphOpen01(double ticks) {
        return (float) clamp01((ticks - GLYPH_OPEN_TICK) / (double) (GLYPH_OPEN_END - GLYPH_OPEN_TICK));
    }

    /** 对射光束的强度 0..1。 */
    public static float converge01(double ticks) {
        return (float) clamp01((ticks - CONVERGE_TICK) / 110.0D);
    }

    /** 巨大命令方块的显形进度 0..1（爆发后带着弹性涨到满）。 */
    public static float coreBurst01(double ticks) {
        double raw = clamp01((ticks - CORE_BURST_TICK) / (double) (WARMUP_LENGTH - CORE_BURST_TICK));
        // 弹性曲线：先猛地涨过头一点，再回落稳定，看起来像「炸出来」
        double overshoot = Math.sin(raw * Math.PI * 1.6D) * 0.18D * (1.0D - raw);
        return (float) clamp01(raw + overshoot);
    }

    /**
     * 魔法阵从展开完成那一刻起转过的累计角度（度，不取模）。
     *
     * <p>角速度 <code>v(s) = 慢 + (快 - 慢)·e^(-s/τ)</code>，积分得到累计角。
     * 所以「由快到慢」是连续减速，不会像分段那样在接缝处一顿。</p>
     */
    public static double glyphAngleRawDeg(double spinTicks) {
        double s = Math.max(0.0D, spinTicks);
        double fast = GLYPH_FAST_DEG_PER_TICK;
        double slow = GLYPH_SLOW_DEG_PER_TICK;
        return slow * s + (fast - slow) * GLYPH_DECAY_TICKS * (1.0D - Math.exp(-s / GLYPH_DECAY_TICKS));
    }

    /** 当前角速度占起步速度的比例 0..1：魔法阵的亮度跟着它走。 */
    public static float glyphSpinRamp01(double spinTicks) {
        double s = Math.max(0.0D, spinTicks);
        double speed = GLYPH_SLOW_DEG_PER_TICK + (GLYPH_FAST_DEG_PER_TICK - GLYPH_SLOW_DEG_PER_TICK)
                * Math.exp(-s / GLYPH_DECAY_TICKS);
        return (float) (speed / GLYPH_FAST_DEG_PER_TICK);
    }

    public static double normalize(double angle) {
        double wrapped = angle % 360.0D;
        return wrapped < 0.0D ? wrapped + 360.0D : wrapped;
    }

    private static double clamp01(double value) {
        return Mth.clamp(value, 0.0D, 1.0D);
    }
}
