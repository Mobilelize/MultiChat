package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.screens.Screen;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.fabriclib.world.LzWorld;
import net.mobilelize.multichat.client.Colors;
import net.mobilelize.multichat.client.quickaction.QuickActionsEditScreen;

public abstract class ConfigScreen extends LzScreen {

    public enum NavPage { TABS, PANELS, QUICK_ACTIONS }

    public static final int LIST_W = 400;
    public static final int SB_W   = 6;

    protected final Screen parent;

    protected ConfigScreen(String title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        if (LzWorld.isLoaded()) LzDraw.fill(ctx, 0, 0, width, height, Colors.OVERLAY);
        super.lzRender(ctx, mouseX, mouseY, delta);
    }

    /** Called before any nav-button navigation. Override to save state, etc. */
    protected void beforeNavigate() {}

    /** Called before close(). Override to save state. */
    protected void beforeClose() {}

    protected void addConfigNav(int width, int height, NavPage current) {
        int sx = width / 2 - 136;
        var tabs = addRenderableWidget(LzButton.of("Tabs",
            sx, height - 48, 88, 20, b -> { beforeNavigate(); LzClient.mc().setScreen(new TabListScreen(parent)); }));
        var panels = addRenderableWidget(LzButton.of("Panels",
            sx + 92, height - 48, 88, 20, b -> { beforeNavigate(); LzClient.mc().setScreen(new PanelListScreen(parent)); }));
        var qa = addRenderableWidget(LzButton.of("Quick Actions",
            sx + 184, height - 48, 88, 20, b -> { beforeNavigate(); LzClient.mc().setScreen(new QuickActionsEditScreen(parent)); }));
        if (current == NavPage.TABS)          tabs.active   = false;
        if (current == NavPage.PANELS)        panels.active = false;
        if (current == NavPage.QUICK_ACTIONS) qa.active     = false;
        addRenderableWidget(LzButton.of(TabListScreen.quickDeleteMode ? "Del: ON" : "Del: OFF",
            sx, height - 24, 88, 20, b -> {
                TabListScreen.quickDeleteMode = !TabListScreen.quickDeleteMode;
                b.setMessage(LzText.literal(TabListScreen.quickDeleteMode ? "Del: ON" : "Del: OFF"));
            }));
        addRenderableWidget(LzButton.of("Settings",
            sx + 92, height - 24, 88, 20, b -> LzClient.mc().setScreen(ConfigMenu.create(this))));
        addRenderableWidget(LzButton.of("Done", sx + 184, height - 24, 88, 20, b -> onClose()));
    }

    @Override
    public void onClose() {
        beforeClose();
        LzClient.mc().setScreen(parent);
    }
}
