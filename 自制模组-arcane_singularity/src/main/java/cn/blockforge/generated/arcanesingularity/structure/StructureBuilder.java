package cn.blockforge.generated.arcanesingularity.structure;

import cn.blockforge.generated.arcanesingularity.block.StargateControllerBlock;
import cn.blockforge.generated.arcanesingularity.blockentity.SingularityControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.StargateControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.TimeBeaconControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.WorldCoreControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 成型终端：按蓝图自动搭建多方块结构。
 *
 * <p>创造模式无视材料，一次补完所有还能放的位置；生存模式则「有多少放多少」——
 * 只要背包或快捷栏里还有对应建材就继续摆放，缺材料的构件先跳过，多次使用即可逐步补齐。
 * 被错误方块占住的位置永远不替换。</p>
 */
public final class StructureBuilder {
    private StructureBuilder() {
    }

    public static boolean build(Player player, BlockPos controllerPos, StructureKind kind) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        Level level = player.level();
        BlockEntity existing = level.getBlockEntity(controllerPos);
        if (kind == StructureKind.SINGULARITY && existing instanceof SingularityControllerBlockEntity be
                && be.isFormed()) {
            player.displayClientMessage(Component.translatable("message.arcane_singularity.already_formed"), true);
            return false;
        }
        if (kind == StructureKind.STARGATE && existing instanceof StargateControllerBlockEntity be
                && be.isFormed()) {
            player.displayClientMessage(Component.translatable("message.arcane_singularity.already_formed"), true);
            return false;
        }
        if (kind == StructureKind.TIMEBEACON && existing instanceof TimeBeaconControllerBlockEntity be
                && be.isFormed()) {
            player.displayClientMessage(Component.translatable("message.arcane_singularity.already_formed"), true);
            return false;
        }
        if (kind == StructureKind.WORLD_CORE && existing instanceof WorldCoreControllerBlockEntity be
                && be.isFormed()) {
            player.displayClientMessage(Component.translatable("message.arcane_singularity.already_formed"), true);
            return false;
        }

        Direction facing = level.getBlockState(controllerPos).getBlock() instanceof StargateControllerBlock
                ? level.getBlockState(controllerPos).getValue(StargateControllerBlock.FACING)
                : Direction.NORTH;
        List<ArcaneStructure.Part> parts = ArcaneStructure.buildParts(kind, facing);
        List<ArcaneStructure.Part> placeable = new ArrayList<>();
        int blocked = 0;
        int unloaded = 0;

        for (ArcaneStructure.Part part : parts) {
            if (part.type() == ArcaneStructure.PartType.CONTROLLER) {
                continue;
            }
            BlockPos target = controllerPos.offset(part.offset());
            if (!level.isInWorldBounds(target)) {
                player.displayClientMessage(Component.translatable("message.arcane_singularity.out_of_world"), true);
                return false;
            }
            if (!level.isLoaded(target)) {
                // 只跳过这一块，继续搭其余部分；玩家走近再把缺的补上即可
                unloaded++;
                continue;
            }
            BlockState state = level.getBlockState(target);
            if (state.getBlock() == part.block()) {
                continue;
            }
            if (!ProjectionService.isReplaceableForStructure(state)) {
                // 被错误方块占住的位置跳过，但不影响其他位置继续施工。
                blocked++;
                continue;
            }
            placeable.add(part);
        }

        if (placeable.isEmpty()) {
            boolean formed = tryForm(level, controllerPos, blocked == 0 && unloaded == 0 ? serverPlayer : null);
            if (!formed) {
                player.displayClientMessage(Component.translatable("message.arcane_singularity.build_none"), true);
            }
            return formed;
        }

        boolean creative = player.isCreative();
        int placed = 0;
        int skipped = 0;
        for (ArcaneStructure.Part part : placeable) {
            if (!creative && !takeOne(player, part.block().asItem())) {
                skipped++;
                continue;
            }
            level.setBlockAndUpdate(controllerPos.offset(part.offset()), part.block().defaultBlockState());
            placed++;
        }

        if (placed > 0) {
            level.playSound(null, controllerPos, SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.BLOCKS, 1.2F, 0.7F);
            boolean incomplete = skipped > 0 || blocked > 0 || unloaded > 0;
            boolean formed = tryForm(level, controllerPos, incomplete ? null : serverPlayer);
            if (!formed && skipped > 0) {
                player.displayClientMessage(Component.translatable("message.arcane_singularity.partial",
                        placed, skipped), true);
            } else if (!formed && blocked > 0) {
                player.displayClientMessage(Component.translatable("message.arcane_singularity.build_blocked"), true);
            } else if (!formed && unloaded > 0) {
                player.displayClientMessage(Component.translatable("message.arcane_singularity.chunk_unloaded"), true);
            }
        } else {
            player.displayClientMessage(Component.translatable("message.arcane_singularity.partial",
                    0, skipped), true);
        }
        return placed > 0;
    }

    private static boolean tryForm(Level level, BlockPos controllerPos, ServerPlayer player) {
        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        if (blockEntity instanceof SingularityControllerBlockEntity be) {
            return be.tryForm(player);
        }
        if (blockEntity instanceof StargateControllerBlockEntity be) {
            return be.tryForm(player);
        }
        if (blockEntity instanceof TimeBeaconControllerBlockEntity be) {
            return be.tryForm(player);
        }
        if (blockEntity instanceof WorldCoreControllerBlockEntity be) {
            return be.tryForm(player);
        }
        return false;
    }

    private static boolean takeOne(Player player, Item item) {
        if (item == net.minecraft.world.item.Items.AIR) {
            return false;
        }
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                stack.shrink(1);
                player.getInventory().setChanged();
                return true;
            }
        }
        return false;
    }
}
