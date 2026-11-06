package cn.blockforge.generated.arcanesingularity.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 带说明文字的方块物品。
 *
 * <p>贵重机器的方块物品也能在物品栏里直接读到用法，不用先进法典翻一章。</p>
 */
public class LoreBlockItem extends BlockItem {
    private final String[] lines;

    public LoreBlockItem(Block block, Properties properties, String... lines) {
        super(block, properties);
        this.lines = lines;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        for (String line : lines) {
            tooltip.add(Component.translatable(line).withStyle(style -> style.withColor(0x7FE8FF)));
        }
    }
}
