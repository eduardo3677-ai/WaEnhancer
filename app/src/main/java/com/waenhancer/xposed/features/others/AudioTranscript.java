package com.waenhancer.xposed.features.others;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.waenhancer.BuildConfig;
import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import com.waenhancer.xposed.core.FeatureLoader;

public class AudioTranscript extends Feature {

    public AudioTranscript(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    private static String deobfuscate(String input) {
        if (input == null || input.isEmpty()) return "";
        byte[] decoded = java.util.Base64.getDecoder().decode(input);
        return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String getFoundryKey() {
        return deobfuscate(BuildConfig.AZURE_FK);
    }

    private static String getFoundryEndpoint() {
        return deobfuscate(BuildConfig.AZURE_FE);
    }

    @Override
    public void doHook() throws Throwable {

        if (!prefs.getBoolean("audio_transcription", false))
            return;

        String foundryKey = getFoundryKey();
        String foundryEndpoint = getFoundryEndpoint();
        if (TextUtils.isEmpty(foundryKey) || TextUtils.isEmpty(foundryEndpoint))
            return;

        var transcribeMethod = Unobfuscator.loadTranscribeMethod(classLoader);
        Class<?> TranscriptionSegmentClass = Unobfuscator.loadTranscriptSegment(classLoader);

        XposedBridge.hookMethod(transcribeMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var pttTranscriptionRequest = param.args[0];
                var fieldFMessage = ReflectionUtils.getFieldByExtendType(pttTranscriptionRequest.getClass(), FMessageWpp.TYPE);
                var fmessageObj = fieldFMessage.get(pttTranscriptionRequest);
                var fmessage = new FMessageWpp(fmessageObj);
                File file = fmessage.getMediaFile();
                if (file == null) {
                    Utils.showToast(FeatureLoader.getModuleString(Utils.getApplication(), R.string.download_not_available), 1);
                    return;
                }
                var callback = param.args[1];
                var onComplete = ReflectionUtils.findMethodUsingFilter(callback.getClass(), method -> method.getParameterCount() == 4);
                if (file == null || !file.exists())
                    return;

                String transcript = transcriptionAzureFoundry(file);

                var segments = new ArrayList<>();
                var words = transcript.split("\\s");
                var totalLength = 0;
                for (var word : words) {
                    segments.add(XposedHelpers.newInstance(TranscriptionSegmentClass, totalLength, word.length(), 100, -1, -1));
                    totalLength += word.length() + 1;
                }
                ReflectionUtils.callMethod(onComplete, callback, fmessageObj, transcript, segments, 1);
                param.setResult(null);
            }
        });

    }

    private String transcriptionAzureFoundry(File fileAudio) throws Exception {
        String key = getFoundryKey();
        String endpoint = getFoundryEndpoint();
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(endpoint)) {
            return "Azure Foundry not configured";
        }

        String deploymentName = "whisper";
        String url = endpoint + "openai/deployments/" + deploymentName + "/audio/transcriptions?api-version=2024-02-15-preview";

        OkHttpClient client = new OkHttpClient();

        okhttp3.MultipartBody.Builder multipartBuilder = new okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", fileAudio.getName(),
                        RequestBody.create(fileAudio, MediaType.parse("audio/ogg")))
                .addFormDataPart("response_format", "json");

        Request transcribeRequest = new Request.Builder()
                .url(url)
                .addHeader("api-key", key)
                .post(multipartBuilder.build())
                .build();

        try (okhttp3.Response response = client.newCall(transcribeRequest).execute()) {
            if (!response.isSuccessful()) {
                return "Failed to transcribe audio: " + response.code() + " - " + response.message();
            }

            JSONObject result = new JSONObject(response.body().string());
            return result.getString("text");
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Audio Transcript";
    }
}
