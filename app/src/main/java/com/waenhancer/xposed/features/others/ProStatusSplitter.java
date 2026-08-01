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

        // Hook the video trim/max duration method to split long videos
        try {
            Method trimMethod = Unobfuscator.loadVideoTrimMethod(classLoader);
            if (trimMethod != null) {
                XposedBridge.hookMethod(trimMethod, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        for (int i = 0; i < param.args.length; i++) {
                            if (param.args[i] instanceof Integer) {
                                int val = (Integer) param.args[i];
                                if (val > 30) param.args[i] = 30;
                            }
                        }
                    }
                });
            }
        } catch (Throwable ignored) {}

        // Hook MediaQuality to increase video limit for status uploads
        try {
            Class<?> processVideoQualityClass = XposedHelpers.findClass(
                    "com.whatsapp.mediaview.ProcessVideoQuality", classLoader);
            java.lang.reflect.Field limitField = null;
            for (java.lang.reflect.Field f : processVideoQualityClass.getDeclaredFields()) {
                if (f.getName().contains("videoLimitMb") || f.getName().contains("limitMb")) {
                    limitField = f;
                    break;
                }
            }
            if (limitField != null) {
                limitField.setAccessible(true);
                XposedBridge.hookAllConstructors(processVideoQualityClass, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            java.lang.reflect.Field f = null;
                            for (java.lang.reflect.Field fld : param.thisObject.getClass().getDeclaredFields()) {
                                if (fld.getName().contains("videoLimitMb") || fld.getName().contains("limitMb")) {
                                    f = fld;
                                    break;
                                }
                            }
                            if (f != null) {
                                f.setAccessible(true);
                                f.setInt(param.thisObject, 300);
                            }
                        } catch (Throwable ignored) {}
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
