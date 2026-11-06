package cn.blockforge.generated.arcanesingularity.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 世界之核控制器里那行命令输入的语法提示。
 *
 * <p>这块原来是直接用原版的 {@code CommandSuggestions}，但它给候选列表定位时，
 * 只要 {@code anchorToBottom} 是 {@code false} 就把纵坐标写死成 72；那是为聊天栏准备的，
 * 跟这个界面里输入框的实际位置毫无关系。于是候选列表被画在面板中间，一行压一行地
 * 盖在输入框和输出面板上，字全糊在一起。</p>
 *
 * <p>所以这里不用原版那套渲染，只借用 Brigadier 的补全数据：从客户端同步下来的命令树里
 * 取建议，然后自己按输入框的位置画一个不透明的下拉列表 —— 在上下的空隙里固定行高、
 * 固定宽度，永远不会和输入行、命令输出重叠。键盘上 Tab 补全、上下键选择、Esc 收起，
 * 鼠标可以直接点某一项，滚轮翻页。</p>
 */
public final class CommandAutocomplete {
    private static final int MAX_ROWS = 5;
    private static final int ROW_HEIGHT = 10;
    private static final int MIN_WIDTH = 80;

    private static final int FILL = 0xFF04090D;
    private static final int BORDER = 0xFF2E5A66;
    private static final int EDGE = 0xFF3E6E7A;
    private static final int SELECTED_FILL = 0xFF0F2C35;
    private static final int GOLD = 0xFFD79A4A;
    private static final int TEXT = 0xFFE8DEFF;
    private static final int DIM = 0xFF8D82A8;

    private final Minecraft minecraft;
    private final Font font;
    private final EditBox input;

    /** 列表左上角与最大宽度（屏幕坐标）。 */
    private final int x;
    private final int y;
    private final int maxWidth;

    private final List<String> candidates = new ArrayList<>();
    private StringRange range = StringRange.at(0);
    private boolean visible;
    private boolean enabled = true;
    private int selected;
    private int scroll;
    private int listWidth = MIN_WIDTH;
    private int generation;

    public CommandAutocomplete(Minecraft minecraft, Font font, EditBox input, int x, int y, int maxWidth) {
        this.minecraft = minecraft;
        this.font = font;
        this.input = input;
        this.x = x;
        this.y = y;
        this.maxWidth = Math.max(MIN_WIDTH, maxWidth);
    }

    /** 命令能不能输入（仪式没跑完之前整块是锁死的）。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            hide();
        }
    }

    public boolean isVisible() {
        return visible && !candidates.isEmpty();
    }

    public void hide() {
        this.visible = false;
        this.candidates.clear();
        this.selected = 0;
        this.scroll = 0;
        this.generation++;
    }

    /** 输入内容或光标变化后重新问一次命令树。 */
    public void refresh() {
        if (!enabled) {
            hide();
            return;
        }
        String value = input.getValue();
        int offset = value.startsWith("/") ? 1 : 0;
        String body = value.substring(offset);
        int cursor = Mth.clamp(input.getCursorPosition() - offset, 0, body.length());
        // 只敲一个 "/" 时 body 是空的，这时候也照样列出全部命令（和聊天栏一样）
        if (value.isEmpty()
                || minecraft.player == null
                || minecraft.player.connection == null
                || minecraft.player.connection.commands == null) {
            hide();
            return;
        }

        CommandDispatcher<SharedSuggestionProvider> dispatcher = minecraft.player.connection.commands;
        ParseResults<SharedSuggestionProvider> parsed = dispatcher.parse(body,
                minecraft.player.connection.getSuggestionsProvider());

        int request = ++this.generation;
        dispatcher.getCompletionSuggestions(parsed, cursor)
                // 有些服务器的建议提供者要服务端上下文，客户端算不出来时当作没有候选，别抛出到渲染线程
                .exceptionally(error -> new Suggestions(StringRange.at(0), List.of()))
                .thenAcceptAsync(suggestions -> {
                    if (request == this.generation) {
                        accept(suggestions);
                    }
                }, minecraft);
    }

    private void accept(Suggestions suggestions) {
        List<Suggestion> list = suggestions.getList();
        if (list.isEmpty()) {
            hide();
            return;
        }
        this.candidates.clear();
        for (Suggestion suggestion : list) {
            String text = suggestion.getText();
            if (!text.isEmpty()) {
                this.candidates.add(text);
            }
        }
        if (this.candidates.isEmpty()) {
            hide();
            return;
        }
        this.range = suggestions.getRange();
        this.visible = true;
        this.selected = 0;
        this.scroll = 0;

        int widest = MIN_WIDTH;
        for (String candidate : this.candidates) {
            widest = Math.max(widest, font.width(candidate) + 14);
        }
        this.listWidth = Math.min(this.maxWidth, widest);
    }

