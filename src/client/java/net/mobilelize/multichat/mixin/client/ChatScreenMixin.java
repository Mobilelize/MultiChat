package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.input.LzKeys;
import net.mobilelize.fabriclib.network.LzConnection;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzStyle;
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

    @Shadow public abstract String normalize(String chatText);
    @Shadow protected TextFieldWidget chatField;
    @Shadow private ChatInputSuggestor chatInputSuggestor;

    @Unique
    private boolean isMouseOverSuggestor(double mouseX, double mouseY) {
        ChatInputSuggestorAccessor acc = (ChatInputSuggestorAccessor) chatInputSuggestor;
        if (!acc.invokeIsOpen()) return false;
        ChatInputSuggestor.SuggestionWindow window = acc.getWindow();
        if (window == null) return false;
        var area = ((ChatInputSuggestorWindowAccessor) window).getArea();
        return area != null && area.contains((int) mouseX, (int) mouseY);
    }

    @Inject(method = "init()V", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        if (MultichatClient.pendingChatText != null) {
            chatField.setText(MultichatClient.pendingChatText);
            MultichatClient.pendingChatText = null;
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("RETURN"))
    private void renderOverlays(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
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
                    int chatWidth    = selPanel.resolveRenderWidth();
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
                                _selLines != null ? _selLines.get(idx) : null, localX);
                            ChatSelection.extend(selPanel, idx, charIdx);
                        }
                    }
                }
            } else {
                ChatSelection.endDrag();
            }
        }

        if (ChatScrollbarDrag.isDragging()) {
            long handle = LzClient.windowHandle();
            boolean held = org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle,
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (held) {
                ChatPanel dragPanel = ChatScrollbarDrag.panel();
                if (dragPanel != null && dragPanel.isEnabled()) {
                    ChatScrollbarDrag.updateFromMouse(dragPanel, mouseY, LzClient.scaledWindowHeight());
                }
            } else {
                ChatScrollbarDrag.end();
            }
        }

        ChatTab ctxTab = ChatManager.contextTab;
        if (ctxTab != null && !ctxTab.contextPrefix().isEmpty()
                && MultichatClient.CONFIG.showContextDisplay) {
            String status = ctxTab.name() + " ▶ " + (ctxTab.contextCommandMode() ? "/" : "") + ctxTab.contextPrefixJoined() + "<message>";
            int screenH = LzDraw.scaledHeight(lzCtx);
            LzDraw.drawText(lzCtx, status, 4, screenH - 26, Colors.TEXT_STATUS);
        }

        ContextMenu.render(lzCtx);
        MessageContextMenu.render(lzCtx);

        updateCursor(mouseX, mouseY);
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

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers,
                               CallbackInfoReturnable<Boolean> cir) {
        if (MultichatClient.CONFIG.modDisabled) return;
        if (MultichatClient.CONFIG.selectionEnabled
                && keyCode == Keys.C && (modifiers & 2) != 0 && ChatSelection.hasSelection()) {
            String text = ChatSelection.getSelectedText();
            if (!text.isEmpty()) {
                LzClient.setClipboard(text);
                cir.setReturnValue(true);
                return;
            }
        }

        if (MultichatClient.CONFIG.searchKeybindEnabled
                && keyCode == MultichatClient.CONFIG.searchKeybindKey
                && (!MultichatClient.CONFIG.searchKeybindRequireCtrl || (modifiers & 2) != 0)) {
            String selText = ChatSelection.hasSelection()
                ? ChatSelection.getSelectedText().replaceAll("\n", "").trim() : "";
            LzClient.mc().setScreen(new SearchOverlay(LzClient.currentScreen(), null, selText));
            cir.setReturnValue(true);
            return;
        }

        if (MultichatClient.CONFIG.quickActionKeybindEnabled
                && keyCode == MultichatClient.CONFIG.quickActionKeybindKey
                && (!MultichatClient.CONFIG.quickActionKeybindRequireCtrl || (modifiers & 2) != 0)) {
            String sel = ChatSelection.hasSelection()
                ? ChatSelection.getSelectedText().replaceAll("\n", " ").trim() : "";
            LzClient.mc().setScreen(new QuickActionsOverlay(LzClient.currentScreen(), sel));
            cir.setReturnValue(true);
            return;
        }

        if (MultichatClient.CONFIG.arrowKeySwitchTabs
                && (keyCode == Keys.RIGHT || keyCode == Keys.LEFT)
                && chatField.getText().isEmpty()) {
            boolean goRight   = keyCode == Keys.RIGHT;
            boolean ctrlHeld  = (modifiers & 2) != 0;

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

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button,
                                 CallbackInfoReturnable<Boolean> cir) {
        // Truncate to int to match render()'s mouseX/mouseY, which the hover-cursor hit-test
        // (updateCursor) uses — the click gives full sub-pixel precision, so leaving it unfloored
        // makes click land a fraction of a pixel to the right/below hover, missing at edges.
        mouseX = (int) mouseX; mouseY = (int) mouseY;
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
                int chatWidth = panel.resolveRenderWidth();
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

        if (button == 0 && MultichatClient.CONFIG.scrollbarDragEnabled) {
            for (ChatPanel panel : ChatManager.getAllPanels()) {
                if (!ChatScrollbarDrag.isMouseOverRegion(panel, mouseX, mouseY, windowWidth, windowHeight)) continue;
                ChatScrollbarDrag.start(panel, mouseY, windowHeight);
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
                    int chatWidth = panel.resolveRenderWidth();
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
                        _dblLines != null ? _dblLines.get(exactIdx) : null, localX);
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
                int chatWidth = panel.resolveRenderWidth();
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
                List<LzChatLine> _sglLines = panel.getTabLines(active);
                LzChatLine _sglLine = _sglLines != null && exactIdx < _sglLines.size()
                    ? _sglLines.get(exactIdx) : null;

                // Same hit-test geometry as the hover cursor — skip starting a selection
                // when the click lands on a clickable span, so the link click falls through.
                boolean ctrlSkip = MultichatClient.CONFIG.selectionCtrlSkipsClicks
                    && LzKeys.isCtrlDown();
                if (!ctrlSkip && _sglLine != null) {
                    LzStyle style = ChatSelection.styleAtX(_sglLine, localX);
                    if (style != null && style.mc().getClickEvent() != null) break;
                }

                int charIdx  = ChatSelection.charIndexAtX(_sglLine, localX);
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

            int backW = panel.isExpanded() ? ChatRenderer.backButtonWidth() : 0;
            if (backW > 0) {
                double backEnd = (backW + xOffset) * (double) scale;
                if (button == 0 && mouseX < backEnd) {
                    panel.collapseMultiTab();
                    cir.setReturnValue(true);
                    return;
                }
            }

            if (button == 0) {
                if (hasLeft) {
                    double leftArrowEnd = (backW + ChatRenderer.ARROW_W + xOffset) * (double) scale;
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

            int renderX    = -4 + backW + (hasLeft ? ChatRenderer.ARROW_W : 0);
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
                        if (tab.isMultiTab()) panel.expandMultiTab(tab); else panel.setActiveTabId(i);
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
            int   chatWidth       = panel.resolveRenderWidth();
            int   lineH           = panel.resolveLineHeight();
            int   visibleLineCount = panel.resolveLines();
            int   topY            = panel.resolveTopY(windowHeight);
            if (!isMouseInMessageArea(panel, mouseY, topY, scale, lineH, visibleLineCount)) continue;

            int    xOff   = panel.computeXOffset(windowWidth);
            double localX = mouseX / (double) scale - 4.0 - xOff;
            if (localX < -4.0 || localX > chatWidth + 8) continue; // see ChatSelection.hitLine

            // Clickable text under the mouse: leave it to vanilla, which resolves the style through
            // ChatHudMixin's getTextStyleAt hook (MC 1.21.9 has no Screen-level click-event invoker).
            var hovered = ChatRenderer.lastHoveredStyle;
            if (hovered != null && hovered.getClickEvent() != null
                    && !(MultichatClient.CONFIG.selectionCtrlSkipsClicks && LzKeys.isCtrlDown())) return;

            cir.setReturnValue(true);
            return;
        }
    }

    @Unique private static double lastClickX    = -1;
    @Unique private static double lastClickY    = -1;
    @Unique private static long   lastClickTime = 0;

    @Unique
    private void updateCursor(int mouseX, int mouseY) {
        if (!MultichatClient.CONFIG.cursorChangingEnabled) return;

        if (isMouseOverSuggestor(mouseX, mouseY)) {
            MouseClient.resetCursor();
            return;
        }

        if (ContextMenu.containsMouse(mouseX, mouseY) || MessageContextMenu.containsMouse(mouseX, mouseY)) {
            MouseClient.setHandCursor();
            return;
        }

        int windowWidth  = LzClient.scaledWindowWidth();
        int windowHeight = LzClient.scaledWindowHeight();
        boolean ctrlHeld = LzKeys.isCtrlDown();

        for (ChatPanel panel : ChatManager.getAllPanels()) {
            if (!panel.isEnabled()) continue;
            float scale        = panel.resolveScale();
            int   chatWidth    = panel.resolveWidth();       // tab bar bounds below — never extends
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
                    int backWHover = panel.isExpanded() ? ChatRenderer.backButtonWidth() : 0;
                    boolean overTab  = false;
                    if (backWHover > 0 && mouseX < (backWHover + xOff) * (double) scale) {
                        overTab = true;
                    } else if (hasLeft && mouseX < (backWHover + ChatRenderer.ARROW_W + xOff) * (double) scale) {
                        overTab = true;
                    } else if (hasRight && mouseX >= (chatWidth + 4 - ChatRenderer.ARROW_W + 4 + xOff) * (double) scale) {
                        overTab = true;
                    } else {
                        int renderX    = -4 + backWHover + (hasLeft ? ChatRenderer.ARROW_W : 0);
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
                    if (overTab) { MouseClient.setHandCursor(); return; }
                }
            }

            if (!isMouseInMessageArea(panel, mouseY, topY, scale, lineH, visibleLines)) continue;

            // Single hit-test against our own rendered line — same geometry drives both the
            // hand (clickable) and IBeam (plain text) decision, instead of hand-checking against
            // vanilla's own ChatHud.getTextStyleAt (different coordinate space/geometry than our
            // custom panels), which is what let hand↔IBeam and text↔text transitions get stuck.
            int hit = ChatSelection.hitLine(panel, mouseX, mouseY,
                visibleLines, scale, lineH, windowHeight, panel.resolveRenderWidth(), xOff);
            if (hit >= 0) {
                ChatTab active = panel.getActiveTab();
                List<LzChatLine> lines = active != null ? panel.getTabLines(active) : null;
                if (lines != null && hit < lines.size()) {
                    LzChatLine    line    = lines.get(hit);
                    LzOrderedText content = line.content();
                    float localX = Math.max(0f, mouseX / scale - 4 - xOff);
                    int   textW  = LzClient.mc().textRenderer.getWidth(content.mc());
                    if (localX <= textW) {
                        // Clickable text → hand (skipped when Ctrl held — Ctrl is selection mode)
                        if (!ctrlHeld) {
                            LzStyle style = ChatSelection.styleAtX(line, localX);
                            if (style != null && style.mc().getClickEvent() != null) {
                                MouseClient.setHandCursor();
                                return;
                            }
                        }
                        // IBeam when there's actual text under the mouse — applies to both Ctrl and normal selection
                        if (ctrlHeld || MultichatClient.CONFIG.selectionEnabled) {
                            MouseClient.setIBeamCursor();
                            return;
                        }
                    }
                }
            }
        }

        if (MultichatClient.CONFIG.selectionEnabled && ChatSelection.isSelecting()) {
            MouseClient.setIBeamCursor();
            return;
        }

        MouseClient.resetCursor();
    }

    @Unique
    private static boolean isMouseInMessageArea(ChatPanel panel, double mouseY,
                                                int topY, float scale, int lineH, int visibleLineCount) {
        double areaTop = (topY - visibleLineCount * lineH) * (double) scale;
        if (!panel.showTabs()) return mouseY >= areaTop && mouseY < topY * (double) scale;
        int tabY = ChatRenderer.getTabBarY(panel, topY, visibleLineCount, lineH);
        if (panel.tabsAtTop()) {
            double tabBarBot = (tabY + ChatRenderer.TAB_HEIGHT) * (double) scale;
            return mouseY > tabBarBot && mouseY < topY * (double) scale;
        }
        return mouseY >= areaTop && mouseY < tabY * (double) scale;
    }


    // ------------------------------------------------------------------
    // Send message — apply context prefix
    // ------------------------------------------------------------------

    @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void interceptSendMessage(String chatText, boolean addToHistory, CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        String text = normalize(chatText);
        if (text.isEmpty() || text.startsWith("/")) return;

        if (MessageSentCallback.EVENT.invoker().onMessageSent(text)) {
            ci.cancel();
            return;
        }

        ChatTab ctxTab = ChatManager.contextTab;
        if (ctxTab == null || ctxTab.contextPrefix().isEmpty()) return;

        String full = ctxTab.contextPrefixJoined() + text;
        ci.cancel();
        if (addToHistory) LzChat.addToMessageHistory(full);
        if (ctxTab.contextCommandMode()) {
            LzConnection.sendChatCommand(full);
        } else {
            LzConnection.sendChatMessage(full);
        }
    }
}
