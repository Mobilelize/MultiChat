package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.screens.Screen;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.client.LzTextField;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.fabriclib.world.LzWorld;
import net.minecraft.client.input.KeyEvent;
import net.mobilelize.multichat.client.*;

public class RenameTabScreen extends LzScreen {

    private final Screen  parent;
    private final ChatTab tab;
    private LzTextField field;

    public RenameTabScreen(Screen parent, ChatTab tab) {
        super("Rename Tab");
        this.parent = parent;
        this.tab    = tab;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        field = new LzTextField(cx - 100, cy - 10, 200, 20);
        field.setValue(tab.name());
        field.setMaxLength(64);
        addWidget(field);
        setInitialFocus(field);

        addRenderableOnly(LzButton.of("OK",     cx - 53, cy + 18, 50, 18, b -> save()));
        addRenderableWidget(LzButton.of("Cancel", cx + 3,  cy + 18, 50, 18, b -> onClose()));
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        if (LzWorld.isLoaded()) LzDraw.fill(ctx, 0, 0, width, height, Colors.OVERLAY);
        super.lzRender(ctx, mouseX, mouseY, delta);
        LzDraw.drawCenteredText(ctx, LzText.wrapMc(title), width / 2, height / 2 - 26, Colors.TEXT_WHITE);
        field.extractRenderState(ctx.mc(), mouseX, mouseY, delta);
    }

    private void save() {
        String name = field.getValue().trim();
        if (name.isEmpty()) return;
        boolean nameConflict = ChatManager.getTabs().stream()
            .filter(t -> t != tab && t.type() == tab.type())
            .anyMatch(t -> t.name().equalsIgnoreCase(name));
        if (nameConflict) return;
        tab.setName(name);
        MultichatClient.saveTabsToConfig();
        onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == Keys.ENTER || input.key() == Keys.NUMPAD_ENTER) { save(); return true; }
        if (input.key() == Keys.ESCAPE) { onClose(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public void onClose() { LzClient.mc().setScreen(parent); }
}
