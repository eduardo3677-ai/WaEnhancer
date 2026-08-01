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

public class RecoverDeletedMedia extends Feature {

    public RecoverDeletedMedia(@NonNull ClassLoader loader, @NonNull SharedPreferences prefs) {
        super(loader, prefs);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("recover_deleted_media", false)) return;

        Method deleteMethod = Unobfuscator.loadDeleteMessageMethod(classLoader);
        if (deleteMethod == null) return;

        XposedBridge.hookMethod(deleteMethod, new XC_MethodHook() {
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
                if (mediaFile == null || !mediaFile.exists()) return;

                String filePath = mediaFile.getAbsolutePath();
                String recoveredPath = filePath + ".recovered";
                File recoveredFile = new File(recoveredPath);
                try {
                    java.nio.file.Files.copy(mediaFile.toPath(), recoveredFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    XposedBridge.log("WAE_RecoverDeletedMedia: Recovered " + filePath);
                } catch (Throwable t) {
                    XposedBridge.log("WAE_RecoverDeletedMedia: Failed to copy: " + t);
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Recover Deleted Media";
    }
}
