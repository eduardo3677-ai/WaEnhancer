package com.waenhancer.xposed.features.customization;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ProHelper;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.util.WeakHashMap;

public class FloatingBottomBar extends Feature {

    private static final float CORNER_RADIUS_DP = 24f;
    private static final float SIDE_MARGIN_DP = 12f;
    private static final float BOTTOM_MARGIN_DP = 16f;
    private static final float ELEVATION_DP = 6f;
    private static final float BACKGROUND_ALPHA = 0.95f;
    private static final float STROKE_ALPHA = 0.25f;
    private static final float FAB_GAP_DP = 12f;
    private static final String[] FAB_RESOURCE_NAMES = new String[]{"fab", "fab_second", "extended_mini_fab"};

    private static final WeakHashMap<ViewGroup, Boolean> processedBars = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, Integer> setupAttempts = new WeakHashMap<>();

    private static boolean pillDesignPro = false;
    private static boolean pillDesignIos = false;
    private static int userBottomMarginDp = 16;
    private static int userSideMarginDp = 12;

    public FloatingBottomBar(@NonNull ClassLoader loader, @NonNull SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public String getPluginName() {
        return "Floating Bottom Bar";
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("floating_bottom_bar", true)) return;

        String designPref = prefs.getString("floating_bottom_bar_pill_design", "regular");
        pillDesignPro = "pro".equals(designPref) && ProHelper.isPillDesignProEnabled();
        pillDesignIos = "ios_glass".equals(designPref) && ProHelper.isPillDesignProEnabled();

        userBottomMarginDp = prefs.getInt("floating_bottom_bar_margin_bottom", (int) BOTTOM_MARGIN_DP);
        userSideMarginDp = prefs.getInt("floating_bottom_bar_margin_horizontal", (int) SIDE_MARGIN_DP);

        XposedHelpers.findAndHookMethod(
                View.class,
                "onAttachedToWindow",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;
                        if (view.getId() == View.NO_ID) return;
                        try {
                            String entryName = view.getResources().getResourceEntryName(view.getId());
                            if ("bottom_nav".equals(entryName) || "navigation_bar".equals(entryName) || "tab_layout".equals(entryName)) {
                                if (view instanceof ViewGroup) {
                                    scheduleSetup((ViewGroup) view);
                                }
                                return;
                            }
                            for (String fabName : FAB_RESOURCE_NAMES) {
                                if (fabName.equals(entryName)) {
                                    view.post(() -> positionFabAboveCurrentBar(view));
                                    break;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                View.class,
                "onDetachedFromWindow",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;
                        if (view.getId() == View.NO_ID) return;
                        try {
                            String entryName = view.getResources().getResourceEntryName(view.getId());
                            if ("bottom_nav".equals(entryName) || "navigation_bar".equals(entryName) || "tab_layout".equals(entryName)) {
                                if (view instanceof ViewGroup) {
                                    ViewGroup bar = (ViewGroup) view;
                                    setupAttempts.remove(bar);
                                    processedBars.remove(bar);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
        );

        Class<?> tabFrameClass = Unobfuscator.loadTabFrameClass(classLoader);
        if (tabFrameClass != null) {
            XposedBridge.hookAllMethods(tabFrameClass, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    if (view instanceof ViewGroup) {
                        scheduleSetup((ViewGroup) view);
                    }
                }
            });
        }
    }

    private void scheduleSetup(final ViewGroup bar) {
        if (processedBars.containsKey(bar)) {
            ensureBarOverlay(bar);
            return;
        }

        bar.post(() -> {
            if (setupFloatingBar(bar)) {
                processedBars.put(bar, true);
                setupAttempts.remove(bar);
                return;
            }
            retrySetup(bar);
        });
    }

    private void retrySetup(final ViewGroup bar) {
        int attempt = setupAttempts.containsKey(bar) ? setupAttempts.get(bar) : 0;
        if (attempt >= 3) return;
        setupAttempts.put(bar, attempt + 1);
        bar.postDelayed(() -> {
            if (processedBars.containsKey(bar)) return;
            if (setupFloatingBar(bar)) {
                processedBars.put(bar, true);
                setupAttempts.remove(bar);
            } else {
                retrySetup(bar);
            }
        }, 100L);
    }

    private void ensureBarOverlay(final ViewGroup bar) {
        if (bar.getParent() instanceof ViewGroup) {
            ViewGroup container = (ViewGroup) bar.getParent();
            if (!(container.getParent() instanceof FrameLayout)) {
                bar.post(() -> setupFloatingBar(bar));
            }
        }
    }

    private boolean setupFloatingBar(ViewGroup bar) {
        try {
            ViewParent parent = bar.getParent();
            if (!(parent instanceof ViewGroup)) return false;
            ViewGroup container = (ViewGroup) parent;

            FrameLayout rootView = findRootView(bar);
            if (rootView == null) return false;

            if (container.getParent() == rootView) {
                updateOverlayLayout(rootView, container, bar);
                applyTransparentShadowStyle(container, bar);
                positionFabsAboveBar(rootView, container);
                return true;
            }

            if (container.getParent() instanceof ViewGroup) {
                ViewGroup originalParent = (ViewGroup) container.getParent();
                originalParent.setOnApplyWindowInsetsListener((v, insets) -> {
                    v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), 0);
                    return insets;
                });
                originalParent.removeView(container);
            }

            container.setOnApplyWindowInsetsListener((v, insets) -> insets);
            bar.setOnApplyWindowInsetsListener((v, insets) -> insets);

            float density = bar.getContext().getResources().getDisplayMetrics().density;
            int bottomMargin = navigationBarInset(rootView) + (int) (userBottomMarginDp * density);

            FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rootParams.gravity = Gravity.BOTTOM;
            rootParams.bottomMargin = bottomMargin;
            rootView.addView(container, rootParams);

            applyTransparentShadowStyle(container, bar);
            positionFabsAboveBar(rootView, container);
            return true;
        } catch (Throwable e) {
            XposedBridge.log("[WAEX-FBB] Error setting up floating bar: " + e);
            return false;
        }
    }

    private void updateOverlayLayout(FrameLayout rootView, ViewGroup container, ViewGroup bar) {
        ViewGroup.LayoutParams lp = container.getLayoutParams();
        float density = bar.getContext().getResources().getDisplayMetrics().density;
        int bottomMargin = navigationBarInset(rootView) + (int) (userBottomMarginDp * density);

        if (lp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
            flp.gravity = Gravity.BOTTOM;
            flp.bottomMargin = bottomMargin;
            container.setLayoutParams(flp);
        }

        ViewGroup.LayoutParams barLp = bar.getLayoutParams();
        if (barLp != null) {
            barLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            barLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            bar.setLayoutParams(barLp);
        }
    }

    private int navigationBarInset(View view) {
        try {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(view);
            if (insets != null) {
                return insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private FrameLayout findRootView(View startView) {
        View current = startView;
        FrameLayout lastFrameLayout = null;
        while (current != null) {
            if (current instanceof FrameLayout) {
                lastFrameLayout = (FrameLayout) current;
            }
            ViewParent p = current.getParent();
            current = (p instanceof View) ? (View) p : null;
        }
        return lastFrameLayout;
    }

    private void applyTransparentShadowStyle(ViewGroup container, ViewGroup bar) {
        container.setBackgroundColor(Color.TRANSPARENT);

        if (container.getParent() instanceof ViewGroup) {
            ((ViewGroup) container.getParent()).setClipChildren(false);
            ((ViewGroup) container.getParent()).setClipToPadding(false);
        }
        container.setClipChildren(false);
        container.setClipToPadding(false);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);

        int dividerId = bar.getContext().getResources().getIdentifier("bottom_nav_divider", "id", bar.getContext().getPackageName());
        if (dividerId > 0) {
            View divider = container.findViewById(dividerId);
            if (divider != null) {
                divider.setVisibility(View.GONE);
            }
        }

        float density = bar.getContext().getResources().getDisplayMetrics().density;
        int barColor = resolveBarColor(bar);
        int transparentColor = (barColor & 0x00FFFFFF) | (((int) (BACKGROUND_ALPHA * 255)) << 24);
        int strokeColor = isLightColor(barColor) ?
                Color.argb((int) (STROKE_ALPHA * 255), 0, 0, 0) :
                Color.argb((int) (STROKE_ALPHA * 255), 255, 255, 255);

        int userRadius = prefs.getInt("floating_bottom_bar_radius", (int) CORNER_RADIUS_DP);
        float radius = userRadius * density;

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(radius);
        background.setColor(transparentColor);
        background.setStroke((int) (1.5f * density), strokeColor);

        bar.setBackground(background);
        bar.setElevation(ELEVATION_DP * density);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        }

        int sideMargin = (int) (userSideMarginDp * density);
        ViewGroup.LayoutParams params = bar.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) params;
            mlp.leftMargin = sideMargin;
            mlp.rightMargin = sideMargin;
            bar.setLayoutParams(mlp);
        }

        if (pillDesignPro || pillDesignIos) {
            try {
                ClassLoader pluginLoader = (ClassLoader) System.getProperties().get("com.waex.helper.classloader");
                if (pluginLoader != null) {
                    Class<?> pillProClass = Class.forName("com.waex.helper.PillDesignPro", true, pluginLoader);
                    String style = pillDesignIos ? "ios_glass" : "pro";
                    pillProClass.getMethod("applyProDesign", View.class, float.class, String.class).invoke(null, bar, density, style);
                }
            } catch (Throwable t) {
                XposedBridge.log("Failed to load PillDesignPro: " + t.getMessage());
            }
        }
    }

    private void positionFabAboveCurrentBar(View fab) {
        FrameLayout rootView = findRootView(fab);
        if (rootView == null) return;
        positionFabsAboveBar(rootView, null);
    }

    private void positionFabsAboveBar(ViewGroup rootView, ViewGroup barContainer) {
        ViewGroup container = barContainer;
        if (container == null) {
            int navId = rootView.getContext().getResources().getIdentifier("bottom_nav", "id", rootView.getContext().getPackageName());
            if (navId <= 0) {
                navId = rootView.getContext().getResources().getIdentifier("navigation_bar", "id", rootView.getContext().getPackageName());
            }
            if (navId > 0) {
                View navView = rootView.findViewById(navId);
                if (navView != null && navView.getParent() instanceof ViewGroup) {
                    container = (ViewGroup) navView.getParent();
                }
            }
        }

        if (container == null) return;

        int barHeight = container.getHeight();
        if (barHeight <= 0) {
            final ViewGroup targetContainer = container;
            rootView.postDelayed(() -> positionFabsAboveBar(rootView, targetContainer), 100L);
            return;
        }

        int bottomMargin = 0;
        ViewGroup.LayoutParams lp = container.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            bottomMargin = ((ViewGroup.MarginLayoutParams) lp).bottomMargin;
        }

        float density = container.getContext().getResources().getDisplayMetrics().density;
        float totalOffset = -(barHeight + bottomMargin + (FAB_GAP_DP * density));

        for (String name : FAB_RESOURCE_NAMES) {
            int id = container.getContext().getResources().getIdentifier(name, "id", container.getContext().getPackageName());
            if (id <= 0) continue;
            View fab = rootView.findViewById(id);
            if (fab != null) {
                fab.setTranslationY(totalOffset);
                fab.bringToFront();
            }
        }
    }

    private int resolveBarColor(ViewGroup bar) {
        Drawable currentBg = bar.getBackground();
        if (currentBg instanceof ColorDrawable) {
            return ((ColorDrawable) currentBg).getColor();
        }
        try {
            TypedValue typedValue = new TypedValue();
            bar.getContext().getTheme().resolveAttribute(android.R.attr.colorBackground, typedValue, true);
            return typedValue.data;
        } catch (Throwable ignored) {
            return 0xFF121212;
        }
    }

    private boolean isLightColor(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return (red * 299 + green * 587 + blue * 114) / 1000 > 180;
    }
}