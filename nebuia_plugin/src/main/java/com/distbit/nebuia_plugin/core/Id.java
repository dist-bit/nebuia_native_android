package com.distbit.nebuia_plugin.core;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import androidx.annotation.Keep;

@Keep
public class Id {
    static {
        System.loadLibrary("nebuia_plugin");
    }

    @Keep
    public class Obj
    {
        public float x;
        public float y;
        public float w;
        public float h;
        public String label;
        public float prob;
    }

    public native boolean Init(AssetManager mgr);
    public native Obj[] Detect(Bitmap bitmap, Bitmap out);
}