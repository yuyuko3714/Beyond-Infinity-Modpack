package cn.blockforge.generated.arcanesingularity.entity;

import cn.blockforge.generated.arcanesingularity.blockentity.WorldCoreControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.structure.ArcaneStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

/**
 * 世界之核的核心 —— 原来那个巨大的命令方块，现在是一个<b>有碰撞箱的实体</b>。
 *
 * <p>以前的版本是在控制器的方块实体渲染器里，往特效缓冲里直接 {@code renderSingleBlock}
 * 一个被放大 3.7 倍、还在自转倾斜的命令方块，外面再叠几圈水平的光环。那个阶段玩家正好
 * 站在核心周围的高度上：方块的缩放位姿、光环的水平四边形、以及方块渲染器临时切换的
 * 顶点格式全挤在同一个缓冲区里，画面就被切得七零八落。</p>
 *
 * <p>现在改成和浑天仪环、时间信标汇聚环一样的做法：</p>
 * <ul>
 *     <li><b>位置和碰撞</b>由这个实体负责 —— 它悬在控制器中轴上方 22 格，
 *         带着一个 3.2 格见方的碰撞箱，玩家、生物、掉落物撞到的是真实存在的东西；</li>
 *     <li><b>外观</b>由 {@code WorldCoreCoreRenderer} 在实体渲染阶段用方块模型画出来：
 *         中心是命令方块模型，外面两圈是像浑天仪环那样由小方块模型拼出来的能量环；
 *         所有几何都是立体的小方块，不再有横跨几十格的大面片，也就不会再出现
 *         「所有细线朝准星汇聚」或「屏幕被切成四块」的花屏。</li>
 * </ul>
 *
 * <p>这些实体不写进存档：世界重载后由控制器按状态补建，退出保存时也不会在存档里留下残影。</p>
 */
public class WorldCoreCoreEntity extends Entity {
    /** 核心完全展开后的碰撞箱边长（核心模型 3.7 格，碰撞箱贴着方块的外接立方）。 */
    public static final float CORE_WIDTH = 3.6F;
    public static final float CORE_HEIGHT = 3.6F;

    /** 爆发进度 0..1，由控制器同步过来，渲染器按它决定核心大小与环展开程度。 */
    private static final EntityDataAccessor<Float> DATA_BURST =
            SynchedEntityData.defineId(WorldCoreCoreEntity.class, EntityDataSerializers.FLOAT);
    /** 命令方块模式：0 = 脉冲（橙），1 = 循环（紫）。 */
    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(WorldCoreCoreEntity.class, EntityDataSerializers.INT);

    private BlockPos owner = BlockPos.ZERO;
    private float previousBurst;

    public WorldCoreCoreEntity(EntityType<? extends WorldCoreCoreEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public void setup(BlockPos owner) {
        this.owner = owner;
        snapToAnchor();
    }

    public BlockPos getOwnerPos() {
        return owner;
    }

    /** 渲染用的爆发进度：在相邻两帧之间插值，爆发那一下不会一跳一跳。 */
    public float burstAt(float partialTick) {
        float current = entityData.get(DATA_BURST);
        if (partialTick <= 0.0F) {
            return current;
        }
        return Mth.lerp(partialTick, previousBurst, current);
    }

    public int getCommandMode() {
        return entityData.get(DATA_MODE);
    }

    @Override
    public void tick() {
        super.tick();
        previousBurst = entityData.get(DATA_BURST);
        if (level().isClientSide) {
            return;
        }
        // 先看区块有没有加载，别把未加载的区块同步拉起来
        if (!level().isLoaded(owner)) {
            return;
        }
        if (!(level().getBlockEntity(owner) instanceof WorldCoreControllerBlockEntity controller)) {
            discard();
            return;
        }
        if (!controller.isFormed() || !controller.isActive()) {
            discard();
            return;
        }
        long gameTime = level().getGameTime();
        float burst = controller.coreBurstAt(gameTime);
        entityData.set(DATA_BURST, burst);
        entityData.set(DATA_MODE, controller.getCommandMode());
        snapToAnchor();
    }

    /** 核心中心永远落在控制器中轴上方 22 格；实体坐标是碰撞箱底面中心。 */
    private void snapToAnchor() {
        double x = owner.getX() + 0.5D + ArcaneStructure.WORLD_CORE_AXIS_X;
        double z = owner.getZ() + 0.5D;
        double y = owner.getY() + ArcaneStructure.WORLD_CORE_FOCUS_Y - CORE_HEIGHT * 0.5D;
        setPos(x, y, z);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(CORE_WIDTH, CORE_HEIGHT);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_BURST, 0.0F);
        entityData.define(DATA_MODE, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        owner = BlockPos.of(tag.getLong("Owner"));
        previousBurst = tag.getFloat("Burst");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Owner", owner.asLong());
        tag.putFloat("Burst", entityData.get(DATA_BURST));
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
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    /** 悬在空中的核心是实心的：玩家、生物、掉落物都会撞到它。 */
    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    /** 实体本身由实体渲染器画，眼睛离得再近也要画；剔除交给实体跟踪范围。 */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}
