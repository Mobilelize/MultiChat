package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.Keys;

public class DeleteConfirmScreen extends LzScreen {

    private final Screen   returnTo;
    private final String   message;
    private final Runnable onConfirm;

    public DeleteConfirmScreen(Screen returnTo, String message, Runnable onConfirm) {
        super("Confirm Delete");
        this.returnTo  = returnTo;
        this.message   = message;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int by = height / 2 + 10;
        addDrawableChild(LzButton.of("Delete", cx - 62, by, 58, 20, b -> { onConfirm.run(); close(); }));
        addDrawableChild(LzButton.of("Cancel", cx + 4,  by, 58, 20, b -> close()));
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        super.lzRender(ctx, mouseX, mouseY, delta);

        int mw  = 240, mh = 72;
        int mx1 = width  / 2 - mw / 2;
        int my1 = height / 2 - mh / 2;

        LzDraw.fill(ctx, mx1 + 4, my1 + 4, mx1 + mw + 4, my1 + mh + 4, 0x55000000);
        LzDraw.fill(ctx, mx1, my1, mx1 + mw, my1 + mh, 0xF0141414);
        LzDraw.fill(ctx, mx1, my1, mx1 + mw, my1 + 3, 0xFFAA2222);
        LzDraw.drawBorder(ctx, mx1, my1, mx1 + mw, my1 + mh, 0xFF4A4A4A);

        LzDraw.drawCenteredText(ctx, LzText.literal(message), width / 2, my1 + 14, 0xFFEEEEEE);
        LzDraw.drawCenteredText(ctx, LzText.literal("This cannot be undone."),
            width / 2, my1 + 26, 0xFF888888);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Keys.ESCAPE) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() { LzClient.mc().setScreen(returnTo); }
}
