package dev.pulseclient.module;

/** Категории модулей — панели в ClickGUI. */
public enum Category {
    HUD("HUD", "▣"),
    RENDER("Визуалы", "✦"),
    PLAYER("Игрок", "☺"),
    WORLD("Мир", "☀"),
    MISC("Разное", "⚙");

    private final String displayName;
    private final String icon;

    Category(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }
}
