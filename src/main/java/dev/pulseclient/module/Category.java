package dev.pulseclient.module;

/** Категории модулей — вкладки в ClickGUI. */
public enum Category {
    HUD("HUD"),
    RENDER("Render"),
    PLAYER("Player"),
    WORLD("World"),
    MISC("Misc");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
