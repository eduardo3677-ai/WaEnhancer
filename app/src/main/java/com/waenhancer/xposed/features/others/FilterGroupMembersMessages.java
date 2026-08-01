package com.waenhancer.xposed.features.others;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class FilterGroupMembersMessages extends Feature {

    public FilterGroupMembersMessages(@NonNull ClassLoader loader, @NonNull SharedPreferences prefs) {
        super(loader, prefs);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("filter_group_members_messages", false)) return;

        Method processMethod = Unobfuscator.loadProcessIncomingMessageMethod(classLoader);
        if (processMethod == null) return;

        XposedBridge.hookMethod(processMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String filterText = prefs.getString("filter_group_members_text", "");
                if (filterText == null || filterText.isEmpty()) return;

                for (Object arg : param.args) {
                    if (arg == null) continue;
                    String className = arg.getClass().getName();
                    if (!className.contains("FMessage") && !className.contains("Message")) continue;
                    try {
                        Method getText = arg.getClass().getMethod("getText");
                        String text = (String) getText.invoke(arg);
                        if (text == null) return;
                        String lower = text.toLowerCase();
                        for (String word : filterText.toLowerCase().split(",")) {
                            String w = word.trim();
                            if (!w.isEmpty() && lower.contains(w)) {
                                param.setResult(null);
                                return;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Filter Group Messages";
    }
}
