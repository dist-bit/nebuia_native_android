package com.distbit.nebuia_plugin.utils.progresshud;

public enum ProgressHUDAnimationType {
    Flat("flat"),    // default animation type, custom flat animation (indefinite animated ring)
    Native("native");  // iOS native UIActivityIndicatorView

    private String name;

    ProgressHUDAnimationType(String name) {
        this.name = name;
    }

    public static ProgressHUDAnimationType fromString(String text) {
        for (ProgressHUDAnimationType b : ProgressHUDAnimationType.values()) {
            if (b.name.equalsIgnoreCase(text)) {
                return b;
            }
        }
        return null;
    }
}
