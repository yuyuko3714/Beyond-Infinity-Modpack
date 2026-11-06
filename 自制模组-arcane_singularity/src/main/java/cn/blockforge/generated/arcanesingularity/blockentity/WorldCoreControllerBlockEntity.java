package cn.blockforge.generated.arcanesingularity.blockentity;

import cn.blockforge.generated.arcanesingularity.block.ArcaneProperties;
import cn.blockforge.generated.arcanesingularity.block.WorldCoreControllerBlock;
import cn.blockforge.generated.arcanesingularity.energy.ArcaneEnergyNetwork;
import cn.blockforge.generated.arcanesingularity.entity.WorldCoreCoreEntity;
import cn.blockforge.generated.arcanesingularity.menu.WorldCoreMenu;
import cn.blockforge.generated.arcanesingularity.redstone.RedstoneMode;
import cn.blockforge.generated.arcanesingularity.registry.ModBlockEntities;
import cn.blockforge.generated.arcanesingularity.registry.ModEntities;
import cn.blockforge.generated.arcanesingularity.registry.ModItems;
import cn.blockforge.generated.arcanesingularity.structure.ArcaneStructure;
import cn.blockforge.generated.arcanesingularity.structure.ChunkKeeper;
import cn.blockforge.generated.arcanesingularity.structure.ProjectionService;
import cn.blockforge.generated.arcanesingularity.structure.StructureKind;
import cn.blockforge.generated.arcanesingularity.structure.WorldCoreAssembly;
import cn.blockforge.generated.arcanesingularity.structure.WorldCoreLink;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 世界之核控制器。
 *
 * <p>它是这套装置的总闸：只有<u>黑洞发生器、星门、时间信标三座结构同时在线</u>，并且各自的控制器
 * 都用「世界之核专属管道」接到本结构的三个专属接口上，再往核心槽里放一枚世界核心，它才会启动。</p>
 *
 * <p>开机一分钟，分五幕（见 {@link WorldCoreAssembly}）：三台接口激光器充能 → 三道光在天空汇聚 →
 * 巨大魔法阵展开并由快到慢旋转 → 魔法阵中心与顶部激光器对射、汇于一点 →
 * 一个巨大的命令方块从能量里爆发出来，像末地水晶一样旋转，随后稳定。稳定之后界面里的
 * 命令输入框才解锁，可以像命令方块一样执行命令。</p>
 */
public class WorldCoreControllerBlockEntity extends BaseContainerBlockEntity {
    public static final int DATA_COUNT = 18;
    public static final int INDEX_COMMAND_MODE = 0;
    public static final int INDEX_CONDITIONAL = 1;
    public static final int INDEX_FORMED = 2;
    public static final int INDEX_POWERED = 3;
    public static final int INDEX_RUNNING = 4;
    public static final int INDEX_WARMUP = 5;
    public static final int INDEX_PHASE = 6;
    public static final int INDEX_STATUS = 7;
    public static final int INDEX_REDSTONE_MODE = 8;
    public static final int INDEX_REDSTONE_SIGNAL = 9;
    public static final int INDEX_PROJECTION = 10;
    public static final int INDEX_LINK_MASK = 11;
    public static final int INDEX_LINK_A = 12;
    public static final int INDEX_LINK_B = 13;
    public static final int INDEX_LINK_C = 14;
    public static final int INDEX_HAS_CORE = 15;
    public static final int INDEX_STAGE = 16;
    public static final int INDEX_RESULT = 17;

