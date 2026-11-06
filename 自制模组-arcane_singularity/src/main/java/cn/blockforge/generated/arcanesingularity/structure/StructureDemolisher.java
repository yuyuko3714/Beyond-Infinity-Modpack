package cn.blockforge.generated.arcanesingularity.structure;

import cn.blockforge.generated.arcanesingularity.block.SingularityControllerBlock;
import cn.blockforge.generated.arcanesingularity.block.StargateControllerBlock;
import cn.blockforge.generated.arcanesingularity.block.TimeBeaconControllerBlock;
import cn.blockforge.generated.arcanesingularity.block.WorldCoreControllerBlock;
import cn.blockforge.generated.arcanesingularity.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 成型终端的「一键拆除」。
 *
 * <p>手里拿着成型终端、按住潜行左键点两种控制器（奇点摇篮 / 星门控制器）时，整座多方块结构
 * ——构件<b>加</b>地基——按蓝图一块块拆掉，不是只拆控制器留一地零件。</p>
 *
 * <ul>
 *   <li><b>创造模式</b>：只拆不返，方块直接消失，免得背包被几千块建材塞满；</li>
 *   <li><b>生存模式</b>：每一块都按它自己的战利品表算掉落，先进背包，装不下的掉在脚边。</li>
 * </ul>
 *
 * <p>只拆「蓝图里这个位置该有的那种方块」：玩家后来塞进去别的东西（箱子、机器）原样留着，
 * 不会因为一座结构拆了就误伤。先拆控制器，于是它自己的 {@code onRemove} 先跑完——强加载解除、
 * 门环 / 转环实体收掉、构件恢复未成型外观——再逐块拆构件，全程不会出现拆到一半的中间态。</p>
 */
public final class StructureDemolisher {
    private StructureDemolisher() {
    }

    /** 由 {@code MinecraftForge.EVENT_BUS} 调用的左键监听：潜行 + 拿着成型终端点控制器就整座拆除。 */
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        Player player = event.getEntity();
        if (player == null || !player.isShiftKeyDown()) {
            return;
        }
        if (!player.getMainHandItem().is(ModItems.FORMATION_TERMINAL.get())
                && !player.getOffhandItem().is(ModItems.FORMATION_TERMINAL.get())) {
            return;
        }
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof SingularityControllerBlock)
                && !(state.getBlock() instanceof StargateControllerBlock)
                && !(state.getBlock() instanceof TimeBeaconControllerBlock)
                && !(state.getBlock() instanceof WorldCoreControllerBlock)) {
            return;
        }
        if (level.isClientSide) {
            // 客户端什么也别做：这里要是一次性取消掉，原版就连 START_DESTROY_BLOCK 都不发给服务端，
            // 拆除反而永远触发不了。让预测破坏先跑，服务端拆完再把它挡回去。
            return;
        }
        if (player instanceof ServerPlayer serverPlayer && demolish(serverPlayer, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /**
     * 按蓝图拆掉整座结构。
     *
     * @return 真的拆了东西就返回 {@code true}
     */
    public static boolean demolish(ServerPlayer player, BlockPos controllerPos) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockState controllerState = level.getBlockState(controllerPos);
        StructureKind kind;
        Direction facing;
        if (controllerState.getBlock() instanceof SingularityControllerBlock) {
            kind = StructureKind.SINGULARITY;
            facing = Direction.NORTH;
        } else if (controllerState.getBlock() instanceof StargateControllerBlock) {
            kind = StructureKind.STARGATE;
            facing = controllerState.getValue(StargateControllerBlock.FACING);
        } else if (controllerState.getBlock() instanceof TimeBeaconControllerBlock) {
            kind = StructureKind.TIMEBEACON;
            facing = Direction.NORTH;
        } else if (controllerState.getBlock() instanceof WorldCoreControllerBlock) {
            kind = StructureKind.WORLD_CORE;
            facing = Direction.NORTH;
        } else {
            return false;
        }

        boolean creative = player.isCreative();
        List<ItemStack> loot = new ArrayList<>();

        // 控制器自己的产出先算好：拆掉之后方块实体就没了
        if (!creative) {
            loot.addAll(Block.getDrops(controllerState, level, controllerPos,
                    level.getBlockEntity(controllerPos), player, ItemStack.EMPTY));
        }
        // 先拆控制器：它的 onRemove 负责解除强加载、收掉门环 / 转环实体、把构件恢复未成型
        level.removeBlock(controllerPos, false);

        int removed = 1;
        for (ArcaneStructure.Part part : ArcaneStructure.buildParts(kind, facing)) {
            if (part.type() == ArcaneStructure.PartType.CONTROLLER) {
                continue;
            }
            BlockPos pos = controllerPos.offset(part.offset());
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() != part.block()) {
                // 空位、或者玩家后来换进去的别的东西：都不动
                continue;
            }
            if (!creative) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                loot.addAll(Block.getDrops(state, level, pos, blockEntity, player, ItemStack.EMPTY));
            }
            level.removeBlock(pos, false);
            removed++;
        }

        if (!creative) {
            for (ItemStack stack : loot) {
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack copy = stack.copy();
                if (!player.getInventory().add(copy)) {
                    player.drop(copy, false);
                }
            }
        }

        level.playSound(null, controllerPos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2F, 0.65F);
        level.sendParticles(ParticleTypes.PORTAL,
                controllerPos.getX() + 0.5D, controllerPos.getY() + 1.0D, controllerPos.getZ() + 0.5D,
                48, 4.0D, 3.0D, 4.0D, 0.35D);
        player.displayClientMessage(Component.translatable(
                creative ? "message.arcane_singularity.demolished_creative"
                        : "message.arcane_singularity.demolished",
                removed), true);
        return true;
    }
}
