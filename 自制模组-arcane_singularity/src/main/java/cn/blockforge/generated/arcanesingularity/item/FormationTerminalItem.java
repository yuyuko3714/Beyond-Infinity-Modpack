package cn.blockforge.generated.arcanesingularity.item;

import cn.blockforge.generated.arcanesingularity.block.SingularityControllerBlock;
import cn.blockforge.generated.arcanesingularity.block.StargateControllerBlock;
import cn.blockforge.generated.arcanesingularity.block.TimeBeaconControllerBlock;
import cn.blockforge.generated.arcanesingularity.block.WorldCoreControllerBlock;
import cn.blockforge.generated.arcanesingularity.structure.StructureBuilder;
import cn.blockforge.generated.arcanesingularity.structure.StructureKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 成型终端：潜行右击控制器即可按蓝图自动搭建多方块结构。
 *
 * <p>关键点：原版/Forge 的右键流程里，只要「潜行 + 手上拿着物品」，就会跳过
 * {@code BlockState#use}（见 {@code ServerPlayerGameMode#useItemOn} 里的 flag1），
 * 直接走到物品的 {@code useOn}。所以潜行搭建必须写在物品上，方块一侧只做兜底。</p>
 */
public class FormationTerminalItem extends LoreItem {
    public FormationTerminalItem(Properties properties, String... lines) {
        super(properties, lines);
    }

    @Nullable
    private static StructureKind kindOf(BlockState state) {
        if (state.getBlock() instanceof SingularityControllerBlock) {
            return StructureKind.SINGULARITY;
        }
        if (state.getBlock() instanceof StargateControllerBlock) {
            return StructureKind.STARGATE;
        }
        if (state.getBlock() instanceof TimeBeaconControllerBlock) {
            return StructureKind.TIMEBEACON;
        }
        if (state.getBlock() instanceof WorldCoreControllerBlock) {
            return StructureKind.WORLD_CORE;
        }
        return null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        StructureKind kind = kindOf(level.getBlockState(pos));
        if (kind == null || player == null) {
            return InteractionResult.PASS;
        }
        // 不潜行时保持原样：交给控制器自己的 use，打开机器界面。
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            StructureBuilder.build(player, pos, kind);
        }
        player.swing(context.getHand());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
