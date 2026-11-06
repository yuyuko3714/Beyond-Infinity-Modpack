package cn.blockforge.generated.arcanesingularity.block;

import cn.blockforge.generated.arcanesingularity.blockentity.TimeBeaconControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.data.TimeBeaconData;
import cn.blockforge.generated.arcanesingularity.registry.ModBlockEntities;
import cn.blockforge.generated.arcanesingularity.registry.ModItems;
import cn.blockforge.generated.arcanesingularity.structure.ArcaneStructure;
import cn.blockforge.generated.arcanesingularity.structure.TimeBeaconAssembly;
import cn.blockforge.generated.arcanesingularity.structure.ChunkKeeper;
import cn.blockforge.generated.arcanesingularity.structure.ProjectionService;
import cn.blockforge.generated.arcanesingularity.structure.StructureBuilder;
import cn.blockforge.generated.arcanesingularity.structure.StructureKind;
import cn.blockforge.generated.arcanesingularity.structure.StructureSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * 时间信标控制器（塔基中央那台机器）。
 *
 * <p>结构和浑天仪 / 星门一样是「放控制器 → 成型终端搭 → 界面里开机」，差别在于它是旋转对称的
 * （没有朝向），所以整座塔永远以控制器为轴，方块状态里不含 {@code facing}。</p>
 *
 * <p>放下时就会在 {@link TimeBeaconData} 里占住这个维度：同一维度只允许存在一台时间信标，
 * 已经有别的一台时这台会就地取消并退还给玩家。</p>
 */
