package cn.blockforge.generated.arcanesingularity.client;

import cn.blockforge.generated.arcanesingularity.blockentity.WorldCoreControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.menu.WorldCoreMenu;
import cn.blockforge.generated.arcanesingularity.network.WorldCoreNetwork;
import cn.blockforge.generated.arcanesingularity.redstone.RedstoneMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 世界之核的操作界面。
 *
 * <p>左边是「命令方块」：一行命令输入（{@code >_}）、输入框正下方是自绘的语法提示
 * （{@link CommandAutocomplete}，输入 {@code /} 后自动列出可补全的子命令，Tab 补全、
 * 上下键选择），再下面是输出面板。语法提示是浮层，位置和行高都由输入框自己算，
 * 不会压到输入行或者输出文字上。</p>
 *
 * <p>右边是结构状态、三个接口的连接情况、世界核心槽、红石模式与命令方块模式。
 * 每一块内容都单独占一段纵向空间：文字与按钮之间留了至少 6 像素，按钮与按钮之间留了 4 像素，
 * 不会再出现文字压在按钮上、或者两行字挤在一起的情况。</p>
 *
 * <p>仪式没跑完之前左边是黑屏：充能期间只显示「初始化中 · 第 n 幕」，
 * 等巨大命令方块稳定下来、世界之核真正成形，输入框才解锁。</p>
 */
public class WorldCoreScreen extends AbstractContainerScreen<WorldCoreMenu> {
    private static final int BG = 0xF004080C;
    private static final int PANEL = 0xE6061016;
    private static final int GOLD = 0xFFD79A4A;
    private static final int CYAN = 0xFF7FE8FF;
    private static final int TEXT = 0xFFE8DEFF;
    private static final int DIM = 0xFF8D82A8;
    private static final int GREEN = 0xFF8EF7C0;
    private static final int RED = 0xFFFF8A6B;

    // 左面板：命令方块
    private static final int LEFT_PANEL = 10;
    private static final int LEFT_PANEL_RIGHT = 196;
    private static final int PANEL_TOP = 24;
    private static final int PANEL_BOTTOM = 196;
    private static final int SHELL_LEFT = 16;
    private static final int SHELL_TOP = 32;
    private static final int SHELL_WIDTH = 178;
    private static final int SHELL_HEIGHT = 18;
    private static final int OUTPUT_TOP = 106;
    private static final int OUTPUT_BOTTOM = 160;
    private static final int LEFT_BUTTON_Y = 166;

    // 右面板：结构状态
    private static final int RIGHT_PANEL = 202;
    private static final int RIGHT_PANEL_RIGHT = 310;
    private static final int LINK_ROW_0 = 42;
    private static final int LINK_ROW_HEIGHT = 12;
    private static final int INTERFACE_TITLE_Y = 80;
    private static final int INTERFACE_LAMP_Y = 90;
    private static final int CORE_TITLE_Y = 104;
    private static final int CORE_SLOT_X = 238;
    private static final int CORE_SLOT_Y = 112;
    private static final int CORE_SLOT_SIZE = 20;
    private static final int REDSTONE_BUTTON_Y = 138;
    private static final int MODE_BUTTON_Y = 160;
    private static final int PROGRESS_Y = 184;

    private EditBox commandField;
    private CommandAutocomplete autocomplete;
    private Button projectionButton;
    private Button executeButton;
    private Button cancelButton;
    private Button redstoneButton;
    private Button modeButton;

    public WorldCoreScreen(WorldCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 320;
        this.imageHeight = 300;
        this.titleLabelX = 10;
        this.titleLabelY = 6;
        this.inventoryLabelY = 205;
    }

