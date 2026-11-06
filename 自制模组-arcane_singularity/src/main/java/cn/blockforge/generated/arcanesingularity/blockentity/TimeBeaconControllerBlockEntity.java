package cn.blockforge.generated.arcanesingularity.blockentity;

import cn.blockforge.generated.arcanesingularity.block.ArcaneProperties;
import cn.blockforge.generated.arcanesingularity.block.TimeBeaconControllerBlock;
import cn.blockforge.generated.arcanesingularity.data.TimeBeaconData;
import cn.blockforge.generated.arcanesingularity.energy.ArcaneEnergyNetwork;
import cn.blockforge.generated.arcanesingularity.entity.TimeBeaconRingPartEntity;
import cn.blockforge.generated.arcanesingularity.menu.TimeBeaconMenu;
import cn.blockforge.generated.arcanesingularity.redstone.RedstoneMode;
import cn.blockforge.generated.arcanesingularity.registry.ModBlockEntities;
import cn.blockforge.generated.arcanesingularity.registry.ModBlocks;
import cn.blockforge.generated.arcanesingularity.registry.ModEntities;
import cn.blockforge.generated.arcanesingularity.structure.ArcaneStructure;
import cn.blockforge.generated.arcanesingularity.structure.ArmillaryAssembly;
import cn.blockforge.generated.arcanesingularity.structure.ChunkKeeper;
import cn.blockforge.generated.arcanesingularity.structure.ProjectionService;
import cn.blockforge.generated.arcanesingularity.structure.StructureKind;
import cn.blockforge.generated.arcanesingularity.structure.TimeBeaconAssembly;
import cn.blockforge.generated.arcanesingularity.time.TimeMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间信标控制器。
 *
 * <p>它把黑洞发生器送来的能量变成一道朝天的激光，并用这道激光改变<b>整个维度</b>的时间流速：
 * 减速、加速、正常、停止四种模式，减速 / 加速各自还有五档倍率，都能在界面里调。</p>
 *
 * <p>开机节奏（一共一分钟）：</p>
 * <ol>
 *     <li>0~40 秒：炮管周围两圈能量汇聚环脱离底座，由慢到快转起来，颜色从冷白慢慢烧成该模式的激光色；</li>
 *     <li>33~60 秒：炮口开始聚能，光环往枪口收，能量丝向内塌；</li>
 *     <li>60 秒：激光射出，时间倍率立刻生效。</li>
 * </ol>
 * <p>关机反过来：激光先收、环再刹停，最后方块才恢复。</p>
 *
 * <p>同一维度只允许存在一台时间信标：登记在 {@link TimeBeaconData} 里，放下控制器时就占位，
 * 拆掉控制器才释放。</p>
 */
public class TimeBeaconControllerBlockEntity extends BlockEntity implements MenuProvider {
    /** 充能（开机）全过程长度：一分钟。 */
    public static final int WARMUP_LENGTH = 1200;
    /** 关机动画长度：激光收束 + 环刹停。 */
    public static final int SHUTDOWN_LENGTH = 120;
    /** 强加载自愈间隔（tick）。 */
    private static final int CHUNK_KEEP_INTERVAL = 200;
    /** 炮口开始聚能的时刻（相对开机）：前面那段时间留给汇聚环加速。 */
    private static final int CHARGE_START = 660;

    public static final int DATA_COUNT = 12;
    public static final int INDEX_MODE = 0;
    public static final int INDEX_SPEED = 1;
    public static final int INDEX_FORMED = 2;
    public static final int INDEX_POWERED = 3;
    public static final int INDEX_RUNNING = 4;
    /** 充能进度 0..1000（界面进度条用，避免容器数据的 int 溢出观感）。 */
    public static final int INDEX_WARMUP = 5;
    public static final int INDEX_PHASE = 6;
    public static final int INDEX_STATUS = 7;
    public static final int INDEX_REDSTONE_MODE = 8;
    public static final int INDEX_REDSTONE_SIGNAL = 9;
    public static final int INDEX_PROJECTION = 10;
    /** 当前实际倍率 ×100（停止 = 0，正常 = 100，×8 = 800）。 */
    public static final int INDEX_RATE = 11;

