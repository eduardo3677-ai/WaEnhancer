package com.waenhancer.xposed.features.others;

import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class MessageBomber extends Feature {

    public MessageBomber(@NonNull ClassLoader loader, @NonNull SharedPreferences prefs) {
        super(loader, prefs);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("message_bomber", false)) return;

        Method sendTextMethod = Unobfuscator.loadSendMessageMethod(classLoader);
        if (sendTextMethod == null) return;

        XposedBridge.hookMethod(sendTextMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                int count = prefs.getInt("message_bomber_count", 1);
                if (count <= 1) return;

                for (int i = 0; i < param.args.length; i++) {
                    if (param.args[i] instanceof String) {
                        String text = (String) param.args[i];
                        StringBuilder sb = new StringBuilder();
                        for (int j = 0; j < Math.min(count, 100); j++) {
                            sb.append(text);
                        }
                        param.args[i] = sb.toString();
                        break;
                    }
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Message Bomber";
    }
}
