package com.waenhancer.xposed.features.others;

import androidx.annotation.NonNull;

import com.waenhancer.BuildConfig;
import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;

import org.json.JSONArray;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GoogleTranslate extends Feature {

    private final OkHttpClient client = new OkHttpClient();

    public GoogleTranslate(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    private static String deobfuscate(String input) {
        if (input == null || input.isEmpty()) return "";
        byte[] decoded = java.util.Base64.getDecoder().decode(input);
        return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String getTranslatorKey() {
        return deobfuscate(BuildConfig.AZURE_TK);
    }

    private static String getTranslatorEndpoint() {
        return deobfuscate(BuildConfig.AZURE_TE);
    }

    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("google_translate", false)) return;

        var checkSupportLanguage = Unobfuscator.loadCheckSupportLanguage(classLoader);

        XposedBridge.hookMethod(checkSupportLanguage, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[0] = "pt";
                param.args[1] = "en";
            }
        });

        Class<?> translatorClazz = XposedHelpers.findClass("com.whatsapp.messagetranslation.UnityMessageTranslation", classLoader);

        var pre21Method = XposedHelpers.findMethodExactIfExists(translatorClazz, "translate", String.class);
        if (pre21Method != null) {
            XposedHelpers.findAndHookMethod(translatorClazz, pre21Method.getName(), String.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    var texto = (String) param.args[0];
                    var currentMethod = (Method) param.method;
                    var unityTranslationResultClass = currentMethod.getReturnType();
                    var translation = translateAzure(texto, Locale.getDefault().getLanguage()).get();
                    return unityTranslationResultClass.getConstructor(String.class, float.class, int.class).newInstance(translation, 1, 0);
                }
            });
        }

        var newMethod = XposedHelpers.findMethodExactIfExists(translatorClazz, "translate", List.class);
        if (newMethod != null) {
            XposedHelpers.findAndHookMethod(translatorClazz, "translate", List.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    var list = (List) param.args[0];
                    var translated = new ArrayList<String>();

                    for (var texto : list) {
                        var translation = translateAzure((String) texto, Locale.getDefault().getLanguage()).get();
                        translated.add(translation);
                    }

                    var currentMethod = (Method) param.method;
                    var unityTranslationResultClass = currentMethod.getReturnType();
                    return unityTranslationResultClass.getConstructor(String[].class, float.class, int.class).newInstance(translated.toArray(new String[0]), 1, 0);
                }
            });
        }

        if (pre21Method == null && newMethod == null) throw new Exception("GoogleTranslate method not found");
    }

    public CompletableFuture<String> translateAzure(String text, String languageDest) {
        CompletableFuture<String> future = new CompletableFuture<>();
        String key = getTranslatorKey();
        String endpoint = getTranslatorEndpoint();
        if (key.isEmpty() || endpoint.isEmpty()) {
            future.completeExceptionally(new RuntimeException("Azure Translator not configured"));
            return future;
        }

        String url;
        try {
            url = endpoint + "translate?api-version=3.0&to=" + URLEncoder.encode(languageDest, "UTF-8");
        } catch (Exception e) {
            future.completeExceptionally(new RuntimeException("URL encode error: " + e.getMessage()));
            return future;
        }

        String body = "[{\"Text\":" + quote(text) + "}]";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Ocp-Apim-Subscription-Key", key)
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .addHeader("X-ClientTraceId", UUID.randomUUID().toString())
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(new RuntimeException("Translation failed: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    try {
                        JSONArray jsonArray = new JSONArray(responseData);
                        JSONArray translations = jsonArray.getJSONObject(0).getJSONArray("translations");
                        StringBuilder translation = new StringBuilder();
                        for (int i = 0; i < translations.length(); i++) {
                            translation.append(translations.getJSONObject(i).getString("text"));
                        }
                        future.complete(translation.toString());
                    } catch (Exception e) {
                        future.completeExceptionally(new RuntimeException("Parse error: " + e.getMessage()));
                    }
                } else {
                    future.completeExceptionally(new RuntimeException("Translation HTTP " + response.code()));
                }
            }
        });

        return future;
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "";
    }
}
