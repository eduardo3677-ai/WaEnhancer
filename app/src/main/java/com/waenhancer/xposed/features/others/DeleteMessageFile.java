package com.waenhancer.xposed.features.others;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;

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
        boolean deleteReceived = prefs.getBoolean("delete_message_file", false);
        boolean deleteSent = prefs.getBoolean("delete_message_file_sent", false);
        if (!deleteReceived && !deleteSent) return;

        Method deleteMessageMethod = Unobfuscator.loadDeleteMessageMethod(classLoader);
        if (deleteMessageMethod == null) return;

        XposedBridge.hookMethod(deleteMessageMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object fmessageObj = null;
                for (Object arg : param.args) {
                    if (arg != null && FMessageWpp.TYPE.isInstance(arg)) {
                        fmessageObj = arg;
                        break;
                    }
                }
                if (fmessageObj == null) return;

                FMessageWpp fmessage = new FMessageWpp(fmessageObj);
                File mediaFile = fmessage.getMediaFile();
                if (mediaFile != null && mediaFile.exists()) {
                    mediaFile.delete();
                }

                String filePath = fmessage.getMediaFilePath();
                if (filePath != null && !filePath.isEmpty()) {
                    File f = new File(filePath);
                    if (f.exists()) f.delete();
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
