package net.mobilelize.multichat.client;

public final class Colors {
    private Colors() {}

    // === OVERLAYS ===
    public static final int OVERLAY_DARK        = 0x88000000;
    public static final int OVERLAY             = 0x55000000;
    public static final int OVERLAY_LIGHT       = 0x44000000;

    // === MENU / PANEL BACKGROUNDS ===
    public static final int MENU_BG             = 0xDD111111;
    public static final int PANEL_BG            = 0x88111111;
    public static final int MODAL_BG            = 0xF0141414;
    public static final int NOTE_EDIT_BG        = 0xAA111111;

    // === BORDERS ===
    public static final int BORDER              = 0xFF666666;
    public static final int BORDER_DARK         = 0xFF444444;
    public static final int BORDER_DARKEST      = 0xFF333333;
    public static final int BORDER_DEEPER       = 0xFF4A4A4A;
    public static final int BORDER_DIM          = 0x88666666;

    // === TEXT ===
    public static final int TEXT_LINK           = 0x55FFFF;   // RGB only — for Style.withColor(), not drawing
    public static final int TEXT_WHITE          = 0xFFFFFFFF;
    public static final int TEXT_LIGHT          = 0xFFEEEEEE;
    public static final int TEXT_STATUS         = 0xFFDEDEDE;
    public static final int TEXT_LIGHT_GRAY     = 0xFFCCCCCC;
    public static final int TEXT_SCROLLBAR_DRAG = 0xFFDDDDDD;
    public static final int TEXT_DIMMED         = 0xFFAAAAAA;
    public static final int TEXT_GRAY           = 0xFF888888;
    public static final int TEXT_MID_GRAY       = 0xFF777777;
    public static final int TEXT_DARK_GRAY      = 0xFF666666;
    public static final int TEXT_DISABLED       = 0xFF555555;
    public static final int TEXT_INACTIVE       = 0xFF999999;

    // === ACCENT / TITLE ===
    public static final int GOLD                = 0xFFFFCC44;
    public static final int BLUE                = 0xFFAADDFF;
    public static final int TEAL                = 0xFFAAFFCC;

    // === TAB TYPE TEXT ===
    public static final int TAB_ALL_TEXT        = 0xFFAADDFF;
    public static final int TAB_SEARCH_TEXT     = 0xFFAAFFDD;
    public static final int TAB_API_TEXT        = 0xFFFFCC88;
    public static final int TAB_NOTES_TEXT      = 0xFFFFEE88;

    // === TAB TYPE BACKGROUND TINTS ===
    public static final int TAB_FILTERED_BG     = 0x55FFFFFF;
    public static final int TAB_SEARCH_BG       = 0x4400FFAA;
    public static final int TAB_API_BG          = 0x44FF9900;
    public static final int TAB_NOTES_BG        = 0x44FFDD55;
    public static final int TAB_DEFAULT_BG      = 0x44AAAAFF;

    // === TAB TYPE BORDER TINTS ===
    public static final int TAB_FILTERED_BORDER = 0x88AAAAAA;
    public static final int TAB_SEARCH_BORDER   = 0x8800CC88;
    public static final int TAB_API_BORDER      = 0x88CC6600;
    public static final int TAB_NOTES_BORDER    = 0x88DDAA00;
    public static final int TAB_DEFAULT_BORDER  = 0x886688CC;

    // === TAB HOVER ===
    public static final int TAB_HOVER           = 0x44FFFFFF;
    public static final int TAB_DEFAULT_HOVER   = 0x4466AAFF;

    // === TAB ACTIVE INDICATOR ===
    public static final int TAB_ACTIVE_BG           = 0x88FFDD00;
    public static final int TAB_ACTIVE_BG_HOVER     = 0x66FFDD44;
    public static final int TAB_ACTIVE_BG_DIM       = 0x33AA8800;
    public static final int TAB_ACTIVE_BORDER       = 0xFFFFDD00;
    public static final int TAB_ACTIVE_BORDER_DIM   = 0x66DDAA00;
    public static final int TAB_ACTIVE_TEXT         = 0xFFFFEE00;
    public static final int TAB_ACTIVE_TEXT_DIM     = 0xFFBBAA44;

