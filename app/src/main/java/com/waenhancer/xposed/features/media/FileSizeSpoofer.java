package com.waenhancer.xposed.features.media;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
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

        Method sendPreviewMethod = findSendPreviewMethod(classLoader);
        if (sendPreviewMethod == null) {
            XposedBridge.log(TAG + ": Could not find send preview method");
            return;
        }

        XposedBridge.hookMethod(sendPreviewMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object activity = param.thisObject != null ? param.thisObject : param.args[0];
                if (activity == null) return;

                View rootView = null;
                if (activity instanceof android.app.Activity) {
                    rootView = ((android.app.Activity) activity).getWindow().getDecorView();
                } else {
                    try {
                        Method getView = activity.getClass().getMethod("getWindowManager");
                        Object windowManager = getView.invoke(activity);
                        if (windowManager != null) {
                            Method getDecorView = windowManager.getClass().getMethod("getDefaultDisplay");
                        }
                    } catch (Throwable ignored) {}
                }

                if (rootView == null) {
                    try {
                        Field field = ReflectionUtils.getFieldByType(activity.getClass(), View.class);
                        if (field != null) rootView = (View) field.get(activity);
                    } catch (Throwable ignored) {}
                }

                if (rootView == null) return;

                Object fileField = findFileField(rootView, activity);
                if (fileField == null) return;

                addSpoofButton(rootView, fileField, activity);
            }
        });
    }

    private Method findSendPreviewMethod(ClassLoader cl) {
        try {
            return Unobfuscator.loadSendPreviewMethod(cl);
        } catch (Throwable ignored) {}
        try {
            for (Method m : cl.loadClass("com.whatsapp.conversation.dialog.SendPreviewDialog").getDeclaredMethods()) {
                if (m.getName().startsWith("onCreate") || m.getName().startsWith("onStart") || m.getName().startsWith("onResume")) {
                    return m;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Object findFileField(View rootView, Object activity) {
        try {
            List<Field> fileFields = ReflectionUtils.getFieldsByType(activity.getClass(), cl -> {
                try {
                    return cl.getName().contains("FMessage") || cl.getName().contains("DocumentShareView");
                } catch (Throwable t) {
                    return false;
                }
            });
            if (!fileFields.isEmpty()) {
                return fileFields.get(0).get(activity);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void addSpoofButton(View rootView, Object fileMessage, Object activity) {
        try {
            ViewGroup container = findContainer(rootView);
            if (container == null) return;

            boolean alreadyAdded = false;
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if ("spoof_btn".equals(child.getTag())) {
                    alreadyAdded = true;
                    break;
                }
            }
            if (alreadyAdded) return;

            Button spoofBtn = new Button(rootView.getContext());
            spoofBtn.setText("Spoof File Size");
            spoofBtn.setTag("spoof_btn");

            spoofBtn.setOnClickListener(v -> {
                try {
                    spoofFileSize(fileMessage, rootView.getContext());
                    Toast.makeText(rootView.getContext(), "File size spoofed!", Toast.LENGTH_SHORT).show();
                    spoofBtn.setText("File Size: SPOOFED");
                    spoofBtn.setEnabled(false);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": Failed to spoof: " + t);
                    Toast.makeText(rootView.getContext(), "Spoof failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
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
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                if (child instanceof LinearLayout || child instanceof ViewGroup) {
                    ViewGroup deeper = findContainer(child);
                    if (deeper != null) return deeper;
                }
            }
            if (group instanceof LinearLayout || group.getClass().getName().contains("Conversation")) {
                return group;
            }
        }
        return null;
    }

    private void spoofFileSize(Object fmessage, android.content.Context context) throws Exception {
        Class<?> fmessageClass = fmessage.getClass();
        HashMap<String, Field> fields = ReflectionUtils.getAllFields(fmessageClass);

        Field fileSizeField = fields.get("fileSize");
        if (fileSizeField == null) fileSizeField = fields.get("MediaSize");
        if (fileSizeField == null) fileSizeField = fields.get("mediaSize");
        if (fileSizeField == null) fileSizeField = fields.get("size");

        if (fileSizeField != null) {
            long originalSize = fileSizeField.getLong(fmessage);
            long spoofedSize = 1L;
            XposedBridge.log(TAG + ": Spoofing file size from " + originalSize + " to " + spoofedSize);
            fileSizeField.setLong(fmessage, spoofedSize);
            return;
        }

        for (Field f : fmessageClass.getDeclaredFields()) {
            if (f.getType() == long.class || f.getType() == int.class) {
                String name = f.getName().toLowerCase();
                if (name.contains("size") || name.contains("length") || name.contains("bytes")) {
                    f.setAccessible(true);
                    if (f.getType() == long.class) {
                        f.setLong(fmessage, 1L);
                    } else {
                        f.setInt(fmessage, 1);
                    }
                    XposedBridge.log(TAG + ": Spoofed field: " + f.getName());
                    return;
                }
            }
        }

        try {
            for (Method m : fmessageClass.getDeclaredMethods()) {
                if (m.getName().startsWith("set") && m.getName().toLowerCase().contains("size")) {
                    m.setAccessible(true);
                    m.invoke(fmessage, 1L);
                    XposedBridge.log(TAG + ": Spoofed via method: " + m.getName());
                    return;
                }
            }
        } catch (Throwable ignored) {}

        throw new RuntimeException("No file size field found on " + fmessageClass.getName());
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "File Size Spoofer";
    }
}
