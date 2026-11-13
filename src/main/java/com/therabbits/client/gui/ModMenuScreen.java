package com.therabbits.client.gui;

import com.therabbits.client.Config;
import com.therabbits.client.ModuleManager;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.NumberSetting;
import com.therabbits.client.setting.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Right Shift menu: category sidebar on the left, module list on the right.
 * Click a module's switch to toggle it, click its name to open its settings.
 */
public class ModMenuScreen extends Screen {
    private static final int W = 420, H = 260, SIDEBAR = 110, ROW = 30, SETTING_ROW = 18;

    private final Screen parent;
    private Category selected = Category.HUD;
    private Module expanded;
    private double scroll;
    private NumberSetting draggingSlider;
    private int sliderX, sliderW;

    /** Click targets collected while rendering, used by mouseClicked. */
    private final List<Hit> hits = new ArrayList<>();
    private record Hit(int x, int y, int w, int h, Runnable action) {}

    public ModMenuScreen(Screen parent) {
        super(Component.literal("TheRabbits Menu"));
        this.parent = parent;
    }

    private int left() { return width / 2 - W / 2; }
    private int top() { return height / 2 - H / 2; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xA0000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        hits.clear();
        int x = left(), y = top();

        Draw.rect(g, x, y, W, H, Theme.PANEL);
        Draw.outline(g, x, y, W, H, Theme.BORDER);

        renderSidebar(g, x, y, mouseX, mouseY);
        renderModules(g, x + SIDEBAR, y, mouseX, mouseY);
    }

    private void renderSidebar(GuiGraphics g, int x, int y, int mx, int my) {
        Draw.rect(g, x, y, SIDEBAR, H, 0xFF0A0A0A);
        g.fill(x + SIDEBAR - 1, y, x + SIDEBAR, y + H, Theme.BORDER);
        Draw.logo(g, x + SIDEBAR / 2 - 30, y + 6, 60);
        Draw.centered(g, "TheRabbits", x + SIDEBAR / 2, y + 68, Theme.TEXT);

        int cy = y + 86;
        for (Category c : Category.values()) {
            boolean sel = c == selected;
            boolean hover = Draw.inside(mx, my, x + 6, cy, SIDEBAR - 12, 18);
            if (sel) Draw.rect(g, x + 6, cy, SIDEBAR - 12, 18, Theme.ACCENT);
            else if (hover) Draw.rect(g, x + 6, cy, SIDEBAR - 12, 18, Theme.HOVER);
            Draw.text(g, c.label(), x + 14, cy + 5, sel ? 0xFF000000 : Theme.TEXT_DIM);
            hits.add(new Hit(x + 6, cy, SIDEBAR - 12, 18, () -> { selected = c; expanded = null; scroll = 0; }));
            cy += 22;
        }

        int by = y + H - 26;
        boolean hover = Draw.inside(mx, my, x + 6, by, SIDEBAR - 12, 18);
        Draw.outline(g, x + 6, by, SIDEBAR - 12, 18, hover ? Theme.ACCENT : Theme.BORDER);
        Draw.centered(g, "Edit HUD", x + SIDEBAR / 2, by + 5, hover ? Theme.ACCENT : Theme.TEXT_DIM);
        hits.add(new Hit(x + 6, by, SIDEBAR - 12, 18, () -> minecraft.setScreen(new HudEditorScreen(this))));
    }

    private void renderModules(GuiGraphics g, int x, int y, int mx, int my) {
        int areaX = x + 8, areaY = y + 8, areaW = W - SIDEBAR - 16, areaH = H - 16;
        g.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);

        int ry = areaY - (int) scroll;
        for (Module m : ModuleManager.in(selected)) {
            boolean hover = Draw.inside(mx, my, areaX, ry, areaW, ROW - 4) && my >= areaY && my < areaY + areaH;
            Draw.rect(g, areaX, ry, areaW, ROW - 4, hover ? Theme.HOVER : Theme.RAISED);
            Draw.text(g, m.name(), areaX + 8, ry + 5, m.enabled() ? Theme.TEXT : Theme.TEXT_DIM);
            Draw.text(g, m.description(), areaX + 8, ry + 15, Theme.TEXT_MUTED);
            int tx = areaX + areaW - 26;
            Draw.toggle(g, tx, ry + 8, m.enabled());
            if (!m.settings().isEmpty())
                Draw.text(g, expanded == m ? "-" : "+", tx - 12, ry + 9, Theme.TEXT_DIM);

            addClipped(tx - 4, ry, 30, ROW - 4, areaY, areaH, m::toggle);
            addClipped(areaX, ry, areaW - 30, ROW - 4, areaY, areaH, () -> expanded = expanded == m ? null : m);
            ry += ROW;

            if (expanded == m) ry = renderSettings(g, m, areaX + 8, ry, areaW - 8, mx, my, areaY, areaH);
        }
        g.disableScissor();

        int content = ry + (int) scroll - areaY;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, content - areaH)));
    }

    private int renderSettings(GuiGraphics g, Module m, int x, int y, int w, int mx, int my, int areaY, int areaH) {
        for (Setting<?> s : m.settings()) {
            Draw.text(g, s.name(), x + 4, y + 5, Theme.TEXT_DIM);
            if (s instanceof BoolSetting b) {
                int tx = x + w - 26;
                Draw.toggle(g, tx, y + 4, b.on());
                addClipped(tx - 4, y, 30, SETTING_ROW, areaY, areaH, b::toggle);
            } else if (s instanceof NumberSetting n) {
                int sw = 120, sx = x + w - sw - 40;
                Draw.rect(g, sx, y + 8, sw, 2, Theme.OFF);
                int kx = sx + (int) (n.fraction() * sw);
                Draw.rect(g, sx, y + 8, kx - sx, 2, Theme.ACCENT);
                Draw.rect(g, kx - 2, y + 5, 5, 8, Theme.ACCENT);
                Draw.text(g, n.display(), sx + sw + 8, y + 5, Theme.TEXT);
                addClipped(sx - 3, y, sw + 6, SETTING_ROW, areaY, areaH, () -> {
                    draggingSlider = n; sliderX = sx; sliderW = sw;
                });
            }
            y += SETTING_ROW;
        }
        return y + 4;
    }

    private void addClipped(int x, int y, int w, int h, int areaY, int areaH, Runnable r) {
        int top = Math.max(y, areaY), bottom = Math.min(y + h, areaY + areaH);
        if (bottom > top) hits.add(new Hit(x, top, w, bottom - top, r));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (Hit h : List.copyOf(hits)) {
                if (Draw.inside(event.x(), event.y(), h.x(), h.y(), h.w(), h.h())) {
                    h.action().run();
                    if (draggingSlider != null) updateSlider(event.x());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingSlider != null) {
            updateSlider(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    private void updateSlider(double mouseX) {
        draggingSlider.setFraction((mouseX - sliderX) / sliderW);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingSlider = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * 16;
        if (scroll < 0) scroll = 0;
        return true;
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