    /** 把当前选中的候选词填进输入框。 */
    public void apply() {
        if (!isVisible()) {
            return;
        }
        String text = this.candidates.get(this.selected);
        String value = input.getValue();
        int offset = value.startsWith("/") ? 1 : 0;
        int start = Mth.clamp(this.range.getStart() + offset, 0, value.length());
        int end = Mth.clamp(this.range.getEnd() + offset, start, value.length());
        String result = value.substring(0, start) + text + value.substring(end);

        hide();
        input.setValue(result);
        input.setCursorPosition(Mth.clamp(start + text.length(), 0, result.length()));
        // 补完之后如果这个位置还有别的候选（比如注册名列表），继续列出来
        refresh();
    }

    private void move(int delta) {
        if (this.candidates.isEmpty()) {
            return;
        }
        int size = this.candidates.size();
        this.selected = Math.floorMod(this.selected + delta, size);
        int rows = Math.min(MAX_ROWS, size);
        if (this.selected < this.scroll) {
            this.scroll = this.selected;
        } else if (this.selected >= this.scroll + rows) {
            this.scroll = this.selected - rows + 1;
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isVisible()) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_TAB:
                apply();
                return true;
            case GLFW.GLFW_KEY_UP:
                move(-1);
                return true;
            case GLFW.GLFW_KEY_DOWN:
                move(1);
                return true;
            case GLFW.GLFW_KEY_ESCAPE:
                hide();
                return true;
            default:
                // 其它键照常交给输入框，顺手刷新候选
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    hide();
                }
                return false;
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isVisible() || !inside(mouseX, mouseY)) {
            return false;
        }
        if (this.candidates.size() <= MAX_ROWS) {
            return true;
        }
        int maxScroll = this.candidates.size() - MAX_ROWS;
        this.scroll = Mth.clamp(this.scroll - (int) Math.signum(delta), 0, maxScroll);
        this.selected = Mth.clamp(this.selected, this.scroll, this.scroll + MAX_ROWS - 1);
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible()) {
            return false;
        }
        if (!inside(mouseX, mouseY)) {
            // 点别处就收起；点回输入框时保留，方便继续改字
            if (!input.isMouseOver(mouseX, mouseY)) {
                hide();
            }
            return false;
        }
        int row = (int) ((mouseY - this.y - 1) / ROW_HEIGHT);
        int index = this.scroll + row;
        if (button == 0 && index >= 0 && index < this.candidates.size()) {
            this.selected = index;
            apply();
        }
        return true;
    }

    private boolean inside(double mouseX, double mouseY) {
        int boxHeight = Math.min(MAX_ROWS, this.candidates.size()) * ROW_HEIGHT + 2;
        return mouseX >= this.x && mouseX < this.x + this.listWidth
                && mouseY >= this.y && mouseY < this.y + boxHeight;
    }

    /** 画在输入框正下方，不透明的一整块，下面的输出面板不会被透出来。 */
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isVisible()) {
            return;
        }
        int rows = Math.min(MAX_ROWS, this.candidates.size());
        int left = this.x;
        int right = this.x + this.listWidth;
        int top = this.y;
        int bottom = top + rows * ROW_HEIGHT + 2;

        graphics.fill(left, top, right, bottom, FILL);
        graphics.fill(left, top, right, top + 1, EDGE);
        graphics.fill(left, bottom - 1, right, bottom, BORDER);
        graphics.fill(left, top, left + 1, bottom, BORDER);
        graphics.fill(right - 1, top, right, bottom, BORDER);

        int textWidth = this.listWidth - 14;
        if (this.candidates.size() > rows) {
            // 右边留一条滚动条
            textWidth -= 4;
            int trackTop = top + 2;
            int trackHeight = rows * ROW_HEIGHT - 2;
            int barHeight = Math.max(8, trackHeight * rows / this.candidates.size());
            int maxScroll = this.candidates.size() - rows;
            int barTop = trackTop + (trackHeight - barHeight) * this.scroll / Math.max(1, maxScroll);
            graphics.fill(right - 5, trackTop, right - 3, trackTop + trackHeight, 0xFF0A1A20);
            graphics.fill(right - 5, barTop, right - 3, barTop + barHeight, 0xFF3E6E7A);
        }

        for (int row = 0; row < rows; row++) {
            int index = this.scroll + row;
            if (index >= this.candidates.size()) {
                break;
            }
            int rowTop = top + 1 + row * ROW_HEIGHT;
            boolean hovered = mouseX >= left && mouseX < right
                    && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT;
            boolean highlight = index == this.selected;
            if (highlight || hovered) {
                graphics.fill(left + 1, rowTop, right - 1, rowTop + ROW_HEIGHT - (row == rows - 1 ? 1 : 0),
                        SELECTED_FILL);
                graphics.fill(left + 1, rowTop, left + 2, rowTop + ROW_HEIGHT, highlight ? GOLD : DIM);
            }
            String label = font.plainSubstrByWidth(this.candidates.get(index), textWidth);
            graphics.drawString(font, label, left + 6, rowTop + 1,
                    highlight ? GOLD : (hovered ? TEXT : DIM), false);
        }
    }
}
