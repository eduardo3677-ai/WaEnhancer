package com.waenhancer.xposed.features.others;

import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.features.general.Others;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class StatusCustomization extends Feature {

    public StatusCustomization(@NonNull ClassLoader loader, @NonNull SharedPreferences prefs) {
        super(loader, prefs);
    }

    @Override
    public void doHook() throws Throwable {
        boolean any = prefs.getBoolean("remove_status_bottom_tile", false)
                || prefs.getBoolean("remove_status_quick_reactions", false)
                || prefs.getBoolean("remove_status_heart_button", false)
                || prefs.getBoolean("status_bottom_play_pause_button", false)
                || prefs.getBoolean("add_status_reply_menu_item", false)
                || prefs.getBoolean("status_video_fast_gesture", false)
                || prefs.getBoolean("status_video_fast_speed", false)
                || prefs.getBoolean("disable_status_swipe_up", false);
        if (!any) return;

        // Use WhatsApp props to disable UI elements server-side
        if (prefs.getBoolean("remove_status_bottom_tile", false)) {
            Others.propsBoolean.put(9286, false);
            Others.propsBoolean.put(6481, false);
        }
        if (prefs.getBoolean("remove_status_quick_reactions", false)) {
            Others.propsBoolean.put(10380, false);
        }
        if (prefs.getBoolean("remove_status_heart_button", false)) {
            Others.propsBoolean.put(6972, false);
        }

        // Hook StatusPlaybackActivity.onCreate to remove views after layout
        try {
            Class<?> activityClass = XposedHelpers.findClass("com.whatsapp.status.playback.StatusPlaybackActivity", classLoader);
            Method onCreate = XposedHelpers.findMethodExactIfExists(activityClass, "onCreate", android.os.Bundle.class);
            if (onCreate != null) {
                XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object activity = param.thisObject;
                        if (!(activity instanceof android.app.Activity)) return;
                        View decorView = ((android.app.Activity) activity).getWindow().getDecorView();
                        decorView.post(() -> modifyStatusViews(decorView));
                    }
                });
            }
        } catch (Throwable ignored) {}

        // Hook video playback speed
        if (prefs.getBoolean("status_video_fast_speed", false)) {
            try {
                Method playbackSpeedMethod = Unobfuscator.loadPlaybackSpeed(classLoader);
                if (playbackSpeedMethod != null) {
                    XposedBridge.hookMethod(playbackSpeedMethod, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            for (int i = 0; i < param.args.length; i++) {
                                if (param.args[i] instanceof Float) {
                                    param.args[i] = 2.0f;
                                    break;
                                }
                            }
                        }
                    });
                }
            } catch (Throwable ignored) {}
        }

        // Disable swipe-up gesture
        if (prefs.getBoolean("disable_status_swipe_up", false)) {
            try {
                Class<?> viewPagerClass = XposedHelpers.findClass("androidx.viewpager.widget.ViewPager", classLoader);
                XposedHelpers.findAndHookMethod(viewPagerClass, "onInterceptTouchEvent",
                        android.view.MotionEvent.class, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                android.view.MotionEvent ev = (android.view.MotionEvent) param.args[0];
                                if (ev != null && ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                                    float y = ev.getY();
                                    float h = ((View) param.thisObject).getHeight();
                                    if (y > h * 0.7f) param.setResult(false);
                                }
                            }
                        });
            } catch (Throwable ignored) {}
        }
    }

    private void modifyStatusViews(View root) {
        if (prefs.getBoolean("remove_status_bottom_tile", false)) {
            hideViewById(root, "status_bottom_tile");
            hideViewById(root, "bottom_bar");
        }
        if (prefs.getBoolean("remove_status_quick_reactions", false)) {
            hideViewById(root, "quick_reactions");
            hideViewByClass(root, "QuickReaction");
        }
        if (prefs.getBoolean("remove_status_heart_button", false)) {
            hideViewById(root, "heart_button");
            hideViewById(root, "status_heart");
        }
    }

    private void hideViewById(View root, String idName) {
        try {
            int resId = root.getContext().getResources().getIdentifier(idName, "id", root.getContext().getPackageName());
            if (resId != 0) {
                View v = root.findViewById(resId);
                if (v != null) v.setVisibility(View.GONE);
            }
        } catch (Throwable ignored) {}
    }

    private void hideViewByClass(View root, String namePart) {
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            if (child.getClass().getName().contains(namePart)) child.setVisibility(View.GONE);
            if (child instanceof ViewGroup) hideViewByClass(child, namePart);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Status Customization";
    }
}
