package cn.blockforge.generated.arcanesingularity.structure;

import cn.blockforge.generated.arcanesingularity.block.ArcanePartBlock;
import cn.blockforge.generated.arcanesingularity.block.ArcaneProperties;
import cn.blockforge.generated.arcanesingularity.block.EnergyPortBlock;
import cn.blockforge.generated.arcanesingularity.blockentity.SingularityControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.StargateControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.TimeBeaconControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.WorldCoreControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * 结构破坏/回收的辅助逻辑。
 *
 * <p>以前是围着被破坏的方块扫一大片立方体（几万次 getBlockEntity/getBlockState），
 * 每次都可能把未加载的区块同步拉起来，退出保存时尤其要命。现在改成按蓝图反推：
 * 构件的相对偏移是固定的，用「被破坏的位置 - 构件偏移」就能直接算出控制器该在哪，
 * 只查那几百个候选点，而且先看区块加载状态，绝不为了一次查询去加载区块。</p>
 */
public final class StructureSupport {
    private static final Direction[] GATE_FACINGS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private StructureSupport() {
    }

    public static void onStructureBlockBroken(Level level, BlockPos brokenPos) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        if (collapseOwner(level, brokenPos, StructureKind.SINGULARITY, Direction.NORTH)) {
            return;
        }
        if (collapseOwner(level, brokenPos, StructureKind.TIMEBEACON, Direction.NORTH)) {
            return;
        }
        if (collapseOwner(level, brokenPos, StructureKind.WORLD_CORE, Direction.NORTH)) {
            return;
        }
        for (Direction facing : GATE_FACINGS) {
            if (collapseOwner(level, brokenPos, StructureKind.STARGATE, facing)) {
                return;
            }
        }
    }

    private static boolean collapseOwner(Level level, BlockPos brokenPos, StructureKind kind, Direction facing) {
        Set<BlockPos> candidates = new HashSet<>();
        for (ArcaneStructure.Part part : ArcaneStructure.parts(kind, facing)) {
            if (part.type() == ArcaneStructure.PartType.CONTROLLER) {
                continue;
            }
            candidates.add(brokenPos.subtract(part.offset()));
        }
        for (BlockPos candidate : candidates) {
            if (!level.isLoaded(candidate)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(candidate);
            if (kind == StructureKind.SINGULARITY
                    && blockEntity instanceof SingularityControllerBlockEntity singularity
                    && singularity.isFormed() && singularity.containsWorldPos(brokenPos)) {
                singularity.collapseStructure(true);
                return true;
            }
            if (kind == StructureKind.STARGATE
                    && blockEntity instanceof StargateControllerBlockEntity gate
                    && gate.isFormed() && gate.containsWorldPos(brokenPos)) {
                gate.collapseStructure(true);
                return true;
            }
            if (kind == StructureKind.TIMEBEACON
                    && blockEntity instanceof TimeBeaconControllerBlockEntity beacon
                    && beacon.isFormed() && beacon.containsWorldPos(brokenPos)) {
                beacon.collapseStructure(true);
                return true;
            }
            if (kind == StructureKind.WORLD_CORE
                    && blockEntity instanceof WorldCoreControllerBlockEntity core
                    && core.isFormed() && core.containsWorldPos(brokenPos)) {
                core.collapseStructure(true);
                return true;
            }
        }
        return false;
    }

    /**
     * 控制器被炸掉之后，把还留在世界里的构件恢复成未成型外观。
     * 同样按蓝图偏移逐个处理，不再扫描整个立方体区域。
     */
    public static void cleanupOrphanParts(Level level, BlockPos origin, StructureKind kind) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        if (kind == StructureKind.SINGULARITY) {
            resetParts(level, origin, StructureKind.SINGULARITY, Direction.NORTH);
            return;
        }
        if (kind == StructureKind.TIMEBEACON) {
            resetParts(level, origin, StructureKind.TIMEBEACON, Direction.NORTH);
            return;
        }
        if (kind == StructureKind.WORLD_CORE) {
            resetParts(level, origin, StructureKind.WORLD_CORE, Direction.NORTH);
            return;
        }
        for (Direction facing : GATE_FACINGS) {
            resetParts(level, origin, StructureKind.STARGATE, facing);
        }
    }

    private static void resetParts(Level level, BlockPos origin, StructureKind kind, Direction facing) {
        for (ArcaneStructure.Part part : ArcaneStructure.parts(kind, facing)) {
            if (part.type() == ArcaneStructure.PartType.CONTROLLER) {
                continue;
            }
            BlockPos pos = origin.offset(part.offset());
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ArcanePartBlock) {
                boolean formed = state.hasProperty(ArcaneProperties.FORMED)
                        && state.getValue(ArcaneProperties.FORMED);
                boolean active = state.hasProperty(ArcaneProperties.ACTIVE)
                        && state.getValue(ArcaneProperties.ACTIVE);
                if (!formed && !active) {
                    continue;
                }
                BlockState next = state.setValue(ArcaneProperties.FORMED, false);
                if (next.hasProperty(ArcaneProperties.ACTIVE)) {
                    next = next.setValue(ArcaneProperties.ACTIVE, false);
                }
                level.setBlock(pos, next, 3);
            } else if (state.getBlock() instanceof EnergyPortBlock
                    && state.hasProperty(ArcaneProperties.FORMED)
                    && state.getValue(ArcaneProperties.FORMED)) {
                level.setBlock(pos, state
                        .setValue(ArcaneProperties.FORMED, false)
                        .setValue(ArcaneProperties.ACTIVE, false), 3);
            }
        }
    }
}
