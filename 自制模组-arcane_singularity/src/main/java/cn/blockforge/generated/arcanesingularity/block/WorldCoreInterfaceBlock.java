package cn.blockforge.generated.arcanesingularity.block;

import cn.blockforge.generated.arcanesingularity.blockentity.WorldCoreControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.blockentity.WorldCoreInterfaceBlockEntity;
import cn.blockforge.generated.arcanesingularity.registry.ModBlockEntities;
import cn.blockforge.generated.arcanesingularity.structure.StructureSupport;
import cn.blockforge.generated.arcanesingularity.structure.WorldCoreLink;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 世界之核的专属接口。
 *
 * <p>它站在地基表面（不是埋进地基里），上面压着一台专属激光器。玩家要用世界之核专属管道
 * 把黑洞发生器、星门、时间信标三台控制器接到这个接口上，接口才会「亮起来」。</p>
 *
 * <p>右键可以直接读到它当前连到了哪几台控制器，方便排查接线。</p>
 */
public class WorldCoreInterfaceBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);

    public WorldCoreInterfaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(ArcaneProperties.FORMED, false)
                .setValue(ArcaneProperties.ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ArcaneProperties.FORMED, ArcaneProperties.ACTIVE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            int mask = WorldCoreLink.scan(level, pos);
            boolean singularity = (mask & WorldCoreLink.LINK_SINGULARITY) != 0;
            boolean gate = (mask & WorldCoreLink.LINK_STARGATE) != 0;
            boolean beacon = (mask & WorldCoreLink.LINK_BEACON) != 0;
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.arcane_singularity.world_core_interface",
                    Component.translatable(singularity
                            ? "gui.arcane_singularity.online" : "gui.arcane_singularity.offline"),
                    Component.translatable(gate
                            ? "gui.arcane_singularity.online" : "gui.arcane_singularity.offline"),
                    Component.translatable(beacon
                            ? "gui.arcane_singularity.online" : "gui.arcane_singularity.offline")), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(ArcaneProperties.FORMED)) {
            StructureSupport.onStructureBlockBroken(level, pos);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorldCoreInterfaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.WORLD_CORE_INTERFACE.get(),
                WorldCoreInterfaceBlockEntity::serverTick);
    }

    /** 接口上方的那台激光器是不是还在：渲染器用它决定要不要画这道光。 */
    public static boolean hasEmitter(Level level, BlockPos interfacePos) {
        BlockPos above = interfacePos.above();
        return level.isLoaded(above) && level.getBlockState(above).getBlock()
                instanceof WorldCoreEmitterBlock;
    }

    /** 找到这个接口所属的世界之核控制器（供渲染器用）。 */
    @Nullable
    public static WorldCoreControllerBlockEntity controllerOf(Level level, BlockPos interfacePos) {
        if (!(level.getBlockEntity(interfacePos) instanceof WorldCoreInterfaceBlockEntity interfaceEntity)) {
            return null;
        }
        return interfaceEntity.findController();
    }
}
