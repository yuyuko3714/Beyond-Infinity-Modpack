package cn.blockforge.generated.arcanesingularity.structure;

import cn.blockforge.generated.arcanesingularity.block.ProjectionGhostBlock;
import cn.blockforge.generated.arcanesingularity.blockentity.ProjectionGhostBlockEntity;
import cn.blockforge.generated.arcanesingularity.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 全息投影的服务端实现。
 *
 * <p>投影期间会在每个缺失构件的坐标放一个不可见、无碰撞、可被替换的幻影方块。
 * 客户端把幻影渲染成目标构件材质的半透明虚影，而准星指向它时 Jade 会读到目标构件名称。
 * 玩家直接放置方块、成型终端搭建或投影到期都会让幻影消失。</p>
 */
public final class ProjectionService {
    private ProjectionService() {
    }

    public static void show(Level level, BlockPos controllerPos, StructureKind kind, Direction facing) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        for (ArcaneStructure.Part part : ArcaneStructure.buildParts(kind, facing)) {
            if (part.type() == ArcaneStructure.PartType.CONTROLLER) {
                continue;
            }
            BlockPos target = controllerPos.offset(part.offset());
            if (!level.isInWorldBounds(target) || !level.isLoaded(target)) {
                continue;
            }
            BlockState state = level.getBlockState(target);
            if (state.getBlock() == part.block()) {
                continue;
            }
            if (isGhost(state)) {
                if (level.getBlockEntity(target) instanceof ProjectionGhostBlockEntity ghost) {
                    ghost.setup(controllerPos, part.block().defaultBlockState(), ghost.getPrevious());
                }
                continue;
            }
            // 只有空气和自然地形才让位给幻影：玩家自己放的建筑不会被投影挖掉。
            // 地形也包括进来，幻影才会铺满地基，准星指到任意一格都能在 Jade 里读到构件名。
            if (!state.isAir() && !isNaturalTerrain(state)) {
                continue;
            }
            level.setBlock(target, ModBlocks.PROJECTION_GHOST.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            BlockEntity blockEntity = level.getBlockEntity(target);
            if (blockEntity instanceof ProjectionGhostBlockEntity ghost) {
                ghost.setup(controllerPos, part.block().defaultBlockState(), state);
            }
        }
    }

    public static void clear(Level level, BlockPos controllerPos, StructureKind kind, Direction facing) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        // 地基也要清：投影时它们是真实存在的幻影方块，不清会留在地上。
        for (ArcaneStructure.Part part : ArcaneStructure.buildParts(kind, facing)) {
            if (part.type() == ArcaneStructure.PartType.CONTROLLER) {
                continue;
            }
            BlockPos target = controllerPos.offset(part.offset());
            if (!level.isLoaded(target)) {
                continue;
            }
            BlockState state = level.getBlockState(target);
            if (!isGhost(state)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(target);
            if (blockEntity instanceof ProjectionGhostBlockEntity ghost) {
                if (!controllerPos.equals(ghost.getController())) {
                    continue;
                }
                ghost.removeGhost();
            } else {
                level.setBlock(target, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS);
            }
        }
    }

    public static boolean isGhost(BlockState state) {
        return state.getBlock() instanceof ProjectionGhostBlock;
    }

    /** 玩家、成型终端或地形生成占住的位置，都算「可以就地替换」。 */
    public static boolean isReplaceableForStructure(BlockState state) {
        return state.isAir() || isGhost(state) || isNaturalTerrain(state);
    }

    /**
     * 自然地形：石头、泥土、沙砾、原木树叶、花草、水…… 成型终端会直接把这些换掉，
     * 玩家不用先自己挖平场地；玩家自己放的建筑构件（有方块实体的、各种机器）不在其中，不会被拆。
     */
    public static boolean isNaturalTerrain(BlockState state) {
        if (state.liquid() || state.canBeReplaced()) {
            return true;
        }
        return state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.SAND)
                || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.SNOW) || state.is(BlockTags.ICE)
                || state.is(BlockTags.TERRACOTTA)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.END_STONE)
                || state.is(Blocks.SCULK) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.PODZOL) || state.is(Blocks.NETHERRACK) || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.SOUL_SOIL) || state.is(Blocks.OBSIDIAN) || state.is(Blocks.MAGMA_BLOCK);
    }
}
