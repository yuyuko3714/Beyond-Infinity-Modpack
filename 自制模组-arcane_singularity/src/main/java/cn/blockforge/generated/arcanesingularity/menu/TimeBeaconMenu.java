package cn.blockforge.generated.arcanesingularity.menu;

import cn.blockforge.generated.arcanesingularity.blockentity.TimeBeaconControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.registry.ModMenus;
import cn.blockforge.generated.arcanesingularity.time.TimeMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 时间信标的操作界面。
 *
 * <p>按钮编号：1 投影、2 红石模式、3~6 四种时间模式、7 倍率减、8 倍率加。</p>
 */
public class TimeBeaconMenu extends AbstractContainerMenu {
    public static final int BUTTON_PROJECTION = 1;
    public static final int BUTTON_REDSTONE = 2;
    public static final int BUTTON_MODE_BASE = 3;
    public static final int BUTTON_SPEED_DOWN = 7;
    public static final int BUTTON_SPEED_UP = 8;

    private final BlockPos pos;
    private final ContainerData data;
    @Nullable
    private final TimeBeaconControllerBlockEntity blockEntity;

    public TimeBeaconMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenus.TIME_BEACON.get(), containerId);
        this.pos = pos;
        if (inventory.player.level().getBlockEntity(pos) instanceof TimeBeaconControllerBlockEntity entity) {
            this.blockEntity = entity;
            this.data = entity.dataAccess;
        } else {
            this.blockEntity = null;
            this.data = new SimpleContainerData(TimeBeaconControllerBlockEntity.DATA_COUNT);
        }
        addDataSlots(data);
    }

    public TimeBeaconMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos());
    }

    public int get(int index) {
        return data.get(index);
    }

    public BlockPos getPos() {
        return pos;
    }

    @Nullable
    public TimeBeaconControllerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == BUTTON_PROJECTION) {
            return blockEntity.startProjection(serverPlayer);
        }
        if (id == BUTTON_REDSTONE) {
            return blockEntity.cycleRedstoneMode(serverPlayer);
        }
        if (id >= BUTTON_MODE_BASE && id < BUTTON_MODE_BASE + TimeMode.values().length) {
            return blockEntity.setMode(serverPlayer, TimeMode.byId(id - BUTTON_MODE_BASE));
        }
        if (id == BUTTON_SPEED_DOWN) {
            return blockEntity.adjustSpeed(serverPlayer, -1);
        }
        if (id == BUTTON_SPEED_UP) {
            return blockEntity.adjustSpeed(serverPlayer, 1);
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }
}
