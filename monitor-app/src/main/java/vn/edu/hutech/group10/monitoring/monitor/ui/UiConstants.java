package vn.edu.hutech.group10.monitoring.monitor.ui;

import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;

import java.awt.Color;
import java.awt.Font;

/** Colours, fonts and small shared bits of the user interface. */
public final class UiConstants {

    public static final Color ONLINE = new Color(0x1B8A3A);
    public static final Color WARNING = new Color(0xC97A00);
    public static final Color OFFLINE = new Color(0xC62828);
    public static final Color UNKNOWN = new Color(0x666666);

    public static final Color PANEL_BACKGROUND = new Color(0xF5F6F8);
    public static final Color CARD_BACKGROUND = Color.WHITE;

    public static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 16);
    public static final Font VALUE_FONT = new Font("SansSerif", Font.BOLD, 22);
    public static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 12);
    public static final Font MONO_FONT = new Font("Monospaced", Font.PLAIN, 12);

    private UiConstants() {
    }

    /** Colour used for a status, in the table and on the detail screen. */
    public static Color colorOf(AgentStatus status) {
        if (status == null) {
            return UNKNOWN;
        }
        switch (status) {
            case ONLINE:
                return ONLINE;
            case WARNING:
                return WARNING;
            case OFFLINE:
                return OFFLINE;
            default:
                return UNKNOWN;
        }
    }
}
