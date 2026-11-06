package cn.blockforge.generated.arcanesingularity.time;

/**
 * 时间信标的四种时间模式。
 *
 * <p>减速 / 加速两种模式各自还有五档倍率（界面上的「－」「＋」按钮切换），
 * 正常与停止没有档位。倍率直接乘在当前维度昼夜推进的速度上：
 * 停止就是 0，正常是 1，加速最高 32 倍，减速最低 1/32。</p>
 */
public enum TimeMode {
    /** 减速：时间走得比正常慢。 */
    SLOW("slow"),
    /** 加速：时间走得比正常快。 */
    ACCELERATE("accelerate"),
    /** 正常：原版速度，激光只是亮着。 */
    NORMAL("normal"),
    /** 停止：昼夜彻底定格。 */
    STOP("stop");

    /** 倍率档位数（0..LEVELS-1）。 */
    public static final int LEVELS = 5;

    private final String name;

    TimeMode(String name) {
        this.name = name;
    }

    public int id() {
        return ordinal();
    }

    public String translationKey() {
        return "gui.arcane_singularity.time.mode." + name;
    }

    /** 这种模式能不能调倍率。 */
    public boolean adjustable() {
        return this == SLOW || this == ACCELERATE;
    }

    public TimeMode next() {
        TimeMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static TimeMode byId(int id) {
        TimeMode[] values = values();
        if (id < 0 || id >= values.length) {
            return NORMAL;
        }
        return values[id];
    }

    public static int clampLevel(int level) {
        return level < 0 ? 0 : (level >= LEVELS ? LEVELS - 1 : level);
    }

    /**
     * 当前模式、当前档位下的时间倍率（每 tick 昼夜推进多少 tick）。
     *
     * <p>减速档是 1/2、1/4、1/8、1/16、1/32；加速档是 ×2、×4、×8、×16、×32。</p>
     */
    public double rate(int level) {
        int index = clampLevel(level) + 1;
        return switch (this) {
            case SLOW -> 1.0D / (1 << index);
            case ACCELERATE -> (double) (1 << index);
            case NORMAL, STOP -> this == NORMAL ? 1.0D : 0.0D;
        };
    }

    /**
     * 倍率的显示文本，例如「×8」「×1/4」「停止」。
     *
     * <p>返回的是<b>翻译键</b>加参数，客户端直接用 {@code Component.translatable} 渲染，
     * 中英文各自写各自的说法。</p>
     */
    public String rateKey(int level) {
        return switch (this) {
            case STOP -> "gui.arcane_singularity.time.rate.stop";
            case NORMAL -> "gui.arcane_singularity.time.rate.normal";
            case SLOW -> "gui.arcane_singularity.time.rate.slow." + clampLevel(level);
            case ACCELERATE -> "gui.arcane_singularity.time.rate.fast." + clampLevel(level);
        };
    }
}
