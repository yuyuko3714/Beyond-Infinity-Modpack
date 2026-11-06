package cn.blockforge.generated.arcanesingularity.block;

import cn.blockforge.generated.arcanesingularity.blockentity.StargateControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.registry.ModBlockEntities;
import cn.blockforge.generated.arcanesingularity.registry.ModItems;
import cn.blockforge.generated.arcanesingularity.structure.ChunkKeeper;
import cn.blockforge.generated.arcanesingularity.structure.ProjectionService;
import cn.blockforge.generated.arcanesingularity.structure.StargateRingAssembly;
import cn.blockforge.generated.arcanesingularity.structure.StructureBuilder;
import cn.blockforge.generated.arcanesingularity.structure.StructureKind;
import cn.blockforge.generated.arcanesingularity.structure.StructureSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class StargateControllerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public StargateControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(ArcaneProperties.FORMED, false)
                .setValue(ArcaneProperties.ACTIVE, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ArcaneProperties.FORMED, ArcaneProperties.ACTIVE, FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.is(ModItems.FORMATION_TERMINAL.get())) {
            if (!level.isClientSide) {
                StructureBuilder.build(player, pos, StructureKind.STARGATE);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof StargateControllerBlockEntity be) {
                be.tryForm(serverPlayer);
                NetworkHooks.openScreen(serverPlayer, be, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof StargateControllerBlockEntity be)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        boolean alongX = facing.getAxis() == Direction.Axis.X;
        double cx = pos.getX() + 0.5D;
        // 门心高度直接读蓝图常量：门环抬高、控制器抬高之后粒子自动跟着走
        double cy = pos.getY() + StargateRingAssembly.PORTAL_CENTER_Y;
        double cz = pos.getZ() + 0.5D;
        double time = level.getGameTime();
        float open = be.openProgress(time);

        // 张开 / 闭合过程：门框上的电弧被吸向门心
        boolean animating = be.isOpen() ? open < 1.0F : open > 0.01F;
        if (animating) {
            for (int i = 0; i < 3; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = 7.0D - 4.2D * open;
                double u = Math.cos(angle) * radius;
                double v = Math.sin(angle) * radius;
                double pull = 0.06D + 0.10D * open;
                if (alongX) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, cx, cy + v, cz + u,
                            0.0D, -v * pull, -u * pull);
                } else {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, cx + u, cy + v, cz,
                            -u * pull, -v * pull, 0.0D);
                }
            }
            if (open < 0.18F && random.nextFloat() < 0.4F) {
                level.addParticle(ParticleTypes.EXPLOSION, cx, cy, cz, 0.0D, 0.0D, 0.0D);
            }
            if (random.nextFloat() < 0.3F) {
                level.addParticle(ParticleTypes.END_ROD, cx, cy, cz,
                        (random.nextDouble() - 0.5D) * 0.3D, (random.nextDouble() - 0.5D) * 0.3D,
                        (random.nextDouble() - 0.5D) * 0.3D);
            }
        }

        if (be.isOpen()) {
            // 门面里的能量：一边顺着漩涡切向加速，一边被吸向门框
            for (int i = 0; i < 5; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = 0.8D + random.nextDouble() * 5.2D;
                double u = Math.cos(angle) * radius;
                double v = Math.sin(angle) * radius;
                double swirl = 0.10D + 0.10D * (radius / 6.0D);
                double tu = -Math.sin(angle) * swirl + Math.cos(angle) * 0.05D;
                double tv = Math.cos(angle) * swirl + Math.sin(angle) * 0.05D;
                if (alongX) {
                    level.addParticle(ParticleTypes.PORTAL, cx, cy + v, cz + u, 0.0D, tv, tu);
                } else {
                    level.addParticle(ParticleTypes.PORTAL, cx + u, cy + v, cz, tu, tv, 0.0D);
                }
            }
            // 门框内缘甩出去的电弧：门转得越快，火花被甩得越远
            for (int i = 0; i < 2; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = 6.5D + random.nextDouble() * 0.45D;
                double u = Math.cos(angle) * radius;
                double v = Math.sin(angle) * radius;
                double tu = -Math.sin(angle) * 0.22D;
                double tv = Math.cos(angle) * 0.22D;
                if (alongX) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, cx, cy + v, cz + u, 0.0D, tv, tu);
                } else {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, cx + u, cy + v, cz, tu, tv, 0.0D);
                }
            }
            if (random.nextFloat() < 0.35F) {
                level.addParticle(ParticleTypes.END_ROD, cx, cy + 3.0D, cz, 0.0D, 0.08D, 0.0D);
            }
            if (random.nextFloat() < 0.16F) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = 5.0D + random.nextDouble() * 1.6D;
                double u = Math.cos(angle) * radius;
                double v = Math.sin(angle) * radius;
                if (alongX) {
                    level.addParticle(ParticleTypes.DRAGON_BREATH, cx, cy + v, cz + u, 0.0D, 0.0D, 0.0D);
                } else {
                    level.addParticle(ParticleTypes.DRAGON_BREATH, cx + u, cy + v, cz, 0.0D, 0.0D, 0.0D);
                }
            }
        } else if (be.isFormed() && random.nextFloat() < 0.2F) {
            // 关闭但已成型：门心留一点待机能量
            double u = (random.nextDouble() - 0.5D) * 3.0D;
            double v = (random.nextDouble() - 0.5D) * 3.0D;
            if (alongX) {
                level.addParticle(ParticleTypes.ENCHANT, cx, cy + v, cz + u, 0.0D, 0.03D, 0.0D);
            } else {
                level.addParticle(ParticleTypes.ENCHANT, cx + u, cy + v, cz, 0.0D, 0.03D, 0.0D);
            }
        }

        if (be.getProjectionTicks() > 0 && random.nextFloat() < 0.3F) {
            level.addParticle(ParticleTypes.ENCHANT,
                    cx + (random.nextDouble() - 0.5D) * 14.0D,
                    pos.getY() + 1.0D + random.nextDouble() * 15.0D,
                    cz + (random.nextDouble() - 0.5D) * 14.0D, 0.0D, 0.1D, 0.0D);
        }
    }

    /** 放下星门控制器：把整座星门覆盖到的区块全部强加载，门环和传送面在玩家走远后照样运转。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        int chunks = ChunkKeeper.keep(level, pos, StructureKind.STARGATE, state.getValue(FACING));
        if (placer instanceof ServerPlayer player && chunks > 0) {
            player.displayClientMessage(Component.translatable(
                    "message.arcane_singularity.chunks_kept", chunks), true);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            if (state.getValue(ArcaneProperties.FORMED)) {
                StructureSupport.onStructureBlockBroken(level, pos);
            }
            // 真正解除强加载放在 onRemove：方块确实移除之后才撤销，拆到一半不会先把区块卸载掉
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.arcane_singularity.chunks_released"), true);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            ProjectionService.clear(level, pos, StructureKind.STARGATE, state.getValue(StargateControllerBlock.FACING));
            if (state.getValue(ArcaneProperties.FORMED)) {
                StructureSupport.cleanupOrphanParts(level, pos, StructureKind.STARGATE);
            }
            // 方块确实被移除（拆掉、炸掉、被指令替换）之后才撤销强加载，拆到一半不会先卸载区块
            ChunkKeeper.release(level, pos, StructureKind.STARGATE, state.getValue(StargateControllerBlock.FACING));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StargateControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.STARGATE_CONTROLLER.get(),
                StargateControllerBlockEntity::serverTick);
    }
}
