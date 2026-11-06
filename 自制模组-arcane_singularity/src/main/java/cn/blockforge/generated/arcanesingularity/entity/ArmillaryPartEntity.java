package cn.blockforge.generated.arcanesingularity.entity;

import cn.blockforge.generated.arcanesingularity.blockentity.SingularityControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.structure.ArmillaryAssembly;
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
 * 浑天仪转子上的一小段「实体化」构件。
 *
 * <p>黑洞发生器启动后，控制器把 y=8 那圈浑天仪环和四台激光稳定器方块换成空模型、
 * 清掉自身碰撞，然后放出这些实体：蓝图里每一个方块对应一个实体，各自带着 1 格见方的碰撞箱，
 * 按 {@link ArmillaryAssembly} 的方位角与转速绕圆平面中轴旋转。外观由控制器渲染器画出
 * 与该方块完全相同的模型，所以看到的是「方块材质组成的整圈环连着四台稳定器在转」，
 * 撞到的也是同一圈东西。稳定器的激光由渲染器从它自己的炮口射出，自然跟着一起转。</p>
 *
 * <p>这些实体不写进存档：世界重载后由控制器按状态补建，既不会在存档里残留，
 * 也不会在退出保存时增加负担。</p>
 */
public class ArmillaryPartEntity extends Entity {
    public static final int KIND_SEGMENT = 0;
    public static final int KIND_POD = 1;

    private BlockPos owner = BlockPos.ZERO;
    private int partIndex;
    private int kind = KIND_SEGMENT;
    private long startTick;

    public ArmillaryPartEntity(EntityType<? extends ArmillaryPartEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public void setup(BlockPos owner, int partIndex, long startTick) {
        this.owner = owner;
        this.partIndex = partIndex;
        this.startTick = startTick;
        List<ArmillaryAssembly.RotorPart> parts = ArmillaryAssembly.allParts();
        ArmillaryAssembly.RotorPart part = partIndex >= 0 && partIndex < parts.size() ? parts.get(partIndex) : null;
        this.kind = part != null && part.pod() ? KIND_POD : KIND_SEGMENT;
        updateTransform(null);
    }

    public BlockPos getOwnerPos() {
        return owner;
    }

    public int getPartIndex() {
        return partIndex;
    }

    public int getKind() {
        return kind;
    }

    public boolean isPod() {
        return kind == KIND_POD;
    }

    @Nullable
    private ArmillaryAssembly.RotorPart part() {
        List<ArmillaryAssembly.RotorPart> parts = ArmillaryAssembly.allParts();
        return partIndex >= 0 && partIndex < parts.size() ? parts.get(partIndex) : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        // 先看区块有没有加载。直接 getBlockEntity 会同步把区块拉起来，
        // 玩家走远时会让区块反复加载/卸载，世界保存就会被拖住。
        if (!level().isLoaded(owner)) {
            return;
        }
        if (!(level().getBlockEntity(owner) instanceof SingularityControllerBlockEntity controller)) {
            discard();
            return;
        }
        if (!controller.isFormed() || !controller.isActive()) {
            discard();
            return;
        }
        long controllerStart = controller.getRingStartTick();
        if (controllerStart > 0L) {
            startTick = controllerStart;
        }
        updateTransform(controller);
    }

    private void updateTransform(@Nullable SingularityControllerBlockEntity controller) {
        ArmillaryAssembly.RotorPart part = part();
        if (part == null) {
            discard();
            return;
        }
        double time = level().getGameTime();
        double angle;
        double bob = 0.0D;
        if (part.top()) {
            // 最顶上那圈环：同一条加速曲线，一边上下震动一边转
            angle = controller == null
                    ? ArmillaryAssembly.topRingAngleDeg(time, startTick)
                    : controller.topRingAngleAt(time);
            bob = controller == null
                    ? ArmillaryAssembly.topRingBob(time, startTick)
                    : controller.topRingBobAt(time);
        } else {
            angle = controller == null
                    ? ArmillaryAssembly.rotorAngleDeg(time, startTick)
                    : controller.rotorAngleAt(time);
        }
        double radians = Math.toRadians(part.angleDeg() + angle);
        // 中环那四块水晶还会先向外扩出一段：碰撞箱与渲染走同一条曲线，撞到的位置就是看到的位置
        double radius = part.radius();
        if (part.crystal()) {
            radius += controller == null
                    ? ArmillaryAssembly.crystalOutward(Math.max(0.0D, time - startTick))
                    : controller.crystalOutwardAt(time);
        }
        double centerX = owner.getX() + 0.5D;
        double centerZ = owner.getZ() + 0.5D;
        double x = centerX + Math.cos(radians) * radius;
        double z = centerZ + Math.sin(radians) * radius;
        double planeY = owner.getY() + (part.top() ? ArmillaryAssembly.TOP_RING_Y + bob : ArmillaryAssembly.CENTER_Y);
        // 平面就是这些方块的中心高度，碰撞箱以它为中心
        setPos(x, planeY - ArmillaryAssembly.PART_HEIGHT * 0.5D, z);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(ArmillaryAssembly.PART_WIDTH, ArmillaryAssembly.PART_HEIGHT);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        owner = BlockPos.of(tag.getLong("Owner"));
        partIndex = tag.getInt("Part");
        kind = tag.getInt("Kind");
        startTick = tag.getLong("Start");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Owner", owner.asLong());
        tag.putInt("Part", partIndex);
        tag.putInt("Kind", kind);
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

    /** 玩家、生物和掉落物都会被这圈转子挡住；外观由控制器渲染。 */
    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return false;
    }
}
