package cn.blockforge.generated.arcanesingularity.block;

import cn.blockforge.generated.arcanesingularity.blockentity.SingularityControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.registry.ModBlockEntities;
import cn.blockforge.generated.arcanesingularity.registry.ModItems;
import cn.blockforge.generated.arcanesingularity.structure.ArmillaryAssembly;
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

public class SingularityControllerBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public SingularityControllerBlock(Properties properties) {
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
                StructureBuilder.build(player, pos, StructureKind.SINGULARITY);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof SingularityControllerBlockEntity be) {
                be.tryForm(serverPlayer);
                NetworkHooks.openScreen(serverPlayer, be, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof SingularityControllerBlockEntity be)) {
            return;
        }
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + ArmillaryAssembly.CENTER_Y;
        double cz = pos.getZ() + 0.5D;
        int ticks = be.getAnimationTicks();
        if (be.isFormed()) {
            if (be.isStable()) {
                // 吸积盘外溢的火星
                for (int i = 0; i < 2; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double radius = 1.8D + random.nextDouble() * 1.8D;
                    double x = cx + Math.cos(angle) * radius;
                    double z = cz + Math.sin(angle) * radius;
                    double y = cy + (random.nextDouble() - 0.5D) * 2.6D;
                    level.addParticle(ParticleTypes.PORTAL, x, y, z,
                            -Math.cos(angle) * 0.12D, (random.nextDouble() - 0.5D) * 0.08D,
                            -Math.sin(angle) * 0.12D);
                }
                if (random.nextFloat() < 0.35F) {
                    level.addParticle(ParticleTypes.END_ROD, cx, cy + 2.2D, cz,
                            (random.nextDouble() - 0.5D) * 0.25D, 0.05D,
                            (random.nextDouble() - 0.5D) * 0.25D);
                }
                // 稳定器绕环扫过时甩出的激光余辉
                if (random.nextFloat() < 0.5F) {
                    int pod = random.nextInt(ArmillaryAssembly.PODS);
                    double angle = Math.toRadians(ArmillaryAssembly.podAngleDeg(
                            be.rotorAngleAt(level.getGameTime() + random.nextDouble()), pod));
                    double px = cx + Math.cos(angle) * ArmillaryAssembly.ROTOR_RADIUS;
                    double pz = cz + Math.sin(angle) * ArmillaryAssembly.ROTOR_RADIUS;
                    level.addParticle(ParticleTypes.END_ROD, px, cy, pz,
                            (cx - px) * 0.06D, (random.nextDouble() - 0.5D) * 0.03D, (cz - pz) * 0.06D);
                }
            } else if (ticks > 0) {
                // 三个阶段越往后吸得越急
                double phaseProgress = Math.min(1.0D, ticks / (double) SingularityControllerBlockEntity.RITUAL_LENGTH);
                double pull = 0.10D + 0.5D * phaseProgress;
                for (int i = 0; i < 4; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double radius = 6.5D + random.nextDouble() * 5.0D;
                    double x = cx + Math.cos(angle) * radius;
                    double z = cz + Math.sin(angle) * radius;
                    double y = cy + (random.nextDouble() - 0.5D) * 9.0D;
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                            (cx - x) * pull * 0.12D, (cy - y) * pull * 0.12D, (cz - z) * pull * 0.12D);
                    if (random.nextFloat() < 0.25F) {
                        level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0.0D, 0.0D, 0.0D);
                    }
                    if (random.nextFloat() < 0.18F) {
                        level.addParticle(ParticleTypes.DRAGON_BREATH, x, y, z,
                                (cx - x) * pull * 0.08D, (cy - y) * pull * 0.08D, (cz - z) * pull * 0.08D);
                    }
                }
                // 四个稳定器炮口的充能火花
                for (int pod = 0; pod < ArmillaryAssembly.PODS; pod++) {
                    if (random.nextFloat() > 0.35F) {
                        continue;
                    }
                    double angle = Math.toRadians(ArmillaryAssembly.podAngleDeg(
                            be.rotorAngleAt(level.getGameTime() + random.nextDouble()), pod));
                    double px = cx + Math.cos(angle) * (ArmillaryAssembly.ROTOR_RADIUS - 0.6D);
                    double pz = cz + Math.sin(angle) * (ArmillaryAssembly.ROTOR_RADIUS - 0.6D);
                    level.addParticle(ParticleTypes.END_ROD, px, cy, pz,
                            (cx - px) * 0.06D, 0.01D, (cz - pz) * 0.06D);
                }
                // 阶段切换的爆发
                boolean secondPhase = ticks >= 198 && ticks <= 204;
                boolean thirdPhase = ticks >= 398 && ticks <= 404;
                boolean ignition = ticks >= 594;
                if ((secondPhase || thirdPhase || ignition) && random.nextFloat() < 0.35F) {
                    level.addParticle(ParticleTypes.EXPLOSION, cx + (random.nextDouble() - 0.5D) * 3.0D,
                            cy + (random.nextDouble() - 0.5D) * 3.0D,
                            cz + (random.nextDouble() - 0.5D) * 3.0D, 0.0D, 0.0D, 0.0D);
                }
            }
            if (random.nextFloat() < 0.2F) {
                level.addParticle(ParticleTypes.END_ROD, cx, pos.getY() + 2.5D, cz,
                        (random.nextDouble() - 0.5D) * 0.06D, 0.1D, (random.nextDouble() - 0.5D) * 0.06D);
            }
        }
        if (be.getProjectionTicks() > 0 && random.nextFloat() < 0.35F) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 5.0D + random.nextDouble() * 8.0D;
            level.addParticle(ParticleTypes.ENCHANT,
                    cx + Math.cos(angle) * radius, pos.getY() + 1.0D + random.nextDouble() * 16.0D,
                    cz + Math.sin(angle) * radius, 0.0D, 0.1D, 0.0D);
        }
    }

    /** 放下奇点摇篮：把整座浑天仪覆盖到的区块全部强加载，玩家走远后黑洞和转环也照常运转。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        int chunks = ChunkKeeper.keep(level, pos, StructureKind.SINGULARITY, Direction.NORTH);
        if (placer instanceof ServerPlayer player && chunks > 0) {
            player.displayClientMessage(Component.translatable(
                    "message.arcane_singularity.chunks_kept", chunks), true);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof SingularityControllerBlockEntity be) {
                be.dropContents();
            }
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
            if (level.getBlockEntity(pos) instanceof SingularityControllerBlockEntity be) {
                be.dropContents();
            }
            ProjectionService.clear(level, pos, StructureKind.SINGULARITY, Direction.NORTH);
            if (state.getValue(ArcaneProperties.FORMED)) {
                StructureSupport.cleanupOrphanParts(level, pos, StructureKind.SINGULARITY);
            }
            // 方块确实被移除（拆掉、炸掉、被指令替换）之后才撤销强加载，拆到一半不会先卸载区块
            ChunkKeeper.release(level, pos, StructureKind.SINGULARITY, Direction.NORTH);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SingularityControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.SINGULARITY_CONTROLLER.get(),
                SingularityControllerBlockEntity::serverTick);
    }
}
