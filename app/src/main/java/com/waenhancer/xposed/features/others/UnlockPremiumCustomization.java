package com.waenhancer.xposed.features.others;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

import de.robv.android.xposed.XC_MethodHook;
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

        // Unlock premium themes by forcing the premium check to return true
        try {
            Method premiumCheckMethod = Unobfuscator.loadPremiumCustomizationCheckMethod(classLoader);
            if (premiumCheckMethod != null) {
                XposedBridge.hookMethod(premiumCheckMethod, XC_MethodReplacement.returnConstant(true));
            }
        } catch (Throwable ignored) {}

        // Override the premium feature flag property
        try {
            Class<?> propsClass = XposedHelpers.findClass("com.whatsapp.props.PrivacySetting", classLoader);
            Method getPremiumMethod = propsClass.getMethod("isPremium");
            XposedBridge.hookMethod(getPremiumMethod, XC_MethodReplacement.returnConstant(true));
        } catch (Throwable ignored) {}

        // Force premium customization flags
        try {
            Method propsBooleanMethod = Unobfuscator.loadPropsBooleanMethod(classLoader);
            if (propsBooleanMethod != null) {
                XposedBridge.hookMethod(propsBooleanMethod, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        int id = (Integer) param.args[0];
                        // Premium theme property IDs
                        if (id == 5549 || id == 4497) {
                            param.setResult(true);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Unlock Premium Customization";
    }
}
