package cn.blockforge.generated.arcanesingularity.energy;

import cn.blockforge.generated.arcanesingularity.block.ArcaneProperties;
import cn.blockforge.generated.arcanesingularity.block.EnergyCableBlock;
import cn.blockforge.generated.arcanesingularity.block.EnergyPortBlock;
import cn.blockforge.generated.arcanesingularity.blockentity.EnergyCableBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.EnergyPortBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.SingularityControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.data.GateId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 轻量级符文导管网络。沿导管 BFS 查找已点亮的能量输出端口。
 * 传输速率不做限制：只要网络能连到供能源，星门的任何功率需求都能被满足。
 */
public final class ArcaneEnergyNetwork {
    public static final int CABLE_LIMIT = 2048;
    public static final int OPEN_DEMAND = 150_000;
    public static final int IDLE_DEMAND = 60_000;

    public record Source(String id, BlockPos portPos, BlockPos controllerPos, int distance) {
    }

    public record Result(List<Source> sources, int cableCount, int demand) {
        @Nullable
        public Source choose(String avoidId) {
            Source fallback = null;
            for (Source source : sources) {
                if (fallback == null) {
                    fallback = source;
                }
                if (avoidId == null || !avoidId.equals(source.id())) {
                    return source;
                }
            }
            return fallback;
        }

        public boolean overloaded() {
            return false;
        }
    }

    private ArcaneEnergyNetwork() {
    }

    public static Result scan(Level level, List<BlockPos> gatePorts, int demand) {
        if (gatePorts.isEmpty()) {
            return new Result(List.of(), 0, 0);
        }
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, Source> sources = new LinkedHashMap<>();

        for (BlockPos gatePort : gatePorts) {
            BlockPos origin = gatePort.immutable();
            for (Direction direction : Direction.values()) {
                BlockPos next = origin.relative(direction);
                if (!level.isLoaded(next)) {
                    continue;
                }
                if (level.getBlockState(next).getBlock() instanceof EnergyCableBlock && visited.add(next)) {
                    queue.add(next);
                }
            }
        }

        while (!queue.isEmpty() && visited.size() <= CABLE_LIMIT) {
            BlockPos cablePos = queue.poll();
            BlockState cableState = level.getBlockState(cablePos);
            if (!(cableState.getBlock() instanceof EnergyCableBlock)) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = cablePos.relative(direction);
                if (!level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof EnergyCableBlock) {
                    if (visited.add(next)) {
                        queue.add(next);
                    }
                    continue;
                }
                if (!(state.getBlock() instanceof EnergyPortBlock port) || !port.isOutput()
                        || !state.getValue(ArcaneProperties.ACTIVE)) {
                    continue;
                }
                BlockEntity blockEntity = level.getBlockEntity(next);
                if (!(blockEntity instanceof EnergyPortBlockEntity portEntity)) {
                    continue;
                }
                BlockPos controllerPos = portEntity.getControllerPos();
                if (controllerPos == null) {
                    continue;
                }
                if (!level.isLoaded(controllerPos)) {
                    // 控制器所在区块没加载就不要去查：否则每次扫描都会把它同步拉起来，
                    // 区块反复加载/卸载会拖慢世界保存。
                    continue;
                }
                if (!(level.getBlockEntity(controllerPos) instanceof SingularityControllerBlockEntity singularity)
                        || !singularity.isFormed() || !singularity.isStable()) {
                    continue;
                }
                BlockPos immutablePort = next.immutable();
                sources.putIfAbsent(controllerPos, new Source(
                        GateId.of(level, controllerPos).encode(),
                        immutablePort,
                        controllerPos.immutable(),
                        (int) Math.sqrt(immutablePort.distSqr(gatePorts.get(0)))));
            }
        }

        int cableCount = visited.size();
        boolean powered = !sources.isEmpty();
        int load = powered ? demand : 0;
        for (BlockPos cablePos : visited) {
            BlockEntity blockEntity = level.getBlockEntity(cablePos);
            if (blockEntity instanceof EnergyCableBlockEntity cable) {
                cable.setLoad(load, level.getGameTime());
            }
        }
        return new Result(List.copyOf(new ArrayList<>(sources.values())), cableCount, load);
    }
}
