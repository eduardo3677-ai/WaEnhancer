package com.waenhancer.xposed.features.others;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;

import java.io.File;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class DeleteMessageFile extends Feature {

    public DeleteMessageFile(@NonNull ClassLoader loader, @NonNull SharedPreferences prefs) {
        super(loader, prefs);
    }

    @Override
    public void doHook() throws Throwable {
        boolean delReceived = prefs.getBoolean("delete_message_file", false);
        boolean delSent = prefs.getBoolean("delete_message_file_sent", false);
        if (!delReceived && !delSent) return;

        Method deleteMethod = Unobfuscator.loadDeleteMessageMethod(classLoader);
        if (deleteMethod == null) return;

        XposedBridge.hookMethod(deleteMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                for (Object arg : param.args) {
                    if (arg != null && FMessageWpp.TYPE.isInstance(arg)) {
                        FMessageWpp fmessage = new FMessageWpp(arg);
                        File mediaFile = fmessage.getMediaFile();
                        if (mediaFile != null && mediaFile.exists()) {
                            mediaFile.delete();
                        }
                        break;
                    }
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Delete Message File";
    }
}
