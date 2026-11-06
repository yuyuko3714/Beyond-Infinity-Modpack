package cn.blockforge.generated.arcanesingularity.item;

import cn.blockforge.generated.arcanesingularity.blockentity.StargateControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.data.GateId;
import cn.blockforge.generated.arcanesingularity.data.StargateData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 星门绑定器。
 *
 * <p>和成型终端一致，所有绑定动作都要 <b>潜行 + 右键</b>：潜行右击第一座星门控制器记下它的
 * 维度与坐标，再潜行右击第二座把它们两两配对；对同一座门再潜行右击一次会解除手上的绑定。
 * 不潜行时右键仍然交给控制器自己（打开界面），不会被这个物品截走。</p>
 */
public class PairingRodItem extends Item {
    private static final String BOUND = "BoundGate";

    public PairingRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof StargateControllerBlockEntity)) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        // 只有潜行右键才绑定/配对：普通右键留给控制器打开界面。
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        CompoundTag tag = stack.getOrCreateTag();
        GateId target = GateId.of(level, pos);
        String stored = tag.getString(BOUND);
        if (stored.isEmpty()) {
            tag.putString(BOUND, target.encode());
            player.displayClientMessage(Component.translatable("message.arcane_singularity.rod_bound",
                    target.dimension().toString(), target.pos().getX(), target.pos().getY(),
                    target.pos().getZ()), true);
            return InteractionResult.SUCCESS;
        }
        GateId first = GateId.parse(stored);
        if (first == null) {
            tag.remove(BOUND);
            player.displayClientMessage(Component.translatable("message.arcane_singularity.rod_corrupt"), true);
            return InteractionResult.SUCCESS;
        }
        if (first.equals(target)) {
            tag.remove(BOUND);
            player.displayClientMessage(Component.translatable("message.arcane_singularity.rod_cleared"), true);
            return InteractionResult.SUCCESS;
        }
        StargateData data = StargateData.get(level.getServer());
        data.pair(first, target);
        tag.remove(BOUND);
        player.displayClientMessage(Component.translatable("message.arcane_singularity.rod_paired",
                first.dimension().toString(), first.pos().getX(), first.pos().getY(), first.pos().getZ(),
                target.dimension().toString(), target.pos().getX(), target.pos().getY(), target.pos().getZ()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(BOUND) && !level.isClientSide) {
            if (player.isShiftKeyDown()) {
                tag.remove(BOUND);
                player.displayClientMessage(Component.translatable("message.arcane_singularity.rod_cleared"), true);
            } else {
                GateId gate = GateId.parse(tag.getString(BOUND));
                if (gate != null) {
                    player.displayClientMessage(Component.translatable("message.arcane_singularity.rod_current",
                            gate.dimension().toString(), gate.pos().getX(), gate.pos().getY(), gate.pos().getZ()), true);
                }
            }
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(BOUND);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(BOUND)) {
            GateId gate = GateId.parse(tag.getString(BOUND));
            if (gate != null) {
                tooltip.add(Component.translatable("tooltip.arcane_singularity.rod_bound",
                        gate.dimension().toString(), gate.pos().getX(), gate.pos().getY(), gate.pos().getZ())
                        .withStyle(ChatFormatting.AQUA));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.arcane_singularity.rod_empty")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.arcane_singularity.rod_help")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }
}
