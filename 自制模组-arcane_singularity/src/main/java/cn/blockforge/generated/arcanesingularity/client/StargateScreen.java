package cn.blockforge.generated.arcanesingularity.client;

import cn.blockforge.generated.arcanesingularity.blockentity.StargateControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.menu.StargateMenu;
import cn.blockforge.generated.arcanesingularity.redstone.RedstoneMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 星门控制器的操作界面。
 *
 * <p>左半边是星门预览图，右半边是一列信息：第一行状态，后面每行都是「标签：值」一整句，
 * 值统一按可用宽度截断，行距 12，所以中文再长也不会串到下一行或压到别的字上。
 * 三个按钮统一排在底部，各自占一格，互不重叠。</p>
 */
public class StargateScreen extends AbstractContainerScreen<StargateMenu> {
    private static final int BG = 0xF0080710;
    private static final int PANEL = 0xCC151127;
    private static final int GOLD = 0xFFD79A4A;
    private static final int TEXT = 0xFFE8DEFF;
    private static final int DIM = 0xFF8D82A8;
    private static final int SIGNAL_ON = 0xFFFF7A6B;

    /** 右侧信息面板。 */
    private static final int PANEL_LEFT = 118;
    private static final int PANEL_TOP = 44;
    private static final int PANEL_RIGHT = 246;
    private static final int PANEL_BOTTOM = 140;
    /** 面板内文字列。 */
    private static final int TEXT_X = 124;
    private static final int TEXT_WIDTH = 116;
    /** 左侧星门预览的圆心。 */
    private static final int PREVIEW_CX = 62;
    private static final int PREVIEW_CY = 92;
    private static final int PREVIEW_RADIUS = 44;

    private Button projectionButton;
    private Button closeButton;
    private Button redstoneButton;

    public StargateScreen(StargateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 196;
    }

