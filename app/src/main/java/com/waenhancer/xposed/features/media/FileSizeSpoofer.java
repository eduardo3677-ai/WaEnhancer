package com.waenhancer.xposed.features.media;

import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class FileSizeSpoofer extends Feature {

    private static final String TAG = "WAE_FileSizeSpoofer";

    public FileSizeSpoofer(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("file_size_spoofer", false)) return;

        try {
            Class<?> sendPreviewClass = XposedHelpers.findClass("com.whatsapp.conversation.dialog.SendPreviewDialog", classLoader);
            Method showMethod = sendPreviewClass.getMethod("show");
            XposedBridge.hookMethod(showMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object dialog = param.thisObject;
                    if (dialog == null) return;
                    View rootView = null;
                    try {
                        Field field = ReflectionUtils.getFieldByType(dialog.getClass(), View.class);
                        if (field != null) rootView = (View) field.get(dialog);
                    } catch (Throwable ignored) {}
                    if (rootView == null) {
                        try {
                            Method getView = dialog.getClass().getMethod("getView");
                            rootView = (View) getView.invoke(dialog);
                        } catch (Throwable ignored) {}
                    }
                    if (rootView == null) return;
                    addSpoofButton(rootView, dialog);
                }
            });
        } catch (Throwable ignored) {}
    }

    private void addSpoofButton(View rootView, Object dialog) {
        try {
            ViewGroup container = findContainer(rootView);
            if (container == null) return;
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if ("spoof_btn".equals(child.getTag())) return;
            }

            Button spoofBtn = new Button(rootView.getContext());
            spoofBtn.setText("Spoof File Size");
            spoofBtn.setTag("spoof_btn");
            spoofBtn.setOnClickListener(v -> {
                try {
                    spoofFileSizeInDialog(dialog, rootView.getContext());
                    Toast.makeText(rootView.getContext(), "File size spoofed!", Toast.LENGTH_SHORT).show();
                    spoofBtn.setText("File Size: SPOOFED");
                    spoofBtn.setEnabled(false);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": Failed: " + t);
                    Toast.makeText(rootView.getContext(), "Spoof failed", Toast.LENGTH_SHORT).show();
                }
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(16, 8, 16, 8);
            container.addView(spoofBtn, params);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": addSpoofButton failed: " + t);
        }
    }

    private ViewGroup findContainer(View root) {
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof LinearLayout) return (LinearLayout) child;
                if (child instanceof ViewGroup) {
                    ViewGroup deeper = findContainer(child);
                    if (deeper != null) return deeper;
                }
            }
        }
        return null;
    }

    private void spoofFileSizeInDialog(Object dialog, android.content.Context context) throws Exception {
        List<Field> fmessageFields = ReflectionUtils.getFieldsByType(dialog.getClass(), FMessageWpp.TYPE);
        if (fmessageFields.isEmpty()) throw new RuntimeException("No FMessage field found");
        Object fmessageObj = fmessageFields.get(0).get(dialog);
        if (fmessageObj == null) throw new RuntimeException("FMessage is null");
        FMessageWpp fmessage = new FMessageWpp(fmessageObj);

        for (Field f : fmessageObj.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            String name = f.getName().toLowerCase();
            if ((f.getType() == long.class || f.getType() == int.class)
                    && (name.contains("size") || name.contains("length") || name.contains("bytes"))) {
                if (f.getType() == long.class) {
                    f.setLong(fmessageObj, 1L);
                } else {
                    f.setInt(fmessageObj, 1);
                }
                XposedBridge.log(TAG + ": Spoofed field: " + f.getName());
                return;
            }
        }
        throw new RuntimeException("No file size field found on " + fmessageObj.getClass().getName());
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "File Size Spoofer";
    }
}
