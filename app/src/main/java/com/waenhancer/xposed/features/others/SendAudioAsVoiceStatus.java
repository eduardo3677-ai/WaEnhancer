package com.waenhancer.xposed.features.others;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class SendAudioAsVoiceStatus extends Feature {

    public SendAudioAsVoiceStatus(@NonNull ClassLoader loader, @NonNull SharedPreferences prefs) {
        super(loader, prefs);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("send_audio_as_voice_status", false)) return;

        // Hook the audio type method to force voice note type
        try {
            Method sendAudioTypeMethod = Unobfuscator.loadSendAudioTypeMethod(classLoader);
            if (sendAudioTypeMethod != null) {
                XposedBridge.hookMethod(sendAudioTypeMethod, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        for (int i = 0; i < param.args.length; i++) {
                            if (param.args[i] instanceof Integer) {
                                param.args[i] = 2; // 2 = voice note
                                break;
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
        return "Send Audio as Voice Status";
    }
}
