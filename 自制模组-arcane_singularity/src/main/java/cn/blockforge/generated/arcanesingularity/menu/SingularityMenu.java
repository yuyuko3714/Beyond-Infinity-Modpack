package cn.blockforge.generated.arcanesingularity.menu;

import cn.blockforge.generated.arcanesingularity.blockentity.SingularityControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.registry.ModMenus;
import cn.blockforge.generated.arcanesingularity.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SingularityMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Container container;
    private final ContainerData data;
    @Nullable
    private final SingularityControllerBlockEntity blockEntity;

    public SingularityMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenus.SINGULARITY.get(), containerId);
        this.pos = pos;
        if (inventory.player.level().getBlockEntity(pos) instanceof SingularityControllerBlockEntity entity) {
            this.blockEntity = entity;
            this.container = entity;
            this.data = entity.dataAccess;
        } else {
            this.blockEntity = null;
            this.container = new SimpleContainer(1);
            this.data = new SimpleContainerData(SingularityControllerBlockEntity.DATA_COUNT);
        }
        addSlot(new Slot(container, 0, 46, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModTags.SINGULARITY_CATALYSTS);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 44 + col * 18, 150 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 44 + col * 18, 208));
        }
        addDataSlots(data);
    }

    public SingularityMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos());
    }

    public int get(int index) {
        return data.get(index);
    }

    public BlockPos getPos() {
        return pos;
    }

    @Nullable
    public SingularityControllerBlockEntity getBlockEntity() {
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
        if (id == 3) {
            return blockEntity.cycleRedstoneMode(serverPlayer);
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index == 0) {
                if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity != null) {
            return blockEntity.stillValid(player);
        }
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }
}
