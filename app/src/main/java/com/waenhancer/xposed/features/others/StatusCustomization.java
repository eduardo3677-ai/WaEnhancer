package com.waenhancer.xposed.features.others;

import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class StatusCustomization extends Feature {

    public StatusCustomization(@NonNull ClassLoader loader, @NonNull SharedPreferences prefs) {
        super(loader, prefs);
    }

    @Override
    public void doHook() throws Throwable {
        boolean anyEnabled = prefs.getBoolean("remove_status_bottom_tile", false)
                || prefs.getBoolean("remove_status_quick_reactions", false)
                || prefs.getBoolean("remove_status_heart_button", false)
                || prefs.getBoolean("status_bottom_play_pause_button", false)
                || prefs.getBoolean("add_status_reply_menu_item", false)
                || prefs.getBoolean("status_video_fast_gesture", false)
                || prefs.getBoolean("status_video_fast_speed", false)
                || prefs.getBoolean("disable_status_swipe_up", false);

        if (!anyEnabled) return;

        hookStatusPlaybackFragment();
        hookStatusBar();
        hookStatusGesture();
    }

    private void hookStatusPlaybackFragment() throws Throwable {
        Method onCreateMethod = null;
        try {
            Class<?> clazz = XposedHelpers.findClass("com.whatsapp.status.playback.StatusPlaybackActivity", classLoader);
            onCreateMethod = XposedHelpers.findMethodExactIfExists(clazz, "onCreate", android.os.Bundle.class);
        } catch (Throwable ignored) {}

        if (onCreateMethod == null) {
            try {
                onCreateMethod = Unobfuscator.loadStatusPlaybackOnCreate(classLoader);
            } catch (Throwable ignored) {}
        }

        if (onCreateMethod == null) return;

        XposedBridge.hookMethod(onCreateMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object activity = param.thisObject;
                if (activity == null) return;

                View decorView = null;
                if (activity instanceof android.app.Activity) {
                    decorView = ((android.app.Activity) activity).getWindow().getDecorView();
                }
                if (decorView == null) return;

                if (prefs.getBoolean("remove_status_bottom_tile", false)) {
                    removeViewById(decorView, "status_bottom_tile");
                    removeViewByClass(decorView, "BottomBar");
                }
                if (prefs.getBoolean("remove_status_quick_reactions", false)) {
                    removeViewById(decorView, "quick_reactions");
                    removeViewByClass(decorView, "QuickReaction");
                }
                if (prefs.getBoolean("remove_status_heart_button", false)) {
                    removeViewById(decorView, "heart_button");
                    removeViewById(decorView, "status_heart");
                }
                if (prefs.getBoolean("status_bottom_play_pause_button", false)) {
                    addPlayPauseButton(decorView);
                }
                if (prefs.getBoolean("add_status_reply_menu_item", false)) {
                    addReplyMenuItem(decorView);
                }
            }
        });
    }

    private void hookStatusBar() throws Throwable {
        if (!prefs.getBoolean("disable_status_swipe_up", false)) return;
        try {
            Class<?> viewPagerClass = XposedHelpers.findClass("androidx.viewpager.widget.ViewPager", classLoader);
            XposedHelpers.findAndHookMethod(viewPagerClass, "onInterceptTouchEvent", android.view.MotionEvent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            android.view.MotionEvent ev = (android.view.MotionEvent) param.args[0];
                            if (ev != null && ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                                float y = ev.getY();
                                float height = ((View) param.thisObject).getHeight();
                                if (y > height * 0.7f) {
                                    param.setResult(false);
                                }
                            }
                        }
                    });
        } catch (Throwable ignored) {}
    }

    private void hookStatusGesture() throws Throwable {
        if (!prefs.getBoolean("status_video_fast_gesture", false) && !prefs.getBoolean("status_video_fast_speed", false))
            return;
        try {
            Class<?> videoClass = XposedHelpers.findClass("com.whatsapp.status.playback.widget.StatusVideoView", classLoader);
            XposedBridge.hookAllConstructors(videoClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (prefs.getBoolean("status_video_fast_speed", false)) {
                        try {
                            Method setSpeed = param.thisObject.getClass().getMethod("setPlaybackSpeed", float.class);
                            setSpeed.invoke(param.thisObject, 2.0f);
                        } catch (Throwable ignored) {}
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private void removeViewById(View root, String idName) {
        try {
            int resId = root.getContext().getResources().getIdentifier(idName, "id", root.getContext().getPackageName());
            if (resId != 0) {
                View v = root.findViewById(resId);
                if (v != null) v.setVisibility(View.GONE);
            }
        } catch (Throwable ignored) {}
    }

    private void removeViewByClass(View root, String classNamePart) {
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            if (child.getClass().getName().contains(classNamePart)) {
                child.setVisibility(View.GONE);
            }
            if (child instanceof ViewGroup) {
                removeViewByClass(child, classNamePart);
            }
        }
    }

    private void addPlayPauseButton(View root) {
        try {
            int resId = root.getContext().getResources().getIdentifier("status_bottom_container", "id", root.getContext().getPackageName());
            if (resId == 0) return;
            View container = root.findViewById(resId);
            if (!(container instanceof ViewGroup)) return;
            android.widget.Button btn = new android.widget.Button(root.getContext());
            btn.setText("Play/Pause");
            btn.setOnClickListener(v -> {
                try {
                    int videoId = root.getContext().getResources().getIdentifier("status_video_view", "id", root.getContext().getPackageName());
                    if (videoId != 0) {
                        View videoView = root.findViewById(videoId);
                        if (videoView != null) {
                            try {
                                Method isPlaying = videoView.getClass().getMethod("isPlaying");
                                boolean playing = (boolean) isPlaying.invoke(videoView);
                                Method method = videoView.getClass().getMethod(playing ? "pause" : "start");
                                method.invoke(videoView);
                            } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
            });
            ((ViewGroup) container).addView(btn);
        } catch (Throwable ignored) {}
    }

    private void addReplyMenuItem(View root) {
        // Reply menu item is added via menu inflation hook
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Status Customization";
    }
}
