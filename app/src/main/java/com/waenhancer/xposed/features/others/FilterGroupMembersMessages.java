package com.waenhancer.xposed.features.others;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class FilterGroupMembersMessages extends Feature {

    public FilterGroupMembersMessages(@NonNull ClassLoader loader, @NonNull SharedPreferences prefs) {
        super(loader, prefs);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("filter_group_members_messages", false)) return;

        Method processMessageMethod = Unobfuscator.loadProcessIncomingMessageMethod(classLoader);
        if (processMessageMethod == null) return;

        XposedBridge.hookMethod(processMessageMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String filterText = prefs.getString("filter_group_members_text", "");
                if (filterText == null || filterText.isEmpty()) return;

                String[] filterWords = filterText.toLowerCase().split(",");
                Object fmessageObj = null;
                for (Object arg : param.args) {
                    if (arg != null && arg.getClass().getName().contains("FMessage")) {
                        fmessageObj = arg;
                        break;
                    }
                }
                if (fmessageObj == null) return;

                try {
                    Method getTextMethod = fmessageObj.getClass().getMethod("getText");
                    String messageText = (String) getTextMethod.invoke(fmessageObj);
                    if (messageText == null) return;

                    String lower = messageText.toLowerCase();
                    for (String word : filterWords) {
                        String trimmed = word.trim();
                        if (!trimmed.isEmpty() && lower.contains(trimmed)) {
                            param.setResult(null);
                            return;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Filter Group Messages";
    }
}