    // === CHAT TAB BAR ===
    public static final int TAB_BAR_NO_COLOR_ACTIVE   = 0xBB888888;
    public static final int TAB_BAR_NO_COLOR_INACTIVE = 0x88444444;
    public static final int TAB_BAR_ARROW_TEXT        = 0xFFCCCCCC;

    // === CHAT HIGHLIGHTS (RGB component — alpha OR'd at runtime) ===
    public static final int HIGHLIGHT_SEARCH_RGB = 0x00FFAA00;
    public static final int HIGHLIGHT_NEW_RGB    = 0x0055CC55;
    public static final int HIGHLIGHT_FLASH_RGB  = 0x00CCCCCC;
    public static final int SELECTION            = 0x7744AAFF;

    // === ROW STATES ===
    public static final int ROW_EDITING         = 0x55FFCC44;
    public static final int ROW_SELECTED        = 0x55FFFF44;
    public static final int ROW_SELECTED_GOLD   = 0x66FFCC44;
    public static final int ROW_SWAP_SEL        = 0x553366FF;
    public static final int ROW_SWAP_HOVER      = 0x3300FFAA;
    public static final int ROW_HOVER           = 0x22FFFFFF;
    public static final int ROW_HOVER_STRONG    = 0x44FFFFFF;
    public static final int TEXT_GOLD_DIM       = 0xFFAA8822;

    // === SCROLLBAR ===
    public static final int SCROLLBAR_TRACK      = 0x44FFFFFF;
    public static final int SCROLLBAR_TRACK_DIM  = 0x33FFFFFF;
    public static final int SCROLLBAR_THUMB      = 0xBBAAAAAA;
    public static final int SCROLLBAR_THUMB_DRAG = 0xFFDDDDDD;

    // === BUTTONS (up/down/swap/move) ===
    public static final int BTN_HOVER    = 0xAA888888;
    public static final int BTN_NORMAL   = 0x66555555;
    public static final int BTN_DISABLED = 0x33333333;
    public static final int BTN_BORDER   = 0x88666666;

    // === SWAP BUTTON ===
    public static final int BTN_SWAP_PENDING = 0xAA4455FF;

    // === EDIT BUTTON ===
    public static final int BTN_EDIT_HOVER  = 0xAAFFCC44;
    public static final int BTN_EDIT_BG     = 0x44FFCC44;
    public static final int BTN_EDIT_BORDER = 0x88FFCC44;

    // === DELETE BUTTON (quick actions / note editor) ===
    public static final int BTN_DELETE_INSTANT_HOVER  = 0xAAFF3333;
    public static final int BTN_DELETE_INSTANT_BG     = 0x44FF3333;
    public static final int BTN_DELETE_WARN_HOVER     = 0xAAFF8800;
    public static final int BTN_DELETE_WARN_BG        = 0x44884400;
    public static final int BTN_DELETE_INSTANT_BORDER = 0x88FF5555;
    public static final int BTN_DELETE_WARN_BORDER    = 0x88FFAA00;
    public static final int BTN_DELETE_INSTANT_TEXT   = 0xFFFF8888;
    public static final int BTN_DELETE_WARN_TEXT      = 0xFFFFAA44;

    // === REMOVE BUTTON (tab list / panel list — slightly different shades) ===
    public static final int BTN_REMOVE_INSTANT_HOVER = 0xAAFF4444;
    public static final int BTN_REMOVE_INSTANT_BG    = 0x66882222;
    public static final int BTN_REMOVE_WARN_BG       = 0x66552200;

    // === DIALOG ACCENTS ===
    public static final int DIALOG_CONFIRM_ACCENT = 0xFF4477AA;
    public static final int DIALOG_DELETE_ACCENT  = 0xFFAA2222;

