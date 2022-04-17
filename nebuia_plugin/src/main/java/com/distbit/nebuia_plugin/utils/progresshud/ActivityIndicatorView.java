package com.distbit.nebuia_plugin.utils.progresshud;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.ImageView;

import com.distbit.nebuia_plugin.R;


public class ActivityIndicatorView extends ImageView implements View.OnLayoutChangeListener {
    private Runnable animationAction;

    private long delayMillis = 100;
    private float toDegrees = 0;

    public ActivityIndicatorView(Context context) {
        super(context);
        setImageResource(R.drawable.progresshud_spinning);
        removeOnLayoutChangeListener(this);
    }

    private void startAnimation() {
        if (animationAction == null) {
            animationAction = () -> {
                toDegrees += 45;
                if (toDegrees > 360) toDegrees = toDegrees - 360;

                invalidate();
                postDelayed(animationAction, delayMillis);
            };
        }
        post(animationAction);
    }

    private void stopAnimation() {
        removeCallbacks(animationAction);
        animationAction = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.rotate(toDegrees, getWidth() / 2.0f, getHeight() / 2.0f);
        super.onDraw(canvas);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        stopAnimation();
    }
}
