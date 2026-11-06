package cn.blockforge.generated.arcanesingularity.menu;

import cn.blockforge.generated.arcanesingularity.blockentity.WorldCoreControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.registry.ModMenus;
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

/**
 * 世界之核的操作界面。
 *
 * <p>布局是「左边命令方块、右边结构状态」：左侧是命令输入与输出，右侧是结构在线状态、
 * 红石模式、命令方块模式（脉冲 / 循环，没有连锁）、条件开关与世界核心槽。</p>
 *
 * <p>按钮编号：1 投影、2 红石模式、3 命令方块模式、4 条件开关（界面上不再显示条件按钮）。</p>
 */
public class WorldCoreMenu extends AbstractContainerMenu {
    public static final int BUTTON_PROJECTION = 1;
    public static final int BUTTON_REDSTONE = 2;
    public static final int BUTTON_COMMAND_MODE = 3;
    public static final int BUTTON_CONDITIONAL = 4;

    private final BlockPos pos;
    private final Container container;
    private final ContainerData data;
    @Nullable
    private final WorldCoreControllerBlockEntity blockEntity;

    public WorldCoreMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenus.WORLD_CORE.get(), containerId);
        this.pos = pos;
        if (inventory.player.level().getBlockEntity(pos) instanceof WorldCoreControllerBlockEntity entity) {
            this.blockEntity = entity;
            this.container = entity;
            this.data = entity.dataAccess();
        } else {
            this.blockEntity = null;
            this.container = new SimpleContainer(1);
            this.data = new SimpleContainerData(WorldCoreControllerBlockEntity.DATA_COUNT);
        }
        addSlot(new Slot(container, 0, 240, 114) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(cn.blockforge.generated.arcanesingularity.registry.ModItems.WORLD_CORE.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 44 + col * 18, 216 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 44 + col * 18, 274));
        }
        addDataSlots(data);
    }

    public WorldCoreMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos());
    }

    public int get(int index) {
        return data.get(index);
    }

    public BlockPos getPos() {
        return pos;
    }

    @Nullable
    public WorldCoreControllerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        return switch (id) {
            case BUTTON_PROJECTION -> blockEntity.startProjection(serverPlayer);
            case BUTTON_REDSTONE -> blockEntity.cycleRedstoneMode(serverPlayer);
            case BUTTON_COMMAND_MODE -> blockEntity.cycleCommandMode(serverPlayer);
            case BUTTON_CONDITIONAL -> blockEntity.cycleConditional(serverPlayer);
            default -> false;
        };
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
