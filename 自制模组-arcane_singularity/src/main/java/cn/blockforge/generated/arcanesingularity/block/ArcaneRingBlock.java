package cn.blockforge.generated.arcanesingularity.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 会被转子带着一起转的构件（浑天仪环、四方激光稳定器）。
 *
 * <p>黑洞发生器启动后，控制器把这些方块标成 {@code active}：方形模型换成空模型、碰撞箱清空，
 * 外观和碰撞全部交给旋转的 {@code ArmillaryPartEntity}。这样玩家看到的就是「整圈环连着
 * 四个激光稳定器脱离底座、绕圆平面的中轴转起来」，而不是一圈钉死的方块。</p>
 */
public class ArcaneRingBlock extends ArcanePartBlock {
    public ArcaneRingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(ArcaneProperties.FORMED, false)
                .setValue(ArcaneProperties.ACTIVE, false));
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
