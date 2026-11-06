package cn.blockforge.generated.arcanesingularity.blockentity;

import cn.blockforge.generated.arcanesingularity.block.ArcaneProperties;
import cn.blockforge.generated.arcanesingularity.registry.ModBlockEntities;
import cn.blockforge.generated.arcanesingularity.structure.ArcaneStructure;
import cn.blockforge.generated.arcanesingularity.structure.WorldCoreLink;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 世界之核的专属接口。
 *
 * <p>它自己不做别的事，只负责一件事：定期从自己这个接口出发，沿<b>专属管道</b>把整张网走一遍，
 * 看能不能摸到黑洞发生器、星门、时间信标的控制器，并把摸到的三位掩码存下来。
 * 世界之核控制器会来读这个掩码，据此在界面里显示「在线 / 离线」。</p>
 *
 * <p>接口上方的专属激光器、以及那道射向天空的激光，都由这个接口的渲染器按控制器给的节拍来画。</p>
 */
public class WorldCoreInterfaceBlockEntity extends BlockEntity {
    /** 结构里一共有几个接口。 */
    public static final int INTERFACE_COUNT = 3;

    private int linkMask;
    private int scanCooldown;
    private String lastSyncKey = "";

    public WorldCoreInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WORLD_CORE_INTERFACE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  WorldCoreInterfaceBlockEntity be) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (--be.scanCooldown <= 0) {
            be.scanCooldown = 20;
            int mask = WorldCoreLink.scan(level, pos);
            if (mask != be.linkMask) {
                be.linkMask = mask;
                be.updateActiveBlock();
                be.setChanged();
                be.sync();
            }
        }
    }

    private void updateActiveBlock() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        boolean active = linkMask != 0;
        if (state.hasProperty(ArcaneProperties.ACTIVE) && state.getValue(ArcaneProperties.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(ArcaneProperties.ACTIVE, active), 3);
        }
    }

    public int getLinkMask() {
        return linkMask;
    }

    /**
     * 这个接口属于哪个世界之核：按蓝图偏移反推控制器位置。
     *
     * @return 找到的世界之核控制器；接口被玩家随手放在别处、或者控制器已经不在了就返回 {@code null}
     */
    @Nullable
    public WorldCoreControllerBlockEntity findController() {
        if (level == null) {
            return null;
        }
        for (BlockPos offset : ArcaneStructure.worldCoreInterfaceOffsets()) {
            BlockPos controllerPos = worldPosition.subtract(offset);
            if (!level.isLoaded(controllerPos)) {
                continue;
            }
            if (level.getBlockEntity(controllerPos) instanceof WorldCoreControllerBlockEntity controller) {
                return controller;
            }
        }
        return null;
    }

    private void sync() {
        if (level == null || level.isClientSide || isRemoved()) {
            return;
        }
        String key = linkMask + "|" + worldPosition.asLong();
        if (key.equals(lastSyncKey)) {
            return;
        }
        lastSyncKey = key;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("LinkMask", linkMask);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        linkMask = tag.getInt("LinkMask");
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
}
