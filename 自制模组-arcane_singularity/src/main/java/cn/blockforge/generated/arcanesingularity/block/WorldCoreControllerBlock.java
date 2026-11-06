package cn.blockforge.generated.arcanesingularity.block;

import cn.blockforge.generated.arcanesingularity.blockentity.WorldCoreControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.registry.ModBlockEntities;
import cn.blockforge.generated.arcanesingularity.registry.ModItems;
import cn.blockforge.generated.arcanesingularity.structure.ArcaneStructure;
import cn.blockforge.generated.arcanesingularity.structure.ChunkKeeper;
import cn.blockforge.generated.arcanesingularity.structure.ProjectionService;
import cn.blockforge.generated.arcanesingularity.structure.StructureBuilder;
import cn.blockforge.generated.arcanesingularity.structure.StructureKind;
import cn.blockforge.generated.arcanesingularity.structure.StructureSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 世界之核控制器：整个仪式场正中央那台机器。
 *
 * <p>整座结构以它为轴（没有朝向），三个专属接口、四角巨柱、中心塔的坐标都由蓝图固定。
 * 放下时会把整座结构覆盖的区块强加载；界面打开后就是那一套「命令方块 + 结构状态」的双栏布局。</p>
 */
public class WorldCoreControllerBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public WorldCoreControllerBlock(Properties properties) {
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
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.is(ModItems.FORMATION_TERMINAL.get())) {
            if (!level.isClientSide) {
                StructureBuilder.build(player, pos, StructureKind.WORLD_CORE);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof WorldCoreControllerBlockEntity be) {
                be.tryForm(serverPlayer);
                NetworkHooks.openScreen(serverPlayer, be, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof WorldCoreControllerBlockEntity be)) {
            return;
        }
        double cx = pos.getX() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        double time = level.getGameTime();
        float charge = be.chargeAt(time);
        float gather = be.gatherAt(time);
        float glyph = be.glyphOpenAt(time);
        float converge = be.convergeAt(time);
        float burst = be.coreBurstAt(time);

        if (!be.isFormed()) {
            if (be.getProjectionTicks() > 0 && random.nextFloat() < 0.3F) {
                level.addParticle(ParticleTypes.ENCHANT,
                        cx + (random.nextDouble() - 0.5D) * 28.0D,
                        pos.getY() + 1.0D + random.nextDouble() * 12.0D,
                        cz + (random.nextDouble() - 0.5D) * 28.0D, 0.0D, 0.1D, 0.0D);
            }
            return;
        }
        if (!be.isActive()) {
            return;
        }

        double axisX = cx + ArcaneStructure.WORLD_CORE_AXIS_X;
        double gatherY = pos.getY() + ArcaneStructure.WORLD_CORE_GATHER_Y;
        double focusY = pos.getY() + ArcaneStructure.WORLD_CORE_FOCUS_Y;

        // 三台接口激光器充能：每个接口上方收拢能量
        if (charge > 0.03F) {
            for (BlockPos offset : ArcaneStructure.worldCoreInterfaceOffsets()) {
                double ex = pos.getX() + 0.5D + offset.getX();
                double ey = pos.getY() + ArcaneStructure.WORLD_CORE_EMITTER_MUZZLE_Y;
                double ez = pos.getZ() + 0.5D + offset.getZ();
                for (int i = 0; i < 2; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double radius = (1.8D * (1.0D - charge) + 0.25D) * (0.5D + random.nextDouble());
                    level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                            ex + Math.cos(angle) * radius, ey + random.nextDouble() * 1.4D,
                            ez + Math.sin(angle) * radius,
                            -Math.cos(angle) * 0.14D, 0.02D, -Math.sin(angle) * 0.14D);
                }
                level.addParticle(ParticleTypes.END_ROD, ex, ey + random.nextDouble() * 0.8D, ez,
                        (random.nextDouble() - 0.5D) * 0.06D, 0.12D, (random.nextDouble() - 0.5D) * 0.06D);
            }
        }

        // 天上汇聚点：三道光打上去之后一直有能量外溢
        if (gather > 0.5F && random.nextFloat() < 0.8F) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 1.6D + random.nextDouble() * 3.4D;
            level.addParticle(ParticleTypes.END_ROD,
                    axisX + Math.cos(angle) * radius, gatherY + (random.nextDouble() - 0.5D) * 1.2D,
                    cz + Math.sin(angle) * radius,
                    -Math.cos(angle) * 0.12D, (random.nextDouble() - 0.5D) * 0.06D,
                    -Math.sin(angle) * 0.12D);
            if (random.nextFloat() < 0.35F) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        axisX + (random.nextDouble() - 0.5D) * 10.0D, gatherY + (random.nextDouble() - 0.5D) * 2.0D,
                        cz + (random.nextDouble() - 0.5D) * 10.0D, 0.0D, 0.02D, 0.0D);
            }
        }

        // 魔法阵散落的符文光点
        if (glyph > 0.4F && random.nextFloat() < 0.5F) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 4.0D + random.nextDouble() * 13.0D;
            level.addParticle(ParticleTypes.ENCHANT,
                    axisX + Math.cos(angle) * radius, gatherY + (random.nextDouble() - 0.5D) * 0.8D,
                    cz + Math.sin(angle) * radius, 0.0D, -0.02D, 0.0D);
        }

        // 对撞点：汇聚成型时冒一点细碎的电光（只是粒子，不是原来那道雷暴）
        if (converge > 0.85F && random.nextFloat() < 0.10F) {
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        axisX + (random.nextDouble() - 0.5D) * 1.4D,
                        focusY + (random.nextDouble() - 0.5D) * 1.2D,
                        cz + (random.nextDouble() - 0.5D) * 1.4D,
                        (random.nextDouble() - 0.5D) * 0.12D, (random.nextDouble() - 0.5D) * 0.12D,
                        (random.nextDouble() - 0.5D) * 0.12D);
            }
        }
        if (burst > 0.1F && random.nextFloat() < 0.25F) {
            level.addParticle(ParticleTypes.PORTAL,
                    cx + (random.nextDouble() - 0.5D) * 4.0D,
                    focusY + (random.nextDouble() - 0.5D) * 4.0D,
                    cz + (random.nextDouble() - 0.5D) * 4.0D,
                    (random.nextDouble() - 0.5D) * 0.2D, (random.nextDouble() - 0.5D) * 0.2D,
                    (random.nextDouble() - 0.5D) * 0.2D);
        }
        if (converge > 0.5F && random.nextFloat() < 0.4F) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, axisX, focusY, cz, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        int chunks = ChunkKeeper.keep(level, pos, StructureKind.WORLD_CORE, Direction.NORTH);
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
            if (level.getBlockEntity(pos) instanceof WorldCoreControllerBlockEntity be) {
                be.collapseStructure(false);
                be.dropContents();
            }
            ProjectionService.clear(level, pos, StructureKind.WORLD_CORE, Direction.NORTH);
            if (state.getValue(ArcaneProperties.FORMED)) {
                StructureSupport.cleanupOrphanParts(level, pos, StructureKind.WORLD_CORE);
            }
            ChunkKeeper.release(level, pos, StructureKind.WORLD_CORE, Direction.NORTH);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorldCoreControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.WORLD_CORE_CONTROLLER.get(),
                WorldCoreControllerBlockEntity::serverTick);
    }
}
