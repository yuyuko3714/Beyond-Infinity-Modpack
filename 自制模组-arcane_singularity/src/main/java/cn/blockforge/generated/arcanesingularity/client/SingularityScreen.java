package cn.blockforge.generated.arcanesingularity.client;

import cn.blockforge.generated.arcanesingularity.menu.SingularityMenu;
import cn.blockforge.generated.arcanesingularity.redstone.RedstoneMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 奇点摇篮的操作界面。
 *
 * <p>布局按「一格只放一样东西」排：上面一块信息面板里，左边是核心槽与最多四行状态文字，
 * 右边只有两个按钮和一条红石信号条，下面才是仪式进度条。所有横向位置都留出了余量
 * ——中文标签比英文宽，以前「状态」两个字和状态值挤在同一个坐标上，字体就叠在一起了。</p>
 */
public class SingularityScreen extends AbstractContainerScreen<SingularityMenu> {
    private static final int BG = 0xF00A0812;
    private static final int PANEL = 0xCC171228;
    private static final int GOLD = 0xFFD79A4A;
    private static final int VIOLET = 0xFF9A6BFF;
    private static final int TEXT = 0xFFE8DEFF;
    private static final int DIM = 0xFF8D82A8;
    private static final int SIGNAL_ON = 0xFFFF7A6B;

    /** 信息面板范围。 */
    private static final int PANEL_LEFT = 10;
    private static final int PANEL_TOP = 22;
    private static final int PANEL_RIGHT = 246;
    private static final int PANEL_BOTTOM = 132;
    /** 左侧文字列。 */
    private static final int TEXT_X = 22;
    private static final int STATUS_VALUE_X = 64;
    /** 右键操作区（按钮 + 信号条）。 */
    private static final int ACTION_X = 136;
    private static final int ACTION_WIDTH = 104;

    private Button projectionButton;
    private Button redstoneButton;

    public SingularityScreen(SingularityMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 232;
        this.inventoryLabelX = 44;
        this.inventoryLabelY = 138;
    }

    @Override
    protected void init() {
        super.init();
        this.projectionButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.arcane_singularity.projection"),
                        button -> sendButton(1))
                .bounds(leftPos + ACTION_X, topPos + 28, ACTION_WIDTH, 18).build());
        this.redstoneButton = addRenderableWidget(Button.builder(
                        redstoneLabel(),
                        button -> sendButton(3))
                .bounds(leftPos + ACTION_X, topPos + 50, ACTION_WIDTH, 18).build());
    }

    private Component redstoneLabel() {
        return Component.translatable(RedstoneMode.byId(menu.get(10)).translationKey());
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, BG, 0xF01A102B);
        graphics.fill(x, y, x + imageWidth, y + 2, GOLD);
        graphics.fill(x, y + imageHeight - 2, x + imageWidth, y + imageHeight, 0xFF5B3A82);
        graphics.fill(x, y, x + 2, y + imageHeight, 0xFF6A4B2A);
        graphics.fill(x + imageWidth - 2, y, x + imageWidth, y + imageHeight, 0xFF6A4B2A);

        // 信息面板
        graphics.fill(x + PANEL_LEFT, y + PANEL_TOP, x + PANEL_RIGHT, y + PANEL_BOTTOM, PANEL);
        graphics.fill(x + PANEL_LEFT, y + PANEL_TOP, x + PANEL_RIGHT, y + PANEL_TOP + 1, 0xFF4A2F75);
        graphics.fill(x + PANEL_LEFT, y + PANEL_TOP, x + PANEL_LEFT + 1, y + PANEL_BOTTOM, 0xFF4A2F75);

        // 核心槽底（槽位在 46,36，16×16）
        graphics.fill(x + 42, y + 32, x + 66, y + 56, 0xFF0C0714);
        graphics.fill(x + 43, y + 33, x + 65, y + 55, 0xFF2A1740);
        graphics.renderOutline(x + 41, y + 31, 26, 26, 0xFFB68C4C);

        // 右侧操作区：按钮由控件自己画，这里只铺底、红石信号条和投影状态
        graphics.fill(x + ACTION_X - 4, y + 24, x + ACTION_X + ACTION_WIDTH + 4, y + 96, 0x99100A1C);
        graphics.renderOutline(x + ACTION_X - 4, y + 24, ACTION_WIDTH + 8, 72, 0xFF3A2858);
        graphics.fill(x + ACTION_X, y + 72, x + ACTION_X + ACTION_WIDTH, y + 76, 0xFF0A0711);
        int signal = Math.min(15, menu.get(11));
        if (signal > 0) {
            graphics.fill(x + ACTION_X, y + 72, x + ACTION_X + ACTION_WIDTH * signal / 15, y + 76,
                    SIGNAL_ON);
        }

        // 状态指示灯（左侧文字列的排头）
        int status = menu.get(8);
        int lamp = status == 4 ? 0xFF8EF7C0 : status == 0 ? 0xFF6B5B82 : status == 5 ? 0xFFC97B7B : 0xFFFFC46B;
        graphics.fill(x + 12, y + 63, x + 18, y + 69, lamp);
        graphics.fill(x + 14, y + 65, x + 16, y + 67, 0xFFFFFFFF);

        // 仪式进度条
        int progressX = x + PANEL_LEFT + 8;
        int progressY = y + 116;
        int progressWidth = PANEL_RIGHT - PANEL_LEFT - 16;
        graphics.fill(progressX, progressY, progressX + progressWidth, progressY + 10, 0xFF0A0711);
        int amount = Math.min(600, menu.get(0));
        int filled = amount * progressWidth / 600;
        if (filled > 0) {
            graphics.fillGradient(progressX, progressY, progressX + filled, progressY + 10,
                    0xFF7A4DFF, 0xFFFFC46B);
        }
        for (int i = 1; i < 6; i++) {
            int tick = progressX + i * progressWidth / 6;
            graphics.fill(tick, progressY, tick + 1, progressY + 10, 0x665A3C7C);
        }
        graphics.renderOutline(progressX - 1, progressY - 1, progressWidth + 2, 12, 0xFF4A2F75);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, title, 128, 8, GOLD);
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.core_slot"),
                72, 40, DIM, false);

        int status = menu.get(8);
        // 状态：标签和值分成两列，各自有位置，不会再挤到一起
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.status"),
                TEXT_X, 62, GOLD, false);
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.singularity.status." + status),
                STATUS_VALUE_X, 62, TEXT, false);

        // 其余三行各自是「标签：值」一整句，行距 14，互不重叠
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.phase." + menu.get(7)),
                TEXT_X, 76, VIOLET, false);
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.output",
                menu.get(6) / 1000), TEXT_X, 90, DIM, false);
        int signal = menu.get(11);
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.redstone_signal", signal),
                TEXT_X, 104, signal > 0 ? SIGNAL_ON : DIM, false);

        // 进度条上的百分比：居中画在条里，不另占一行
        graphics.drawCenteredString(font, Component.translatable("gui.arcane_singularity.progress",
                Math.min(100, menu.get(0) * 100 / 600)), 128, 117, 0xFFF4EEFF);
        if (menu.get(4) > 0) {
            // 投影状态放在右侧操作区底部，单独一行，不和左边的读数抢位置
            graphics.drawString(font, Component.translatable("gui.arcane_singularity.projection_active"),
                    ACTION_X + 2, 84, 0xFF8FE7FF, false);
        }
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, DIM, false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        projectionButton.active = menu.get(1) == 0;
        redstoneButton.setMessage(redstoneLabel());
    }
}
