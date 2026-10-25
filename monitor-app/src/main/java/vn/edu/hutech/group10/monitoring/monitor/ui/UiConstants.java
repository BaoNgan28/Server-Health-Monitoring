package vn.edu.hutech.group10.monitoring.monitor.ui;

import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;

import javax.swing.BorderFactory;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;

/** Colours, fonts and shared styling constants supporting both Dark and Light themes. */
public final class UiConstants {

    private static boolean darkMode = true;

    // Dark Palette (Current Modern DevOps Dashboard)
    public static final Color DARK_ONLINE = new Color(0x10, 0xB9, 0x81);      // Emerald 500
    public static final Color DARK_ONLINE_BG = new Color(0x06, 0x3E, 0x2E);   // Emerald dark tint
    public static final Color DARK_WARNING = new Color(0xF5, 0x9E, 0x0B);     // Amber 500
    public static final Color DARK_WARNING_BG = new Color(0x45, 0x24, 0x07);  // Amber dark tint
    public static final Color DARK_OFFLINE = new Color(0xEF, 0x44, 0x44);     // Rose 500
    public static final Color DARK_OFFLINE_BG = new Color(0x4C, 0x11, 0x11);  // Rose dark tint
    public static final Color DARK_UNKNOWN = new Color(0x94, 0xA3, 0xB8);     // Slate 400
    public static final Color DARK_UNKNOWN_BG = new Color(0x1E, 0x29, 0x3B);  // Slate dark tint

    public static final Color DARK_PANEL_BACKGROUND = new Color(0x0F, 0x17, 0x2A); // Slate 900
    public static final Color DARK_CARD_BACKGROUND = new Color(0x1E, 0x29, 0x3B);  // Slate 800
    public static final Color DARK_CARD_BORDER = new Color(0x33, 0x41, 0x55);      // Slate 700
    public static final Color DARK_HEADER_BACKGROUND = new Color(0x09, 0x0E, 0x1A); // Deep navy header
    public static final Color DARK_TEXT_PRIMARY = new Color(0xF8, 0xFA, 0xFC);     // Slate 50
    public static final Color DARK_TEXT_MUTED = new Color(0x94, 0xA3, 0xB8);       // Slate 400
    public static final Color DARK_TEXT_DIM = new Color(0x64, 0x74, 0x8B);         // Slate 500
    public static final Color DARK_ACCENT_CYAN = new Color(0x38, 0xBD, 0xF8);      // Sky 400
    public static final Color DARK_TABLE_STRIPE = new Color(0x16, 0x22, 0x35);

    // Light Palette (Original initial UI design)
    public static final Color LIGHT_ONLINE = new Color(0x1B, 0x8A, 0x3A);     // Original green
    public static final Color LIGHT_ONLINE_BG = new Color(0xE0, 0xF2, 0xE9);  // Light green tint
    public static final Color LIGHT_WARNING = new Color(0xC9, 0x7A, 0x00);    // Original amber
    public static final Color LIGHT_WARNING_BG = new Color(0xFE, 0xF3, 0xC7); // Light amber tint
    public static final Color LIGHT_OFFLINE = new Color(0xC6, 0x28, 0x28);    // Original red
    public static final Color LIGHT_OFFLINE_BG = new Color(0xFE, 0xE2, 0xE2); // Light red tint
    public static final Color LIGHT_UNKNOWN = new Color(0x66, 0x66, 0x66);    // Original grey
    public static final Color LIGHT_UNKNOWN_BG = new Color(0xF1, 0xF5, 0xF9); // Light slate tint

    public static final Color LIGHT_PANEL_BACKGROUND = new Color(0xF5, 0xF6, 0xF8); // Original panel bg
    public static final Color LIGHT_CARD_BACKGROUND = Color.WHITE;                  // Original card bg
    public static final Color LIGHT_CARD_BORDER = new Color(0xDD, 0xDD, 0xDD);      // Original border
    public static final Color LIGHT_HEADER_BACKGROUND = new Color(0xFA, 0xFA, 0xFB); // Light header bg
    public static final Color LIGHT_TEXT_PRIMARY = new Color(0x1E, 0x29, 0x3B);    // Slate 900
    public static final Color LIGHT_TEXT_MUTED = new Color(0x64, 0x74, 0x8B);      // Slate 500
    public static final Color LIGHT_TEXT_DIM = new Color(0x94, 0xA3, 0xB8);        // Slate 400
    public static final Color LIGHT_ACCENT_CYAN = new Color(0x02, 0x84, 0xC7);     // Sky 600
    public static final Color LIGHT_TABLE_STRIPE = new Color(0xF8, 0xFA, 0xFC);