    @Override
    protected void init() {
        super.init();
        WorldCoreControllerBlockEntity be = menu.getBlockEntity();
        String initial = be == null ? "" : be.getCommandText();
        if (!initial.isEmpty() && !initial.startsWith("/")) {
            initial = "/" + initial;
        }

        this.commandField = new EditBox(font, leftPos + SHELL_LEFT + 12, topPos + SHELL_TOP + 4,
                SHELL_WIDTH - 16, 10, Component.translatable("gui.arcane_singularity.world_core.command"));
        this.commandField.setMaxLength(512);
        this.commandField.setBordered(false);
        this.commandField.setTextColor(GREEN);
        this.commandField.setTextColorUneditable(DIM);
        this.commandField.setValue(initial);
        this.commandField.setHint(Component.translatable("gui.arcane_singularity.world_core.hint"));
        addRenderableWidget(this.commandField);

        // 语法提示：自己画在输入行正下方，位置跟着输入框走，不受原版聊天栏那套定死的坐标影响
        this.autocomplete = new CommandAutocomplete(minecraft, this.font, this.commandField,
                leftPos + SHELL_LEFT, topPos + SHELL_TOP + SHELL_HEIGHT + 2, SHELL_WIDTH);
        this.commandField.setResponder(text -> {
            if (this.autocomplete != null) {
                this.autocomplete.refresh();
            }
        });

        this.projectionButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.arcane_singularity.projection"),
                        button -> sendButton(WorldCoreMenu.BUTTON_PROJECTION))
                .bounds(leftPos + SHELL_LEFT, topPos + LEFT_BUTTON_Y, 56, 18).build());
        this.executeButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.arcane_singularity.world_core.execute"),
                        button -> submit(true))
                .bounds(leftPos + SHELL_LEFT + 60, topPos + LEFT_BUTTON_Y, 56, 18).build());
        this.cancelButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.arcane_singularity.world_core.cancel"),
                        button -> onClose())
                .bounds(leftPos + SHELL_LEFT + 120, topPos + LEFT_BUTTON_Y, 58, 18).build());

        this.redstoneButton = addRenderableWidget(Button.builder(
                        redstoneLabel(),
                        button -> sendButton(WorldCoreMenu.BUTTON_REDSTONE))
                .bounds(leftPos + 206, topPos + REDSTONE_BUTTON_Y, 100, 18).build());
        this.modeButton = addRenderableWidget(Button.builder(
                        modeLabel(),
                        button -> sendButton(WorldCoreMenu.BUTTON_COMMAND_MODE))
                .bounds(leftPos + 206, topPos + MODE_BUTTON_Y, 100, 18).build());
    }

    private Component redstoneLabel() {
        return Component.translatable(RedstoneMode.byId(
                menu.get(WorldCoreControllerBlockEntity.INDEX_REDSTONE_MODE)).translationKey());
    }

    private Component modeLabel() {
        return Component.translatable(menu.get(WorldCoreControllerBlockEntity.INDEX_COMMAND_MODE) == 0
                ? "gui.arcane_singularity.world_core.mode.pulse"
                : "gui.arcane_singularity.world_core.mode.cycle");
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void submit(boolean execute) {
        String value = commandField.getValue();
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (this.autocomplete != null) {
            this.autocomplete.hide();
        }
        WorldCoreNetwork.sendCommand(menu.getPos(), value, execute);
    }

    private boolean running() {
        return menu.get(WorldCoreControllerBlockEntity.INDEX_RUNNING) == 1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // 语法提示画在最上层：一整块不透明的下拉，下面的输出文字不会透出来
        if (this.autocomplete != null) {
            this.autocomplete.render(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.autocomplete != null && this.autocomplete.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.autocomplete != null && this.autocomplete.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, BG, 0xF002060A);
        graphics.fill(x, y, x + imageWidth, y + 2, GOLD);
        graphics.fill(x, y + imageHeight - 2, x + imageWidth, y + imageHeight, 0xFF3E6E7A);

        // 左面板：命令方块
        graphics.fill(x + LEFT_PANEL, y + PANEL_TOP, x + LEFT_PANEL_RIGHT, y + PANEL_BOTTOM, PANEL);
        graphics.renderOutline(x + LEFT_PANEL, y + PANEL_TOP,
                LEFT_PANEL_RIGHT - LEFT_PANEL, PANEL_BOTTOM - PANEL_TOP, 0xFF1D4954);
        // 命令输入行外壳
        graphics.fill(x + SHELL_LEFT, y + SHELL_TOP, x + SHELL_LEFT + SHELL_WIDTH, y + SHELL_TOP + SHELL_HEIGHT,
                0xFF020806);
        graphics.renderOutline(x + SHELL_LEFT, y + SHELL_TOP, SHELL_WIDTH, SHELL_HEIGHT, 0xFF2E5A66);
        // 输出面板
        graphics.fill(x + SHELL_LEFT, y + OUTPUT_TOP, x + SHELL_LEFT + SHELL_WIDTH, y + OUTPUT_BOTTOM,
                0xFF02060A);
        graphics.renderOutline(x + SHELL_LEFT, y + OUTPUT_TOP, SHELL_WIDTH,
                OUTPUT_BOTTOM - OUTPUT_TOP, 0xFF17343D);

        // 右面板：结构状态
        graphics.fill(x + RIGHT_PANEL, y + PANEL_TOP, x + RIGHT_PANEL_RIGHT, y + PANEL_BOTTOM, PANEL);
        graphics.renderOutline(x + RIGHT_PANEL, y + PANEL_TOP,
                RIGHT_PANEL_RIGHT - RIGHT_PANEL, PANEL_BOTTOM - PANEL_TOP, 0xFF1D4954);

        // 世界核心槽
        graphics.fill(x + CORE_SLOT_X, y + CORE_SLOT_Y,
                x + CORE_SLOT_X + CORE_SLOT_SIZE, y + CORE_SLOT_Y + CORE_SLOT_SIZE, 0xFF02060A);
        graphics.renderOutline(x + CORE_SLOT_X, y + CORE_SLOT_Y, CORE_SLOT_SIZE, CORE_SLOT_SIZE, 0xFF2E5A66);

        // 初始化进度条（按钮下面单独一条，不压任何文字）
        int progress = menu.get(WorldCoreControllerBlockEntity.INDEX_WARMUP);
        int barY = y + PROGRESS_Y;
        graphics.fill(x + 206, barY, x + 306, barY + 6, 0xFF02060A);
        int filled = 100 * Math.min(1000, progress) / 1000;
        if (filled > 0) {
            int color = running() ? CYAN : 0xFFB076FF;
            graphics.fillGradient(x + 206, barY, x + 206 + filled, barY + 6, 0xFF2F7F9A, color);
        }
        graphics.renderOutline(x + 206, barY, 100, 6, 0xFF245967);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, Component.translatable("block.arcane_singularity.world_core_controller"),
                160, 8, GOLD);

        boolean running = running();
        boolean warming = menu.get(WorldCoreControllerBlockEntity.INDEX_PHASE) == 1;

        // 左边：黑屏 / 初始化中 / 命令输出
        if (running) {
            graphics.drawString(font, Component.literal(">"), SHELL_LEFT + 4, SHELL_TOP + 4, GREEN, false);
            renderOutput(graphics);
        } else {
            String label = warming
                    ? Component.translatable("gui.arcane_singularity.world_core.initializing",
                    menu.get(WorldCoreControllerBlockEntity.INDEX_STAGE)).getString()
                    : Component.translatable("gui.arcane_singularity.world_core.offline").getString();
            int color = warming ? 0xFFB076FF : RED;
            int centerX = SHELL_LEFT + SHELL_WIDTH / 2;
            graphics.drawCenteredString(font, Component.literal(label), centerX, 118, color);
            if (warming) {
                graphics.drawCenteredString(font, Component.literal(
                        menu.get(WorldCoreControllerBlockEntity.INDEX_WARMUP) / 10 + "%"),
                        centerX, 132, DIM);
            }
            int status = menu.get(WorldCoreControllerBlockEntity.INDEX_STATUS);
            if (status >= 0 && status <= 8) {
                graphics.drawCenteredString(font, Component.translatable(
                                "gui.arcane_singularity.world_core.status." + status),
                        centerX, 146, DIM);
            }
        }

        // 右边：三个结构的在线状态
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.world_core.structures"),
                RIGHT_PANEL + 4, 30, DIM, false);
        int mask = menu.get(WorldCoreControllerBlockEntity.INDEX_LINK_MASK);
        drawLink(graphics, LINK_ROW_0, "gui.arcane_singularity.world_core.singularity", (mask & 1) != 0);
        drawLink(graphics, LINK_ROW_0 + LINK_ROW_HEIGHT, "gui.arcane_singularity.world_core.stargate",
                (mask & 2) != 0);
        drawLink(graphics, LINK_ROW_0 + LINK_ROW_HEIGHT * 2, "gui.arcane_singularity.world_core.beacon",
                (mask & 4) != 0);

        // 三个接口各自的连接情况
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.world_core.interfaces"),
                RIGHT_PANEL + 4, INTERFACE_TITLE_Y, DIM, false);
        for (int i = 0; i < 3; i++) {
            int interfaceMask = menu.get(WorldCoreControllerBlockEntity.INDEX_LINK_A + i);
            int color = interfaceMask != 0 ? GREEN : 0xFF6B5B82;
            graphics.fill(RIGHT_PANEL + 62 + i * 10, INTERFACE_LAMP_Y,
                    RIGHT_PANEL + 68 + i * 10, INTERFACE_LAMP_Y + 6, color);
        }

        // 核心槽标签
        graphics.drawString(font, Component.translatable("gui.arcane_singularity.world_core.core"),
                RIGHT_PANEL + 4, CORE_TITLE_Y, DIM, false);
    }

    private void drawLink(GuiGraphics graphics, int row, String key, boolean online) {
        graphics.drawString(font, Component.translatable(key), RIGHT_PANEL + 4, row, TEXT, false);
        graphics.drawString(font, Component.translatable(online
                        ? "gui.arcane_singularity.online" : "gui.arcane_singularity.offline"),
                RIGHT_PANEL + 74, row, online ? GREEN : DIM, false);
    }

    /** 输出面板：最多六行，从后往前取，最新的在最下面。 */
    private void renderOutput(GuiGraphics graphics) {
        WorldCoreControllerBlockEntity be = menu.getBlockEntity();
        String output = be == null ? "" : be.getLastOutput();
        if (output.isBlank()) {
            graphics.drawString(font, Component.translatable("gui.arcane_singularity.world_core.awaiting"),
                    SHELL_LEFT + 4, OUTPUT_TOP + 4, DIM, false);
            return;
        }
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        for (String line : output.split("\n")) {
            lines.addAll(font.split(Component.literal(line), SHELL_WIDTH - 10));
        }
        int max = 5;
        int start = Math.max(0, lines.size() - max);
        for (int i = start; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i),
                    SHELL_LEFT + 4, OUTPUT_TOP + 4 + (i - start) * 10, GREEN, false);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean running = running();
        boolean formed = menu.get(WorldCoreControllerBlockEntity.INDEX_FORMED) == 1;
        commandField.setEditable(running);
        commandField.active = running;
        if (autocomplete != null) {
            autocomplete.setEnabled(running);
        }
        executeButton.active = running;
        projectionButton.active = !formed;
        redstoneButton.setMessage(redstoneLabel());
        modeButton.setMessage(modeLabel());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 输入框聚焦时，这个界面就是一台「打字机」：只认文字输入与补全，
        // 其余任何键位（E 关界面、Q 丢弃、数字键换槽……）都在这里被吞掉，不会漏给容器和游戏。
        if (commandField != null && commandField.isFocused()) {
            // Tab / 上下键交给语法提示（补全、选择候选）
            if (this.autocomplete != null
                    && this.autocomplete.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (running()) {
                    if (this.autocomplete != null) {
                        this.autocomplete.hide();
                    }
                    submit(true);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                // 第一次 Esc 只退出输入状态，不关界面；再按一次才会正常关闭
                commandField.setFocused(false);
                if (this.autocomplete != null) {
                    this.autocomplete.hide();
                }
                return true;
            }
            // 退格、方向键、Home / End 等文本编辑键由输入框自己处理
            commandField.keyPressed(keyCode, scanCode, modifiers);
            if (this.autocomplete != null) {
                this.autocomplete.refresh();
            }
            // 剩下的键一律吞掉，绝不往下传
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // 只有聚焦时才收字符，避免失焦状态下敲键盘出现「隐形输入」
        if (commandField != null && commandField.isFocused() && commandField.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }
}
