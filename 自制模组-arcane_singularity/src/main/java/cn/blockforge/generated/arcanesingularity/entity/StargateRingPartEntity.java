package cn.blockforge.generated.arcanesingularity.entity;

import cn.blockforge.generated.arcanesingularity.block.StargateControllerBlock;
import cn.blockforge.generated.arcanesingularity.blockentity.StargateControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.structure.StargateRingAssembly;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 星门符文环上的一个「实体化」方块构件。
 *
 * <p>星门打开后，控制器把门环方块换成空模型并清掉自身碰撞，然后放出这些实体：
 * 蓝图里每一个门环方块对应一个带碰撞箱的实体，像车轮一样绕门心自转。
 * 外观由星门渲染器画出与方块完全相同的模型，所以看到的是「门框方块组成的轮子在转」，
 * 撞到的也是同一圈东西。关闭时车轮刹回原位后实体才回收，方块也才恢复。</p>
 *
 * <p>与浑天仪转子一样，这些实体不写进存档，由控制器按状态补建。</p>
 */
public class StargateRingPartEntity extends Entity {
    private BlockPos owner = BlockPos.ZERO;
    private int partIndex;
    private Direction facing = Direction.NORTH;
    private long startTick;

    public StargateRingPartEntity(EntityType<? extends StargateRingPartEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public void setup(BlockPos owner, int partIndex, Direction facing, long startTick) {
        this.owner = owner;
        this.partIndex = partIndex;
        this.facing = facing;
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
    private StargateRingAssembly.RingPart part() {
        List<StargateRingAssembly.RingPart> parts = StargateRingAssembly.ringParts();
        return partIndex >= 0 && partIndex < parts.size() ? parts.get(partIndex) : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        // 不在这里同步加载区块：玩家走远时那些反复加载会拖住世界保存。
        if (!level().isLoaded(owner)) {
            return;
        }
        if (!(level().getBlockEntity(owner) instanceof StargateControllerBlockEntity controller)) {
            discard();
            return;
        }
        if (!controller.isFormed() || !controller.isRingSpinning()) {
            discard();
            return;
        }
        long openStamp = controller.getOpenStamp();
        if (openStamp > 0L) {
            startTick = openStamp;
        }
        Direction facing = level().getBlockState(owner).getBlock() instanceof StargateControllerBlock
                ? level().getBlockState(owner).getValue(StargateControllerBlock.FACING)
                : this.facing;
        updateTransform(controller, facing);
    }

    private void updateTransform(@Nullable StargateControllerBlockEntity controller) {
        updateTransform(controller, facing);
    }

    private void updateTransform(@Nullable StargateControllerBlockEntity controller, Direction facing) {
        this.facing = facing;
        StargateRingAssembly.RingPart part = part();
        if (part == null) {
            discard();
            return;
        }
        double time = level().getGameTime();
        double angle = controller == null
                ? StargateRingAssembly.ringAngleDeg(time, startTick, 0L, true)
                : controller.ringAngleAt(time);
        Vec3 center = StargateRingAssembly.partCenter(owner, facing, part, angle);
        setPos(center.x, center.y - StargateRingAssembly.PART_HEIGHT * 0.5D, center.z);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(StargateRingAssembly.PART_WIDTH, StargateRingAssembly.PART_HEIGHT);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        owner = BlockPos.of(tag.getLong("Owner"));
        partIndex = tag.getInt("Part");
        facing = Direction.from2DDataValue(tag.getInt("Facing"));
        startTick = tag.getLong("Start");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Owner", owner.asLong());
        tag.putInt("Part", partIndex);
        tag.putInt("Facing", facing.get2DDataValue());
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
