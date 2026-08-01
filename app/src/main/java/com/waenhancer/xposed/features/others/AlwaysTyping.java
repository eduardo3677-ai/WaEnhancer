package com.waenhancer.xposed.features.others;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class AlwaysTyping extends Feature {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Random random = new Random();

    public AlwaysTyping(@NonNull ClassLoader loader, @NonNull SharedPreferences prefs) {
        super(loader, prefs);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("always_typing_global", false)) return;

        final Method sendComposingMethod = Unobfuscator.loadGhostModeMethod(classLoader);
        if (sendComposingMethod == null) return;

        executor.scheduleAtFixedRate(() -> {
            try {
                if (!prefs.getBoolean("always_typing_global", false)) return;

                int type = prefs.getInt("always_typing_global_type", 0);
                String contactsStr = prefs.getString("always_typing_contacts", "");
                if (contactsStr == null || contactsStr.isEmpty()) return;

                System.setProperty("com.waex.helper.AlwaysTyping.isEngineTriggering", "true");

                int state = (type == 1) ? 1 : 0;

                for (String rawPhone : contactsStr.split(",")) {
                    String phone = rawPhone.trim().replaceAll("[^0-9]", "");
                    if (phone.isEmpty()) continue;
                    try {
                        Class<?> userJidClass = XposedHelpers.findClass("com.whatsapp.jid.UserJid", classLoader);
                        Method parseMethod = userJidClass.getMethod("parse", String.class);
                        Object jid = parseMethod.invoke(null, phone + "@s.whatsapp.net");
                        if (jid != null) {
                            ReflectionUtils.callMethod(sendComposingMethod, null, jid, state);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("WAE_AlwaysTyping: " + t);
                    }
                }

                Thread.sleep((3 + random.nextInt(4)) * 1000L);
            } catch (Throwable t) {
                XposedBridge.log("WAE_AlwaysTyping: " + t);
            } finally {
                System.clearProperty("com.waex.helper.AlwaysTyping.isEngineTriggering");
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Always Typing";
    }
}
