package cn.blockforge.generated.arcanesingularity.redstone;

/**
 * 控制器的红石接口工作模式。
 * <p>
 * 控制器任意一面都可以接入拉杆、按钮、红石线、红石火把或中继器，
 * 拉杆拨到「开」即启动，拨回「关」即停机。
 */
public enum RedstoneMode {
    /** 忽略红石：沿用「放入核心即点火」的自动行为。 */
    AUTO("auto"),
    /** 高电平运行：有红石信号时启动 / 维持，信号消失即关闭。 */
    SIGNAL_ON("signal_on"),
    /** 低电平运行：没有红石信号时运行，通上电则关闭。 */
    SIGNAL_OFF("signal_off");

    private final String name;

    RedstoneMode(String name) {
        this.name = name;
    }

    public int id() {
        return ordinal();
    }

    public String translationKey() {
        return "gui.arcane_singularity.redstone." + name;
    }

    public RedstoneMode next() {
        RedstoneMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** 当前模式下，给定红石信号强度是否允许机器运行。 */
    public boolean permits(int signal) {
        return switch (this) {
            case AUTO -> true;
            case SIGNAL_ON -> signal > 0;
            case SIGNAL_OFF -> signal == 0;
        };
    }

    public static RedstoneMode byId(int id) {
        RedstoneMode[] values = values();
        if (id < 0 || id >= values.length) {
            return AUTO;
        }
        return values[id];
    }
}