    /** 命令跑完后最多显示多少行输出。 */
    private static final int MAX_OUTPUT_LINES = 12;
    /** 循环模式两次执行之间的间隔（tick）：一秒一次，免得把服务器刷爆。 */
    private static final int LOOP_INTERVAL = 20;
    /** 强加载自愈间隔（tick）。 */
    private static final int CHUNK_KEEP_INTERVAL = 200;
    /** 命令文本长度上限。 */
    private static final int MAX_COMMAND_LENGTH = 512;

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    private boolean formed;
    private boolean powered;
    private boolean running;
    private int warmupTicks;
    private long warmupStartTick;
    private long shutdownStamp;
    /** 关机瞬间魔法阵的累计转角：关机期间冻结在这里，不再继续转。 */
    private double shutdownGlyphAngle;
    private int projectionTicks;
    private int status;
    private int validateCooldown;
    private int powerCooldown;
    private int syncCooldown;
    private int linkCooldown;
    private int chunkKeepCooldown;
    private int loopCooldown;
    /** 核心实体（爆发的命令方块）的检查间隔。 */
    private int coreSyncCooldown;
    private RedstoneMode redstoneMode = RedstoneMode.AUTO;
    private int redstoneSignal;
    /** 三个接口各自的管道检测结果（三位掩码）。 */
    private int[] interfaceMasks = new int[3];
    /** 命令方块模式：0 = 脉冲，1 = 循环（没有连锁）。 */
    private int commandMode;
    private boolean conditional;
    private String commandText = "";
    private String lastOutput = "";
    private int lastResult;
    private String lastSyncKey = "";

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case INDEX_COMMAND_MODE -> commandMode;
                case INDEX_CONDITIONAL -> conditional ? 1 : 0;
                case INDEX_FORMED -> formed ? 1 : 0;
                case INDEX_POWERED -> powered ? 1 : 0;
                case INDEX_RUNNING -> running ? 1 : 0;
                case INDEX_WARMUP -> Math.min(1000, warmupTicks * 1000 / WorldCoreAssembly.WARMUP_LENGTH);
                case INDEX_PHASE -> phase();
                case INDEX_STATUS -> status;
                case INDEX_REDSTONE_MODE -> redstoneMode.id();
                case INDEX_REDSTONE_SIGNAL -> redstoneSignal;
                case INDEX_PROJECTION -> projectionTicks;
                case INDEX_LINK_MASK -> linkMaskAll();
                case INDEX_LINK_A -> maskAt(0);
                case INDEX_LINK_B -> maskAt(1);
                case INDEX_LINK_C -> maskAt(2);
                case INDEX_HAS_CORE -> hasCore() ? 1 : 0;
                case INDEX_STAGE -> stage();
                case INDEX_RESULT -> lastResult;
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

