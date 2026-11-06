package cn.blockforge.generated.arcanesingularity.time;

import cn.blockforge.generated.arcanesingularity.blockentity.TimeBeaconControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.data.TimeBeaconData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 时间信标对「维度时间」的作用。
 *
 * <p>原版每 tick 让 {@code dayTime} 往前走 1，这里在每个维度 tick <b>结束</b> 时把它改写成
 * 自己维护的那条时间轴：倍率 8 就是每 tick 走 8，倍率 0 就是原地不动。因此减速、加速、停止
 * 三种效果对昼夜（以及依赖昼夜的阳光、怪物燃烧、村民作息……）都是真的生效，而不只是一个数字。</p>
 *
 * <p>为什么用「改写」而不是「叠加」：叠加只能加速，停不下来；而且原版那 +1 在
 * {@code RULE_DAYLIGHT} 关掉时根本不会发生，叠加就完全失效了。改写两种情况下都准。</p>
 *
 * <p>遇到外部改动（睡觉跳过夜晚、{@code /time set}、其他模组改时间）时，偏差会超过阈值，
 * 这时直接跟随一次，不会把玩家的床或者指令顶回去。</p>
 */
public final class TimeBeaconService {
    /** 外部改动判定阈值（tick）：正常情况下每 tick 最多推进 32，超过 64 一定是别人改的。 */
    private static final long EXTERNAL_JUMP = 64L;

    /** 每个维度自己的时间轴（double，保留小数，减速时不会因为取整而停住）。 */
    private static final Map<ResourceKey<Level>, Double> CLOCK = new HashMap<>();
    /** 上一 tick 写进去的值，用来判断「这一 tick 是不是被外部改过」。 */
    private static final Map<ResourceKey<Level>, Long> APPLIED = new HashMap<>();

    private TimeBeaconService() {
    }

    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        TimeBeaconControllerBlockEntity beacon = activeBeacon(level);
        if (beacon == null) {
            // 没在运转：把时间轴交还给原版，下次启动从当下的时间重新算
            CLOCK.remove(dimension);
            APPLIED.remove(dimension);
            return;
        }
        double rate = beacon.timeRate();
        if (!Double.isFinite(rate)) {
            return;
        }
        long now = level.getDayTime();
        Long last = APPLIED.get(dimension);
        Double stored = CLOCK.get(dimension);
        double clock = stored == null || last == null || Math.abs(now - last) > EXTERNAL_JUMP
                ? now
                : stored;
        clock += rate;
        level.setDayTime((long) Math.floor(clock));
        CLOCK.put(dimension, clock);
        APPLIED.put(dimension, (long) Math.floor(clock));
    }

    /** 该维度里正在运转（激光已经射出）的时间信标；没有就返回 {@code null}。 */
    private static TimeBeaconControllerBlockEntity activeBeacon(ServerLevel level) {
        BlockPos pos = TimeBeaconData.get(level.getServer()).beacon(level);
        if (pos == null || !level.isLoaded(pos)) {
            return null;
        }
        if (!(level.getBlockEntity(pos) instanceof TimeBeaconControllerBlockEntity beacon)
                || !beacon.isRunning()) {
            return null;
        }
        return beacon;
    }

    /** 当前维度实际生效的倍率；没有信标在运转时就是 1。 */
    public static double rateOf(ServerLevel level) {
        TimeBeaconControllerBlockEntity beacon = activeBeacon(level);
        return beacon == null ? 1.0D : beacon.timeRate();
    }
}
