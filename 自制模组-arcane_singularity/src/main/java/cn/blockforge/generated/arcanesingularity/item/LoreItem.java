package cn.blockforge.generated.arcanesingularity.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LoreItem extends Item {
    private final String[] lines;

    public LoreItem(Properties properties, String... lines) {
        super(properties);
        this.lines = lines;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (String line : lines) {
            tooltip.add(Component.translatable(line).withStyle(style -> style.withColor(0xB69BFF)));
        }
    }
}
