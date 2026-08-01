package com.waenhancer.xposed.features.others;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.waenhancer.R;
import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.FeatureLoader;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.xposed.utils.Utils;

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
                String bomberText = prefs.getString("message_bomber_text", "");
                int bomberCount = prefs.getInt("message_bomber_count", 1);
                if (TextUtils.isEmpty(bomberText) || bomberCount <= 1) return;

                Object originalArg0 = param.args[0];
                String originalText = null;
                for (Object arg : param.args) {
                    if (arg instanceof String) {
                        originalText = (String) arg;
                        break;
                    }
                }
                if (originalText == null) return;

                String repeated = originalText.repeat(Math.min(bomberCount, 100));
                for (int i = 0; i < param.args.length; i++) {
                    if (param.args[i] instanceof String) {
                        param.args[i] = repeated;
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