    @Override
    protected void init() {
        super.init();
        // 底部一排三个按钮，各占一格，中间留 6 像素间隔
        this.redstoneButton = addRenderableWidget(Button.builder(
                        redstoneLabel(),
                        button -> sendButton(3))
                .bounds(leftPos + 14, topPos + 152, 96, 20).build());
        this.projectionButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.arcane_singularity.projection"),
                        button -> sendButton(1))
                .bounds(leftPos + 116, topPos + 152, 60, 20).build());
        this.closeButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.arcane_singularity.close_gate"),
                        button -> sendButton(2))
                .bounds(leftPos + 182, topPos + 152, 60, 20).build());
    }

    private Component redstoneLabel() {
        return Component.translatable(RedstoneMode.byId(menu.get(9)).translationKey());
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
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, BG, 0xF0150E2B);
        graphics.fill(x, y, x + imageWidth, y + 2, GOLD);
        graphics.fill(x, y + imageHeight - 2, x + imageWidth, y + imageHeight, 0xFF5B3A82);
        graphics.fill(x, y, x + 2, y + imageHeight, 0xFF6A4B2A);
        graphics.fill(x + imageWidth - 2, y, x + imageWidth, y + imageHeight, 0xFF6A4B2A);

        // 左侧星门预览：一个个圆环，门开时整圈亮起来
        int cx = x + PREVIEW_CX;
        int cy = y + PREVIEW_CY;
        boolean open = menu.get(2) == 1;
        int inner = PREVIEW_RADIUS - 6;
        for (int dy = -inner; dy <= inner; dy++) {
            int half = (int) Math.sqrt(inner * inner - dy * dy);
            int color = open
                    ? (Math.abs(dy) < 13 ? 0xFFD9C8FF : Math.abs(dy) < 30 ? 0xFF8B5CFF : 0xAA4F28A8)
                    : (Math.abs(dy) < 13 ? 0xFF1A1029 : 0xFF231534);
            graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
        }
        for (int i = 0; i < 72; i++) {
            double a = Math.PI * 2.0D * i / 72.0D;
            int px = cx + (int) Math.round(Math.cos(a) * PREVIEW_RADIUS);
            int py = cy + (int) Math.round(Math.sin(a) * PREVIEW_RADIUS);
            graphics.fill(px, py, px + 2, py + 2, open ? 0xFFFFD88A : 0xFF735A3C);
            int qx = cx + (int) Math.round(Math.cos(a) * (PREVIEW_RADIUS - 6));
            int qy = cy + (int) Math.round(Math.sin(a) * (PREVIEW_RADIUS - 6));
            graphics.fill(qx, qy, qx + 1, qy + 1, 0xAA9A6BFF);
        }

        // 右侧信息面板
        graphics.fill(x + PANEL_LEFT, y + PANEL_TOP, x + PANEL_RIGHT, y + PANEL_BOTTOM, PANEL);
        graphics.fill(x + PANEL_LEFT, y + PANEL_TOP, x + PANEL_RIGHT, y + PANEL_TOP + 1, 0xFF4A2F75);
        graphics.fill(x + PANEL_LEFT, y + PANEL_TOP, x + PANEL_LEFT + 1, y + PANEL_BOTTOM, 0xFF4A2F75);
        graphics.renderOutline(x + PANEL_LEFT, y + PANEL_TOP, PANEL_RIGHT - PANEL_LEFT,
                PANEL_BOTTOM - PANEL_TOP, 0xFF3A2858);

        // 红石信号条（面板最后一行）
        int signal = Math.min(15, menu.get(10));
        int barY = y + 132;
        graphics.fill(x + TEXT_X, barY, x + TEXT_X + TEXT_WIDTH, barY + 3, 0xFF0A0711);
        if (signal > 0) {
            graphics.fill(x + TEXT_X, barY, x + TEXT_X + TEXT_WIDTH * signal / 15, barY + 3, SIGNAL_ON);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, title, 128, 8, GOLD);
        graphics.drawCenteredString(font,
                Component.translatable("gui.arcane_singularity.portal_preview"), PREVIEW_CX, 26, DIM);

        int status = menu.get(6);
        // 只有「两端同时开着」才是绿色；单开一边是琥珀色的等待提示
        int statusColor = status == 4 ? 0xFF8EF7C0 : status == 8 ? 0xFFFFD88A : TEXT;
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.stargate.status." + status),
                TEXT_X, 50, statusColor, false);

        StargateControllerBlockEntity be = menu.getBlockEntity();
        String dim = be == null || be.getPartnerDimension().isEmpty()
                ? Component.translatable("gui.arcane_singularity.none").getString()
                : be.getPartnerDimension();
        String coords = be == null || be.getPartnerCoords().isEmpty()
                ? "-"
                : be.getPartnerCoords();
        line(graphics, TEXT_X, 64, Component.translatable("gui.arcane_singularity.partner_dim"), dim);
        line(graphics, TEXT_X, 78, Component.translatable("gui.arcane_singularity.partner_pos"), coords);
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.energy",
                        menu.get(1) == 1 ? Component.translatable("gui.arcane_singularity.online")
                                : Component.translatable("gui.arcane_singularity.offline")),
                TEXT_X, 92, menu.get(1) == 1 ? 0xFFFFD88A : DIM, false);
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.gate_pair",
                        menu.get(3) == 1 ? Component.translatable("gui.arcane_singularity.bound")
                                : Component.translatable("gui.arcane_singularity.unbound")),
                TEXT_X, 106, TEXT, false);
        if (menu.get(2) == 1) {
            graphics.drawString(font, Component.translatable("gui.arcane_singularity.portal_open"),
                    TEXT_X, 120, 0xFF8EF7C0, false);
        } else if (menu.get(5) > 0) {
            graphics.drawString(font, Component.translatable("gui.arcane_singularity.projection_active"),
                    TEXT_X, 120, 0xFF8FE7FF, false);
        }
    }

    /** 「标签：值」一整行，值按剩余宽度截断，保证不会顶出面板或压到下一行。 */
    private void line(GuiGraphics graphics, int x, int y, Component label, String value) {
        String prefix = label.getString() + "：";
        int available = TEXT_WIDTH - font.width(prefix);
        graphics.drawString(font, Component.literal(prefix), x, y, DIM, false);
        graphics.drawString(font, Component.literal(trim(value, Math.max(16, available))),
                x + font.width(prefix), y, TEXT, false);
    }

    private String trim(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        projectionButton.active = menu.get(0) == 0;
        closeButton.active = menu.get(3) == 1;
        redstoneButton.setMessage(redstoneLabel());
    }
}