    private boolean formed;
    private boolean powered;
    private boolean running;
    /** 开机已经走了多少 tick（未开机为 0）。 */
    private int warmupTicks;
    /** 开机时刻；为 0 表示没有在充能。 */
    private long warmupStartTick;
    /** 关机动画起始时刻；为 0 表示没有在关机。 */
    private long shutdownStamp;
    /** 关机瞬间的累计转角与转速占比，用来把它稳稳刹回原位、颜色淡回去。 */
    private double shutdownStartRawAngle;
    private float shutdownStartRamp;
    private TimeMode mode = TimeMode.NORMAL;
    private int speedLevel = 2;
    private RedstoneMode redstoneMode = RedstoneMode.AUTO;
    private int redstoneSignal;
    private int projectionTicks;
    private int status;
    private int validateCooldown;
    private int powerCooldown;
    private int syncCooldown;
    private int partsCooldown;
    private int chunkKeepCooldown;
    /** 汇聚环是否已经交给旋转实体（方块换成空模型 + 无碰撞）。 */
    private boolean ringEngaged;

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case INDEX_MODE -> mode.id();
                case INDEX_SPEED -> speedLevel;
                case INDEX_FORMED -> formed ? 1 : 0;
                case INDEX_POWERED -> powered ? 1 : 0;
                case INDEX_RUNNING -> running ? 1 : 0;
                case INDEX_WARMUP -> Math.min(1000, warmupTicks * 1000 / WARMUP_LENGTH);
                case INDEX_PHASE -> phase();
                case INDEX_STATUS -> status;
                case INDEX_REDSTONE_MODE -> redstoneMode.id();
                case INDEX_REDSTONE_SIGNAL -> redstoneSignal;
                case INDEX_PROJECTION -> projectionTicks;
                case INDEX_RATE -> (int) Math.round(currentRate() * 100.0D);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public TimeBeaconControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TIME_BEACON_CONTROLLER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TimeBeaconControllerBlockEntity be) {
        be.tickServer();
    }

    private void tickServer() {
        if (level == null || level.isClientSide) {
            return;
        }
        // 整座塔覆盖的区块保持强加载：激光、转环、时间倍率在玩家走远后都照常运转
        if (--chunkKeepCooldown <= 0) {
            chunkKeepCooldown = CHUNK_KEEP_INTERVAL;
            ChunkKeeper.keep(level, worldPosition, StructureKind.TIMEBEACON, Direction.NORTH);
        }
        if (projectionTicks > 0) {
            projectionTicks--;
            if (projectionTicks == 0) {
                ProjectionService.clear(level, worldPosition, StructureKind.TIMEBEACON, Direction.NORTH);
                syncBlock();
            } else if (projectionTicks % 10 == 0) {
                syncBlock();
            }
        }

        sampleRedstone();

        // 存档里可能停在一个半截状态（比如关机动画播到一半就退出）：环还处于「已实体化」但机器已经停了，
        // 这里把方块恢复回去，免得留下两圈悬空的环。
        if (ringEngaged && !isActive()) {
            applyRingMode(false);
            partsCooldown = 0;
        }

        if (!formed) {
            if (--validateCooldown <= 0) {
                validateCooldown = 40;
                tryForm(null);
            }
            return;
        }
        if (--validateCooldown <= 0) {
            validateCooldown = 20;
            if (!validateStructure()) {
                collapseStructure(true);
                return;
            }
        }

        // 关机动画播完：环刹回原位，这时才把方块恢复、回收碰撞实体
        if (shutdownStamp > 0L && level.getGameTime() - shutdownStamp >= SHUTDOWN_LENGTH) {
            shutdownStamp = 0L;
            warmupStartTick = 0L;
            warmupTicks = 0;
            shutdownStartRawAngle = 0.0D;
            shutdownStartRamp = 0.0F;
            applyRingMode(false);
            updateActiveBlock();
            partsCooldown = 0;
            level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 1.4F, 0.7F);
            syncBlock();
        }

        if (--powerCooldown <= 0) {
            powerCooldown = 20;
            refreshPower();
        }

        if (warmupStartTick > 0L && shutdownStamp == 0L) {
            int ticks = (int) Math.min(Integer.MAX_VALUE, level.getGameTime() - warmupStartTick);
            if (ticks != warmupTicks) {
                warmupTicks = ticks;
                if (ticks % 5 == 0) {
                    syncBlock();
                }
            }
            if (!running && ticks >= WARMUP_LENGTH) {
                running = true;
                warmupTicks = WARMUP_LENGTH;
                updateActiveBlock();
                level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE,
                        SoundSource.BLOCKS, 2.2F, 0.5F);
                level.playSound(null, worldPosition, SoundEvents.END_PORTAL_SPAWN,
                        SoundSource.BLOCKS, 1.6F, 0.7F);
                syncBlock();
            }
        }

        if (--syncCooldown <= 0) {
            syncCooldown = 20;
            updateActiveBlock();
            syncBlock();
        }
        if (--partsCooldown <= 0) {
            partsCooldown = 20;
            syncRingParts();
        }
    }

    /** 读取任意一面的红石信号，上升沿时自动把接口从「自动」切到「高电平运行」。 */
    private void sampleRedstone() {
        if (level == null || level.isClientSide) {
            return;
        }
        int signal = level.getBestNeighborSignal(worldPosition);
        if (signal == redstoneSignal) {
            return;
        }
        int previous = redstoneSignal;
        redstoneSignal = signal;
        if (previous == 0 && signal > 0 && redstoneMode == RedstoneMode.AUTO) {
            redstoneMode = RedstoneMode.SIGNAL_ON;
            syncBlock();
            for (Player player : level.players()) {
                if (player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D) < 48.0D * 48.0D) {
                    player.displayClientMessage(
                            Component.translatable("message.arcane_singularity.redstone_linked"), true);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 供能与启停
    // ------------------------------------------------------------------

    /** 扫描能量网络，并按「供能 + 红石 + 成型」决定开机还是关机。 */
    private void refreshPower() {
        if (level == null || level.isClientSide) {
            return;
        }
        List<BlockPos> ports = new ArrayList<>();
        for (BlockPos offset : ArcaneStructure.offsetsOf(StructureKind.TIMEBEACON, Direction.NORTH,
                ArcaneStructure.PartType.PORT)) {
            ports.add(worldPosition.offset(offset));
        }
        ArcaneEnergyNetwork.Result result = ArcaneEnergyNetwork.scan(level, ports,
                running ? ArcaneEnergyNetwork.OPEN_DEMAND : ArcaneEnergyNetwork.IDLE_DEMAND);
        powered = result.choose(null) != null;

        boolean want = formed && powered && redstoneMode.permits(redstoneSignal) && !dimensionConflict();
        if (want && !running && warmupStartTick <= 0L && shutdownStamp == 0L) {
            startWarmup();
        } else if (!want && (running || warmupStartTick > 0L)) {
            beginShutdown();
        }
        updateStatus();
        syncBlock();
    }

    private void startWarmup() {
        if (level == null || level.isClientSide) {
            return;
        }
        warmupStartTick = level.getGameTime();
        warmupTicks = 0;
        shutdownStamp = 0L;
        shutdownStartRawAngle = 0.0D;
        shutdownStartRamp = 0.0F;
        running = false;
        applyRingMode(true);
        partsCooldown = 0;
        updateActiveBlock();
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 2.2F, 0.55F);
        updateStatus();
        syncBlock();
    }

    /**
     * 关机：先记下「这一瞬间转了多少、转速占比多少」，激光立刻衰减，
     * 环按缓出曲线刹回原位，动画结束后方块恢复。
     */
    private void beginShutdown() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean wasDoing = running || warmupStartTick > 0L;
        if (wasDoing && shutdownStamp == 0L) {
            double ticks = warmupStartTick > 0L
                    ? Math.max(0.0D, level.getGameTime() - warmupStartTick) : 0.0D;
            shutdownStartRawAngle = TimeBeaconAssembly.spinAngleRawDeg(ticks);
            shutdownStartRamp = TimeBeaconAssembly.spinRamp01(ticks);
            shutdownStamp = level.getGameTime();
            level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 1.6F, 0.85F);
        }
        running = false;
        warmupTicks = 0;
        warmupStartTick = 0L;
        updateActiveBlock();
        updateStatus();
        partsCooldown = 0;
        syncBlock();
    }

    private void updateStatus() {
        if (!formed) {
            status = 0;
            return;
        }
        if (!redstoneMode.permits(redstoneSignal)) {
            status = 2;
        } else if (!powered) {
            status = 1;
        } else if (dimensionConflict()) {
            status = 5;
        } else if (running) {
            status = 4;
        } else if (warmupStartTick > 0L) {
            status = 3;
        } else if (shutdownStamp > 0L) {
            status = 6;
        } else {
            status = 1;
        }
    }

    /** 有没有另一台时间信标占着这个维度。 */
    public boolean dimensionConflict() {
        if (level == null || level.isClientSide || level.getServer() == null) {
            return false;
        }
        return !TimeBeaconData.get(level.getServer()).isBeacon(level, worldPosition);
    }

    // ------------------------------------------------------------------
    // 结构与成型
    // ------------------------------------------------------------------

    public boolean tryForm(@Nullable Player source) {
        if (formed) {
            return true;
        }
        if (level == null || level.isClientSide) {
            return false;
        }
        List<ArcaneStructure.Part> parts = parts();
        if (!matches(parts)) {
            if (source != null) {
                source.displayClientMessage(Component.translatable("message.arcane_singularity.not_complete"), true);
            }
            return false;
        }
        if (!TimeBeaconData.get(level.getServer()).claim(level, worldPosition)) {
            if (source != null) {
                source.displayClientMessage(
                        Component.translatable("message.arcane_singularity.time_beacon_conflict"), true);
            }
            return false;
        }
        level.setBlock(worldPosition, level.getBlockState(worldPosition)
                .setValue(ArcaneProperties.FORMED, true), 3);
        for (ArcaneStructure.Part part : parts) {
            if (part.type() == ArcaneStructure.PartType.CONTROLLER) {
                continue;
            }
            BlockPos target = worldPosition.offset(part.offset());
            BlockState state = level.getBlockState(target);
            if (state.hasProperty(ArcaneProperties.FORMED) && !state.getValue(ArcaneProperties.FORMED)) {
                BlockState next = state.setValue(ArcaneProperties.FORMED, true);
                if (next.hasProperty(ArcaneProperties.ACTIVE)) {
                    next = next.setValue(ArcaneProperties.ACTIVE, false);
                }
                level.setBlock(target, next, 3);
            }
        }
        formed = true;
        running = false;
        // 顶部的两格时序炮管从这一刻起变成悬浮实体，由渲染器画上下振动
        applyFloatingTop(true);
        updateActiveBlock();
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.BLOCKS, 1.8F, 0.55F);
        if (source != null) {
            source.displayClientMessage(Component.translatable("message.arcane_singularity.beacon_formed"), true);
        }
        refreshPower();
        syncBlock();
        return true;
    }

    private List<ArcaneStructure.Part> parts() {
        return ArcaneStructure.parts(StructureKind.TIMEBEACON, Direction.NORTH);
    }

    private boolean matches(List<ArcaneStructure.Part> parts) {
        if (level == null) {
            return false;
        }
        for (ArcaneStructure.Part part : parts) {
            BlockPos target = worldPosition.offset(part.offset());
            if (!level.isLoaded(target)) {
                return false;
            }
            if (level.getBlockState(target).getBlock() != part.block()) {
                return false;
            }
        }
        return true;
    }

    private boolean validateStructure() {
        if (level == null) {
            return false;
        }
        for (ArcaneStructure.Part part : parts()) {
            BlockPos target = worldPosition.offset(part.offset());
            if (!level.isLoaded(target)) {
                continue;
            }
            if (level.getBlockState(target).getBlock() != part.block()) {
                return false;
            }
        }
        return true;
    }

    public void collapseStructure(boolean unform) {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean wasFormed = formed;
        formed = false;
        running = false;
        powered = false;
        warmupTicks = 0;
        warmupStartTick = 0L;
        shutdownStamp = 0L;
        shutdownStartRawAngle = 0.0D;
        shutdownStartRamp = 0.0F;
        projectionTicks = 0;
        ProjectionService.clear(level, worldPosition, StructureKind.TIMEBEACON, Direction.NORTH);
        applyRingMode(false);
        applyFloatingTop(false);
        partsCooldown = 0;
        syncRingParts();
        if (unform) {
            for (ArcaneStructure.Part part : parts()) {
                BlockPos target = worldPosition.offset(part.offset());
                BlockState state = level.getBlockState(target);
                if (part.type() == ArcaneStructure.PartType.CONTROLLER) {
                    if (state.getBlock() instanceof TimeBeaconControllerBlock) {
                        level.setBlock(target, state
                                .setValue(ArcaneProperties.FORMED, false)
                                .setValue(ArcaneProperties.ACTIVE, false), 3);
                    }
                    continue;
                }
                if (state.getBlock() == part.block()
                        && state.hasProperty(ArcaneProperties.FORMED)
                        && state.getValue(ArcaneProperties.FORMED)) {
                    BlockState next = state.setValue(ArcaneProperties.FORMED, false);
                    if (next.hasProperty(ArcaneProperties.ACTIVE)) {
                        next = next.setValue(ArcaneProperties.ACTIVE, false);
                    }
                    level.setBlock(target, next, 3);
                }
            }
        }
        updateActiveBlock();
        updateStatus();
        if (wasFormed) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 1.2F, 1.1F);
        }
        syncBlock();
    }

    /** 启动后把三圈汇聚环换成「空模型 + 无碰撞」，视觉与碰撞交给绕轴旋转的实体。 */
    private void applyRingMode(boolean engaged) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (int ring = 0; ring < 3; ring++) {
            for (BlockPos offset : ArcaneStructure.beaconRingOffsets(ring)) {
                BlockPos target = worldPosition.offset(offset);
                if (!level.isLoaded(target)) {
                    continue;
                }
                BlockState state = level.getBlockState(target);
                if (state.getBlock() != ModBlocks.CHRONO_RING.get()
                        || !state.hasProperty(ArcaneProperties.ACTIVE)) {
                    continue;
                }
                if (state.getValue(ArcaneProperties.ACTIVE) == engaged) {
                    continue;
                }
                level.setBlock(target, state.setValue(ArcaneProperties.ACTIVE, engaged), 3);
            }
        }
        // 顶部四块奥术晶体跟着小环一起转，同样换上空模型
        setTopCrystalsHidden(engaged);
        ringEngaged = engaged;
    }

    /** 最顶上那两格时序炮管：成型后就换上空模型，由渲染器画成悬浮振动的实体炮管。 */
    private void applyFloatingTop(boolean floating) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (BlockPos offset : ArcaneStructure.beaconTopBarrelOffsets()) {
            BlockPos target = worldPosition.offset(offset);
            if (!level.isLoaded(target)) {
                continue;
            }
            BlockState state = level.getBlockState(target);
            if (!state.hasProperty(ArcaneProperties.ACTIVE)
                    || state.getValue(ArcaneProperties.ACTIVE) == floating) {
                continue;
            }
            level.setBlock(target, state.setValue(ArcaneProperties.ACTIVE, floating), 3);
        }
    }

    /** 顶部四块奥术晶体的隐藏状态（启动后与小型聚能环反向旋转）。 */
    private void setTopCrystalsHidden(boolean hidden) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (BlockPos offset : ArcaneStructure.beaconTopCrystalOffsets()) {
            BlockPos target = worldPosition.offset(offset);
            if (!level.isLoaded(target)) {
                continue;
            }
            BlockState state = level.getBlockState(target);
            if (!state.hasProperty(ArcaneProperties.ACTIVE)
                    || state.getValue(ArcaneProperties.ACTIVE) == hidden) {
                continue;
            }
            level.setBlock(target, state.setValue(ArcaneProperties.ACTIVE, hidden), 3);
        }
    }

    /** 生成/回收汇聚环的碰撞实体，数量对不上时补齐或清掉多余的。 */
    private void syncRingParts() {
        if (level == null || level.isClientSide) {
            return;
        }
        AABB area = new AABB(worldPosition).inflate(16.0D, 26.0D, 16.0D);
        List<TimeBeaconRingPartEntity> parts = level.getEntitiesOfClass(TimeBeaconRingPartEntity.class, area,
                part -> part.getOwnerPos().equals(worldPosition));
        if (!ringEngaged) {
            for (TimeBeaconRingPartEntity part : parts) {
                part.discard();
            }
            return;
        }
        List<TimeBeaconAssembly.RingPart> blueprint = TimeBeaconAssembly.allParts();
        int count = blueprint.size();
        if (parts.size() > count * 2) {
            for (TimeBeaconRingPartEntity part : parts) {
                part.discard();
            }
            parts = List.of();
        }
        boolean[] seen = new boolean[count];
        for (TimeBeaconRingPartEntity part : parts) {
            int index = part.getPartIndex();
            if (index < 0 || index >= count || seen[index]) {
                part.discard();
            } else {
                seen[index] = true;
            }
        }
        double time = level.getGameTime();
        for (int i = 0; i < count; i++) {
            if (seen[i] || !isRingPositionLoaded(blueprint.get(i), time)) {
                continue;
            }
            spawnRingPart(i);
        }
    }

    private boolean isRingPositionLoaded(TimeBeaconAssembly.RingPart part, double time) {
        if (level == null) {
            return false;
        }
        double radians = Math.toRadians(part.angleDeg() + ringAngleAt(time, part.ring()));
        int x = Mth.floor(worldPosition.getX() + 0.5D + Math.cos(radians) * part.radius());
        int y = Mth.floor(worldPosition.getY() + part.y());
        int z = Mth.floor(worldPosition.getZ() + 0.5D + Math.sin(radians) * part.radius());
        return level.isLoaded(new BlockPos(x, y, z));
    }

    private void spawnRingPart(int index) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        TimeBeaconRingPartEntity part = ModEntities.TIME_BEACON_RING_PART.get().create(serverLevel);
        if (part == null) {
            return;
        }
        part.setup(worldPosition, index, warmupStartTick);
        serverLevel.addFreshEntity(part);
    }

    // ------------------------------------------------------------------
    // 动画状态（渲染器与碰撞实体共用）
    // ------------------------------------------------------------------

    /** 汇聚环当前累计转角（不取模）。 */
    public double rawRingAngleAt(double time) {
        if (shutdownStamp > 0L) {
            double progress = Mth.clamp((time - shutdownStamp) / (double) SHUTDOWN_LENGTH, 0.0D, 1.0D);
            return ArmillaryAssembly.brakeRawAngleDeg(shutdownStartRawAngle, progress);
        }
        if (warmupStartTick <= 0L) {
            return 0.0D;
        }
        return TimeBeaconAssembly.spinAngleRawDeg(Math.max(0.0D, time - warmupStartTick));
    }

    /**
     * 第 {@code ring} 圈当前的实际角度（度）：各圈转速方向与比例不同。
     *
     * <p>关机时按「这一圈自己的角度」刹车，而不是拿原始角统一刹车——上环的比例是 -0.82，
     * 原始角刹到 360 的整数倍时，乘上 -0.82 之后并不落在 360 的整数倍上，
     * 方块恢复的瞬间会跳掉一大截。这里每一圈各自刹到自己最近的那个整圈位置，收尾严丝合缝。</p>
     */
    public double ringAngleAt(double time, int ring) {
        return angleForScale(time, TimeBeaconAssembly.scaleOf(ring));
    }

    /** 顶部四块奥术晶体的角度：与小型聚能环方向相反。 */
    public double topCrystalAngleAt(double time) {
        return angleForScale(time, TimeBeaconAssembly.TOP_CRYSTAL_SCALE);
    }

    private double angleForScale(double time, double scale) {
        if (shutdownStamp > 0L) {
            double progress = Mth.clamp((time - shutdownStamp) / (double) SHUTDOWN_LENGTH, 0.0D, 1.0D);
            return TimeBeaconAssembly.normalize(
                    ArmillaryAssembly.brakeRawAngleDeg(shutdownStartRawAngle * scale, progress));
        }
        if (warmupStartTick <= 0L) {
            return 0.0D;
        }
        double ticks = Math.max(0.0D, time - warmupStartTick);
        return TimeBeaconAssembly.normalize(TimeBeaconAssembly.spinAngleRawDeg(ticks) * scale);
    }

    /** 当前转速占比 0..1：汇聚环的颜色与辉光强度跟着它。 */
    public float spinRampAt(double time) {
        if (shutdownStamp > 0L) {
            double progress = Mth.clamp((time - shutdownStamp) / (double) SHUTDOWN_LENGTH, 0.0D, 1.0D);
            return (float) (shutdownStartRamp * (1.0D - progress * progress));
        }
        if (warmupStartTick <= 0L) {
            return 0.0F;
        }
        return TimeBeaconAssembly.spinRamp01(Math.max(0.0D, time - warmupStartTick));
    }

    /** 炮口聚能进度 0..1（开机后半段开始涨，稳定运转时是 1）。 */
    public float chargeAt(double time) {
        if (shutdownStamp > 0L) {
            return 0.0F;
        }
        if (warmupStartTick <= 0L) {
            return 0.0F;
        }
        double ticks = Math.max(0.0D, time - warmupStartTick);
        if (ticks >= WARMUP_LENGTH) {
            return 1.0F;
        }
        return Mth.clamp((float) ((ticks - CHARGE_START) / (double) (WARMUP_LENGTH - CHARGE_START)),
                0.0F, 1.0F);
    }

    /** 激光强度 0..1：关机时从 1 平滑收到 0。 */
    public float beamPowerAt(double time) {
        if (shutdownStamp > 0L) {
            double progress = Mth.clamp((time - shutdownStamp) / (double) SHUTDOWN_LENGTH, 0.0D, 1.0D);
            return Mth.clamp((float) (1.0D - progress * 1.6D), 0.0F, 1.0F);
        }
        return running ? 1.0F : 0.0F;
    }

    public float shutdownProgress(double time) {
        if (shutdownStamp <= 0L) {
            return 0.0F;
        }
        return Mth.clamp((float) ((time - shutdownStamp) / (double) SHUTDOWN_LENGTH), 0.0F, 1.0F);
    }

    public int phase() {
        if (running) {
            return 2;
        }
        if (warmupStartTick > 0L) {
            return 1;
        }
        if (shutdownStamp > 0L) {
            return 3;
        }
        return 0;
    }

    // ------------------------------------------------------------------
    // 界面操作
    // ------------------------------------------------------------------

    public TimeMode getMode() {
        return mode;
    }

    public int getSpeedLevel() {
        return speedLevel;
    }

    public RedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    public int getRedstoneSignal() {
        return redstoneSignal;
    }

    /** 当前实际生效的时间倍率：只有激光在射的时候才改变时间。 */
    public double timeRate() {
        return running ? mode.rate(speedLevel) : 1.0D;
    }

    private double currentRate() {
        return running ? mode.rate(speedLevel) : 1.0D;
    }

    public boolean setMode(@Nullable ServerPlayer player, TimeMode next) {
        if (next == null || next == mode) {
            return false;
        }
        mode = next;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.arcane_singularity.time_mode",
                    Component.translatable(mode.translationKey())), true);
        }
        syncBlock();
        return true;
    }

    /** 「－」「＋」按钮：只在减速 / 加速两种模式下有意义。 */
    public boolean adjustSpeed(@Nullable ServerPlayer player, int delta) {
        if (!mode.adjustable()) {
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("message.arcane_singularity.time_rate_locked"), true);
            }
            return false;
        }
        int next = TimeMode.clampLevel(speedLevel + delta);
        if (next == speedLevel) {
            return false;
        }
        speedLevel = next;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.arcane_singularity.time_rate",
                    Component.translatable(mode.rateKey(speedLevel))), true);
        }
        syncBlock();
        return true;
    }

    public boolean cycleRedstoneMode(@Nullable ServerPlayer player) {
        redstoneMode = redstoneMode.next();
        updateStatus();
        syncBlock();
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.arcane_singularity.redstone_mode",
                    Component.translatable(redstoneMode.translationKey())), true);
        }
        powerCooldown = 0;
        return true;
    }

    public boolean startProjection(ServerPlayer player) {
        if (formed) {
            player.displayClientMessage(Component.translatable("message.arcane_singularity.projection_formed"), true);
            return false;
        }
        projectionTicks = 600;
        ProjectionService.clear(level, worldPosition, StructureKind.TIMEBEACON, Direction.NORTH);
        ProjectionService.show(level, worldPosition, StructureKind.TIMEBEACON, Direction.NORTH);
        syncBlock();
        player.displayClientMessage(Component.translatable("message.arcane_singularity.projection"), true);
        return true;
    }

    // ------------------------------------------------------------------
    // 状态查询
    // ------------------------------------------------------------------

    public boolean isFormed() {
        return formed;
    }

    /**
     * 渲染包围盒：时间信标往天上射的那道光柱有接近两百格高，
     * 另外还有那一圈聚能环与四块反向旋转的奥术晶体。
     * 包围盒必须把整束光柱都包进来，否则镜头一抬、方块出画，光柱就没了。
     */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos pos = getBlockPos();
        return new AABB(pos.getX() - 14.0D, pos.getY() - 4.0D, pos.getZ() - 14.0D,
                pos.getX() + 20.0D, pos.getY() + 248.0D, pos.getZ() + 15.0D);
    }

    public boolean isPowered() {
        return powered;
    }

    /** 激光已经射出、时间倍率正在生效。 */
    public boolean isRunning() {
        return running;
    }

    public boolean isActive() {
        return running || warmupStartTick > 0L || shutdownStamp > 0L;
    }

    public boolean isRingEngaged() {
        return ringEngaged;
    }

    public int getWarmupTicks() {
        return warmupTicks;
    }

    public long getWarmupStartTick() {
        return warmupStartTick;
    }

    public int getProjectionTicks() {
        return projectionTicks;
    }

    public int getStatus() {
        return status;
    }

    public boolean containsWorldPos(BlockPos pos) {
        BlockPos relative = pos.subtract(worldPosition);
        for (ArcaneStructure.Part part : parts()) {
            if (part.offset().equals(relative)) {
                return true;
            }
        }
        return false;
    }

    private void updateActiveBlock() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        boolean active = isActive();
        if (state.hasProperty(ArcaneProperties.ACTIVE) && state.getValue(ArcaneProperties.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(ArcaneProperties.ACTIVE, active), 3);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.arcane_singularity.time_beacon_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new TimeBeaconMenu(containerId, inventory, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Formed", formed);
        tag.putBoolean("Powered", powered);
        tag.putBoolean("Running", running);
        tag.putInt("Warmup", warmupTicks);
        tag.putLong("WarmupStart", warmupStartTick);
        tag.putLong("ShutdownStamp", shutdownStamp);
        tag.putDouble("ShutdownAngle", shutdownStartRawAngle);
        tag.putFloat("ShutdownRamp", shutdownStartRamp);
        tag.putInt("Mode", mode.id());
        tag.putInt("Speed", speedLevel);
        tag.putInt("RedstoneMode", redstoneMode.id());
        tag.putInt("RedstoneSignal", redstoneSignal);
        tag.putInt("Projection", projectionTicks);
        tag.putInt("Status", status);
        tag.putBoolean("RingEngaged", ringEngaged);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        formed = tag.getBoolean("Formed");
        powered = tag.getBoolean("Powered");
        running = tag.getBoolean("Running");
        warmupTicks = tag.getInt("Warmup");
        warmupStartTick = tag.getLong("WarmupStart");
        shutdownStamp = tag.getLong("ShutdownStamp");
        shutdownStartRawAngle = tag.getDouble("ShutdownAngle");
        shutdownStartRamp = tag.getFloat("ShutdownRamp");
        mode = TimeMode.byId(tag.getInt("Mode"));
        speedLevel = TimeMode.clampLevel(tag.getInt("Speed"));
        redstoneMode = RedstoneMode.byId(tag.getInt("RedstoneMode"));
        redstoneSignal = tag.getInt("RedstoneSignal");
        projectionTicks = tag.getInt("Projection");
        status = tag.getInt("Status");
        ringEngaged = tag.getBoolean("RingEngaged");
        if (!formed) {
            running = false;
            warmupTicks = 0;
            warmupStartTick = 0L;
            shutdownStamp = 0L;
            status = 0;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private String lastSyncKey = "";

    /** 内容变了才同步，避免稳定运转时每 tick 都让区块变脏。 */
    private void syncBlock() {
        if (level == null || level.isClientSide) {
            return;
        }
        String key = formed + "|" + powered + "|" + running + "|" + warmupTicks + "|" + warmupStartTick
                + "|" + shutdownStamp + "|" + shutdownStartRawAngle + "|" + shutdownStartRamp + "|"
                + mode.id() + "|" + speedLevel + "|" + redstoneMode.id() + "|" + redstoneSignal + "|"
                + projectionTicks + "|" + status + "|" + ringEngaged;
        if (key.equals(lastSyncKey)) {
            return;
        }
        lastSyncKey = key;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
}
