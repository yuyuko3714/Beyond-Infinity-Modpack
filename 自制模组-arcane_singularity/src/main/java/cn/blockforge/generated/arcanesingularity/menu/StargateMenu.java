package cn.blockforge.generated.arcanesingularity.menu;

import cn.blockforge.generated.arcanesingularity.blockentity.StargateControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.registry.ModMenus;
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

public class StargateMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final ContainerData data;
    @Nullable
    private final StargateControllerBlockEntity blockEntity;

    public StargateMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenus.STARGATE.get(), containerId);
        this.pos = pos;
        if (inventory.player.level().getBlockEntity(pos) instanceof StargateControllerBlockEntity entity) {
            this.blockEntity = entity;
            this.data = entity.dataAccess;
        } else {
            this.blockEntity = null;
            this.data = new SimpleContainerData(StargateControllerBlockEntity.DATA_COUNT);
        }
        addDataSlots(data);
    }

    public StargateMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos());
    }

    public int get(int index) {
        return data.get(index);
    }

    public BlockPos getPos() {
        return pos;
    }

    @Nullable
    public StargateControllerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == 1) {
            return blockEntity.startProjection(serverPlayer);
        }
        if (id == 2) {
            blockEntity.forceClose(serverPlayer);
            return true;
        }
        if (id == 3) {
            return blockEntity.cycleRedstoneMode(serverPlayer);
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