    // Constants for static access compatibility
    public static final Color ONLINE = DARK_ONLINE;
    public static final Color ONLINE_BG = DARK_ONLINE_BG;
    public static final Color WARNING = DARK_WARNING;
    public static final Color WARNING_BG = DARK_WARNING_BG;
    public static final Color OFFLINE = DARK_OFFLINE;
    public static final Color OFFLINE_BG = DARK_OFFLINE_BG;
    public static final Color UNKNOWN = DARK_UNKNOWN;
    public static final Color UNKNOWN_BG = DARK_UNKNOWN_BG;

    public static final Color PANEL_BACKGROUND = DARK_PANEL_BACKGROUND;
    public static final Color CARD_BACKGROUND = DARK_CARD_BACKGROUND;
    public static final Color CARD_BORDER = DARK_CARD_BORDER;
    public static final Color HEADER_BACKGROUND = DARK_HEADER_BACKGROUND;
    public static final Color TEXT_PRIMARY = DARK_TEXT_PRIMARY;
    public static final Color TEXT_MUTED = DARK_TEXT_MUTED;
    public static final Color TEXT_DIM = DARK_TEXT_DIM;
    public static final Color ACCENT_CYAN = DARK_ACCENT_CYAN;
    public static final Color ACCENT_PURPLE = new Color(0xC0, 0x84, 0xFC);    // Purple 400
    public static final Color ACCENT_INDIGO = new Color(0x81, 0x8C, 0xF8);    // Indigo 400

    // Typography
    public static final Font HEADER_FONT = new Font("Segoe UI", Font.BOLD, 17);
    public static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font VALUE_FONT = new Font("Segoe UI", Font.BOLD, 26);
    public static final Font METRIC_FONT = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font SUBTITLE_FONT = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font BADGE_FONT = new Font("Segoe UI", Font.BOLD, 11);
    public static final Font MONO_FONT = new Font("Consolas", Font.PLAIN, 12);

    private UiConstants() {
    }

    public static boolean isDarkMode() {
        return darkMode;
    }

    public static void setDarkMode(boolean dark) {
        darkMode = dark;
    }

    public static Color getPanelBackground() {
        return darkMode ? DARK_PANEL_BACKGROUND : LIGHT_PANEL_BACKGROUND;
    }

    public static Color getCardBackground() {
        return darkMode ? DARK_CARD_BACKGROUND : LIGHT_CARD_BACKGROUND;
    }

    public static Color getCardBorder() {
        return darkMode ? DARK_CARD_BORDER : LIGHT_CARD_BORDER;
    }

    public static Color getHeaderBackground() {
        return darkMode ? DARK_HEADER_BACKGROUND : LIGHT_HEADER_BACKGROUND;
    }

    public static Color getTextPrimary() {
        return darkMode ? DARK_TEXT_PRIMARY : LIGHT_TEXT_PRIMARY;
    }

    public static Color getTextMuted() {
        return darkMode ? DARK_TEXT_MUTED : LIGHT_TEXT_MUTED;
    }

    public static Color getTextDim() {
        return darkMode ? DARK_TEXT_DIM : LIGHT_TEXT_DIM;
    }

    public static Color getAccentCyan() {
        return darkMode ? DARK_ACCENT_CYAN : LIGHT_ACCENT_CYAN;
    }

    public static Color getTableRowStripe() {
        return darkMode ? DARK_TABLE_STRIPE : LIGHT_TABLE_STRIPE;
    }

    /** Modern rounded card border with inner padding. */
    public static Border createCardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(getCardBorder(), 1),
                BorderFactory.createEmptyBorder(12, 14, 12, 14));
    }

    /** Colour used for a status text. */
    public static Color colorOf(AgentStatus status) {
        if (status == null) {
            return darkMode ? DARK_UNKNOWN : LIGHT_UNKNOWN;
        }
        switch (status) {
            case ONLINE:
                return darkMode ? DARK_ONLINE : LIGHT_ONLINE;
            case WARNING:
                return darkMode ? DARK_WARNING : LIGHT_WARNING;
            case OFFLINE:
                return darkMode ? DARK_OFFLINE : LIGHT_OFFLINE;
            default:
                return darkMode ? DARK_UNKNOWN : LIGHT_UNKNOWN;
        }
    }

    /** Background tint used for status badges. */
    public static Color bgColorOf(AgentStatus status) {
        if (status == null) {
            return darkMode ? DARK_UNKNOWN_BG : LIGHT_UNKNOWN_BG;
        }
        switch (status) {
            case ONLINE:
                return darkMode ? DARK_ONLINE_BG : LIGHT_ONLINE_BG;
            case WARNING:
                return darkMode ? DARK_WARNING_BG : LIGHT_WARNING_BG;
            case OFFLINE:
                return darkMode ? DARK_OFFLINE_BG : LIGHT_OFFLINE_BG;
            default:
                return darkMode ? DARK_UNKNOWN_BG : LIGHT_UNKNOWN_BG;
        }
    }
}

