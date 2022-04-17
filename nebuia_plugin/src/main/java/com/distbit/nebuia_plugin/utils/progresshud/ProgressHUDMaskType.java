package com.distbit.nebuia_plugin.utils.progresshud;

public enum ProgressHUDMaskType {
    None("none"),       // default mask type, allow user interactions while HUD is displayed
    Clear("clear"),      // don't allow user interactions with background objects
    Black("black"),      // don't allow user interactions with background objects and dim the UI in the back of the HUD (as seen in iOS 7 and above)
    Gradient("gradient"),   // don't allow user interactions with background objects and dim the UI with a a-la UIAlertView background gradient (as seen in iOS 6)
    Custom("custom");      // don't allow user interactions with background objects and dim the UI in the back of the HUD with a custom color

    private String name;

    ProgressHUDMaskType(String name) {
        this.name = name;
    }

    public static ProgressHUDMaskType fromString(String text) {
        for (ProgressHUDMaskType b : ProgressHUDMaskType.values()) {
            if (b.name.equalsIgnoreCase(text)) {
                return b;
            }
        }
        return null;
    }
}
