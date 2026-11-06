package cn.blockforge.generated.arcanesingularity.blockentity;

import cn.blockforge.generated.arcanesingularity.block.ArcaneProperties;
import cn.blockforge.generated.arcanesingularity.block.StargateControllerBlock;
import cn.blockforge.generated.arcanesingularity.data.GateId;
import cn.blockforge.generated.arcanesingularity.data.StargateData;
import cn.blockforge.generated.arcanesingularity.energy.ArcaneEnergyNetwork;
import cn.blockforge.generated.arcanesingularity.entity.StargateRingPartEntity;
import cn.blockforge.generated.arcanesingularity.menu.StargateMenu;
import cn.blockforge.generated.arcanesingularity.redstone.RedstoneMode;
import cn.blockforge.generated.arcanesingularity.registry.ModBlockEntities;
import cn.blockforge.generated.arcanesingularity.registry.ModBlocks;
import cn.blockforge.generated.arcanesingularity.structure.ArcaneStructure;
import cn.blockforge.generated.arcanesingularity.registry.ModEntities;
import cn.blockforge.generated.arcanesingularity.structure.ChunkKeeper;
import cn.blockforge.generated.arcanesingularity.structure.ProjectionService;
import cn.blockforge.generated.arcanesingularity.structure.StargateRingAssembly;
import cn.blockforge.generated.arcanesingularity.structure.StructureKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class StargateControllerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int DATA_COUNT = 11;
    public static final int INDEX_REDSTONE_MODE = 9;
    public static final int INDEX_REDSTONE_SIGNAL = 10;
    /** 传送门开启动画长度（tick）。 */
    public static final int OPEN_LENGTH = 46;
    /** 传送门面收缩动画长度（tick）；门环刹车另按剩余角度动态计算，比这更长。 */
    public static final int CLOSE_LENGTH = 30;
    /** 强加载自愈间隔（tick）：每十秒确认一次整座星门覆盖的区块都还在强加载列表里。 */
    private static final int CHUNK_KEEP_INTERVAL = 200;
    /** 传送判定在门面法线方向上的厚度（半格多一点）：站进门面里就算进去了。 */
    private static final double PORTAL_THICKNESS = 0.9D;

    private boolean formed;
    private boolean powered;
    private boolean open;
    private boolean partnerOnline;
    /** 对面那扇门此刻是不是也开着（跨维度读 {@link StargateData} 的开门记录）。 */
    private boolean partnerOpen;
    private boolean hasPair;
    private int projectionTicks;
    private int status;
    private int validateCooldown;
    private int powerCooldown;
    private int syncCooldown;
    private String sourceId = "";
    private String partnerDimension = "";
    private String partnerCoords = "";
    private String partnerSource = "";
    private RedstoneMode redstoneMode = RedstoneMode.AUTO;
    private int redstoneSignal;
    /** 传送门张开 / 闭合的时刻，客户端按它自己推算动画进度，不必每 tick 同步。 */
    private long openStamp;
    private long closeStamp;
    /** 门环是否已经交给实体（方块换成空模型 + 无碰撞）。 */
    private boolean ringEngaged;
    private int ringPartsCooldown;
    /** load() 里发现「未成型但仍留有配对」时只做标记，真正清理放到 tick，避免在区块加载时碰 SavedData。 */
    private boolean pendingInvalidate;
    /** 结构强加载的定期自愈倒计时。初值 0 ⇒ 方块实体第一 tick 就会登记一次（重进世界也能补回来）。 */
    private int chunkKeepCooldown;
    /** 上一 tick 是否供能：断开再接通就等于玩家重新授权开门。 */
    private boolean previousPowered;
    /** 是否已经记录过一次供能状态：读档后的第一 tick 不能当成「重新接电」。 */
    private boolean powerInitialized;

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> formed ? 1 : 0;
                case 1 -> powered ? 1 : 0;
                case 2 -> open ? 1 : 0;
                case 3 -> hasPair ? 1 : 0;
                case 4 -> partnerOnline ? 1 : 0;
                case 5 -> projectionTicks;
                case 6 -> status;
                case 7 -> sourceId.isEmpty() ? 0 : 1;
                case 8 -> DATA_COUNT;
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

    public StargateControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STARGATE_CONTROLLER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StargateControllerBlockEntity be) {
        be.tickServer();
    }

    private void tickServer() {
        if (level == null || level.isClientSide) {
            return;
        }
        // 整座星门覆盖的区块保持强加载：第一 tick 登记，之后定期补一次，免得被别的东西撤掉。
        if (--chunkKeepCooldown <= 0) {
            chunkKeepCooldown = CHUNK_KEEP_INTERVAL;
            ChunkKeeper.keep(level, worldPosition, StructureKind.STARGATE, facing());
        }
        if (pendingInvalidate) {
            pendingInvalidate = false;
            GateId self = GateId.of(level, worldPosition);
            StargateData data = StargateData.get(level.getServer());
            if (data.partnerOf(self) != null) {
                data.invalidate(self, level.getGameTime());
            }
        }

        if (projectionTicks > 0) {
            projectionTicks--;
            if (projectionTicks == 0) {
                ProjectionService.clear(level, worldPosition, StructureKind.STARGATE, facing());
                syncBlock();
            } else if (projectionTicks % 10 == 0) {
                syncBlock();
            }
        }

        boolean signalChanged = sampleRedstone();
        if (signalChanged && formed) {
            refreshPower();
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

        // 关门动画播完：门环刹回原位，这时才把门框方块放回去、回收带碰撞的环实体
        if (ringEngaged && !open && closeStamp > 0L
                && level.getGameTime() - closeStamp
                >= StargateRingAssembly.releaseTicksAt(openStamp, closeStamp)) {
            applyRingMode(false);
            syncRingParts();
        }

        if (--powerCooldown <= 0) {
            powerCooldown = 20;
            refreshPower();
        }
        if (--syncCooldown <= 0) {
            syncCooldown = 10;
            syncBlock();
        }
        if (--ringPartsCooldown <= 0) {
            ringPartsCooldown = 20;
            syncRingParts();
        }
        if (open && level.getGameTime() % 3L == 0L) {
            teleportEntities();
        }
    }

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
        BlockState controller = level.getBlockState(worldPosition);
        if (controller.getBlock() instanceof StargateControllerBlock) {
            level.setBlock(worldPosition, controller.setValue(ArcaneProperties.FORMED, true), 3);
        }
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
        open = false;
        powered = false;
        updateActiveBlock();
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.6F, 0.7F);
        if (source != null) {
            source.displayClientMessage(Component.translatable("message.arcane_singularity.gate_formed"), true);
        }
        refreshPower();
        syncBlock();
        return true;
    }

    private List<ArcaneStructure.Part> parts() {
        return ArcaneStructure.parts(StructureKind.STARGATE, facing());
    }

    private Direction facing() {
        if (level == null) {
            return Direction.NORTH;
        }
        BlockState state = level.getBlockState(worldPosition);
        return state.getBlock() instanceof StargateControllerBlock
                ? state.getValue(StargateControllerBlock.FACING)
                : Direction.NORTH;
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

    private void refreshPower() {
        if (level == null || level.isClientSide) {
            return;
        }
        GateId self = GateId.of(level, worldPosition);
        StargateData data = StargateData.get(level.getServer());
        GateId partner = data.partnerOf(self);
        long now = level.getGameTime();

        List<BlockPos> ports = new ArrayList<>();
        for (BlockPos offset : ArcaneStructure.offsetsOf(StructureKind.STARGATE, facing(),
                ArcaneStructure.PartType.PORT)) {
            ports.add(worldPosition.offset(offset));
        }
        ArcaneEnergyNetwork.Result result = ArcaneEnergyNetwork.scan(level, ports,
                open ? ArcaneEnergyNetwork.OPEN_DEMAND : ArcaneEnergyNetwork.IDLE_DEMAND);
        partnerSource = partner == null ? "" : data.sourceOf(partner);
        ArcaneEnergyNetwork.Source source = result.choose(partnerSource);
        sourceId = source == null ? "" : source.id();
        powered = source != null;

        hasPair = partner != null;
        if (partner != null) {
            partnerDimension = partner.dimension().toString();
            partnerCoords = partner.pos().getX() + ", " + partner.pos().getY() + ", " + partner.pos().getZ();
            partnerOnline = data.isOnline(partner, now);
            partnerOpen = data.isGateOpen(partner, now);
        } else {
            partnerDimension = "";
            partnerCoords = "";
            partnerOnline = false;
            partnerOpen = false;
        }

        data.report(self, powered, sourceId, now);

        // 供能断开又恢复，等于玩家重新授权开门：这时才解除「强制关闭」锁。
        if (powerInitialized && powered && !previousPowered) {
            data.unlock(self);
        }
        powerInitialized = true;
        previousPowered = powered;

        long forcedUntil = data.forcedUntil(self);
        boolean sameSource = source != null && !partnerSource.isEmpty() && source.id().equals(partnerSource);
        // 关闭锁：自己锁着、或者对端（玩家关了另一边）锁着，都不许开
        boolean forced = forcedUntil > now || data.isCloseLocked(self)
                || (partner != null && data.isCloseLocked(partner));
        boolean gate = redstoneMode.permits(redstoneSignal);
        boolean canOpen = gate && formed && powered && partnerOnline && !sameSource && !forced
                && partner != null && self.equals(data.partnerOf(partner));

        if (sameSource) {
            status = 5;
        } else if (!hasPair) {
            status = 1;
        } else if (!gate) {
            status = 7;
        } else if (forced) {
            status = 6;
        } else if (!powered) {
            status = 2;
        } else if (!partnerOnline) {
            status = 3;
        } else if (canOpen) {
            // 传送要求「两个星门同时打开」，所以只有对面也开着才显示「已开启」
            status = partnerOpen ? 4 : 8;
        } else {
            status = 3;
        }

        if (open != canOpen) {
            open = canOpen;
            if (open) {
                openStamp = level.getGameTime();
                // 门一开就把门框方块换成空模型，并立刻铺出带碰撞的环实体
                applyRingMode(true);
                ringPartsCooldown = 0;
            } else {
                closeStamp = level.getGameTime();
            }
            updateActiveBlock();
            updatePorts();
            level.playSound(null, worldPosition,
                    open ? SoundEvents.PORTAL_TRIGGER : SoundEvents.PORTAL_TRAVEL,
                    SoundSource.BLOCKS, 1.4F, open ? 0.6F : 1.4F);
        }
        // 本扇门的开门状态广播给另一扇门：传送判定要靠它确认「两端同时开着」
        data.reportOpen(self, open, now);
        syncBlock();
    }

    /**
     * 读取控制器任意一面接入的红石信号，上升沿时自动把接口从「自动」切到「高电平运行」。
     */
    private boolean sampleRedstone() {
        if (level == null || level.isClientSide) {
            return false;
        }
        int signal = level.getBestNeighborSignal(worldPosition);
        if (signal == redstoneSignal) {
            return false;
        }
        int previous = redstoneSignal;
        redstoneSignal = signal;
        if (previous == 0 && signal > 0) {
            // 重新拉一次红石＝重新开门：把「强制关闭」的锁解掉
            StargateData data = StargateData.get(level.getServer());
            if (data.isCloseLocked(GateId.of(level, worldPosition))) {
                data.unlock(GateId.of(level, worldPosition));
                refreshPower();
            }
        }
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
        return true;
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
        refreshPower();
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.arcane_singularity.redstone_mode",
                    Component.translatable(redstoneMode.translationKey())), true);
        }
        return true;
    }

    /** 门环当前转角（度）。开门时像车轮一样匀速转，关门时用关门动画的长度刹回原位。 */
    public double ringAngleAt(double time) {
        return StargateRingAssembly.ringAngleDeg(time, openStamp, closeStamp, open);
    }

    /** 门环是否还处在实体化状态（开启中，或正在刹车归位）。 */
    public boolean isRingSpinning() {
        if (!formed) {
            return false;
        }
        if (open) {
            return true;
        }
        return closeStamp > 0L && level != null
                && level.getGameTime() - closeStamp
                < StargateRingAssembly.releaseTicksAt(openStamp, closeStamp);
    }

    public boolean isRingEngaged() {
        return ringEngaged;
    }

    /**
     * 把门框方块换成「空模型 + 无碰撞」，视觉与碰撞交给绕门心自转的实体；停止时换回来。
     */
    private void applyRingMode(boolean engaged) {
        if (level == null || level.isClientSide) {
            return;
        }
        Direction facing = facing();
        for (StargateRingAssembly.RingPart part : StargateRingAssembly.ringParts()) {
            BlockPos offset = ArcaneStructure.rotateLocal(part.offset().getX(), part.offset().getY(),
                    part.offset().getZ(), facing);
            BlockPos target = worldPosition.offset(offset);
            if (!level.isLoaded(target)) {
                continue;
            }
            BlockState state = level.getBlockState(target);
            if (state.getBlock() != ModBlocks.STARGATE_FRAME.get()
                    || !state.hasProperty(ArcaneProperties.ACTIVE)) {
                continue;
            }
            if (state.getValue(ArcaneProperties.ACTIVE) == engaged) {
                continue;
            }
            level.setBlock(target, state.setValue(ArcaneProperties.ACTIVE, engaged), 3);
        }
        ringEngaged = engaged;
    }

    /** 生成/回收门环的碰撞实体，数量对不上时补齐或清掉多余的。 */
    private void syncRingParts() {
        if (level == null || level.isClientSide) {
            return;
        }
        AABB area = new AABB(worldPosition).inflate(12.0D, 20.0D, 12.0D);
        List<StargateRingPartEntity> parts = level.getEntitiesOfClass(StargateRingPartEntity.class, area,
                part -> part.getOwnerPos().equals(worldPosition));
        if (!isRingSpinning()) {
            for (StargateRingPartEntity part : parts) {
                part.discard();
            }
            return;
        }
        List<StargateRingAssembly.RingPart> blueprint = StargateRingAssembly.ringParts();
        int count = blueprint.size();
        if (parts.size() > count * 2) {
            // 兜底：历史存档若留下多余实体，整批回收后按蓝图重铺
            for (StargateRingPartEntity part : parts) {
                part.discard();
            }
            parts = List.of();
        }
        boolean[] seen = new boolean[count];
        for (StargateRingPartEntity part : parts) {
            int index = part.getPartIndex();
            if (index < 0 || index >= count || seen[index]) {
                part.discard();
            } else {
                seen[index] = true;
            }
        }
        double ringAngle = ringAngleAt(level.getGameTime());
        Direction facing = facing();
        for (int i = 0; i < count; i++) {
            if (seen[i]) {
                continue;
            }
            if (!isRingPositionLoaded(blueprint.get(i), ringAngle, facing)) {
                continue;
            }
            spawnRingPart(i);
        }
    }

    private boolean isRingPositionLoaded(StargateRingAssembly.RingPart part, double ringAngle, Direction facing) {
        if (level == null) {
            return false;
        }
        Vec3 center = StargateRingAssembly.partCenter(worldPosition, facing, part, ringAngle);
        return level.isLoaded(BlockPos.containing(center.x, center.y, center.z));
    }

    private void spawnRingPart(int index) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        StargateRingPartEntity part = ModEntities.STARGATE_RING_PART.get().create(serverLevel);
        if (part == null) {
            return;
        }
        part.setup(worldPosition, index, facing(), openStamp);
        serverLevel.addFreshEntity(part);
    }

    private void updatePorts() {
        if (level == null) {
            return;
        }
        for (BlockPos offset : ArcaneStructure.offsetsOf(StructureKind.STARGATE, facing(),
                ArcaneStructure.PartType.PORT)) {
            BlockPos target = worldPosition.offset(offset);
            if (!level.isLoaded(target)) {
                continue;
            }
            BlockState state = level.getBlockState(target);
            if (state.getBlock() != ModBlocks.ENERGY_INPUT_PORT.get()) {
                continue;
            }
            BlockState next = state.setValue(ArcaneProperties.ACTIVE, open);
            if (next != state) {
                level.setBlock(target, next, 3);
            }
            if (level.getBlockEntity(target) instanceof EnergyPortBlockEntity port) {
                port.setControllerPos(worldPosition);
            }
        }
    }

    private void updateActiveBlock() {
        if (level == null) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(ArcaneProperties.ACTIVE) && state.getValue(ArcaneProperties.ACTIVE) != open) {
            level.setBlock(worldPosition, state.setValue(ArcaneProperties.ACTIVE, open), 3);
        }
    }

    public void collapseStructure(boolean unform) {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean wasFormed = formed;
        formed = false;
        open = false;
        powered = false;
        partnerOnline = false;
        // 结构被破坏：门环直接复位，不播刹车动画
        closeStamp = 0L;
        applyRingMode(false);
        hasPair = false;
        projectionTicks = 0;
        ProjectionService.clear(level, worldPosition, StructureKind.STARGATE, facing());
        status = 0;
        sourceId = "";
        partnerDimension = "";
        partnerCoords = "";
        partnerSource = "";
        ringPartsCooldown = 0;
        syncRingParts();
        if (wasFormed) {
            StargateData data = StargateData.get(level.getServer());
            data.invalidate(GateId.of(level, worldPosition), level.getGameTime());
        }
        updatePorts();
        if (unform) {
            Direction facing = facing();
            for (ArcaneStructure.Part part : ArcaneStructure.parts(StructureKind.STARGATE, facing)) {
                BlockPos target = worldPosition.offset(part.offset());
                BlockState state = level.getBlockState(target);
                if (part.type() == ArcaneStructure.PartType.CONTROLLER) {
                    if (state.getBlock() instanceof StargateControllerBlock) {
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
        if (wasFormed) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2F, 1.2F);
        }
        syncBlock();
    }

    public boolean startProjection(ServerPlayer player) {
        if (formed) {
            player.displayClientMessage(Component.translatable("message.arcane_singularity.projection_formed"), true);
            return false;
        }
        projectionTicks = 600;
        ProjectionService.clear(level, worldPosition, StructureKind.STARGATE, facing());
        ProjectionService.show(level, worldPosition, StructureKind.STARGATE, facing());
        syncBlock();
        player.displayClientMessage(Component.translatable("message.arcane_singularity.projection"), true);
        return true;
    }

    public void forceClose(ServerPlayer player) {
        if (level == null || level.isClientSide) {
            return;
        }
        StargateData data = StargateData.get(level.getServer());
        GateId self = GateId.of(level, worldPosition);
        GateId partner = data.partnerOf(self);
        long now = level.getGameTime();
        data.forceClosePair(self, now);
        // 「关一个另一个一起关」：直接把对端也切到关门动画，不等它下一次自检。
        forceClosePartnerEntity(partner, now);
        open = false;
        data.reportOpen(self, false, now);
        // 强制关闭也要走关门动画，门环会像车轮一样刹停再复位
        closeStamp = now;
        status = 6;
        updateActiveBlock();
        updatePorts();
        syncBlock();
        player.displayClientMessage(Component.translatable("message.arcane_singularity.forced_close"), true);
    }

    /** 找到对端的方块实体并让它立刻关门（跨维度也能找到）。 */
    private void forceClosePartnerEntity(@Nullable GateId partner, long now) {
        if (level == null || partner == null) {
            return;
        }
        net.minecraft.server.MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        ServerLevel other = server.getLevel(ResourceKey.create(Registries.DIMENSION, partner.dimension()));
        if (other == null) {
            return;
        }
        other.getChunkAt(partner.pos());
        if (other.getBlockEntity(partner.pos()) instanceof StargateControllerBlockEntity otherGate) {
            otherGate.forceCloseFromPartner(now);
        }
    }

    /** 对端按了「关闭星门」：这边同步收起传送面，播放同样的刹车动画。 */
    public void forceCloseFromPartner(long now) {
        if (level == null || level.isClientSide || !formed) {
            return;
        }
        if (!open && closeStamp > 0L) {
            return;
        }
        open = false;
        StargateData data = StargateData.get(level.getServer());
        data.reportOpen(GateId.of(level, worldPosition), false, now);
        closeStamp = now;
        status = 6;
        updateActiveBlock();
        updatePorts();
        level.playSound(null, worldPosition, SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 1.4F, 1.4F);
        syncBlock();
    }

    private void teleportEntities() {
        if (!(level instanceof ServerLevel serverLevel) || !open) {
            return;
        }
        long now = serverLevel.getGameTime();
        GateId self = GateId.of(serverLevel, worldPosition);
        StargateData data = StargateData.get(serverLevel.getServer());
        GateId partner = data.partnerOf(self);
        if (partner == null || !data.isOnline(partner, now)) {
            return;
        }
        // 核心规则：只有「两个星门同时开着」才允许传送。
        // 单开一扇、或者两扇都关着（本方法入口已经挡掉了本端没开的情况）都不触发。
        if (!data.isGateOpen(partner, now) || data.isCloseLocked(partner)
                || !self.equals(data.partnerOf(partner))) {
            return;
        }
        ServerLevel targetLevel = serverLevel.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, partner.dimension()));
        if (targetLevel == null) {
            return;
        }

        Direction facing = facing();
        // 门心：门环圆心（蓝图常量）+ 半格，和渲染器、粒子用的是同一个高度
        Vec3 center = new Vec3(worldPosition.getX() + 0.5D,
                worldPosition.getY() + StargateRingAssembly.PORTAL_CENTER_Y,
                worldPosition.getZ() + 0.5D);
        // 判定范围 = 整张传送门面：门平面内按传送膜半径铺成方形，法线方向只有薄薄一层，
        // 所以门里任意高度、任意左右位置都能被传走（以前只覆盖了中间一块，站在门边上不生效）。
        double radius = StargateRingAssembly.PORTAL_RADIUS;
        AABB box = facing.getAxis() == Direction.Axis.X
                ? new AABB(center.x - PORTAL_THICKNESS, center.y - radius, center.z - radius,
                center.x + PORTAL_THICKNESS, center.y + radius, center.z + radius)
                : new AABB(center.x - radius, center.y - radius, center.z - PORTAL_THICKNESS,
                center.x + radius, center.y + radius, center.z + PORTAL_THICKNESS);

        List<Entity> entities = serverLevel.getEntitiesOfClass(Entity.class, box,
                entity -> entity.isAlive() && !(entity instanceof Player player && player.isSpectator())
                        && entity.getPortalCooldown() <= 0);
        if (entities.isEmpty()) {
            return;
        }

        targetLevel.getChunkAt(partner.pos());
        BlockState partnerState = targetLevel.getBlockState(partner.pos());
        Direction exitFacing = partnerState.getBlock() instanceof StargateControllerBlock
                ? partnerState.getValue(StargateControllerBlock.FACING)
                : Direction.NORTH;
        BlockPos exit = partner.pos().offset(exitFacing.getStepX() * 2, 1, exitFacing.getStepZ() * 2);
        for (int i = 0; i < 8; i++) {
            if (targetLevel.getBlockState(exit).isAir()
                    && targetLevel.getBlockState(exit.above()).isAir()) {
                break;
            }
            exit = exit.above();
        }
        double x = exit.getX() + 0.5D;
        double y = exit.getY();
        double z = exit.getZ() + 0.5D;
        float yaw = exitFacing.toYRot();

        for (Entity entity : entities) {
            if (entity instanceof ServerPlayer player) {
                player.teleportTo(targetLevel, x, y, z, yaw, player.getXRot());
                player.setPortalCooldown(100);
            } else {
                Entity moved = entity.changeDimension(targetLevel, new ITeleporter() {
                    @Override
                    public Entity placeEntity(Entity transported, ServerLevel currentWorld, ServerLevel destination,
                                              float rotationYaw, Function<Boolean, Entity> repositionEntity) {
                        Entity placed = repositionEntity.apply(false);
                        placed.moveTo(x, y, z, yaw, placed.getXRot());
                        placed.setPortalCooldown(100);
                        return placed;
                    }
                });
                if (moved != null) {
                    moved.setPortalCooldown(100);
                }
            }
        }
    }

    public boolean isFormed() {
        return formed;
    }

    /**
     * 渲染包围盒：整圈门框、传送膜和内缘等离子环都在方块之外，
     * 默认的一格包围盒会让镜头转开时整道门连同传送面一起消失。
     */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos pos = getBlockPos();
        return new AABB(pos.getX() - 14.0D, pos.getY() - 4.0D, pos.getZ() - 14.0D,
                pos.getX() + 15.0D, pos.getY() + 30.0D, pos.getZ() + 15.0D);
    }

    public boolean isOpen() {
        return open;
    }

    /** 传送门张开进度：0 完全关闭，1 完全打开；关闭时反向播放收缩动画。 */
    public float openProgress(double time) {
        if (open) {
            return Mth.clamp((float) ((time - openStamp) / OPEN_LENGTH), 0.0F, 1.0F);
        }
        // 从没开过门（closeStamp = 0）时传送面必须是收起的；否则新世界开局那几十 tick
        // 会算出一个非零进度，凭空画出一张门面。
        if (closeStamp <= 0L) {
            return 0.0F;
        }
        return 1.0F - Mth.clamp((float) ((time - closeStamp) / CLOSE_LENGTH), 0.0F, 1.0F);
    }

    public long getOpenStamp() {
        return openStamp;
    }

    public long getCloseStamp() {
        return closeStamp;
    }

    public int getProjectionTicks() {
        return projectionTicks;
    }

    public String getPartnerDimension() {
        return partnerDimension;
    }

    public String getPartnerCoords() {
        return partnerCoords;
    }

    public String getSourceId() {
        return sourceId;
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

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.arcane_singularity.stargate_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new StargateMenu(containerId, inventory, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Formed", formed);
        tag.putBoolean("Open", open);
        tag.putBoolean("Powered", powered);
        tag.putBoolean("HasPair", hasPair);
        tag.putBoolean("PartnerOnline", partnerOnline);
        tag.putInt("Projection", projectionTicks);
        tag.putInt("Status", status);
        tag.putString("Source", sourceId);
        tag.putString("PartnerDimension", partnerDimension);
        tag.putString("PartnerCoords", partnerCoords);
        tag.putString("PartnerSource", partnerSource);
        tag.putInt("RedstoneMode", redstoneMode.id());
        tag.putInt("RedstoneSignal", redstoneSignal);
        tag.putLong("OpenStamp", openStamp);
        tag.putLong("CloseStamp", closeStamp);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        formed = tag.getBoolean("Formed");
        open = tag.getBoolean("Open");
        powered = tag.getBoolean("Powered");
        hasPair = tag.getBoolean("HasPair");
        partnerOnline = tag.getBoolean("PartnerOnline");
        projectionTicks = tag.getInt("Projection");
        status = tag.getInt("Status");
        sourceId = tag.getString("Source");
        partnerDimension = tag.getString("PartnerDimension");
        partnerCoords = tag.getString("PartnerCoords");
        partnerSource = tag.getString("PartnerSource");
        redstoneMode = RedstoneMode.byId(tag.getInt("RedstoneMode"));
        redstoneSignal = tag.getInt("RedstoneSignal");
        openStamp = tag.getLong("OpenStamp");
        closeStamp = tag.getLong("CloseStamp");
        // 重载后门框方块本身就是存档里的状态，这里只需要把「已交给实体」的标记对齐
        ringEngaged = formed && open;
        if (!formed) {
            open = false;
            powered = false;
            partnerOnline = false;
            status = 0;
        }
        pendingInvalidate = !formed;
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

    @Override
    public void setRemoved() {
        // 区块卸载时也会走到这里。先确认方块真的不在了（且区块在），
        // 免得在保存/卸载过程中为了查一个方块把区块又同步拉起来。
        if (level != null && !level.isClientSide && level.getServer() != null && level.isLoaded(worldPosition)
                && !(level.getBlockState(worldPosition).getBlock() instanceof StargateControllerBlock)) {
            StargateData data = StargateData.get(level.getServer());
            data.invalidate(GateId.of(level, worldPosition), level.getGameTime());
        }
        super.setRemoved();
    }

    private String lastSyncKey = "";

    /**
     * 内容变了才同步。稳定运行/门开着的时候如果每 tick 都 setChanged，
     * 区块会一直是脏的，退出世界保存时要写的东西就更多。
     */
    private void syncBlock() {
        if (level == null || level.isClientSide) {
            return;
        }
        String key = formed + "|" + powered + "|" + open + "|" + hasPair + "|" + partnerOnline + "|"
                + partnerOpen + "|" + projectionTicks + "|" + status + "|" + sourceId + "|" + partnerDimension + "|"
                + partnerCoords + "|" + redstoneMode.id() + "|" + redstoneSignal + "|" + openStamp + "|"
                + closeStamp + "|" + ringEngaged;
        if (key.equals(lastSyncKey)) {
            return;
        }
        lastSyncKey = key;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
}
