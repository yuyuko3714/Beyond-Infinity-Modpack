package cn.blockforge.generated.arcanesingularity.blockentity;

import cn.blockforge.generated.arcanesingularity.block.ArcaneProperties;
import cn.blockforge.generated.arcanesingularity.block.SingularityControllerBlock;
import cn.blockforge.generated.arcanesingularity.data.GateId;
import cn.blockforge.generated.arcanesingularity.entity.ArmillaryPartEntity;
import cn.blockforge.generated.arcanesingularity.menu.SingularityMenu;
import cn.blockforge.generated.arcanesingularity.redstone.RedstoneMode;
import cn.blockforge.generated.arcanesingularity.registry.ModBlockEntities;
import cn.blockforge.generated.arcanesingularity.registry.ModBlocks;
import cn.blockforge.generated.arcanesingularity.registry.ModEntities;
import cn.blockforge.generated.arcanesingularity.registry.ModItems;
import cn.blockforge.generated.arcanesingularity.registry.ModTags;
import cn.blockforge.generated.arcanesingularity.structure.ArcaneStructure;
import cn.blockforge.generated.arcanesingularity.structure.ArmillaryAssembly;
import cn.blockforge.generated.arcanesingularity.structure.ChunkKeeper;
import cn.blockforge.generated.arcanesingularity.structure.ProjectionService;
import cn.blockforge.generated.arcanesingularity.structure.StructureKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SingularityControllerBlockEntity extends BaseContainerBlockEntity {
    public static final int RITUAL_LENGTH = 600;
    /** 停机动画长度：转子减速、把四个稳定器送回原位后才恢复方块。 */
    public static final int SHUTDOWN_LENGTH = 56;
    /** 强加载自愈间隔（tick）：每十秒确认一次整座结构覆盖的区块都还在强加载列表里。 */
    private static final int CHUNK_KEEP_INTERVAL = 200;
    /** Goety Revelations 的终末之环。没有这条依赖，所以只按注册名认，不做编译期引用。 */
    private static final ResourceLocation END_HALO_ID =
            ResourceLocation.fromNamespaceAndPath("goety_revelation", "halo_of_the_end");
    public static final int OUTPUT_PER_TICK = 1_000_000;
    public static final int DATA_COUNT = 12;
    public static final int INDEX_REDSTONE_MODE = 10;
    public static final int INDEX_REDSTONE_SIGNAL = 11;

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private boolean formed;
    private boolean stable;
    private boolean active;
    private int animationTicks;
    private int projectionTicks;
    private int syncCooldown;
    private int validateCooldown;
    private int partsCooldown;
    /** 转子启动时刻：四个激光稳定器在这一 tick 正好落在四座底座上，之后绕中轴转。 */
    private long ringStartTick;
    /** 停机动画的起始时刻与起始角度；为 0 表示没有在停机。 */
    private long shutdownStamp;
    private double shutdownStartAngle;
    /** 最顶上那圈环在停机开始时的角度，用来把它稳稳刹回原位。 */
    private double topShutdownStartAngle;
    /**
     * 是否已经用掉一枚终焉回响。终焉回响仪式一开始就被消耗，
     * 之后靠这个标记继续供给，玩家不用再往槽里放东西；停机后清零，下次要重新放。
     */
    private boolean charged;
    /**
     * 这一次仪式是用<b>终末之环</b>（Goety Revelations 的 {@code goety_revelation:halo_of_the_end}）
     * 启动的。用它启动时黑洞走另一套更炫的渲染（见 {@code BlackHoleFx#renderEndHalo}），
     * 用本模组终焉回响启动的仍旧是原来的特效。停机动画播完后清零。
     */
    private boolean endHalo;
    private boolean collapsing;
    private RedstoneMode redstoneMode = RedstoneMode.AUTO;
    private int redstoneSignal;
    /** 结构强加载的定期自愈倒计时。初值 0 ⇒ 方块实体第一 tick 就会登记一次（重进世界也能补回来）。 */
    private int chunkKeepCooldown;

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> animationTicks;
                case 1 -> formed ? 1 : 0;
                case 2 -> stable ? 1 : 0;
                case 3 -> hasCatalyst() ? 1 : 0;
                case 4 -> projectionTicks;
                case 5 -> active ? 1 : 0;
                case 6 -> stable ? OUTPUT_PER_TICK : 0;
                case 7 -> phase();
                case 8 -> status();
                case 9 -> RITUAL_LENGTH;
                case INDEX_REDSTONE_MODE -> redstoneMode.id();
                case INDEX_REDSTONE_SIGNAL -> redstoneSignal;
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

    public SingularityControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SINGULARITY_CONTROLLER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SingularityControllerBlockEntity be) {
        be.tickServer();
    }

    private void tickServer() {
        if (level == null || level.isClientSide) {
            return;
        }
        // 整座浑天仪覆盖的区块保持强加载：第一 tick 登记，之后定期补一次，免得被别的东西撤掉。
        if (--chunkKeepCooldown <= 0) {
            chunkKeepCooldown = CHUNK_KEEP_INTERVAL;
            ChunkKeeper.keep(level, worldPosition, StructureKind.SINGULARITY, Direction.NORTH);
        }
        if (projectionTicks > 0) {
            projectionTicks--;
            if (projectionTicks == 0) {
                ProjectionService.clear(level, worldPosition, StructureKind.SINGULARITY, Direction.NORTH);
                syncBlock();
            } else if (projectionTicks % 10 == 0) {
                syncBlock();
            }
        }

        sampleRedstone();

        if (!formed) {
            if (--validateCooldown <= 0) {
                validateCooldown = 40;
                tryForm(null);
            }
            return;
        }

        // 停机动画播完：这时才把环和稳定器还原成方块、回收碰撞实体
        if (shutdownStamp > 0L && level.getGameTime() - shutdownStamp >= SHUTDOWN_LENGTH) {
            shutdownStamp = 0L;
            // 刹车已经归位：把积分角起点一起清掉，装饰环才会真的停住、下次开机再从 0 重新加速，
            // 否则环会拿「当前时间 - 很久以前的开机时刻」继续空转。
            ringStartTick = 0L;
            endHalo = false;
            updateActiveBlock();
            partsCooldown = 0;
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.BLOCKS, 1.3F, 1.15F);
            syncBlock();
        }

        if (--validateCooldown <= 0) {
            validateCooldown = 20;
            if (!validateStructure()) {
                collapseStructure(true);
                return;
            }
        }

        boolean catalyst = hasCatalyst();
        boolean gate = redstoneMode.permits(redstoneSignal);
        if ((!catalyst || !gate) && (stable || animationTicks > 0)) {
            deactivateBlackHole();
        }
        if (catalyst && gate && !stable && animationTicks == 0) {
            startRitual();
        }

        if (animationTicks > 0 && !stable) {
            animationTicks++;
            if (animationTicks == 200) {
                level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 0.6F);
            } else if (animationTicks == 400) {
                level.playSound(null, worldPosition, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.5F, 0.5F);
            } else if (animationTicks >= RITUAL_LENGTH) {
                animationTicks = RITUAL_LENGTH;
                stable = true;
                active = true;
                setPorts(true);
                updateActiveBlock();
                level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0F, 0.4F);
            }
            if (animationTicks % 5 == 0) {
                syncBlock();
            }
        }

        if (--syncCooldown <= 0) {
            // 只在偶尔补一次心跳同步：稳定运转时每 tick 刷区块只会让存档一直脏，
            // 退出世界时要写的脏区块变多、保存更慢。
            syncCooldown = 100;
            updateActiveBlock();
            setPorts(stable);
            syncBlock();
        }
        if (--partsCooldown <= 0) {
            partsCooldown = 20;
            syncArmillaryParts();
            // 顶环中央的注入器：成型期间一直保持「空模型 + 无碰撞」，被别的逻辑改回去也能补回来
            applyInjectorFloat(formed);
        }
    }

    /**
     * 读取控制器任意一面接入的红石信号，并在上升沿自动把接口从「自动」切到「高电平运行」，
     * 这样插上拉杆拨一次就立刻受拉杆控制。
     */
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

    public int getRedstoneSignal() {
        return redstoneSignal;
    }

    public RedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    /** 界面按钮：循环切换「自动 / 高电平运行 / 低电平运行」。 */
    public boolean cycleRedstoneMode(@Nullable ServerPlayer player) {
        redstoneMode = redstoneMode.next();
        syncBlock();
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.arcane_singularity.redstone_mode",
                    Component.translatable(redstoneMode.translationKey())), true);
        }
        return true;
    }

    public boolean tryForm(@Nullable Player source) {
        if (formed) {
            return true;
        }
        if (level == null || level.isClientSide) {
            return false;
        }
        List<ArcaneStructure.Part> parts = ArcaneStructure.parts(StructureKind.SINGULARITY, Direction.NORTH);
        if (!matches(parts)) {
            if (source != null) {
                source.displayClientMessage(Component.translatable("message.arcane_singularity.not_complete"), true);
            }
            return false;
        }
        if (level.getBlockState(worldPosition).getBlock() instanceof SingularityControllerBlock) {
            level.setBlock(worldPosition, level.getBlockState(worldPosition)
                    .setValue(ArcaneProperties.FORMED, true), 3);
        }
        for (ArcaneStructure.Part part : parts) {
            BlockPos target = worldPosition.offset(part.offset());
            BlockState state = level.getBlockState(target);
            if (part.type() == ArcaneStructure.PartType.CONTROLLER) {
                continue;
            }
            if (state.hasProperty(ArcaneProperties.FORMED) && !state.getValue(ArcaneProperties.FORMED)) {
                BlockState next = state.setValue(ArcaneProperties.FORMED, true);
                if (next.hasProperty(ArcaneProperties.ACTIVE)) {
                    next = next.setValue(ArcaneProperties.ACTIVE, false);
                }
                level.setBlock(target, next, 3);
            }
            if (part.block() == ModBlocks.ENERGY_OUTPUT_PORT.get()
                    && level.getBlockEntity(target) instanceof EnergyPortBlockEntity port) {
                port.setControllerPos(worldPosition);
            }
        }
        formed = true;
        active = false;
        shutdownStamp = 0L;
        updateActiveBlock();
        // 成型后顶部注入器脱离方块形态、悬在顶环中央浮动
        applyInjectorFloat(true);
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.8F, 0.5F);
        if (source != null) {
            source.displayClientMessage(Component.translatable("message.arcane_singularity.formed"), true);
        }
        if (hasCatalyst()) {
            startRitual();
        }
        syncBlock();
        return true;
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
        for (ArcaneStructure.Part part : ArcaneStructure.parts(StructureKind.SINGULARITY, Direction.NORTH)) {
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
        if (level == null || level.isClientSide || collapsing) {
            return;
        }
        collapsing = true;
        boolean wasFormed = formed;
        formed = false;
        stable = false;
        active = false;
        animationTicks = 0;
        shutdownStamp = 0L;
        charged = false;
        endHalo = false;
        projectionTicks = 0;
        ProjectionService.clear(level, worldPosition, StructureKind.SINGULARITY, Direction.NORTH);
        setPorts(false);
        if (unform) {
            for (ArcaneStructure.Part part : ArcaneStructure.parts(StructureKind.SINGULARITY, Direction.NORTH)) {
                BlockPos target = worldPosition.offset(part.offset());
                BlockState state = level.getBlockState(target);
                if (part.type() == ArcaneStructure.PartType.CONTROLLER) {
                    if (state.getBlock() instanceof SingularityControllerBlock) {
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
        applyRotorMode(false);
        applyInjectorFloat(false);
        partsCooldown = 0;
        // 被破坏是立即复位：实体当场回收，不等下一次同步
        syncArmillaryParts();
        if (wasFormed) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.4F, 0.8F);
        }
        syncBlock();
        collapsing = false;
    }

    /**
     * 停机：黑洞先塌缩、转子减速并带着四个稳定器转回原位，动画结束后才恢复方块。
     * 结构被破坏走的是 {@link #collapseStructure(boolean)}，那边是立即复位，不播动画。
     */
    public void deactivateBlackHole() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean wasRunning = stable || animationTicks > 0;
        stable = false;
        animationTicks = 0;
        charged = false;
        setPorts(false);
        if (wasRunning && shutdownStamp == 0L) {
            shutdownStamp = level.getGameTime();
            shutdownStartAngle = ArmillaryAssembly.rotorAngleDeg(level.getGameTime(), ringStartTick);
            topShutdownStartAngle = ArmillaryAssembly.topRingAngleDeg(level.getGameTime(), ringStartTick);
            level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 1.5F, 0.8F);
        }
        updateActiveBlock();
        partsCooldown = 0;
        syncBlock();
    }

    public boolean isShuttingDown() {
        return shutdownStamp > 0L;
    }

    public boolean isShuttingDownAt(double time) {
        if (shutdownStamp <= 0L) {
            return false;
        }
        return time - shutdownStamp < SHUTDOWN_LENGTH;
    }

    /** 停机动画进度 0..1。 */
    public float shutdownProgress(double time) {
        if (shutdownStamp <= 0L) {
            return 0.0F;
        }
        return Mth.clamp((float) ((time - shutdownStamp) / SHUTDOWN_LENGTH), 0.0F, 1.0F);
    }

    /**
     * 转子当前角度。停机时用「二次缓出」把角度送回 360 的整数倍：起始角速度最大、越转越慢，
     * 正好在停机动画结束的那一 tick 停回原位，四个稳定器落回四座底座，方块恢复时看不出跳动。
     */
    public double rotorAngleAt(double time) {
        if (shutdownStamp > 0L) {
            double progress = Mth.clamp((time - shutdownStamp) / (double) SHUTDOWN_LENGTH, 0.0D, 1.0D);
            return ArmillaryAssembly.brakeAngleDeg(shutdownStartAngle, progress);
        }
        return ArmillaryAssembly.rotorAngleDeg(time, ringStartTick);
    }

    /**
     * 最顶那圈环当前的角度：与转子共用加速曲线但方向相反，停机时反向刹回原位，
     * 这样它不会在停机瞬间掉头。
     */
    public double topRingAngleAt(double time) {
        if (shutdownStamp > 0L) {
            double progress = Mth.clamp((time - shutdownStamp) / (double) SHUTDOWN_LENGTH, 0.0D, 1.0D);
            return ArmillaryAssembly.brakeAngleBackDeg(topShutdownStartAngle, progress);
        }
        return ArmillaryAssembly.topRingAngleDeg(time, ringStartTick);
    }

    /**
     * 累计转过的「原始角度」（度，不取模）：启动后是对速度曲线的积分，停机时从停机瞬间的角度
     * 平滑刹到下一个 360 的整数倍。
     *
     * <p>装饰环、视界外壳这些需要自己缩放转速的东西都用它再乘系数，避免用
     * 「时间 × 当前转速」导致角度在加速过程中突然跳一大截（看起来就是抽搐）。</p>
     */
    public double rawSpinAngleAt(double time) {
        if (ringStartTick <= 0L) {
            return 0.0D;
        }
        if (shutdownStamp > 0L) {
            double progress = Mth.clamp((time - shutdownStamp) / (double) SHUTDOWN_LENGTH, 0.0D, 1.0D);
            double startRaw = ArmillaryAssembly.spinAngleRawDeg(
                    Math.max(0.0D, shutdownStamp - ringStartTick));
            return ArmillaryAssembly.brakeRawAngleDeg(startRaw, progress);
        }
        return ArmillaryAssembly.spinAngleRawDeg(Math.max(0.0D, time - ringStartTick));
    }

    /**
     * 装饰层（符文珠三环、光子环、紫黑环、吸积盘……）里某一层的角度：
     * 累计原始角 × {@code factor} 再加初始相位 {@code phase}。
     *
     * <p>这里不提前取模：{@code raw * factor + phase} 随时间是连续的，只有最后套到位姿上时才取模，
     * 所以每转过一圈、以及转速由慢到快、由快到慢的过程中都不会跳角度。</p>
     *
     * <p>停机时按同一条缓出曲线，把<b>这一层自己</b>刹到最近的 360 整数倍——三层符文环各有各的
     * 系数，只有各自刹到自己那一圈，关机结束、角度复位成 0 的那一帧才不会甩一下；开机时又从
     * 同一个相位重新由慢到快转起来。</p>
     */
    public double layerAngleAt(double time, double factor, double phase) {
        if (ringStartTick <= 0L || !Double.isFinite(factor) || !Double.isFinite(phase)) {
            return phase;
        }
        if (shutdownStamp <= 0L) {
            return ArmillaryAssembly.spinAngleRawDeg(Math.max(0.0D, time - ringStartTick)) * factor + phase;
        }
        double progress = Mth.clamp((time - shutdownStamp) / (double) SHUTDOWN_LENGTH, 0.0D, 1.0D);
        double startRaw = ArmillaryAssembly.spinAngleRawDeg(Math.max(0.0D, shutdownStamp - ringStartTick));
        double start = startRaw * factor + phase;
        double target = Math.ceil(start / 360.0D) * 360.0D;
        double remaining = target - start;
        if (remaining < 0.5D) {
            remaining = 0.0D;
        }
        double eased = 2.0D * progress - progress * progress;
        return start + remaining * eased;
    }

    /** 最顶那圈环的上下震动偏移；停机时随进度收回到 0，方块就在原位恢复。 */
    public double topRingBobAt(double time) {
        double bob = ArmillaryAssembly.topRingBob(time, ringStartTick);
        if (shutdownStamp > 0L) {
            double progress = Mth.clamp((time - shutdownStamp) / (double) SHUTDOWN_LENGTH, 0.0D, 1.0D);
            return bob * (1.0D - progress);
        }
        return bob;
    }

    /**
     * 中环四块奥术晶体当前向外扩出的距离（格）。
     *
     * <p>开机时由 0 平滑推到最远（这段时间转子几乎还没转，先看清「脱开底座」）；
     * 停机时先保持在外圈继续刹车，最后一段再同时往回收，进度到 1 正好归零，
     * 与「刹回原位 + 方块恢复」是同一瞬间，看不出跳变。</p>
     */
    public double crystalOutwardAt(double time) {
        if (shutdownStamp > 0L) {
            double progress = Mth.clamp((time - shutdownStamp) / (double) SHUTDOWN_LENGTH, 0.0D, 1.0D);
            return ArmillaryAssembly.crystalOutwardOnBrake(progress);
        }
        if (ringStartTick <= 0L) {
            return 0.0D;
        }
        return ArmillaryAssembly.crystalOutward(Math.max(0.0D, time - ringStartTick));
    }

    public void startRitual() {
        if (!formed || stable || animationTicks > 0 || !hasCatalyst()) {
            return;
        }
        if (!redstoneMode.permits(redstoneSignal)) {
            return;
        }
        // 先看槽里是哪种核心：终末之环启动的那一次走炫酷版黑洞，终焉回响还是原来的特效。
        // 必须在 consumeCatalyst() 之前判断（回响会被消耗掉）。
        endHalo = isEndHalo(items.get(0));
        consumeCatalyst();
        animationTicks = 1;
        active = true;
        shutdownStamp = 0L;
        shutdownStartAngle = 0.0D;
        topShutdownStartAngle = 0.0D;
        if (level != null && !level.isClientSide) {
            ringStartTick = level.getGameTime();
            partsCooldown = 0;
        }
        updateActiveBlock();
        if (level != null && !level.isClientSide) {
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0F, 0.5F);
        }
        syncBlock();
    }

    private void setPorts(boolean powered) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (BlockPos offset : ArcaneStructure.offsetsOf(StructureKind.SINGULARITY, Direction.NORTH,
                ArcaneStructure.PartType.PORT)) {
            BlockPos target = worldPosition.offset(offset);
            if (!level.isLoaded(target)) {
                continue;
            }
            BlockState state = level.getBlockState(target);
            if (state.getBlock() != ModBlocks.ENERGY_OUTPUT_PORT.get()) {
                continue;
            }
            BlockState next = state.setValue(ArcaneProperties.ACTIVE, powered);
            if (next != state) {
                level.setBlock(target, next, 3);
            }
            if (level.getBlockEntity(target) instanceof EnergyPortBlockEntity port) {
                port.setControllerPos(worldPosition);
            }
        }
    }

    private void updateActiveBlock() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        boolean shouldBeActive = stable || animationTicks > 0 || shutdownStamp > 0L;
        if (state.hasProperty(ArcaneProperties.ACTIVE) && state.getValue(ArcaneProperties.ACTIVE) != shouldBeActive) {
            level.setBlock(worldPosition, state.setValue(ArcaneProperties.ACTIVE, shouldBeActive), 3);
        }
        active = shouldBeActive;
        applyRotorMode(shouldBeActive);
        if (shouldBeActive && ringStartTick <= 0L) {
            ringStartTick = level.getGameTime();
        }
    }

    /**
     * 顶环中央的激光注入器：成型后切成「空模型 + 无碰撞」，由渲染器画成悬浮的注入器；
     * 拆除或损坏时再切回实心方块。
     */
    private void applyInjectorFloat(boolean floating) {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockPos target = worldPosition.offset(ArcaneStructure.singularityInjectorOffset());
        if (!level.isLoaded(target)) {
            return;
        }
        BlockState state = level.getBlockState(target);
        if (state.getBlock() != ModBlocks.LASER_INJECTOR.get()
                || !state.hasProperty(ArcaneProperties.ACTIVE)
                || state.getValue(ArcaneProperties.ACTIVE) == floating) {
            return;
        }
        level.setBlock(target, state.setValue(ArcaneProperties.ACTIVE, floating), 3);
    }

    /**
     * 启动后把整圈浑天仪环和四台激光稳定器换成「空模型 + 无碰撞」，
     * 视觉与碰撞交给绕着中轴旋转的实体；停止时再换回来。
     */
    private void applyRotorMode(boolean spun) {
        if (level == null || level.isClientSide) {
            return;
        }
        setRingState(ArcaneStructure.rotorRingOffsets(), spun, ModBlocks.ARMILLARY_RING.get());
        setRingState(ArcaneStructure.stabilizerOffsets(), spun, ModBlocks.LASER_STABILIZER.get());
        // 中环四块奥术晶体：启动后同样换成空模型（碰撞清空），交给先外扩再绕圆平面转的实体；
        // 停机动画播完再一起恢复成方块。
        setRingState(ArcaneStructure.rotorCrystalOffsets(), spun, ModBlocks.ARCANE_CRYSTAL_BLOCK.get());
        setRingStateForParts(ArcaneStructure.topRingParts(), spun);
    }

    /** 顶部环里既有浑天仪环方块也有水晶冠饰，按构件自己的方块类型逐个切换。 */
    private void setRingStateForParts(List<ArcaneStructure.Part> parts, boolean spun) {
        if (level == null) {
            return;
        }
        for (ArcaneStructure.Part part : parts) {
            BlockPos target = worldPosition.offset(part.offset());
            if (!level.isLoaded(target)) {
                continue;
            }
            BlockState state = level.getBlockState(target);
            if (state.getBlock() != part.block() || !state.hasProperty(ArcaneProperties.ACTIVE)) {
                continue;
            }
            if (state.getValue(ArcaneProperties.ACTIVE) == spun) {
                continue;
            }
            level.setBlock(target, state.setValue(ArcaneProperties.ACTIVE, spun), 3);
        }
    }

    private void setRingState(List<BlockPos> offsets, boolean spun, Block expected) {
        if (level == null) {
            return;
        }
        for (BlockPos offset : offsets) {
            BlockPos target = worldPosition.offset(offset);
            if (!level.isLoaded(target)) {
                continue;
            }
            BlockState state = level.getBlockState(target);
            if (state.getBlock() != expected || !state.hasProperty(ArcaneProperties.ACTIVE)) {
                continue;
            }
            if (state.getValue(ArcaneProperties.ACTIVE) == spun) {
                continue;
            }
            // 用 3：环变隐藏后自发光从 10 变 0，需要顺带做一次光照/邻接刷新
            level.setBlock(target, state.setValue(ArcaneProperties.ACTIVE, spun), 3);
        }
    }

    /** 生成/回收转子的碰撞实体，数量对不上时补齐或清掉多余的。 */
    private void syncArmillaryParts() {
        if (level == null || level.isClientSide) {
            return;
        }
        // 注意：转子在控制器上方 8.5 格，范围必须把高度也包进来，否则每次检查都会当成「缺实体」重复生成
        AABB area = new AABB(worldPosition).inflate(18.0D, 24.0D, 18.0D);
        List<ArmillaryPartEntity> parts = level.getEntitiesOfClass(ArmillaryPartEntity.class, area,
                part -> part.getOwnerPos().equals(worldPosition));
        boolean running = formed && active;
        if (!running) {
            for (ArmillaryPartEntity part : parts) {
                part.discard();
            }
            return;
        }
        List<ArmillaryAssembly.RotorPart> blueprint = ArmillaryAssembly.allParts();
        int count = blueprint.size();
        if (parts.size() > count * 2) {
            // 兜底：万一历史存档里留下了多余实体，整批回收后按蓝图重铺
            for (ArmillaryPartEntity part : parts) {
                part.discard();
            }
            parts = List.of();
        }
        boolean[] seen = new boolean[count];
        for (ArmillaryPartEntity part : parts) {
            int index = part.getPartIndex();
            if (index < 0 || index >= count || seen[index]) {
                part.discard();
            } else {
                seen[index] = true;
            }
        }
        double now = level.getGameTime();
        double rotor = rotorAngleAt(now);
        double topAngle = topRingAngleAt(now);
        double bob = topRingBobAt(now);
        double crystalOut = crystalOutwardAt(now);
        for (int i = 0; i < count; i++) {
            if (seen[i]) {
                continue;
            }
            if (!isRotorPositionLoaded(blueprint.get(i), blueprint.get(i).top() ? topAngle : rotor, bob, crystalOut)) {
                // 那个位置所在区块还没加载：先不铺，免得往未加载区块里反复塞实体
                continue;
            }
            spawnArmillaryPart(i);
        }
    }

    private boolean isRotorPositionLoaded(ArmillaryAssembly.RotorPart part, double rotor, double bob,
                                          double crystalOut) {
        if (level == null) {
            return false;
        }
        double radius = part.radius() + (part.crystal() ? crystalOut : 0.0D);
        double radians = Math.toRadians(part.angleDeg() + rotor);
        int x = Mth.floor(worldPosition.getX() + 0.5D + Math.cos(radians) * radius);
        int y = Mth.floor(worldPosition.getY()
                + (part.top() ? ArmillaryAssembly.TOP_RING_Y + bob : ArmillaryAssembly.CENTER_Y));
        int z = Mth.floor(worldPosition.getZ() + 0.5D + Math.sin(radians) * radius);
        return level.isLoaded(new BlockPos(x, y, z));
    }

    private void spawnArmillaryPart(int index) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ArmillaryPartEntity part = ModEntities.ARMILLARY_PART.get().create(serverLevel);
        if (part == null) {
            return;
        }
        part.setup(worldPosition, index, ringStartTick);
        serverLevel.addFreshEntity(part);
    }

    public long getRingStartTick() {
        return ringStartTick;
    }

    public boolean startProjection(ServerPlayer player) {
        if (formed) {
            player.displayClientMessage(Component.translatable("message.arcane_singularity.projection_formed"), true);
            return false;
        }
        projectionTicks = 600;
        ProjectionService.clear(level, worldPosition, StructureKind.SINGULARITY, Direction.NORTH);
        ProjectionService.show(level, worldPosition, StructureKind.SINGULARITY, Direction.NORTH);
        syncBlock();
        player.displayClientMessage(Component.translatable("message.arcane_singularity.projection"), true);
        return true;
    }

    public int status() {
        if (!formed) {
            return 0;
        }
        if (!hasCatalyst()) {
            return 1;
        }
        if (!redstoneMode.permits(redstoneSignal)) {
            return 5;
        }
        if (stable) {
            return 4;
        }
        if (animationTicks > 0) {
            return 3;
        }
        return 2;
    }

    public int phase() {
        if (stable) {
            return 4;
        }
        if (animationTicks <= 0) {
            return 0;
        }
        if (animationTicks < 200) {
            return 1;
        }
        if (animationTicks < 400) {
            return 2;
        }
        return 3;
    }

    public boolean isFormed() {
        return formed;
    }

    /**
     * 渲染包围盒：浑天仪环、三个符文珠环、悬空的激光注入器和黑洞都比一个方块大得多。
     * 默认的一格包围盒会让镜头一转、方块本体出画时整台机器的特效全部消失，
     * 所以这里按特效真实的体积给足空间。
     */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos pos = getBlockPos();
        return new AABB(pos.getX() - 16.0D, pos.getY() - 8.0D, pos.getZ() - 16.0D,
                pos.getX() + 17.0D, pos.getY() + 26.0D, pos.getZ() + 17.0D);
    }

    public boolean isStable() {
        return stable;
    }

    public boolean isActive() {
        return active;
    }

    public int getAnimationTicks() {
        return animationTicks;
    }

    public int getProjectionTicks() {
        return projectionTicks;
    }

    public String getSourceId() {
        return level == null ? "" : GateId.of(level, worldPosition).encode();
    }

    public boolean containsWorldPos(BlockPos pos) {
        BlockPos relative = pos.subtract(worldPosition);
        for (ArcaneStructure.Part part : ArcaneStructure.parts(StructureKind.SINGULARITY, Direction.NORTH)) {
            if (part.offset().equals(relative)) {
                return true;
            }
        }
        return false;
    }

    public void dropContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack.copy());
            }
        }
        items.clear();
        setChanged();
    }

    /** 槽里有核心，或者已经消耗掉一枚终焉回响（那次仪式的供给还没用完）。 */
    public boolean hasCatalyst() {
        if (charged) {
            return true;
        }
        return !items.get(0).isEmpty() && items.get(0).is(ModTags.SINGULARITY_CATALYSTS);
    }

    public boolean isCharged() {
        return charged;
    }

    /** 这一次仪式是不是用终末之环启动的 —— 客户端据此切到炫酷版黑洞特效。 */
    public boolean isEndHalo() {
        return endHalo;
    }

    /** 按注册名认终末之环：没有 Goety 依赖，不能直接引用它的 Item 类。 */
    private static boolean isEndHalo(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) {
            return false;
        }
        // 认注册名；顺手也认这个命名空间，免得哪天作者改了物品名就认不出来。
        return END_HALO_ID.equals(id) || "goety_revelation".equals(id.getNamespace());
    }

    /**
     * 仪式启动时结算核心：本模组的终焉回响会被消耗掉（并记下 charged 让仪式继续），
     * 其他模组放进来的核心（例如终末之环）只当催化剂用，原样留在槽里。
     */
    private void consumeCatalyst() {
        ItemStack stack = items.get(0);
        if (stack.isEmpty() || !stack.is(ModItems.ECHO_OF_THE_END.get())) {
            return;
        }
        stack.shrink(1);
        charged = true;
        setChanged();
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return items.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
            syncBlock();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        boolean had = hasCatalyst();
        items.set(slot, stack);
        boolean has = hasCatalyst();
        if (!level.isClientSide) {
            if (!had && has) {
                startRitual();
            } else if (had && !has && (stable || animationTicks > 0)) {
                deactivateBlackHole();
            }
            setChanged();
            syncBlock();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(ModTags.SINGULARITY_CATALYSTS);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.arcane_singularity.singularity_controller");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new SingularityMenu(containerId, inventory, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putBoolean("Formed", formed);
        tag.putBoolean("Stable", stable);
        tag.putBoolean("Active", active);
        tag.putInt("Animation", animationTicks);
        tag.putInt("Projection", projectionTicks);
        tag.putInt("RedstoneMode", redstoneMode.id());
        tag.putInt("RedstoneSignal", redstoneSignal);
        tag.putLong("RingStart", ringStartTick);
        tag.putLong("ShutdownStamp", shutdownStamp);
        tag.putDouble("ShutdownAngle", shutdownStartAngle);
        tag.putDouble("TopShutdownAngle", topShutdownStartAngle);
        tag.putBoolean("Charged", charged);
        tag.putBoolean("EndHalo", endHalo);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        formed = tag.getBoolean("Formed");
        stable = tag.getBoolean("Stable");
        active = tag.getBoolean("Active");
        animationTicks = tag.getInt("Animation");
        projectionTicks = tag.getInt("Projection");
        redstoneMode = RedstoneMode.byId(tag.getInt("RedstoneMode"));
        redstoneSignal = tag.getInt("RedstoneSignal");
        ringStartTick = tag.getLong("RingStart");
        shutdownStamp = tag.getLong("ShutdownStamp");
        shutdownStartAngle = tag.getDouble("ShutdownAngle");
        topShutdownStartAngle = tag.getDouble("TopShutdownAngle");
        charged = tag.getBoolean("Charged");
        endHalo = tag.getBoolean("EndHalo");
        if (!formed) {
            stable = false;
            active = false;
            animationTicks = 0;
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

    /**
     * 内容变了才同步。稳定运转时如果每 tick 都 setChanged，区块会一直是脏的，
     * 退出世界保存时要写的东西就更多、也更容易卡在保存界面。
     */
    private void syncBlock() {
        if (level == null || level.isClientSide) {
            return;
        }
        String key = formed + "|" + stable + "|" + active + "|" + animationTicks + "|" + projectionTicks
                + "|" + redstoneMode.id() + "|" + redstoneSignal + "|" + ringStartTick + "|"
                + shutdownStamp + "|" + shutdownStartAngle + "|" + topShutdownStartAngle + "|"
                + (charged ? 1 : 0) + "|" + (hasCatalyst() ? 1 : 0) + "|" + (endHalo ? 1 : 0);
        if (key.equals(lastSyncKey)) {
            return;
        }
        lastSyncKey = key;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
}
