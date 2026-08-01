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

public class RecoverDeletedMedia extends Feature {

    public RecoverDeletedMedia(@NonNull ClassLoader loader, @NonNull SharedPreferences prefs) {
        super(loader, prefs);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("recover_deleted_media", false)) return;

        // Hook the anti-revoke method to intercept media deletion
        Method revokeMethod = Unobfuscator.loadAntiRevokeMessageMethod(classLoader);
        if (revokeMethod == null) return;

        XposedBridge.hookMethod(revokeMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                for (Object arg : param.args) {
                    if (arg != null && FMessageWpp.TYPE.isInstance(arg)) {
                        FMessageWpp fmessage = new FMessageWpp(arg);
                        File mediaFile = fmessage.getMediaFile();
                        if (mediaFile == null || !mediaFile.exists()) break;
                        try {
                            File recovered = new File(mediaFile.getParent(),
                                    "recovered_" + mediaFile.getName());
                            java.nio.file.Files.copy(mediaFile.toPath(), recovered.toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            XposedBridge.log("WAE_RecoverDeletedMedia: Copied to " + recovered);
                        } catch (Throwable t) {
                            XposedBridge.log("WAE_RecoverDeletedMedia: " + t);
                        }
                        break;
                    }
                }
            }
        });

        // Also hook SQL delete to intercept media deletion
        try {
            XposedBridge.hookAllMethods(android.database.sqlite.SQLiteDatabase.class, "delete",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String table = (String) param.args[0];
                            if (!"message".equals(table)) return;
                            String where = (String) param.args[1];
                            if (where != null && where.contains("media")) {
                                // Media is being deleted from the message table
                            }
                        }
                    });
        } catch (Throwable ignored) {}
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Recover Deleted Media";
    }
}
