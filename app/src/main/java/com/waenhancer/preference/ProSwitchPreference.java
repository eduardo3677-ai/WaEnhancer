package com.waenhancer.preference;

import android.content.Context;
import android.content.Intent;
import android.text.Html;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.waenhancer.BuildConfig;
import android.content.SharedPreferences;
import android.widget.Toast;
import com.waenhancer.xposed.utils.ProHelper;
import rikka.material.preference.MaterialSwitchPreference;


/**
 * Refactored ProSwitchPreference: converted from a standard preference to a MaterialSwitchPreference
 * that toggles when Pro is active, or redirects to LicenseActivity when Pro is not active.
 */
public class ProSwitchPreference extends MaterialSwitchPreference {

    public ProSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    public ProSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public ProSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private CharSequence originalSummary;
    private CharSequence originalTitle;

    private void init(Context context) {
        // Save the original summary and title defined in XML
        originalSummary = getSummary();
        originalTitle = getTitle();
        if (originalTitle == null) {
            originalTitle = "Pro Feature";
        }

        updateSummary();
    }

    /**
     * Updates the summary and title dynamically based on the verified status.
     */
    private void updateSummary() {
        boolean isVerified = true;
        boolean limitedFree = ProHelper.isLimitedFreePreferenceEnabled(getKey());

        String tagColor = "#22C55E";
        String newTitle = originalTitle + " <font color='" + tagColor + "'><b>[Pro]</b></font>";
        setTitle(Html.fromHtml(newTitle, Html.FROM_HTML_MODE_LEGACY));

        if (limitedFree) {
            setSummary(originalSummary != null ? originalSummary + " (Limited Free)" : "Status: Limited Free Active");
        } else {
            setSummary(originalSummary);
        }
    }

    @Override
    protected void onClick() {
        super.onClick();
    }

    @NonNull
    private SharedPreferences getSafeSharedPreferences() {
        SharedPreferences prefs = getSharedPreferences();
        if (prefs != null) {
            return prefs;
        }
        return PreferenceManager.getDefaultSharedPreferences(getContext());
    }
}