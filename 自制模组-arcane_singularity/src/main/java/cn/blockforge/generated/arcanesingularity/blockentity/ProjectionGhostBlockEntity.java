package cn.blockforge.generated.arcanesingularity.blockentity;

import cn.blockforge.generated.arcanesingularity.block.ProjectionGhostBlock;
import cn.blockforge.generated.arcanesingularity.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 幻影方块的方块实体：记录它想表现的构件方块、所属控制器以及过期时间。
 *
 * <p>它实现了 {@link Nameable}，因此 Jade 之类读取方块实体的提示模组会把准星指到的幻影
 * 显示成目标构件（例如「符文砖」），而不是「奥术投影」。</p>
 */
public class ProjectionGhostBlockEntity extends BlockEntity implements Nameable {
    private static final int LIFETIME = 640;
    private BlockState target = Blocks.STONE.defaultBlockState();
    /** 幻影出现之前这里原本是什么方块（地形 / 空气），投影结束时按它恢复。 */
    private BlockState previous = Blocks.AIR.defaultBlockState();
    private BlockPos controller = BlockPos.ZERO;
    private long expireAt;

    public ProjectionGhostBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PROJECTION_GHOST.get(), pos, state);
    }

    public void setup(BlockPos controllerPos, BlockState targetState) {
        setup(controllerPos, targetState, this.previous);
    }

    public void setup(BlockPos controllerPos, BlockState targetState, BlockState previousState) {
        BlockState base = previousState == null ? Blocks.AIR.defaultBlockState() : previousState;
        if (base.getBlock() instanceof ProjectionGhostBlock) {
            base = Blocks.AIR.defaultBlockState();
        }
        boolean changed = !controller.equals(controllerPos) || !target.equals(targetState)
                || !previous.equals(base);
        this.controller = controllerPos;
        this.target = targetState;
        this.previous = base;
        this.expireAt = (level == null ? 0L : level.getGameTime()) + LIFETIME;
        if (!changed) {
            // 内容没变就只续期，不反复把区块标脏、也不反复同步给客户端。
            return;
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public BlockState getTarget() {
        return target;
    }

    /** 投影出现之前这个位置原来的方块，投影结束时用它还原地形。 */
    public BlockState getPrevious() {
        return previous;
    }

    public BlockPos getController() {
        return controller;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ProjectionGhostBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        if (level.getGameTime() >= be.expireAt) {
            be.removeGhost();
            return;
        }
        if (level.getGameTime() % 20L != 0L) {
            return;
        }
        // 投影覆盖范围有 13 格半径，玩家站在边上时控制器所在区块可能已经卸载。
        // 这里绝不能直接 getBlockEntity：那会同步把区块拉起来，区块反复加载/卸载会把世界保存拖住。
        if (be.controller.equals(BlockPos.ZERO) || !level.isLoaded(be.controller)) {
            return;
        }
        BlockEntity owner = level.getBlockEntity(be.controller);
        boolean active = owner instanceof SingularityControllerBlockEntity singularity
                && singularity.getProjectionTicks() > 0;
        if (!active && owner instanceof StargateControllerBlockEntity gate) {
            active = gate.getProjectionTicks() > 0;
        }
        // 时间信标与世界之核的投影也一样：漏掉这两座，幻影会每隔 20 tick 被当成过期回收，
        // 投影在半透明与消失之间来回闪，准星也就读不到 Jade 信息。
        if (!active && owner instanceof TimeBeaconControllerBlockEntity beacon) {
            active = beacon.getProjectionTicks() > 0;
        }
        if (!active && owner instanceof WorldCoreControllerBlockEntity core) {
            active = core.getProjectionTicks() > 0;
        }
        if (!active) {
            be.removeGhost();
        }
    }

    /** 投影结束：把幻影换回原来的方块（空气或原地形），地形不会被投影吃掉。 */
    public void removeGhost() {
        if (level == null) {
            return;
        }
        if (level.getBlockState(worldPosition).getBlock() instanceof ProjectionGhostBlock) {
            BlockState restore = previous;
            if (restore.getBlock() instanceof ProjectionGhostBlock) {
                restore = Blocks.AIR.defaultBlockState();
            }
            level.setBlock(worldPosition, restore, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Component getName() {
        return target.getBlock().getName();
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return target.getBlock().getName();
    }

    @Override
    public boolean hasCustomName() {
        return true;
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
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("TargetBlock", BuiltInRegistries.BLOCK.getKey(target.getBlock()).toString());
        tag.putString("PreviousBlock", BuiltInRegistries.BLOCK.getKey(previous.getBlock()).toString());
        tag.putLong("Controller", controller.asLong());
        tag.putLong("Expire", expireAt);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        String name = tag.getString("TargetBlock");
        if (!name.isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(name);
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                target = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
            }
        }
        String previousName = tag.getString("PreviousBlock");
        if (!previousName.isEmpty()) {
            ResourceLocation previousId = ResourceLocation.tryParse(previousName);
            if (previousId != null && BuiltInRegistries.BLOCK.containsKey(previousId)) {
                previous = BuiltInRegistries.BLOCK.get(previousId).defaultBlockState();
            }
        }
        controller = BlockPos.of(tag.getLong("Controller"));
        expireAt = tag.getLong("Expire");
    }
}
