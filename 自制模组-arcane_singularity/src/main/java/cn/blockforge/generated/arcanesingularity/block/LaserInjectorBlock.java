package cn.blockforge.generated.arcanesingularity.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 黑洞发生器顶部的激光注入器。
 *
 * <p>和普通构件一样有 {@code formed}，另外多一个 {@code active}：结构成型后控制器把它标成
 * {@code active}，方块换成空模型、碰撞清空，外观交给控制器渲染器画成一座<b>悬在顶环正中央、
 * 一直上下浮动</b>的注入器；结构拆除或损坏时再切回实心方块。</p>
 */
public class LaserInjectorBlock extends ArcanePartBlock {
    public LaserInjectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ArcaneProperties.ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ArcaneProperties.ACTIVE);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        if (state.getValue(ArcaneProperties.ACTIVE)) {
            return Shapes.empty();
        }
        return super.getCollisionShape(state, level, pos, context);
    }
}
