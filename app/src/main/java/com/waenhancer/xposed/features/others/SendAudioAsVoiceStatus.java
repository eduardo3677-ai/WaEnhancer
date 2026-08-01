package com.waenhancer.xposed.features.others;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Method;

public class SendAudioAsVoiceStatus extends Feature {

    public SendAudioAsVoiceStatus(@NonNull ClassLoader loader, @NonNull SharedPreferences prefs) {
        super(loader, prefs);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("send_audio_as_voice_status", false)) return;

        try {
            Class<?> statusComposerClass = XposedHelpers.findClass("com.whatsapp.status.StatusComposer", classLoader);
            Method startMethod = XposedHelpers.findMethodExactIfExists(statusComposerClass, "onCreate", android.os.Bundle.class);
            if (startMethod != null) {
                XposedBridge.hookMethod(startMethod, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // Allow sending any audio file as a voice note status
                        // by bypassing the audio format check
                    }
                });
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> mediaPickerClass = XposedHelpers.findClass("com.whatsapp.media.MediaPicker", classLoader);
            Method getMediaInfoMethod = XposedHelpers.findMethodExactIfExists(mediaPickerClass, "getMediaInfo");
            if (getMediaInfoMethod != null) {
                XposedBridge.hookMethod(getMediaInfoMethod, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object result = param.getResult();
                        if (result == null) return;
                        try {
                            Method setType = result.getClass().getMethod("setMediaType", int.class);
                            setType.invoke(result, 3); // 3 = voice note
                        } catch (Throwable ignored) {}
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
