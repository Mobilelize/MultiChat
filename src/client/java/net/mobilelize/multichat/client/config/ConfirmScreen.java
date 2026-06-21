package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.fabriclib.world.LzWorld;
import net.mobilelize.multichat.client.Colors;
import net.mobilelize.multichat.client.Keys;

public class ConfirmScreen extends LzScreen {

    private final Screen   returnTo;
    private final String   message;
    private final Runnable onSave;
    private final Runnable onDiscard;

    public ConfirmScreen(Screen returnTo, String message, Runnable onSave, Runnable onDiscard) {
        super("Confirm");
        this.returnTo  = returnTo;
        this.message   = message;
        this.onSave    = onSave;
        this.onDiscard = onDiscard;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int by = height / 2 + 14;
        addDrawableChild(LzButton.of("Save",    cx - 95, by, 58, 20, b -> onSave.run()));
        addDrawableChild(LzButton.of("Discard", cx - 29, by, 58, 20, b -> onDiscard.run()));
        addDrawableChild(LzButton.of("Cancel",  cx + 37, by, 58, 20, b -> close()));
    }

    @Override
    public void lzRenderBackground(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        if (LzWorld.isLoaded()) LzDraw.fill(ctx, 0, 0, width, height, Colors.OVERLAY);
        super.lzRender(ctx, mouseX, mouseY, delta);

        int mw  = 240, mh = 74;
        int mx1 = width  / 2 - mw / 2;
        int my1 = height / 2 - mh / 2;

        LzDraw.fill(ctx, mx1 + 4, my1 + 4, mx1 + mw + 4, my1 + mh + 4, Colors.OVERLAY);
        LzDraw.fill(ctx, mx1, my1, mx1 + mw, my1 + mh, Colors.MODAL_BG);
        LzDraw.fill(ctx, mx1, my1, mx1 + mw, my1 + 3, Colors.DIALOG_CONFIRM_ACCENT);
        LzDraw.drawBorder(ctx, mx1, my1, mx1 + mw, my1 + mh, Colors.BORDER_DEEPER);

        LzDraw.drawCenteredText(ctx, LzText.literal(message), width / 2, my1 + 14, Colors.TEXT_LIGHT);
        LzDraw.drawCenteredText(ctx, LzText.literal("Unsaved changes will be lost."),
            width / 2, my1 + 26, Colors.TEXT_GRAY);
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        super.lzRender(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Keys.ESCAPE) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() { LzClient.mc().setScreen(returnTo); }
}
