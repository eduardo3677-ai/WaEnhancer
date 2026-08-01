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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AlwaysTyping(@NonNull ClassLoader loader, @NonNull SharedPreferences prefs) {
        super(loader, prefs);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("always_typing_global", false)) return;

        Method sendComposingMethod = Unobfuscator.loadGhostModeMethod(classLoader);
        if (sendComposingMethod == null) return;

        startTypingEngine(sendComposingMethod);
    }

    private void startTypingEngine(Method sendComposingMethod) {
        executor.scheduleAtFixedRate(() -> {
            try {
                if (!prefs.getBoolean("always_typing_global", false)) return;

                int targetType = prefs.getInt("always_typing_global_type", 0);
                int mode = prefs.getInt("always_typing_global_mode", 1);
                String contactsStr = prefs.getString("always_typing_contacts", "");

                System.setProperty("com.waex.helper.AlwaysTyping.isEngineTriggering", "true");

                Class<?> jidClass = XposedHelpers.findClass("com.whatsapp.jid.UserJid", classLoader);
                Method parseMethod = jidClass.getMethod("parse", String.class);
                Object nullJid = null;
                Object composingState = 0;
                Object recordingState = 1;

                if (targetType == 1) {
                    composingState = 1;
                    recordingState = 0;
                }

                sendComposingForContacts(sendComposingMethod, contactsStr, parseMethod, jidClass, composingState);

                int delay = 3 + random.nextInt(4);
                Thread.sleep(delay * 1000L);

            } catch (Throwable t) {
                XposedBridge.log("WAE_AlwaysTyping: " + t);
            } finally {
                System.clearProperty("com.waex.helper.AlwaysTyping.isEngineTriggering");
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void sendComposingForContacts(Method sendComposingMethod, String contactsStr,
                                          Method parseMethod, Class<?> jidClass, Object state) {
        if (contactsStr == null || contactsStr.isEmpty()) return;
        String[] contacts = contactsStr.split(",");
        for (String rawPhone : contacts) {
            String phone = rawPhone.trim().replaceAll("[^0-9]", "");
            if (phone.isEmpty()) continue;
            try {
                String jidStr = phone + "@s.whatsapp.net";
                Object jid = parseMethod.invoke(null, jidStr);
                if (jid != null) {
                    Object[] args = new Object[]{jid, state};
                    ReflectionUtils.callMethod(sendComposingMethod, null, args);
                }
            } catch (Throwable t) {
                XposedBridge.log("WAE_AlwaysTyping: sendComposing failed for " + phone + ": " + t);
            }
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Always Typing";
    }
}
