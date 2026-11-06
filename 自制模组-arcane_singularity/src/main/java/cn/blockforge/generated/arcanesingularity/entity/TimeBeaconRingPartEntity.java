package cn.blockforge.generated.arcanesingularity.entity;

import cn.blockforge.generated.arcanesingularity.blockentity.TimeBeaconControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.structure.ArcaneStructure;
import cn.blockforge.generated.arcanesingularity.structure.TimeBeaconAssembly;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 时间信标能量汇聚环上的一小段「实体化」构件。
 *
 * <p>开机后控制器把两圈汇聚环方块换成空模型、清掉自身碰撞，然后放出这些实体：蓝图里每一个
 * 汇聚环方块对应一个实体，各自带一格见方的碰撞箱，按 {@link TimeBeaconAssembly} 的角度绕炮管中轴旋转。
 * 外观由控制器渲染器画出与该方块完全相同的模型，所以「看到的」和「撞到的」是同一圈东西。</p>
 *
 * <p>这些实体不写进存档：世界重载后由控制器按状态补建。</p>
 */
public class TimeBeaconRingPartEntity extends Entity {
    /** 碰撞箱比方块略宽，转起来没有能钻过去的缝。 */
    public static final float PART_WIDTH = 1.1F;
    public static final float PART_HEIGHT = 0.9F;

    private BlockPos owner = BlockPos.ZERO;
    private int partIndex;
    private long startTick;

    public TimeBeaconRingPartEntity(EntityType<? extends TimeBeaconRingPartEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public void setup(BlockPos owner, int partIndex, long startTick) {
        this.owner = owner;
        this.partIndex = partIndex;
        this.startTick = startTick;
        updateTransform(null);
    }

    public BlockPos getOwnerPos() {
        return owner;
    }

    public int getPartIndex() {
        return partIndex;
    }

    @Nullable
    private TimeBeaconAssembly.RingPart part() {
        List<TimeBeaconAssembly.RingPart> parts = TimeBeaconAssembly.allParts();
        return partIndex >= 0 && partIndex < parts.size() ? parts.get(partIndex) : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        // 先看区块加载状态，免得把未加载的区块同步拉起来
        if (!level().isLoaded(owner)) {
            return;
        }
        if (!(level().getBlockEntity(owner) instanceof TimeBeaconControllerBlockEntity controller)) {
            discard();
            return;
        }
        if (!controller.isRingEngaged()) {
            discard();
            return;
        }
        long controllerStart = controller.getWarmupStartTick();
        if (controllerStart > 0L) {
            startTick = controllerStart;
        }
        updateTransform(controller);
    }

    private void updateTransform(@Nullable TimeBeaconControllerBlockEntity controller) {
        TimeBeaconAssembly.RingPart part = part();
        if (part == null) {
            discard();
            return;
        }
        double time = level().getGameTime();
        double angle = controller == null
                ? TimeBeaconAssembly.ringAngleDeg(part.ring(), Math.max(0.0D, time - startTick))
                : controller.ringAngleAt(time, part.ring());
        double radians = Math.toRadians(part.angleDeg() + angle);
        double centerX = owner.getX() + 0.5D + ArcaneStructure.BEACON_AXIS_X;
        double centerZ = owner.getZ() + 0.5D + ArcaneStructure.BEACON_AXIS_Z;
        double x = centerX + Math.cos(radians) * part.radius();
        double z = centerZ + Math.sin(radians) * part.radius();
        // 方块中心在 owner.y + part.y()，碰撞箱以它为中心
        setPos(x, owner.getY() + part.y() - PART_HEIGHT * 0.5D, z);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(PART_WIDTH, PART_HEIGHT);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        owner = BlockPos.of(tag.getLong("Owner"));
        partIndex = tag.getInt("Part");
        startTick = tag.getLong("Start");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Owner", owner.asLong());
        tag.putInt("Part", partIndex);
        tag.putLong("Start", startTick);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return false;
    }
}
