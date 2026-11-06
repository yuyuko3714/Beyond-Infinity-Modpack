package cn.blockforge.generated.arcanesingularity.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GuideBookScreen extends Screen {
    private static final int GOLD = 0xFFD79A4A;
    private static final int TEXT = 0xFFE8DEFF;
    private static final int DIM = 0xFF9B8DB8;

    private final List<PageView> pages = new ArrayList<>();
    private final List<Integer> scrollOffsets = new ArrayList<>();
    private int pageIndex;
    private int bookX;
    private int bookY;
    private int bookWidth;
    private int bookHeight;
    private int spineX;
    private int contentX;
    private int bodyTop;
    private int contentBottom;
    private int textWidth;
    private int tocSpacing = 22;
    private Button previousButton;
    private Button nextButton;
    private boolean draggingScroll;

    public GuideBookScreen() {
        super(Component.translatable("item.arcane_singularity.arcane_codex"));
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(new GuideBookScreen());
        }
    }

    @Override
    protected void init() {
        super.init();
        this.bookWidth = Math.min(420, width - 40);
        this.bookHeight = Math.min(238, height - 40);
        this.bookX = (width - bookWidth) / 2;
        this.bookY = (height - bookHeight) / 2;
        this.spineX = bookX + 132;
        this.contentX = spineX + 16;
        this.textWidth = Math.max(96, bookX + bookWidth - 22 - contentX);
        this.bodyTop = bookY + 48;
        this.contentBottom = bookY + bookHeight - 44;
        this.tocSpacing = Math.max(14, Math.min(20,
                (bookHeight - 60) / Math.max(1, GuideBookData.CHAPTERS.size())));
        rebuildPages();
        this.pageIndex = Math.min(pageIndex, Math.max(0, pages.size() - 1));
        this.previousButton = addRenderableWidget(Button.builder(Component.literal("<"),
                        button -> turn(-1))
                .bounds(bookX + bookWidth - 108, bookY + bookHeight - 30, 48, 20).build());
        this.nextButton = addRenderableWidget(Button.builder(Component.literal(">"),
                        button -> turn(1))
                .bounds(bookX + bookWidth - 54, bookY + bookHeight - 30, 48, 20).build());
        updateButtons();
    }

    private void rebuildPages() {
        pages.clear();
        scrollOffsets.clear();
        for (int chapterIndex = 0; chapterIndex < GuideBookData.CHAPTERS.size(); chapterIndex++) {
            GuideBookData.Chapter chapter = GuideBookData.CHAPTERS.get(chapterIndex);
            List<FormattedCharSequence> lines =
                    new ArrayList<>(font.split(Component.literal(chapter.body()), textWidth));
            pages.add(new PageView(chapterIndex, chapter.title(), List.copyOf(lines), chapter.icon()));
            scrollOffsets.add(0);
        }
    }

    private void turn(int direction) {
        int next = pageIndex + direction;
        if (next >= 0 && next < pages.size()) {
            pageIndex = next;
            if (minecraft != null) {
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                        .forUI(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F));
            }
            updateButtons();
        }
    }

    private void updateButtons() {
        if (previousButton != null) {
            previousButton.active = pageIndex > 0;
        }
        if (nextButton != null) {
            nextButton.active = pageIndex < pages.size() - 1;
        }
    }

    private int viewHeight() {
        return Math.max(10, contentBottom - bodyTop);
    }

    private int contentHeight() {
        if (pages.isEmpty()) {
            return 0;
        }
        return pages.get(pageIndex).lines().size() * 10;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - viewHeight());
    }

    private int scroll() {
        if (pages.isEmpty() || scrollOffsets.isEmpty()) {
            return 0;
        }
        return Math.min(scrollOffsets.get(pageIndex), maxScroll());
    }

    private void setScroll(int value) {
        if (pages.isEmpty() || scrollOffsets.isEmpty()) {
            return;
        }
        scrollOffsets.set(pageIndex, Math.max(0, Math.min(value, maxScroll())));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fillGradient(bookX, bookY, bookX + bookWidth, bookY + bookHeight,
                0xFF0B0814, 0xFF1A102B);
        graphics.fill(bookX, bookY, bookX + bookWidth, bookY + 2, GOLD);
        graphics.fill(bookX, bookY + bookHeight - 2, bookX + bookWidth, bookY + bookHeight, 0xFF5B3A82);
        graphics.fill(bookX, bookY, bookX + 2, bookY + bookHeight, 0xFF6A4B2A);
        graphics.fill(bookX + bookWidth - 2, bookY, bookX + bookWidth, bookY + bookHeight, 0xFF6A4B2A);
        graphics.fill(spineX, bookY + 8, spineX + 1, bookY + bookHeight - 8, 0xFF503574);
        graphics.fill(spineX - 1, bookY + 8, spineX + 2, bookY + bookHeight - 8, 0x333C235C);

        graphics.drawCenteredString(font, Component.translatable("gui.arcane_singularity.codex_title"),
                bookX + 66, bookY + 9, GOLD);
        drawDiamond(graphics, bookX + 16, bookY + 12, GOLD);
        drawDiamond(graphics, bookX + bookWidth - 16, bookY + 12, GOLD);

        for (int i = 0; i < GuideBookData.CHAPTERS.size(); i++) {
            int y = bookY + 30 + i * tocSpacing;
            boolean current = !pages.isEmpty() && pages.get(pageIndex).chapterIndex() == i;
            if (current) {
                graphics.fill(bookX + 10, y - 3, spineX - 8, y + 12, 0x664A2F75);
                graphics.fill(bookX + 10, y - 3, bookX + 12, y + 12, GOLD);
            }
            String label = font.plainSubstrByWidth(GuideBookData.CHAPTERS.get(i).title(), 105);
            graphics.drawString(font, label, bookX + 16, y, current ? 0xFFFFE1A8 : DIM, false);
        }

        if (!pages.isEmpty()) {
            PageView page = pages.get(pageIndex);
            int contentY = bookY + 16;
            if (!page.icon().isEmpty()) {
                graphics.renderItem(page.icon(), contentX, contentY);
                graphics.drawString(font, page.title(), contentX + 22, contentY + 5, GOLD, false);
            } else {
                graphics.drawString(font, page.title(), contentX, contentY + 5, GOLD, false);
            }

            if (contentBottom > bodyTop + 2) {
                int scroll = scroll();
                int top = bodyTop;
                int bottom = contentBottom;
                graphics.enableScissor(contentX - 2, top - 2, bookX + bookWidth - 16, bottom + 2);
                int first = Math.max(0, scroll / 10);
                int last = Math.min(page.lines().size(), first + viewHeight() / 10 + 2);
                for (int i = first; i < last; i++) {
                    int lineY = top + i * 10 - scroll;
                    graphics.drawString(font, page.lines().get(i), contentX, lineY, TEXT, false);
                }
                graphics.disableScissor();
                drawScrollBar(graphics, scroll);
            }
            graphics.drawCenteredString(font, Component.literal((pageIndex + 1) + " / " + pages.size()),
                    bookX + 66, bookY + bookHeight - 14, DIM);
        }

        drawDiamond(graphics, bookX + 18, bookY + bookHeight - 18, 0xFF5B3A82);
        drawDiamond(graphics, bookX + bookWidth - 18, bookY + bookHeight - 18, 0xFF5B3A82);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int thumbHeight() {
        int view = viewHeight();
        return Math.max(18, (int) ((long) view * view / (double) Math.max(1, contentHeight())));
    }

    private int thumbTop(int scroll) {
        int travel = Math.max(1, viewHeight() - thumbHeight());
        return bodyTop + (int) ((long) travel * scroll / (double) Math.max(1, maxScroll()));
    }

    private int scrollFromMouse(double mouseY) {
        int travel = Math.max(1, viewHeight() - thumbHeight());
        double ratio = (mouseY - bodyTop - thumbHeight() / 2.0D) / travel;
        return (int) Math.round(Mth.clamp(ratio, 0.0D, 1.0D) * maxScroll());
    }

    private void drawScrollBar(GuiGraphics graphics, int scroll) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int barX = bookX + bookWidth - 11;
        int thumbHeight = thumbHeight();
        int thumbY = thumbTop(scroll);
        graphics.fill(barX, bodyTop, barX + 4, contentBottom, 0x553C235C);
        graphics.fill(barX, thumbY, barX + 4, thumbY + thumbHeight, 0xFFD79A4A);
        graphics.fill(barX + 1, thumbY + 1, barX + 3, thumbY + thumbHeight - 1, 0xFFFFE1A8);
    }

    private static void drawDiamond(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 3, y + 3, color);
        graphics.fill(x - 2, y + 1, x + 5, y + 2, color);
        graphics.fill(x + 1, y - 2, x + 2, y + 5, color);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, 0xDD04030A, 0xF0120824);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !pages.isEmpty()) {
            int barX = bookX + bookWidth - 15;
            if (maxScroll() > 0 && mouseX >= barX && mouseX <= barX + 12
                    && mouseY >= bodyTop && mouseY <= contentBottom) {
                setScroll(scrollFromMouse(mouseY));
                draggingScroll = true;
                return true;
            }
        }
        if (button == 0 && mouseX >= bookX + 10 && mouseX <= bookX + 124) {
            for (int i = 0; i < GuideBookData.CHAPTERS.size(); i++) {
                int y = bookY + 27 + i * tocSpacing;
                if (mouseY >= y && mouseY <= y + 16) {
                    for (int p = 0; p < pages.size(); p++) {
                        if (pages.get(p).chapterIndex() == i) {
                            pageIndex = p;
                            updateButtons();
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!pages.isEmpty() && maxScroll() > 0
                && mouseX >= contentX - 6 && mouseX <= bookX + bookWidth - 6
                && mouseY >= bodyTop - 6 && mouseY <= contentBottom + 6) {
            setScroll(scroll() - (int) Math.round(delta * 24.0D));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScroll) {
            setScroll(scrollFromMouse(mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263) {
            turn(-1);
            return true;
        }
        if (keyCode == 262) {
            turn(1);
            return true;
        }
        if (keyCode == 264 && maxScroll() > 0) {
            setScroll(scroll() + 20);
            return true;
        }
        if (keyCode == 265 && maxScroll() > 0) {
            setScroll(scroll() - 20);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record PageView(int chapterIndex, String title, List<FormattedCharSequence> lines, ItemStack icon) {
    }
}