    // === PANEL LAYOUT SCREEN ===
    public static final int PANEL_NORMAL_LABEL       = 0xFFCCCCCC;
    public static final int PANEL_FILL_FAINT         = 0x11FFFFFF;
    public static final int PANEL_DRAG_HEADER        = 0x66FFAA00;
    public static final int GRID_LINE                = 0x0DFFFFFF;
    public static final int PANEL_PRIMARY_TINT       = 0x55446699;
    public static final int PANEL_SECONDARY_TINT     = 0x55FF8800;
    public static final int PANEL_LOCKED_FILL        = 0x1A4488FF;
    public static final int PANEL_LOCKED_BORDER      = 0xFF4488FF;
    public static final int PANEL_LOCKED_TEXT        = 0xFF88AAFF;
    public static final int PANEL_LOCKED_SUBTITLE    = 0x884488FF;
    public static final int PANEL_UNFOCUSED_LINE     = 0xFF44FF88;
    public static final int PANEL_UNFOCUSED_LINE_DIM = 0x8844FF88;
    public static final int PANEL_DRAG_FILL          = 0x33FFAA00;
    public static final int PANEL_DRAG_BORDER        = 0xFFFFAA00;
    public static final int PANEL_ANCHOR_TEXT        = 0x88FFFFFF;
    public static final int PANEL_RESIZE_ACTIVE      = 0xCCFFAA00;
    public static final int PANEL_RESIZE_HOVER       = 0xAAFFFFFF;

    // === PANEL LIST ROWS ===
    public static final int PANEL_ROW_0_BG     = 0x44AAAAFF;
    public static final int PANEL_ROW_1_BG     = 0x44BB88FF;
    public static final int PANEL_ROW_2_BG     = 0x4488FFAA;
    public static final int PANEL_ROW_0_BORDER = 0x886688CC;
    public static final int PANEL_ROW_1_BORDER = 0x88886699;
    public static final int PANEL_ROW_2_BORDER = 0x8855AA77;
    public static final int STATUS_ENABLED     = 0xFF55FF55;
    public static final int STATUS_DISABLED    = 0xFFFF5555;
    public static final int WL_ACTIVE          = 0xFF88FF88;
    public static final int BL_ACTIVE          = 0xFFFF8888;
    public static final int FLAGS_COLOR        = 0xFF9999CC;
    public static final int TABS_BTN_HOVER     = 0xAA336688;
    public static final int TABS_BTN_BG        = 0x66224466;
    public static final int TABS_BTN_BORDER    = 0x88446688;

    // === PANEL TABS SCREEN ===
    public static final int TOGGLE_ON_HOVER  = 0xCC55AA55;
    public static final int TOGGLE_ON_BG     = 0xAA337733;
    public static final int TOGGLE_OFF_HOVER = 0xCC555555;
    public static final int PANEL_TAB_ENABLED_FILL    = 0x4455FF88;
    public static final int PANEL_TAB_DISABLED_FILL   = 0x22FFFFFF;
    public static final int PANEL_TAB_LOCKED_FILL     = 0x11FFFFFF;
    public static final int PANEL_TAB_ENABLED_BORDER  = 0x8833BB66;
    public static final int PANEL_TAB_DISABLED_BORDER = 0x44888888;
    public static final int PANEL_TAB_LOCKED_BORDER   = 0x22666666;
    public static final int PANEL_TAB_LOCKED_LABEL    = 0x55AAAAAA;
    public static final int PANEL_TAB_LOCK_ICON       = 0xFF884444;
    public static final int PANEL_TAB_ON_TOGGLE_TEXT  = 0xFFAAFFAA;
    public static final int CTX_ACTIVE_BG_HOVER       = 0xCCFFAA00;
    public static final int CTX_ACTIVE_BG             = 0xAABB7700;
    public static final int CTX_INACTIVE_BG_HOVER     = 0xAA556677;
    public static final int CTX_INACTIVE_BG           = 0x66334455;
    public static final int CTX_ACTIVE_TEXT           = 0xFFFFCC00;

    // === FORMAT BUTTONS (note editor) ===
    public static final int FMT_BTN_R      = 0xFFAAAAAA;
    public static final int FMT_BTN_B      = 0xFFFFFFFF;
    public static final int FMT_BTN_I      = 0xFFCCCCFF;
    public static final int FMT_BTN_HOVER  = 0xAA555555;
    public static final int FMT_BTN_BG     = 0x44333333;
    public static final int FMT_BTN_BORDER = 0xFF444444;

    // === MINECRAFT CHAT PALETTE (full ARGB — used for note swatches) ===
    public static final int[] SWATCH_COLORS = {
        0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA,
        0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
        0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF,
        0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF,
    };
}
