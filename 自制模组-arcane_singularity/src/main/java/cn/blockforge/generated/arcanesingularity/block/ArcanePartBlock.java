package cn.blockforge.generated.arcanesingularity.block;

import cn.blockforge.generated.arcanesingularity.structure.StructureSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * 多方块普通构件。{@code formed} 为真时切换为已成型模型，并由控制器统一维护。
 */
public class ArcanePartBlock extends Block {
    public ArcanePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ArcaneProperties.FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ArcaneProperties.FORMED);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(ArcaneProperties.FORMED)) {
            StructureSupport.onStructureBlockBroken(level, pos);
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}
