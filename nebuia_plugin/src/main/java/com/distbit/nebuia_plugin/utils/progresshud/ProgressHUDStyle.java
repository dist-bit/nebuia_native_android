package com.distbit.nebuia_plugin.utils.progresshud;

public enum ProgressHUDStyle {
    Light("light"),  // default style, white HUD with black text, HUD background will be blurred
    Dark("dark"),   // black HUD and white text, HUD background will be blurred
    Custom("custom"); // uses the fore- and background color properties

    private String name;

    ProgressHUDStyle(String name) {
        this.name = name;
    }

    public static ProgressHUDStyle fromString(String text) {
        for (ProgressHUDStyle b : ProgressHUDStyle.values()) {
            if (b.name.equalsIgnoreCase(text)) {
                return b;
            }
        }
        return null;
    }
}
