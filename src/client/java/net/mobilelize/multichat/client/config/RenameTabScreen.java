package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.client.LzTextField;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.fabriclib.world.LzWorld;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.Keys;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.ChatManager;

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
        field.setText(tab.name());
        field.setMaxLength(64);
        addSelectableChild(field);
        setInitialFocus(field);

        addDrawableChild(LzButton.of("OK",     cx - 53, cy + 18, 50, 18, b -> save()));
        addDrawableChild(LzButton.of("Cancel", cx + 3,  cy + 18, 50, 18, b -> close()));
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        if (LzWorld.isLoaded()) LzDraw.fill(ctx, 0, 0, width, height, 0x55000000);
        super.lzRender(ctx, mouseX, mouseY, delta);
        LzDraw.drawCenteredText(ctx, LzText.wrapMc(title), width / 2, height / 2 - 26, 0xFFFFFF);
        field.render(ctx.mc(), mouseX, mouseY, delta);
    }

    private void save() {
        String name = field.getText().trim();
        if (name.isEmpty()) return;
        boolean nameConflict = ChatManager.getTabs().stream()
            .filter(t -> t != tab && t.type() == tab.type())
            .anyMatch(t -> t.name().equalsIgnoreCase(name));
        if (nameConflict) return;
        tab.setName(name);
        MultichatClient.saveTabsToConfig();
        close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Keys.ENTER || keyCode == Keys.NUMPAD_ENTER) { save(); return true; }
        if (keyCode == Keys.ESCAPE) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() { LzClient.mc().setScreen(parent); }
}
