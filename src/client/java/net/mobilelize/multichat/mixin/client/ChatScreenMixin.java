package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Style;
import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.input.LzKeys;
import net.mobilelize.fabriclib.network.LzConnection;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.multichat.api.MessageSentCallback;
import net.mobilelize.multichat.api.PanelFocusedCallback;
import net.mobilelize.multichat.api.TabChangedCallback;
import net.mobilelize.multichat.client.*;
import net.mobilelize.multichat.client.mouse.MouseClient;
import net.mobilelize.multichat.client.quickaction.QuickActionsOverlay;
import net.mobilelize.multichat.client.render.ChatRenderer;
import net.mobilelize.multichat.client.search.SearchOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    // ------------------------------------------------------------------
    // Render — status bar + context menu overlays
    // ------------------------------------------------------------------

    @Shadow public abstract String normalizeChatMessage(String chatText);
    @Shadow protected EditBox input;
    @Shadow private CommandSuggestions commandSuggestions;

    @Invoker("handleComponentClicked")
    abstract boolean invokeHandleComponentClicked(Style style, boolean shiftDown);

    @Unique
    private boolean isMouseOverSuggestor(double mouseX, double mouseY) {
        if (!commandSuggestions.isVisible()) return false;
        CommandSuggestions.SuggestionsList list =
            ((ChatInputSuggestorAccessor) commandSuggestions).getSuggestionsList();
        if (list == null) return false;
        Rect2i rect = ((ChatInputSuggestorWindowAccessor) (Object) list).getRect();
        return rect != null && rect.contains((int) mouseX, (int) mouseY);
    }

    @Inject(method = "init()V", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        if (MultichatClient.pendingChatText != null) {
            input.setValue(MultichatClient.pendingChatText);
            MultichatClient.pendingChatText = null;
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("RETURN"))
    private void renderOverlays(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        LzDrawContext lzCtx = LzDrawContext.wrap(context);
        if (MultichatClient.CONFIG.selectionEnabled && ChatSelection.isSelecting()) {
            long handle = LzClient.windowHandle();
            boolean held = org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle,
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (held) {
                ChatPanel selPanel = ChatSelection.getPanel();
                if (selPanel != null && selPanel.isEnabled()) {
                    int windowWidth  = LzClient.scaledWindowWidth();
                    int windowHeight = LzClient.scaledWindowHeight();
                    float scale      = selPanel.resolveScale();
                    int chatWidth    = selPanel.resolveWidth();
                    int lineH        = selPanel.resolveLineHeight();
                    int xOff         = selPanel.computeXOffset(windowWidth);
                    ChatTab active   = selPanel.getActiveTab();
                    if (active != null && active.hasMessages()) {
                        int idx = ChatSelection.hitLine(selPanel, mouseX, mouseY,
                            selPanel.resolveLines(), scale, lineH, windowHeight, chatWidth, xOff);
                        if (idx >= 0) {
                            float localX = Math.max(0f, (mouseX / scale) - 4 - xOff);
                            List<LzChatLine> _selLines = selPanel.getTabLines(active);
                        int charIdx  = ChatSelection.charIndexAtX(
                                _selLines != null ? _selLines.get(idx).content() : null, localX);
                            ChatSelection.extend(selPanel, idx, charIdx);
                        }
                    }
                }
            } else {
                ChatSelection.endDrag();
            }
        }

        ChatTab ctxTab = ChatManager.contextTab;
        if (ctxTab != null && !ctxTab.contextPrefix().isEmpty()
                && MultichatClient.CONFIG.showContextDisplay) {
            String status = ctxTab.name() + " ▶ " + (ctxTab.contextCommandMode() ? "/" : "") + ctxTab.contextPrefix() + " <message>";
            int screenH = LzDraw.scaledHeight(lzCtx);
            LzDraw.drawText(lzCtx, status, 4, screenH - 26, Colors.TEXT_STATUS);
        }

        ContextMenu.render(lzCtx);
        MessageContextMenu.render(lzCtx);

        updateCursor(context, mouseX, mouseY);
    }

    @Inject(method = "removed()V", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        ContextMenu.close();
        MessageContextMenu.close();
        ChatSelection.clear();
        MouseClient.resetCursor();
    }

    // ------------------------------------------------------------------
    // Key press — search keybind
    // ------------------------------------------------------------------

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent input,
                               CallbackInfoReturnable<Boolean> cir) {
        if (MultichatClient.CONFIG.modDisabled) return;
        if (MultichatClient.CONFIG.selectionEnabled
                && input.key() == Keys.C && (input.modifiers() & 2) != 0 && ChatSelection.hasSelection()) {
            String text = ChatSelection.getSelectedText();
            if (!text.isEmpty()) {
                LzClient.setClipboard(text);
                cir.setReturnValue(true);
                return;
            }
        }

        if (MultichatClient.CONFIG.searchKeybindEnabled
                && input.key() == MultichatClient.CONFIG.searchKeybindKey
                && (!MultichatClient.CONFIG.searchKeybindRequireCtrl || (input.modifiers() & 2) != 0)) {
            String selText = ChatSelection.hasSelection()
                ? ChatSelection.getSelectedText().replaceAll("\n", "").trim() : "";
            LzClient.mc().gui.setScreen(new SearchOverlay(LzClient.currentScreen(), null, selText));
            cir.setReturnValue(true);
            return;
        }

        if (MultichatClient.CONFIG.quickActionKeybindEnabled
                && input.key() == MultichatClient.CONFIG.quickActionKeybindKey
                && (!MultichatClient.CONFIG.quickActionKeybindRequireCtrl || (input.modifiers() & 2) != 0)) {
            String sel = ChatSelection.hasSelection()
                ? ChatSelection.getSelectedText().replaceAll("\n", " ").trim() : "";
            LzClient.mc().gui.setScreen(new QuickActionsOverlay(LzClient.currentScreen(), sel));
            cir.setReturnValue(true);
            return;
        }

        if (MultichatClient.CONFIG.arrowKeySwitchTabs
                && (input.key() == Keys.RIGHT || input.key() == Keys.LEFT)
                && this.input.getValue().isEmpty()) {
            boolean goRight   = input.key() == Keys.RIGHT;
            boolean ctrlHeld  = (input.modifiers() & 2) != 0;

            if (ctrlHeld) {
                // Ctrl+Arrow → cycle focused panel
                List<ChatPanel> candidates = ChatManager.getAllPanels().stream()
                    .filter(p -> p.isEnabled() && !p.getVisibleTabs().isEmpty())
                    .toList();
                if (candidates.size() > 1) {
                    int idx = candidates.indexOf(ChatManager.focusedPanel);
                    if (idx < 0) idx = 0;
                    ChatPanel prevPanel = ChatManager.focusedPanel;
                    ChatManager.focusedPanel = goRight
                        ? candidates.get((idx + 1) % candidates.size())
                        : candidates.get((idx - 1 + candidates.size()) % candidates.size());
                    PanelFocusedCallback.EVENT.invoker().onPanelFocused(ChatManager.focusedPanel, prevPanel);
                    if (MultichatClient.CONFIG.flashOnPanelFocus)
                        ChatManager.focusedPanel.flash(ChatRenderer.currentTick);
                    if (MultichatClient.CONFIG.panelContextEnabled) {
                        ChatManager.contextTab = ChatManager.focusedPanel.resolveContextTab();
                        MultichatClient.saveTabsToConfig();
                    }
                }
            } else {
                // Arrow → switch tab on focused panel
                ChatPanel panel = ChatManager.focusedPanel;
                if (panel == null || !panel.isEnabled() || panel.getVisibleTabs().isEmpty()) panel = ChatManager.primaryPanel();
                if (panel == null) return;
                List<ChatTab> visible = panel.getVisibleTabs();
                if (visible.size() > 1) {
                    panel.setActiveTabId(goRight
                        ? (panel.activeTabIndex() + 1) % visible.size()
                        : (panel.activeTabIndex() - 1 + visible.size()) % visible.size());
                    ChatTab nowActive = panel.getActiveTab();
                    nowActive.clearUnread();
                    if (MultichatClient.CONFIG.flashOnTabSwitch)
                        panel.flash(ChatRenderer.currentTick);
                    panel.scrollActiveTabIntoView();
                    TabChangedCallback.EVENT.invoker().onTabChanged(panel, nowActive);
                    if (MultichatClient.CONFIG.panelContextEnabled && panel.useActiveTabContext() && panel == ChatManager.focusedPanel)
                        ChatManager.contextTab = nowActive;
                }
            }
            cir.setReturnValue(true);
        }
    }

    // ------------------------------------------------------------------
    // Mouse click — tabs, context menus
    // ------------------------------------------------------------------

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent click, boolean isDoubleClickParam,
                                 CallbackInfoReturnable<Boolean> cir) {
        // Truncate to int to match extractRenderState's mouseX/mouseY (what populates
        // ChatRenderer.lastHoveredStyle) — click gives full sub-pixel precision, unfloored,
        // which misses the clickable style at edges vs what hover just captured.
        double mouseX = (int) click.x(); double mouseY = (int) click.y(); int button = click.button();
        if (MultichatClient.CONFIG.modDisabled) return;
        if (isMouseOverSuggestor(mouseX, mouseY)) return; // let vanilla's tab-complete popup handle its own click
        if (button == 0 && ContextMenu.isOpen()) {
            cir.setReturnValue(ContextMenu.handleClick((int) mouseX, (int) mouseY));
            return;
        }
        if (button == 0 && MessageContextMenu.isOpen()) {
            cir.setReturnValue(MessageContextMenu.handleClick((int) mouseX, (int) mouseY));
            return;
        }

        if (button == 0) ChatSelection.clear();

        int windowHeight = LzClient.scaledWindowHeight();
        int windowWidth  = LzClient.scaledWindowWidth();

        if (button == 1) {
            for (ChatPanel panel : ChatManager.getAllPanels()) {
                if (!panel.isEnabled()) continue;
                float scale = panel.resolveScale();
                int chatWidth = panel.resolveWidth();
                int lineH = panel.resolveLineHeight();
                int visibleLineCount = panel.resolveLines();
                int topY = panel.resolveTopY(windowHeight);
                if (!isMouseInMessageArea(panel, mouseY, topY, scale, lineH, visibleLineCount)) continue;
                ChatTab active = panel.getActiveTab();
                if (active == null) continue;

                int xOff = panel.computeXOffset(windowWidth);
                int hIdx = ChatRenderer.computeHoveredIndex(
                    panel, (int) mouseX, (int) mouseY,
                    visibleLineCount, windowHeight, scale, lineH, chatWidth, xOff, true);
                if (hIdx < 0) continue;

                ContextMenu.close();
                List<LzChatLine> _clickLines = panel.getTabLines(active);
                if (_clickLines == null || hIdx >= _clickLines.size()) continue;
                LzChatLine clicked = _clickLines.get(hIdx);
                MessageContextMenu.open(clicked, panel, (int) mouseX, (int) mouseY);
                cir.setReturnValue(true);
                return;
            }
        }

        if (MultichatClient.CONFIG.selectionEnabled && button == 0) {
            long now = System.currentTimeMillis();
            boolean isDoubleClick = MultichatClient.CONFIG.doubleClickSelectEnabled
                && now - lastClickTime < 400
                && Math.abs(mouseX - lastClickX) < 4
                && Math.abs(mouseY - lastClickY) < 4;

            if (isDoubleClick) {
                lastClickTime = 0;
                for (ChatPanel panel : ChatManager.getAllPanels()) {
                    if (!panel.isEnabled()) continue;
                    float scale = panel.resolveScale();
                    int chatWidth = panel.resolveWidth();
                    int lineH = panel.resolveLineHeight();
                    int visibleLineCount = panel.resolveLines();
                    int topY = panel.resolveTopY(windowHeight);
                    if (!isMouseInMessageArea(panel, mouseY, topY, scale, lineH, visibleLineCount)) continue;
                    ChatTab active = panel.getActiveTab();
                    if (active == null || !active.hasMessages()) continue;

                    int xOff = panel.computeXOffset(windowWidth);
                    int exactIdx = ChatSelection.hitLine(panel, mouseX, mouseY,
                        visibleLineCount, scale, lineH, windowHeight, chatWidth, xOff);
                    if (exactIdx < 0) continue;

                    float localX = Math.max(0f, (float)(mouseX / scale) - 4 - xOff);
                    List<LzChatLine> _dblLines = panel.getTabLines(active);
                    int charIdx  = ChatSelection.charIndexAtX(
                        _dblLines != null ? _dblLines.get(exactIdx).content() : null, localX);
                    ChatSelection.selectWord(panel, exactIdx, charIdx, MultichatClient.CONFIG.doubleClickNameSelect);
                    cir.setReturnValue(true);
                    return;
                }
            } else {
                lastClickX    = mouseX;
                lastClickY    = mouseY;
                lastClickTime = now;
            }

            if (!isDoubleClick) for (ChatPanel panel : ChatManager.getAllPanels()) {
                if (!panel.isEnabled()) continue;
                float scale = panel.resolveScale();
                int chatWidth = panel.resolveWidth();
                int lineH = panel.resolveLineHeight();
                int visibleLineCount = panel.resolveLines();
                int topY = panel.resolveTopY(windowHeight);
                if (!isMouseInMessageArea(panel, mouseY, topY, scale, lineH, visibleLineCount)) continue;
                ChatTab active = panel.getActiveTab();
                if (active == null || !active.hasMessages()) continue;

                int xOff = panel.computeXOffset(windowWidth);
                int exactIdx = ChatSelection.hitLine(panel, mouseX, mouseY,
                    visibleLineCount, scale, lineH, windowHeight, chatWidth, xOff);
                if (exactIdx < 0) continue;

                boolean ctrlSkip = MultichatClient.CONFIG.selectionCtrlSkipsClicks
                    && LzKeys.isCtrlDown();
                if (!ctrlSkip) {
                    var style = ChatRenderer.resolveStyleAt(mouseX, mouseY, windowWidth, windowHeight);
                    if (style != null && style.getClickEvent() != null) {
                        invokeHandleComponentClicked(style, false);
                        cir.setReturnValue(true);
                        return;
                    }
                }

                float localX = Math.max(0f, (float)(mouseX / scale) - 4 - xOff);
                List<LzChatLine> _sglLines = panel.getTabLines(active);
                int charIdx  = ChatSelection.charIndexAtX(
                    _sglLines != null ? _sglLines.get(exactIdx).content() : null, localX);
                ChatSelection.start(panel, exactIdx, charIdx);
                cir.setReturnValue(true);
                return;
            }
        }

        for (ChatPanel panel : ChatManager.getAllPanels()) {
            if (!panel.isEnabled() || !panel.showTabs()) continue;

            float scale = panel.resolveScale();
            int chatWidth = panel.resolveWidth();
            int lineH = panel.resolveLineHeight();
            int visibleLineCount = panel.resolveLines();
            int topY = panel.resolveTopY(windowHeight);

            int tabY = ChatRenderer.getTabBarY(panel, topY, visibleLineCount, lineH);
            double panelTabBarScreenTop = tabY * (double) scale;
            double panelTabBarScreenBot = (tabY + ChatRenderer.TAB_HEIGHT) * (double) scale;
            if (mouseY < panelTabBarScreenTop || mouseY > panelTabBarScreenBot) continue;

            int xOffset = panel.computeXOffset(windowWidth);
            List<ChatTab> visibleTabs = panel.getVisibleTabs();

            boolean hasLeft  = panel.tabScrollOffset() > 0;
            boolean hasRight = panel.tabLastRendered() >= 0 && panel.tabLastRendered() < visibleTabs.size() - 1;

            double panelLeftScreen  = xOffset * (double) scale;
            double panelRightScreen = (chatWidth + 8 + xOffset) * (double) scale;

            if (mouseX < panelLeftScreen || mouseX >= panelRightScreen) continue;

            if (button == 0) {
                if (hasLeft) {
                    double leftArrowEnd = (ChatRenderer.ARROW_W + xOffset) * (double) scale;
                    if (mouseX < leftArrowEnd) {
                        panel.setTabScrollOffset(Math.max(0, panel.tabScrollOffset() - 1));
                        cir.setReturnValue(true);
                        return;
                    }
                }
                if (hasRight) {
                    double rightArrowStart = (chatWidth + 4 - ChatRenderer.ARROW_W + 4 + xOffset) * (double) scale;
                    if (mouseX >= rightArrowStart) {
                        panel.setTabScrollOffset(panel.tabScrollOffset() + 1);
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }

            int renderX    = hasLeft ? ChatRenderer.ARROW_W - 4 : -4;
            int tabAreaEnd = chatWidth + 4 - (hasRight ? ChatRenderer.ARROW_W : 0);

            for (int i = panel.tabScrollOffset(); i < visibleTabs.size(); i++) {
                ChatTab tab = visibleTabs.get(i);
                String label = ChatRenderer.getTabLabel(panel, tab);
                int tabWidth = LzDraw.textWidth(label) + 8;

                boolean fits = renderX + tabWidth <= tabAreaEnd;
                double screenStart = (renderX + 4 + xOffset) * (double) scale;
                double screenEnd   = fits
                    ? (renderX + tabWidth + 4 + xOffset) * (double) scale
                    : (tabAreaEnd + 4 + xOffset) * (double) scale;

                if (!fits && (!hasRight || i != panel.tabLastRendered() + 1)) break;

                if (mouseX >= screenStart && mouseX < screenEnd) {
                    if (button == 0) {
                        boolean wasSameTab  = (i == panel.activeTabIndex());
                        boolean wasFocused  = (ChatManager.focusedPanel == panel);
                        panel.setActiveTabId(i);
                        panel.scrollActiveTabIntoView();
                        tab.clearUnread();
                        if (MultichatClient.CONFIG.switchFocusedPanelOnTabClick) {
                            ChatPanel prevPanel = ChatManager.focusedPanel;
                            ChatManager.focusedPanel = panel;
                            if (!wasFocused) PanelFocusedCallback.EVENT.invoker().onPanelFocused(panel, prevPanel);
                        }
                        if (!wasFocused && MultichatClient.CONFIG.switchFocusedPanelOnTabClick
                                && MultichatClient.CONFIG.flashOnPanelFocus) {
                            panel.flash(ChatRenderer.currentTick);
                        } else if (!wasSameTab && MultichatClient.CONFIG.flashOnMouseTabSwitch) {
                            panel.flash(ChatRenderer.currentTick);
                        }
                        TabChangedCallback.EVENT.invoker().onTabChanged(panel, tab);
                        if (MultichatClient.CONFIG.panelContextEnabled) {
                            if (!wasFocused && MultichatClient.CONFIG.switchFocusedPanelOnTabClick) {
                                ChatManager.contextTab = panel.resolveContextTab();
                                MultichatClient.saveTabsToConfig();
                            } else if (panel.useActiveTabContext()) {
                                ChatManager.contextTab = tab;
                            }
                        }
                    } else if (button == 1) {
                        MessageContextMenu.close();
                        ContextMenu.open(panel, tab, (int) mouseX, (int) mouseY);
                    }
                    cir.setReturnValue(true);
                    return;
                }

                if (!fits) break;
                renderX += tabWidth + 1;
            }
        }

        // Fallback: a click inside an enabled panel's message rectangle that didn't hit an
        // actual line (panel taller than its current message count, e.g. only 1 message showing)
        // must still be swallowed here. Left unhandled, it falls through to ChatScreen's own
        // click handling, which — for a non-vanilla active tab — resolves against vanilla's real
        // ChatHud layout (render-suppressed and invisible, but still there) instead of what's
        // actually on screen.
        for (ChatPanel panel : ChatManager.getAllPanels()) {
            if (!panel.isEnabled()) continue;
            ChatTab active = panel.getActiveTab();
            if (active == null || active.type() == TabType.VANILLA) continue;

            float scale           = panel.resolveScale();
            int   chatWidth       = panel.resolveWidth();
            int   lineH           = panel.resolveLineHeight();
            int   visibleLineCount = panel.resolveLines();
            int   topY            = panel.resolveTopY(windowHeight);
            if (!isMouseInMessageArea(panel, mouseY, topY, scale, lineH, visibleLineCount)) continue;

            int    xOff   = panel.computeXOffset(windowWidth);
            double localX = mouseX / (double) scale - 4.0 - xOff;
            if (localX < -4.0 || localX > chatWidth) continue;

            cir.setReturnValue(true);
            return;
        }
    }

    @Unique private static double lastClickX    = -1;
    @Unique private static double lastClickY    = -1;
    @Unique private static long   lastClickTime = 0;

    // Cursor is requested on the same GuiGraphicsExtractor vanilla applies to the window at the
    // very end of the frame. Setting it via MouseClient's raw glfwSetCursor instead — bypassing
    // that accumulator — desyncs vanilla's own cursor tracking from what's actually on screen:
    // the next time ANY vanilla widget touches the cursor, vanilla's end-of-frame apply silently
    // stomps our hand/IBeam back to arrow one frame later. Routing through context.requestCursor
    // keeps us inside vanilla's own accumulator so there's nothing left to race.
    @Unique
    private void updateCursor(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!MultichatClient.CONFIG.cursorChangingEnabled) return;

        if (isMouseOverSuggestor(mouseX, mouseY)) {
            context.requestCursor(CursorTypes.ARROW);
            return;
        }

        if (ContextMenu.containsMouse(mouseX, mouseY) || MessageContextMenu.containsMouse(mouseX, mouseY)) {
            context.requestCursor(CursorTypes.POINTING_HAND);
            return;
        }

        int windowWidth  = LzClient.scaledWindowWidth();
        int windowHeight = LzClient.scaledWindowHeight();
        boolean ctrlHeld = LzKeys.isCtrlDown();

        for (ChatPanel panel : ChatManager.getAllPanels()) {
            if (!panel.isEnabled()) continue;
            float scale        = panel.resolveScale();
            int   chatWidth    = panel.resolveWidth();
            int   lineH        = panel.resolveLineHeight();
            int   visibleLines = panel.resolveLines();
            int   topY         = panel.resolveTopY(windowHeight);
            int   xOff         = panel.computeXOffset(windowWidth);

            // Tab bar → hand only when over an actual tab or arrow button
            if (panel.showTabs()) {
                int    tabY      = ChatRenderer.getTabBarY(panel, topY, visibleLines, lineH);
                double tabBarTop = tabY * (double) scale;
                double tabBarBot = (tabY + ChatRenderer.TAB_HEIGHT) * (double) scale;
                if (mouseY >= tabBarTop && mouseY <= tabBarBot
                        && mouseX >= xOff * (double) scale
                        && mouseX < (chatWidth + 8 + xOff) * (double) scale) {
                    List<ChatTab> visibleTabs = panel.getVisibleTabs();
                    boolean hasLeft  = panel.tabScrollOffset() > 0;
                    boolean hasRight = panel.tabLastRendered() >= 0
                                    && panel.tabLastRendered() < visibleTabs.size() - 1;
                    boolean overTab  = false;
                    if (hasLeft && mouseX < (ChatRenderer.ARROW_W + xOff) * (double) scale) {
                        overTab = true;
                    } else if (hasRight && mouseX >= (chatWidth + 4 - ChatRenderer.ARROW_W + 4 + xOff) * (double) scale) {
                        overTab = true;
                    } else {
                        int renderX    = hasLeft ? ChatRenderer.ARROW_W - 4 : -4;
                        int tabAreaEnd = chatWidth + 4 - (hasRight ? ChatRenderer.ARROW_W : 0);
                        for (int i = panel.tabScrollOffset(); i < visibleTabs.size(); i++) {
                            ChatTab tab = visibleTabs.get(i);
                            String label = ChatRenderer.getTabLabel(panel, tab);
                            int tabWidth = LzDraw.textWidth(label) + 8;
                            boolean fits = renderX + tabWidth <= tabAreaEnd;
                            double screenStart = (renderX + 4 + xOff) * (double) scale;
                            double screenEnd   = fits
                                ? (renderX + tabWidth + 4 + xOff) * (double) scale
                                : (tabAreaEnd + 4 + xOff) * (double) scale;
                            if (!fits && (!hasRight || i != panel.tabLastRendered() + 1)) break;
                            if (mouseX >= screenStart && mouseX < screenEnd) { overTab = true; break; }
                            if (!fits) break;
                            renderX += tabWidth + 1;
                        }
                    }
                    if (overTab) { context.requestCursor(CursorTypes.POINTING_HAND); return; }
                }
            }

            if (!isMouseInMessageArea(panel, mouseY, topY, scale, lineH, visibleLines)) continue;

            // Clickable text → hand (skipped when Ctrl held — Ctrl is selection mode)
            if (!ctrlHeld) {
                var style = ChatRenderer.lastHoveredStyle;
                if (style != null && style.getClickEvent() != null) {
                    context.requestCursor(CursorTypes.POINTING_HAND);
                    return;
                }
            }

            // IBeam when there's actual text under the mouse — applies to both Ctrl and normal selection
            if (ctrlHeld || MultichatClient.CONFIG.selectionEnabled) {
                int hit = ChatSelection.hitLine(panel, mouseX, mouseY,
                    visibleLines, scale, lineH, windowHeight, chatWidth, xOff);
                if (hit >= 0) {
                    ChatTab active = panel.getActiveTab();
                    List<LzChatLine> lines = active != null ? panel.getTabLines(active) : null;
                    if (lines != null && hit < lines.size()) {
                        float localX = Math.max(0f, mouseX / scale - 4 - xOff);
                        int textW = LzClient.mc().font.width(lines.get(hit).content().mc());
                        if (localX <= textW) {
                            context.requestCursor(CursorTypes.IBEAM);
                            return;
                        }
                    }
                }
            }
        }

        if (MultichatClient.CONFIG.selectionEnabled && ChatSelection.isSelecting()) {
            context.requestCursor(CursorTypes.IBEAM);
            return;
        }

        context.requestCursor(CursorTypes.ARROW);
    }

    @Unique
    private static boolean isMouseInMessageArea(ChatPanel panel, double mouseY,
                                                int topY, float scale, int lineH, int visibleLineCount) {
        if (!panel.showTabs()) return mouseY < topY * (double) scale;
        int tabY = ChatRenderer.getTabBarY(panel, topY, visibleLineCount, lineH);
        if (panel.tabsAtTop()) {
            double tabBarBot = (tabY + ChatRenderer.TAB_HEIGHT) * (double) scale;
            return mouseY > tabBarBot && mouseY < topY * (double) scale;
        }
        return mouseY < tabY * (double) scale;
    }


    // ------------------------------------------------------------------
    // Send message — apply context prefix
    // ------------------------------------------------------------------

    @Inject(method = "handleChatInput(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void interceptSendMessage(String chatText, boolean addToHistory, CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        String text = normalizeChatMessage(chatText);
        if (text.isEmpty() || text.startsWith("/")) return;

        if (MessageSentCallback.EVENT.invoker().onMessageSent(text)) {
            ci.cancel();
            return;
        }

        ChatTab ctxTab = ChatManager.contextTab;
        if (ctxTab == null || ctxTab.contextPrefix().isEmpty()) return;

        String full = ctxTab.contextPrefix() + " " + text;
        ci.cancel();
        if (addToHistory) LzChat.addToMessageHistory(full);
        if (ctxTab.contextCommandMode()) {
            LzConnection.sendChatCommand(full);
        } else {
            LzConnection.sendChatMessage(full);
        }
    }
}
