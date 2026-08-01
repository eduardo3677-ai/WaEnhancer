package com.waenhancer.xposed.features.others;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class UnlockPremiumCustomization extends Feature {

    public UnlockPremiumCustomization(@NonNull ClassLoader loader, @NonNull SharedPreferences prefs) {
        super(loader, prefs);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("unlock_premium_customization", false)) return;

        // Force premium props to true
        try {
            Method checkMethod = Unobfuscator.loadPremiumCustomizationCheckMethod(classLoader);
            if (checkMethod != null) {
                XposedBridge.hookMethod(checkMethod, XC_MethodReplacement.returnConstant(true));
            }
        } catch (Throwable ignored) {}

        // Override WhatsApp's premium feature property flags
        try {
            XposedHelpers.findAndHookMethod("com.whatsapp.props.PrivacySetting", classLoader,
                    "isPremium", XC_MethodReplacement.returnConstant(true));
        } catch (Throwable ignored) {}
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Unlock Premium Customization";
    }
}
