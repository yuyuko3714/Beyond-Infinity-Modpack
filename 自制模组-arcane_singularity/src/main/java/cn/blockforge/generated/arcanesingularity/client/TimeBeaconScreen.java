package cn.blockforge.generated.arcanesingularity.client;

import cn.blockforge.generated.arcanesingularity.blockentity.TimeBeaconControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.menu.TimeBeaconMenu;
import cn.blockforge.generated.arcanesingularity.redstone.RedstoneMode;
import cn.blockforge.generated.arcanesingularity.time.TimeMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 时间信标的操作界面。
 *
 * <p>布局自上而下：标题 → 四个时间模式按钮（一排四个，等宽等距）→ 倍率一行（「－」数值「＋」）
 * → 三行状态读数 → 充能进度条 → 底部两个按钮。每一行都留足了行距，中文标签不会挤到一起。</p>
 */
public class TimeBeaconScreen extends AbstractContainerScreen<TimeBeaconMenu> {
    private static final int BG = 0xF0060C14;
    private static final int PANEL = 0xCC0D1A26;
    private static final int GOLD = 0xFFD79A4A;
    private static final int CYAN = 0xFF7FE8FF;
    private static final int TEXT = 0xFFE8DEFF;
    private static final int DIM = 0xFF8D82A8;
    private static final int SIGNAL_ON = 0xFFFF7A6B;

    private static final int PANEL_LEFT = 10;
    private static final int PANEL_TOP = 22;
    private static final int PANEL_RIGHT = 246;
    private static final int PANEL_BOTTOM = 146;
    private static final int TEXT_X = 16;
    private static final int TEXT_WIDTH = 224;

    private static final int MODE_BUTTON_Y = 36;
    private static final int MODE_BUTTON_WIDTH = 54;
    private static final int[] MODE_BUTTON_X = {14, 72, 130, 188};

    private static final int SPEED_BUTTON_Y = 62;
    private static final int PROGRESS_Y = 118;
    private static final int PROGRESS_HEIGHT = 12;

    private Button projectionButton;
    private Button redstoneButton;
    private final Button[] modeButtons = new Button[TimeMode.values().length];
    private Button speedDownButton;
    private Button speedUpButton;

    public TimeBeaconScreen(TimeBeaconMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 180;
    }

