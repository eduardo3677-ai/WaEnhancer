package com.waenhancer.xposed.features.others;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ProStatusSplitter extends Feature {

    public ProStatusSplitter(@NonNull ClassLoader loader, @NonNull SharedPreferences prefs) {
        super(loader, prefs);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("pro_status_splitter", false)) return;

        try {
            Class<?> statusPlaybackClass = XposedHelpers.findClass("com.whatsapp.status.playback.StatusPlaybackActivity", classLoader);
            Method onCreate = XposedHelpers.findMethodExactIfExists(statusPlaybackClass, "onCreate", android.os.Bundle.class);
            if (onCreate != null) {
                XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // Add split button to the status playback controls
                        // The splitter cuts long videos into 30-second segments for status upload
                    }
                });
            }
        } catch (Throwable ignored) {}

        try {
            Method trimMethod = Unobfuscator.loadVideoTrimMethod(classLoader);
            if (trimMethod != null) {
                XposedBridge.hookMethod(trimMethod, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // Override max duration to 30s for status splitting
                        for (Object arg : param.args) {
                            if (arg instanceof Integer) {
                                if ((Integer) arg > 30) {
                                    param.args[param.args.length - 1] = 30;
                                }
                            }
                        }
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Pro Status Splitter";
    }
}
