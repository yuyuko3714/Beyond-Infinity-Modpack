package cn.blockforge.generated.arcanesingularity.blockentity;

import cn.blockforge.generated.arcanesingularity.block.ArcaneProperties;
import cn.blockforge.generated.arcanesingularity.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class EnergyCableBlockEntity extends BlockEntity {
    /**
     * 符文导管网络不再限制传输速率：任何规模的供能需求都可以通过导管网络。
     * 保留常量是为了兼容旧的过载判定代码，同时让旧存档里已经发红的导管自动冷却。
     */
    public static final int CAPACITY = Integer.MAX_VALUE;
    private long lastLoadUpdate;

    public EnergyCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENERGY_CABLE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EnergyCableBlockEntity be) {
        if (state.getValue(ArcaneProperties.OVERLOADED) && level.getGameTime() - be.lastLoadUpdate > 60L) {
            level.setBlock(pos, state.setValue(ArcaneProperties.OVERLOADED, false), 3);
        }
    }

    public void setLoad(int load, long gameTime) {
        this.lastLoadUpdate = gameTime;
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        boolean hot = load > CAPACITY;
        if (state.getValue(ArcaneProperties.OVERLOADED) != hot) {
            level.setBlock(worldPosition, state.setValue(ArcaneProperties.OVERLOADED, hot), 3);
        }
    }
}
