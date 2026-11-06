package cn.blockforge.generated.arcanesingularity.structure;

import cn.blockforge.generated.arcanesingularity.block.WorldCoreCableBlock;
import cn.blockforge.generated.arcanesingularity.blockentity.SingularityControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.StargateControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.TimeBeaconControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * 世界之核的「专属管道」连通检测。
 *
 * <p>世界之核不靠无线感应，也不认符文导管：它只认自己那一种专属管道。
 * 从某个专属接口出发，沿专属管道一张网 BFS 出去，碰到前三座结构的控制器时就记一位：</p>
 * <ul>
 *     <li>{@link #LINK_SINGULARITY}：黑洞发生器在运行（已成型且奇点稳定）；</li>
 *     <li>{@link #LINK_STARGATE}：星门在运行（已成型且门已开启）；</li>
 *     <li>{@link #LINK_BEACON}：时间信标在运行（已成型且激光已射出）。</li>
 * </ul>
 * <p>三位全满，才算「三个控制器都接好了并且都在线」。符文导管与专属管道互不连接，
 * 所以两种网络永远不会串线。</p>
 */
public final class WorldCoreLink {
    /** 一张网最多走多少格专属管道。 */
    public static final int CABLE_LIMIT = 2048;

    public static final int LINK_SINGULARITY = 1;
    public static final int LINK_STARGATE = 2;
    public static final int LINK_BEACON = 4;
    public static final int LINK_ALL = LINK_SINGULARITY | LINK_STARGATE | LINK_BEACON;

    private WorldCoreLink() {
    }

    /** 从接口出发扫描整张专属管道网，返回三位掩码。 */
    public static int scan(Level level, BlockPos interfacePos) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) {
            BlockPos next = interfacePos.relative(direction);
            if (!level.isLoaded(next)) {
                continue;
            }
            if (level.getBlockState(next).getBlock() instanceof WorldCoreCableBlock && visited.add(next)) {
                queue.add(next);
            }
        }
        int mask = 0;
        while (!queue.isEmpty() && visited.size() <= CABLE_LIMIT) {
            BlockPos cablePos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = cablePos.relative(direction);
                if (!level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof WorldCoreCableBlock) {
                    if (visited.add(next)) {
                        queue.add(next);
                    }
                    continue;
                }
                mask |= controllerBit(level, next);
                if (mask == LINK_ALL) {
                    return mask;
                }
            }
        }
        return mask;
    }

    /** 这个位置上是不是某一座结构的控制器，是的话返回对应位，否则 0。 */
    private static int controllerBit(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SingularityControllerBlockEntity singularity) {
            return singularity.isFormed() && singularity.isStable() ? LINK_SINGULARITY : 0;
        }
        if (blockEntity instanceof StargateControllerBlockEntity gate) {
            return gate.isFormed() && gate.isOpen() ? LINK_STARGATE : 0;
        }
        if (blockEntity instanceof TimeBeaconControllerBlockEntity beacon) {
            return beacon.isFormed() && beacon.isRunning() ? LINK_BEACON : 0;
        }
        return 0;
    }

    /** 这个控制器是不是真的接在某张专属管道网上（用于给控制器侧做提示）。 */
    public static boolean touchesCable(Level level, BlockPos controllerPos) {
        for (Direction direction : Direction.values()) {
            BlockPos next = controllerPos.relative(direction);
            if (level.isLoaded(next) && level.getBlockState(next).getBlock() instanceof WorldCoreCableBlock) {
                return true;
            }
        }
        return false;
    }
}
