package cn.blockforge.generated.arcanesingularity.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * 时间信标的炮管构件。
 *
 * <p>和普通构件一样只有 {@code formed}，但多一个 {@code active}：成型后最顶上那两格「时序炮管」
 * 会换上空模型，改由控制器渲染器画成悬浮上下振动的实体炮管；关机或拆除时再切回实心模型。</p>
 */
public class TimeBarrelBlock extends ArcanePartBlock {
    public TimeBarrelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ArcaneProperties.ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ArcaneProperties.ACTIVE);
    }
}