    @Override
    protected void init() {
        super.init();
        TimeMode[] modes = TimeMode.values();
        for (int i = 0; i < modes.length; i++) {
            TimeMode mode = modes[i];
            final int id = TimeBeaconMenu.BUTTON_MODE_BASE + i;
            modeButtons[i] = addRenderableWidget(Button.builder(
                            modeLabel(mode),
                            button -> sendButton(id))
                    .bounds(leftPos + MODE_BUTTON_X[i], topPos + MODE_BUTTON_Y, MODE_BUTTON_WIDTH, 18)
                    .build());
        }
        this.speedDownButton = addRenderableWidget(Button.builder(
                        Component.literal("-"),
                        button -> sendButton(TimeBeaconMenu.BUTTON_SPEED_DOWN))
                .bounds(leftPos + 16, topPos + SPEED_BUTTON_Y, 22, 18).build());
        this.speedUpButton = addRenderableWidget(Button.builder(
                        Component.literal("+"),
                        button -> sendButton(TimeBeaconMenu.BUTTON_SPEED_UP))
                .bounds(leftPos + 212, topPos + SPEED_BUTTON_Y, 22, 18).build());
        this.projectionButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.arcane_singularity.projection"),
                        button -> sendButton(TimeBeaconMenu.BUTTON_PROJECTION))
                .bounds(leftPos + 14, topPos + 152, 108, 20).build());
        this.redstoneButton = addRenderableWidget(Button.builder(
                        redstoneLabel(),
                        button -> sendButton(TimeBeaconMenu.BUTTON_REDSTONE))
                .bounds(leftPos + 134, topPos + 152, 108, 20).build());
    }

    private Component modeLabel(TimeMode mode) {
        Component base = Component.translatable(mode.translationKey());
        return menu.get(TimeBeaconControllerBlockEntity.INDEX_MODE) == mode.id()
                ? Component.literal("> ").append(base)
                : base;
    }

    private Component redstoneLabel() {
        return Component.translatable(RedstoneMode.byId(menu.get(TimeBeaconControllerBlockEntity.INDEX_REDSTONE_MODE))
                .translationKey());
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
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, BG, 0xF00A1A2A);
        graphics.fill(x, y, x + imageWidth, y + 2, GOLD);
        graphics.fill(x, y + imageHeight - 2, x + imageWidth, y + imageHeight, 0xFF3E6E7A);
        graphics.fill(x, y, x + 2, y + imageHeight, 0xFF2E5A66);
        graphics.fill(x + imageWidth - 2, y, x + imageWidth, y + imageHeight, 0xFF2E5A66);

        // 信号面板
        graphics.fill(x + PANEL_LEFT, y + PANEL_TOP, x + PANEL_RIGHT, y + PANEL_BOTTOM, PANEL);
        graphics.fill(x + PANEL_LEFT, y + PANEL_TOP, x + PANEL_RIGHT, y + PANEL_TOP + 1, 0xFF245967);
        graphics.fill(x + PANEL_LEFT, y + PANEL_TOP, x + PANEL_LEFT + 1, y + PANEL_BOTTOM, 0xFF245967);
        graphics.renderOutline(x + PANEL_LEFT, y + PANEL_TOP, PANEL_RIGHT - PANEL_LEFT,
                PANEL_BOTTOM - PANEL_TOP, 0xFF1D4954);

        // 充能进度条：充能中按开机进度、运转中满是青色、关机中往回退
        int progress = menu.get(TimeBeaconControllerBlockEntity.INDEX_WARMUP);
        graphics.fill(x + TEXT_X, y + PROGRESS_Y,
                x + TEXT_X + TEXT_WIDTH, y + PROGRESS_Y + PROGRESS_HEIGHT, 0xFF04101A);
        int filled = TEXT_WIDTH * Math.min(1000, progress) / 1000;
        if (filled > 0) {
            int right = menu.get(TimeBeaconControllerBlockEntity.INDEX_RUNNING) == 1 ? CYAN : 0xFFB076FF;
            graphics.fillGradient(x + TEXT_X, y + PROGRESS_Y,
                    x + TEXT_X + filled, y + PROGRESS_Y + PROGRESS_HEIGHT, 0xFF2F7F9A, right);
        }
        for (int i = 1; i < 10; i++) {
            int tick = x + TEXT_X + i * TEXT_WIDTH / 10;
            graphics.fill(tick, y + PROGRESS_Y + 1, tick + 1,
                    y + PROGRESS_Y + PROGRESS_HEIGHT - 1, 0x55557F8C);
        }
        graphics.renderOutline(x + TEXT_X - 1, y + PROGRESS_Y - 1,
                TEXT_WIDTH + 2, PROGRESS_HEIGHT + 2, 0xFF245967);

        // 红石信号条：贴在「供能」那一行的右侧，不和左边文字抢位置
        int signal = Math.min(15, menu.get(TimeBeaconControllerBlockEntity.INDEX_REDSTONE_SIGNAL));
        int barY = y + 102;
        graphics.fill(x + 176, barY, x + 176 + 60, barY + 4, 0xFF04101A);
        if (signal > 0) {
            graphics.fill(x + 176, barY, x + 176 + 60 * signal / 15, barY + 4, SIGNAL_ON);
        }
        graphics.renderOutline(x + 176, barY, 60, 4, 0xFF245967);

        // 状态指示灯
        int status = menu.get(TimeBeaconControllerBlockEntity.INDEX_STATUS);
        int lamp = switch (status) {
            case 4 -> 0xFF7FF7D0;
            case 3 -> 0xFFB076FF;
            case 1, 2, 5 -> 0xFFFF8A6B;
            default -> 0xFF6B5B82;
        };
        graphics.fill(x + 218, y + 80, x + 224, y + 86, lamp);
        graphics.fill(x + 220, y + 82, x + 222, y + 84, 0xFFFFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, title, 128, 8, GOLD);
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.time.modes"),
                TEXT_X, 26, DIM, false);

        // 倍率一行：标签、当前倍率、两端各一个按钮
        // 倍率文字放在两个按钮之间，不再压在「-」按钮上
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.time.rate_label"),
                TEXT_X + 28, 67, DIM, false);
        TimeMode mode = TimeMode.byId(menu.get(TimeBeaconControllerBlockEntity.INDEX_MODE));
        graphics.drawCenteredString(font,
                Component.translatable(mode.rateKey(menu.get(TimeBeaconControllerBlockEntity.INDEX_SPEED))),
                128, 67, CYAN);

        // 三行读数
        int status = menu.get(TimeBeaconControllerBlockEntity.INDEX_STATUS);
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.time.status",
                        Component.translatable("gui.arcane_singularity.time.status." + status)),
                TEXT_X, 84, status == 4 ? 0xFF8EF7C0 : TEXT, false);
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.energy",
                        menu.get(TimeBeaconControllerBlockEntity.INDEX_POWERED) == 1
                                ? Component.translatable("gui.arcane_singularity.online")
                                : Component.translatable("gui.arcane_singularity.offline")),
                TEXT_X, 96, menu.get(TimeBeaconControllerBlockEntity.INDEX_POWERED) == 1 ? 0xFF7FE8FF : DIM, false);

        // 当前实际生效的倍率：只有激光在射的时候才会真的改变时间
        boolean running = menu.get(TimeBeaconControllerBlockEntity.INDEX_RUNNING) == 1;
        Component effective = running
                ? Component.translatable(mode.rateKey(menu.get(TimeBeaconControllerBlockEntity.INDEX_SPEED)))
                : Component.translatable("gui.arcane_singularity.time.rate.normal");
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.time.effective",
                        effective.getString()),
                TEXT_X, 108, TEXT, false);

        graphics.drawCenteredString(font,
                Component.translatable("gui.arcane_singularity.time.warmup",
                        Math.min(100, menu.get(TimeBeaconControllerBlockEntity.INDEX_WARMUP) / 10)),
                128, 120, 0xFFF4EEFF);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        projectionButton.active = menu.get(TimeBeaconControllerBlockEntity.INDEX_FORMED) == 0;
        redstoneButton.setMessage(redstoneLabel());
        for (int i = 0; i < modeButtons.length; i++) {
            if (modeButtons[i] != null) {
                modeButtons[i].setMessage(modeLabel(TimeMode.byId(i)));
            }
        }
        boolean adjustable = TimeMode.byId(menu.get(TimeBeaconControllerBlockEntity.INDEX_MODE)).adjustable();
        speedDownButton.active = adjustable;
        speedUpButton.active = adjustable;
    }
}