    public WorldCoreControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WORLD_CORE_CONTROLLER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WorldCoreControllerBlockEntity be) {
        be.tickServer();
    }

    private void tickServer() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (--chunkKeepCooldown <= 0) {
            chunkKeepCooldown = CHUNK_KEEP_INTERVAL;
            ChunkKeeper.keep(level, worldPosition, StructureKind.WORLD_CORE, Direction.NORTH);
        }
        if (projectionTicks > 0) {
            projectionTicks--;
            if (projectionTicks == 0) {
                ProjectionService.clear(level, worldPosition, StructureKind.WORLD_CORE, Direction.NORTH);
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
        if (--validateCooldown <= 0) {
            validateCooldown = 20;
            if (!validateStructure()) {
                collapseStructure(true);
                return;
            }
        }

        if (--linkCooldown <= 0) {
            linkCooldown = 20;
            scanLinks();
        }

        // 关机动画播完：魔法阵散尽，这时才把构件恢复
        if (shutdownStamp > 0L && level.getGameTime() - shutdownStamp >= WorldCoreAssembly.SHUTDOWN_LENGTH) {
            shutdownStamp = 0L;
            warmupStartTick = 0L;
            warmupTicks = 0;
            shutdownGlyphAngle = 0.0D;
            updateActiveBlock();
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
            if (!running && ticks >= WorldCoreAssembly.WARMUP_LENGTH) {
                running = true;
                warmupTicks = WorldCoreAssembly.WARMUP_LENGTH;
                updateActiveBlock();
                level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE,
                        SoundSource.BLOCKS, 2.2F, 0.55F);
                level.playSound(null, worldPosition, SoundEvents.END_PORTAL_SPAWN,
                        SoundSource.BLOCKS, 1.8F, 0.6F);
                syncBlock();
            }
        }

        // 循环命令方块：稳定运行之后每秒执行一次
        if (running && commandMode == 1 && !commandText.isBlank()) {
            if (--loopCooldown <= 0) {
                loopCooldown = LOOP_INTERVAL;
                if (!conditional || lastResult == 1) {
                    runCommand();
                }
            }
        }

        if (--coreSyncCooldown <= 0) {
            coreSyncCooldown = 5;
            syncCoreEntity();
        }

        if (--syncCooldown <= 0) {
            syncCooldown = 20;
            updateActiveBlock();
            syncBlock();
        }
    }

    /**
     * 维护「爆发出来的核心」那个实体：爆发开始就生成，收回去就回收。
     *
     * <p>核心的外观和碰撞都归实体管（见 {@link WorldCoreCoreEntity}），控制器只负责在它该出现时
     * 把它放到中轴上、该消失时把它收掉。实体自己也会检查控制器状态，双保险。</p>
     */
    private void syncCoreEntity() {
        if (level == null || level.isClientSide) {
            return;
        }
        AABB area = new AABB(worldPosition).inflate(6.0D, 26.0D, 6.0D);
        List<WorldCoreCoreEntity> cores = level.getEntitiesOfClass(WorldCoreCoreEntity.class, area,
                core -> core.getOwnerPos().equals(worldPosition));
        float burst = coreBurstAt(level.getGameTime());
        if (burst <= 0.001F) {
            for (WorldCoreCoreEntity core : cores) {
                core.discard();
            }
            return;
        }
        // 同一台控制器只留一个核心
        for (int i = 1; i < cores.size(); i++) {
            cores.get(i).discard();
        }
        if (!cores.isEmpty()) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        WorldCoreCoreEntity core = ModEntities.WORLD_CORE_CORE.get().create(serverLevel);
        if (core == null) {
            return;
        }
        core.setup(worldPosition);
        serverLevel.addFreshEntity(core);
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
    // 结构、链接与供能
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
        updateActiveBlock();
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.BLOCKS, 1.8F, 0.55F);
        if (source != null) {
            source.displayClientMessage(Component.translatable("message.arcane_singularity.world_core_formed"), true);
        }
        linkCooldown = 0;
        scanLinks();
        refreshPower();
        syncBlock();
        return true;
    }

    private List<ArcaneStructure.Part> parts() {
        return ArcaneStructure.parts(StructureKind.WORLD_CORE, Direction.NORTH);
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
        shutdownGlyphAngle = 0.0D;
        projectionTicks = 0;
        interfaceMasks = new int[3];
        ProjectionService.clear(level, worldPosition, StructureKind.WORLD_CORE, Direction.NORTH);
        if (unform) {
            for (ArcaneStructure.Part part : parts()) {
                BlockPos target = worldPosition.offset(part.offset());
                BlockState state = level.getBlockState(target);
                if (part.type() == ArcaneStructure.PartType.CONTROLLER) {
                    if (state.getBlock() instanceof WorldCoreControllerBlock) {
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

    /** 扫描三个接口各自的专属管道网，记下三位掩码。 */
    private void scanLinks() {
        if (level == null || level.isClientSide) {
            return;
        }
        List<BlockPos> offsets = ArcaneStructure.worldCoreInterfaceOffsets();
        int[] masks = new int[offsets.size()];
        for (int i = 0; i < offsets.size(); i++) {
            BlockPos interfacePos = worldPosition.offset(offsets.get(i));
            if (!level.isLoaded(interfacePos)) {
                continue;
            }
            masks[i] = WorldCoreLink.scan(level, interfacePos);
        }
        interfaceMasks = masks;
    }

    private int maskAt(int index) {
        return index >= 0 && index < interfaceMasks.length ? interfaceMasks[index] : 0;
    }

    private int linkMaskAll() {
        int mask = 0;
        for (int value : interfaceMasks) {
            mask |= value;
        }
        return mask;
    }

    /** 三座结构是不是都接好了并且在运行。 */
    public boolean linkOnline() {
        return (linkMaskAll() & WorldCoreLink.LINK_ALL) == WorldCoreLink.LINK_ALL;
    }

    /** 扫描能量网络，并按「供能 + 红石 + 链接 + 核心 + 成型」决定开机还是关机。 */
    private void refreshPower() {
        if (level == null || level.isClientSide) {
            return;
        }
        List<BlockPos> ports = new ArrayList<>();
        for (BlockPos offset : ArcaneStructure.offsetsOf(StructureKind.WORLD_CORE, Direction.NORTH,
                ArcaneStructure.PartType.PORT)) {
            BlockPos portPos = worldPosition.offset(offset);
            ports.add(portPos);
            // 端口自己记一份归属：方块被拆、被换掉时能立刻知道该找哪台控制器
            if (level.isLoaded(portPos) && level.getBlockEntity(portPos) instanceof EnergyPortBlockEntity port) {
                port.setControllerPos(worldPosition);
            }
        }
        ArcaneEnergyNetwork.Result result = ArcaneEnergyNetwork.scan(level, ports,
                running ? ArcaneEnergyNetwork.OPEN_DEMAND : ArcaneEnergyNetwork.IDLE_DEMAND);
        powered = result.choose(null) != null;

        boolean want = formed && powered && redstoneMode.permits(redstoneSignal)
                && linkOnline() && hasCore();
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
        shutdownGlyphAngle = 0.0D;
        loopCooldown = 0;
        running = false;
        updateActiveBlock();
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 2.4F, 0.5F);
        level.playSound(null, worldPosition, SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.BLOCKS, 1.6F, 0.6F);
        updateStatus();
        syncBlock();
    }

    private void beginShutdown() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean wasDoing = running || warmupStartTick > 0L;
        if (wasDoing && shutdownStamp == 0L) {
            double ticks = warmupStartTick > 0L
                    ? Math.max(0.0D, level.getGameTime() - warmupStartTick) : 0.0D;
            shutdownGlyphAngle = WorldCoreAssembly.glyphAngleRawDeg(
                    Math.max(0.0D, ticks - WorldCoreAssembly.GLYPH_OPEN_END));
            shutdownStamp = level.getGameTime();
            level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 1.6F, 0.85F);
        }
        running = false;
        warmupTicks = 0;
        // 注意：warmupStartTick 故意保留到关机动画播完，渲染器要靠它继续算「倒放」的各幕进度
        updateActiveBlock();
        updateStatus();
        syncBlock();
    }

    private void updateStatus() {
        if (!formed) {
            status = 0;
            return;
        }
        if (!hasCore()) {
            status = 1;
        } else if (!powered) {
            status = 2;
        } else if (!redstoneMode.permits(redstoneSignal)) {
            status = 3;
        } else if (!linkOnline()) {
            status = 4;
        } else if (shutdownStamp > 0L) {
            status = 7;
        } else if (running) {
            status = 6;
        } else if (warmupStartTick > 0L) {
            status = 5;
        } else {
            status = 8;
        }
    }

    // ------------------------------------------------------------------
    // 命令
    // ------------------------------------------------------------------

    /** 界面里改命令文本（服务端）。 */
    public void setCommandText(String text) {
        String next = text == null ? "" : text.trim();
        if (next.length() > MAX_COMMAND_LENGTH) {
            next = next.substring(0, MAX_COMMAND_LENGTH);
        }
        if (next.equals(commandText)) {
            return;
        }
        commandText = next;
        lastResult = 0;
        setChanged();
        syncBlock();
    }

    /** 界面里点「完成」：先存命令，再尝试执行一次。 */
    public boolean submitCommand(@Nullable ServerPlayer player, String text) {
        setCommandText(text);
        if (!running) {
            lastOutput = Component.translatable("gui.arcane_singularity.world_core.not_ready").getString();
            lastResult = 2;
            syncBlock();
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("message.arcane_singularity.world_core_not_ready"), true);
            }
            return false;
        }
        if (conditional && lastResult != 1) {
            return false;
        }
        return runCommand();
    }

    /** 真正执行一次命令，输出写进 lastOutput。 */
    public boolean runCommand() {
        if (level == null || level.isClientSide || !running) {
            return false;
        }
        String command = commandText == null ? "" : commandText.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.isEmpty()) {
            lastOutput = "";
            lastResult = 0;
            syncBlock();
            return false;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        MinecraftServer server = serverLevel.getServer();
        CaptureSource capture = new CaptureSource();
        CommandSourceStack source = new CommandSourceStack(capture,
                Vec3.atCenterOf(worldPosition), Vec2.ZERO, serverLevel, 2,
                "WorldCore", Component.translatable("block.arcane_singularity.world_core_controller"),
                server, null);
        try {
            int result = server.getCommands().performPrefixedCommand(source, command);
            lastResult = result > 0 ? 1 : 2;
            String output = capture.joined();
            lastOutput = output.isEmpty()
                    ? Component.translatable(result > 0
                            ? "gui.arcane_singularity.world_core.result_ok"
                            : "gui.arcane_singularity.world_core.result_none").getString()
                    : output;
        } catch (RuntimeException exception) {
            lastResult = 2;
            lastOutput = exception.getMessage() == null ? exception.toString() : exception.getMessage();
        }
        setChanged();
        syncBlock();
        return lastResult == 1;
    }

    /** 把命令执行时反馈的消息收集起来，原样显示在界面的「输出」面板里。 */
    private static final class CaptureSource implements CommandSource {
        private final List<String> lines = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            if (lines.size() < MAX_OUTPUT_LINES) {
                lines.add(message.getString());
            }
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        String joined() {
            return String.join("\n", lines);
        }
    }

    // ------------------------------------------------------------------
    // 动画状态（渲染器用）
    // ------------------------------------------------------------------

    public int phase() {
        if (shutdownStamp > 0L) {
            return 3;
        }
        if (running) {
            return 2;
        }
        if (warmupStartTick > 0L) {
            return 1;
        }
        return 0;
    }

    /** 当前处在第几幕（界面里显示「初始化中 · 第 n 幕」）。 */
    public int stage() {
        if (running) {
            return 6;
        }
        if (warmupStartTick <= 0L) {
            return 0;
        }
        int ticks = warmupTicks;
        if (ticks >= WorldCoreAssembly.CORE_BURST_TICK) {
            return 5;
        }
        if (ticks >= WorldCoreAssembly.CONVERGE_TICK) {
            return 4;
        }
        if (ticks >= WorldCoreAssembly.GLYPH_OPEN_TICK) {
            return 3;
        }
        if (ticks >= WorldCoreAssembly.GATHER_TICK) {
            return 2;
        }
        return 1;
    }

    private double warmTicks(double time) {
        if (warmupStartTick <= 0L) {
            return 0.0D;
        }
        return Math.max(0.0D, time - warmupStartTick);
    }

    /** 关机倒放：每一幕在关机进度的某个区间里收掉，越靠后的幕越晚消失。 */
    private float shutdownFade(double time, double from, double to) {
        if (shutdownStamp <= 0L) {
            return 1.0F;
        }
        float progress = shutdownProgress(time);
        double span = Math.max(1.0E-4D, to - from);
        double amount = Mth.clamp((progress - from) / span, 0.0D, 1.0D);
        return (float) (1.0D - amount);
    }

    public float chargeAt(double time) {
        return WorldCoreAssembly.charge01(warmTicks(time)) * shutdownFade(time, 0.62D, 1.0D);
    }

    public float gatherAt(double time) {
        return WorldCoreAssembly.gather01(warmTicks(time)) * shutdownFade(time, 0.55D, 0.95D);
    }

    public float glyphOpenAt(double time) {
        return WorldCoreAssembly.glyphOpen01(warmTicks(time)) * shutdownFade(time, 0.42D, 0.85D);
    }

    public double glyphAngleRawAt(double time) {
        if (shutdownStamp > 0L) {
            return shutdownGlyphAngle;
        }
        return WorldCoreAssembly.glyphAngleRawDeg(
                Math.max(0.0D, warmTicks(time) - WorldCoreAssembly.GLYPH_OPEN_END));
    }

    public float glyphSpinRampAt(double time) {
        if (shutdownStamp > 0L) {
            return 0.0F;
        }
        return WorldCoreAssembly.glyphSpinRamp01(
                Math.max(0.0D, warmTicks(time) - WorldCoreAssembly.GLYPH_OPEN_END));
    }

    public float convergeAt(double time) {
        return WorldCoreAssembly.converge01(warmTicks(time)) * shutdownFade(time, 0.30D, 0.70D);
    }

    public float coreBurstAt(double time) {
        return WorldCoreAssembly.coreBurst01(warmTicks(time)) * shutdownFade(time, 0.0D, 0.35D);
    }

    /** 整体光效强度：关机时平滑收到 0。 */
    public float beamPowerAt(double time) {
        if (shutdownStamp > 0L) {
            double progress = Mth.clamp((time - shutdownStamp) / (double) WorldCoreAssembly.SHUTDOWN_LENGTH,
                    0.0D, 1.0D);
            return Mth.clamp((float) (1.0D - progress * 1.6D), 0.0F, 1.0F);
        }
        if (running) {
            return 1.0F;
        }
        if (shutdownStamp > 0L) {
            return shutdownFade(time, 0.0D, 1.0D);
        }
        return 0.0F;
    }

    public float shutdownProgress(double time) {
        if (shutdownStamp <= 0L) {
            return 0.0F;
        }
        return Mth.clamp((float) ((time - shutdownStamp) / (double) WorldCoreAssembly.SHUTDOWN_LENGTH),
                0.0F, 1.0F);
    }

    // ------------------------------------------------------------------
    // 界面操作
    // ------------------------------------------------------------------

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

    public boolean cycleCommandMode(@Nullable ServerPlayer player) {
        commandMode = (commandMode + 1) % 2;
        setChanged();
        syncBlock();
        if (player != null) {
            player.displayClientMessage(Component.translatable(
                    "message.arcane_singularity.world_core_mode",
                    Component.translatable(commandMode == 0
                            ? "gui.arcane_singularity.world_core.mode.pulse"
                            : "gui.arcane_singularity.world_core.mode.cycle")), true);
        }
        return true;
    }

    public boolean cycleConditional(@Nullable ServerPlayer player) {
        conditional = !conditional;
        setChanged();
        syncBlock();
        return true;
    }

    public boolean startProjection(ServerPlayer player) {
        if (formed) {
            player.displayClientMessage(Component.translatable("message.arcane_singularity.projection_formed"), true);
            return false;
        }
        projectionTicks = 600;
        ProjectionService.clear(level, worldPosition, StructureKind.WORLD_CORE, Direction.NORTH);
        ProjectionService.show(level, worldPosition, StructureKind.WORLD_CORE, Direction.NORTH);
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
     * 渲染包围盒：整场仪式（三道接口激光、40 格高的魔法阵、对射光束、巨大的命令方块）
     * 都是以这个方块为锚点画出来的，比一个方块大得多。
     *
     * <p>原版用 {@code getRenderBoundingBox()} 做视锥剔除，默认只返回这个方块自己那一格：
     * 站在底座上抬头看魔法阵、或者把镜头转开一点，方块本体还在视野外，
     * 整场特效就会被一起剔掉、凭空消失。这里把包围盒扩大到覆盖全部特效的范围，
     * 于是只要特效本身在视野里，它就一定被画出来。</p>
     */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos pos = getBlockPos();
        return new AABB(pos.getX() - 19.0D, pos.getY() - 3.0D, pos.getZ() - 19.0D,
                pos.getX() + 20.0D, pos.getY() + 54.0D, pos.getZ() + 20.0D);
    }

    public boolean isPowered() {
        return powered;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isActive() {
        return running || warmupStartTick > 0L || shutdownStamp > 0L;
    }

    public int getProjectionTicks() {
        return projectionTicks;
    }

    public int getLinkMask() {
        return linkMaskAll();
    }

    public int getCommandMode() {
        return commandMode;
    }

    public boolean isConditional() {
        return conditional;
    }

    public int getLastResult() {
        return lastResult;
    }

    public String getCommandText() {
        return commandText;
    }

    public String getLastOutput() {
        return lastOutput;
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

    // ------------------------------------------------------------------
    // 容器（世界核心槽）
    // ------------------------------------------------------------------

    public boolean hasCore() {
        return !items.get(0).isEmpty() && items.get(0).is(ModItems.WORLD_CORE.get());
    }

    /** 控制器被拆掉时把槽里的世界核心吐出来，别让它跟着方块一起消失。 */
    public void dropContents() {
        if (level == null || level.isClientSide || isRemoved()) {
            return;
        }
        ItemStack stack = items.get(0);
        if (!stack.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource(level, worldPosition, stack.copy());
            items.set(0, ItemStack.EMPTY);
        }
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
            powerCooldown = 0;
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        boolean had = hasCore();
        items.set(slot, stack);
        boolean has = hasCore();
        if (!level.isClientSide) {
            if (had != has) {
                powerCooldown = 0;
                updateStatus();
            }
            setChanged();
            syncBlock();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(ModItems.WORLD_CORE.get());
    }

    @Override
    public void clearContent() {
        items.set(0, ItemStack.EMPTY);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.arcane_singularity.world_core_controller");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new WorldCoreMenu(containerId, inventory, worldPosition);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.arcane_singularity.world_core_controller");
    }

    public ContainerData dataAccess() {
        return dataAccess;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putBoolean("Formed", formed);
        tag.putBoolean("Powered", powered);
        tag.putBoolean("Running", running);
        tag.putInt("Warmup", warmupTicks);
        tag.putLong("WarmupStart", warmupStartTick);
        tag.putLong("ShutdownStamp", shutdownStamp);
        tag.putDouble("ShutdownGlyph", shutdownGlyphAngle);
        tag.putInt("Projection", projectionTicks);
        tag.putInt("Status", status);
        tag.putInt("RedstoneMode", redstoneMode.id());
        tag.putInt("RedstoneSignal", redstoneSignal);
        tag.putInt("CommandMode", commandMode);
        tag.putBoolean("Conditional", conditional);
        tag.putString("Command", commandText);
        tag.putString("Output", lastOutput);
        tag.putInt("LastResult", lastResult);
        tag.putIntArray("InterfaceMasks", interfaceMasks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.set(0, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        formed = tag.getBoolean("Formed");
        powered = tag.getBoolean("Powered");
        running = tag.getBoolean("Running");
        warmupTicks = tag.getInt("Warmup");
        warmupStartTick = tag.getLong("WarmupStart");
        shutdownStamp = tag.getLong("ShutdownStamp");
        shutdownGlyphAngle = tag.getDouble("ShutdownGlyph");
        projectionTicks = tag.getInt("Projection");
        status = tag.getInt("Status");
        redstoneMode = RedstoneMode.byId(tag.getInt("RedstoneMode"));
        redstoneSignal = tag.getInt("RedstoneSignal");
        commandMode = tag.getInt("CommandMode") % 2;
        conditional = tag.getBoolean("Conditional");
        commandText = tag.getString("Command");
        lastOutput = tag.getString("Output");
        lastResult = tag.getInt("LastResult");
        int[] masks = tag.getIntArray("InterfaceMasks");
        interfaceMasks = new int[3];
        for (int i = 0; i < Math.min(3, masks.length); i++) {
            interfaceMasks[i] = masks[i];
        }
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

    /** 内容变了才同步，避免稳定运转时每 tick 都让区块变脏。 */
    private void syncBlock() {
        if (level == null || level.isClientSide) {
            return;
        }
        String key = formed + "|" + powered + "|" + running + "|" + warmupTicks + "|" + warmupStartTick
                + "|" + shutdownStamp + "|" + shutdownGlyphAngle + "|" + projectionTicks + "|" + status
                + "|" + redstoneMode.id() + "|" + redstoneSignal + "|" + commandMode + "|" + conditional
                + "|" + commandText + "|" + lastOutput + "|" + lastResult + "|" + linkMaskAll();
        if (key.equals(lastSyncKey)) {
            return;
        }
        lastSyncKey = key;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
}