public class TimeBeaconControllerBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public TimeBeaconControllerBlock(Properties properties) {
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
                StructureBuilder.build(player, pos, StructureKind.TIMEBEACON);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof TimeBeaconControllerBlockEntity be) {
                be.tryForm(serverPlayer);
                NetworkHooks.openScreen(serverPlayer, be, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof TimeBeaconControllerBlockEntity be)) {
            return;
        }
        // 粒子全部以塔轴为中心：塔轴相对控制器有 BEACON_AXIS_* 的偏移
        double cx = pos.getX() + 0.5D + ArcaneStructure.BEACON_AXIS_X;
        double cz = pos.getZ() + 0.5D + ArcaneStructure.BEACON_AXIS_Z;
        double time = level.getGameTime();
        double baseY = pos.getY() + ArcaneStructure.BEACON_AXIS_Y;
        double muzzleY = baseY + ArcaneStructure.BEACON_MUZZLE_Y;
        // 顶部两格时序炮管整体在浮动，粒子跟着它走（和渲染器、激光特效同一个值）
        double topMuzzleY = baseY + ArcaneStructure.BEACON_TOP_MUZZLE_Y
                + TimeBeaconAssembly.topBarrelBob(time);
        float ramp = be.spinRampAt(time);
        float charge = be.chargeAt(time);
        float beam = be.beamPowerAt(time);

        if (be.isFormed() && be.isActive()) {
            // 两圈汇聚环扫过时甩出的火花：转过哪一格，哪一格就冒一次
            for (int ring = 0; ring < 2; ring++) {
                double angle = Math.toRadians(be.ringAngleAt(time, ring));
                double radius = ring == 0 ? 6.0D : 5.0D;
                for (int i = 0; i < 2; i++) {
                    double jitter = angle + (random.nextDouble() - 0.5D) * 0.6D;
                    double y = baseY + (ring == 0
                            ? ArcaneStructure.BEACON_RING_A_Y : ArcaneStructure.BEACON_RING_B_Y);
                    level.addParticle(ParticleTypes.END_ROD,
                            cx + Math.cos(jitter) * radius, y + (random.nextDouble() - 0.5D) * 0.4D,
                            cz + Math.sin(jitter) * radius,
                            -Math.cos(jitter) * 0.08D, 0.0D, -Math.sin(jitter) * 0.08D);
                }
            }
            // 汇聚环越快，环绕的电弧越密
            if (random.nextFloat() < 0.25F + 0.5F * ramp) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = 6.0D + (random.nextDouble() - 0.5D) * 0.8D;
                double y = baseY + (random.nextBoolean()
                        ? ArcaneStructure.BEACON_RING_A_Y : ArcaneStructure.BEACON_RING_B_Y);
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        cx + Math.cos(angle) * radius, y, cz + Math.sin(angle) * radius, 0.0D, 0.02D, 0.0D);
            }
        }

        // 炮口聚能：能量往枪口收
        if (charge > 0.02F) {
            for (int i = 0; i < 3; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = 2.4D * (1.0D - charge) + 0.3D + random.nextDouble() * 1.6D;
                double dy = -2.0D + random.nextDouble() * 2.0D;
                double px = cx + Math.cos(angle) * radius;
                double pz = cz + Math.sin(angle) * radius;
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, px, muzzleY + dy, pz,
                        -Math.cos(angle) * 0.16D, (0.0D - dy) * 0.16D, -Math.sin(angle) * 0.16D);
            }
            if (random.nextFloat() < charge * 0.6F) {
                // 第一段：主炮口往上打
                level.addParticle(ParticleTypes.END_ROD, cx, muzzleY + random.nextDouble() * 2.0D, cz,
                        (random.nextDouble() - 0.5D) * 0.1D, 0.12D, (random.nextDouble() - 0.5D) * 0.1D);
                // 第二段：顶部时序炮管口二次聚能
                if (be.isRunning()) {
                    level.addParticle(ParticleTypes.END_ROD, cx,
                            topMuzzleY + random.nextDouble() * 1.6D, cz,
                            (random.nextDouble() - 0.5D) * 0.12D, 0.18D,
                            (random.nextDouble() - 0.5D) * 0.12D);
                }
            }
        }

        // 激光射出之后：沿光柱往上飘的余辉
        if (beam > 0.05F && random.nextFloat() < 0.75F) {
            double height = 4.0D + random.nextDouble() * 46.0D;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 1.4D + random.nextDouble() * 2.2D;
            level.addParticle(ParticleTypes.END_ROD,
                    cx + Math.cos(angle) * radius, topMuzzleY + height, cz + Math.sin(angle) * radius,
                    (random.nextDouble() - 0.5D) * 0.05D, 0.35D, (random.nextDouble() - 0.5D) * 0.05D);
        }

        if (be.getProjectionTicks() > 0 && random.nextFloat() < 0.3F) {
            level.addParticle(ParticleTypes.ENCHANT,
                    cx + (random.nextDouble() - 0.5D) * 22.0D,
                    pos.getY() + 1.0D + random.nextDouble() * 21.0D,
                    cz + (random.nextDouble() - 0.5D) * 22.0D, 0.0D, 0.1D, 0.0D);
        }
    }

    /**
     * 放下控制器：先占住这个维度（同一维度只允许一台时间信标），占不到就当场取消并把方块还给玩家；
     * 占到了就把整座塔覆盖到的区块强加载。
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        TimeBeaconData data = TimeBeaconData.get(level.getServer());
        if (!data.claim(level, pos)) {
            // 这个维度已经有另一台时间信标了：不放下，原样退还给玩家
            level.removeBlock(pos, false);
            if (placer instanceof ServerPlayer player) {
                if (!player.isCreative()) {
                    ItemStack refund = new ItemStack(ModItems.TIME_BEACON_CONTROLLER.get());
                    if (!player.getInventory().add(refund)) {
                        player.drop(refund, false);
                    }
                }
                player.displayClientMessage(
                        Component.translatable("message.arcane_singularity.time_beacon_conflict"), true);
                level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1.0F, 0.9F);
            }
            return;
        }
        int chunks = ChunkKeeper.keep(level, pos, StructureKind.TIMEBEACON, Direction.NORTH);
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
            if (level.getBlockEntity(pos) instanceof TimeBeaconControllerBlockEntity be) {
                be.collapseStructure(false);
            }
            ProjectionService.clear(level, pos, StructureKind.TIMEBEACON, Direction.NORTH);
            if (state.getValue(ArcaneProperties.FORMED)) {
                StructureSupport.cleanupOrphanParts(level, pos, StructureKind.TIMEBEACON);
            }
            // 方块确实不在之后才释放维度登记与强加载
            TimeBeaconData.get(level.getServer()).release(level, pos);
            ChunkKeeper.release(level, pos, StructureKind.TIMEBEACON, Direction.NORTH);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TimeBeaconControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.TIME_BEACON_CONTROLLER.get(),
                TimeBeaconControllerBlockEntity::serverTick);
    }
}
