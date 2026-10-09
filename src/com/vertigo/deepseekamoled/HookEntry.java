package com.vertigo.deepseekamoled;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import java.lang.reflect.Field;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {

    private static final String TARGET_PKG = "com.deepseek.chat";
    private static final String TAG = "[DeepSeekAMOLED] ";

    // Compose Color for pure AMOLED black (0xFF00000000000000L)
    private static final long COLOR_AMOLED_BLACK = 0xFF00000000000000L;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + "Initializing DeepSeek AMOLED hook v1.1.1 in " + lpparam.processName);

        final ClassLoader cl = lpparam.classLoader;

        // 1. Hook Activity window to guarantee AMOLED black backgrounds & status bar
        hookWindow(cl);

        // 2. Hook dark background/surface tokens in palette classes (rm7 / kj6)
        // Only dark backgrounds/surfaces (o, t, x) must be patched to black.
        // Secondary tokens (j, r, h, i) MUST NOT be patched as they define markdown text & citation elements.
        hookPaletteClass(cl, "rm7", new String[]{"o", "t", "x"});
        hookPaletteClass(cl, "kj6", new String[]{"o", "t", "w"});

        // 3. Hook dark theme builders to patch background container tokens
        hookThemeBuilder(cl, "oxb", "D", true);
        hookThemeBuilder(cl, "u2b", "t", false);
    }

    private void hookWindow(final ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    applyWindowBlack((Activity) param.thisObject);
                }
            });

            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    applyWindowBlack((Activity) param.thisObject);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed to hook Activity window: " + t.getMessage());
        }
    }

    private static void applyWindowBlack(Activity activity) {
        if (activity == null) return;
        try {
            Window window = activity.getWindow();
            if (window != null) {
                window.setStatusBarColor(Color.BLACK);
                window.setNavigationBarColor(Color.BLACK);
                View decorView = window.getDecorView();
                if (decorView != null) {
                    decorView.setBackgroundColor(Color.BLACK);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void hookPaletteClass(final ClassLoader cl, final String className, final String[] fieldNames) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz != null) {
                final Class<?> targetClass = clazz;
                patchStaticPalette(targetClass, fieldNames);
                try {
                    XposedHelpers.findAndHookMethod(clazz, "<clinit>", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            patchStaticPalette(targetClass, fieldNames);
                        }
                    });
                } catch (Throwable ignored) {}
                XposedBridge.log(TAG + "Hooked palette class: " + className);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on " + className + ": " + t.getMessage());
        }
    }

    private static void patchStaticPalette(Class<?> clazz, String[] fieldNames) {
        if (clazz == null || fieldNames == null) return;
        for (String fName : fieldNames) {
            try {
                Field f = clazz.getDeclaredField(fName);
                f.setAccessible(true);
                f.setLong(null, COLOR_AMOLED_BLACK);
            } catch (Throwable ignored) {}
        }
    }

    private void hookThemeBuilder(final ClassLoader cl, final String className, final String methodName, final boolean isFn3) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz != null) {
                XposedHelpers.findAndHookMethod(clazz, methodName, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object result = param.getResult();
                        if (result != null) {
                            if (isFn3) {
                                patchFn3(result);
                            } else {
                                patchOr2(result);
                            }
                        }
                    }
                });
                XposedBridge.log(TAG + "Hooked " + className + "." + methodName + "() dark theme builder");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on " + className + "." + methodName + ": " + t.getMessage());
        }
    }

    private static void patchFn3(Object fn3Instance) {
        if (fn3Instance == null) return;
        try {
            // In fn3 (v2.6.0):
            // d is cn3 (background and surface container tokens)
            // NEVER touch fn3.a, fn3.e, or fn3.g because they contain text and markdown content colors!
            Field dField = fn3Instance.getClass().getDeclaredField("d");
            dField.setAccessible(true);
            Object cn3Obj = dField.get(fn3Instance);
            if (cn3Obj != null) {
                // In cn3:
                // c = background (rm7.o) -> AMOLED black
                // g = surface (rm7.t) -> AMOLED black
                setLongField(cn3Obj, "c", COLOR_AMOLED_BLACK);
                setLongField(cn3Obj, "g", COLOR_AMOLED_BLACK);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error patching fn3: " + t.getMessage());
        }
    }

    private static void patchOr2(Object or2Instance) {
        if (or2Instance == null) return;
        try {
            // In or2 (legacy):
            // d is kr2 (background and surface container tokens)
            // NEVER touch or2.a (lr2) because it contains text/content colors (LocalContentColor)!
            Field dField = or2Instance.getClass().getDeclaredField("d");
            dField.setAccessible(true);
            Object kr2Obj = dField.get(or2Instance);
            if (kr2Obj != null) {
                setLongField(kr2Obj, "c", COLOR_AMOLED_BLACK);
                setLongField(kr2Obj, "g", COLOR_AMOLED_BLACK);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error patching or2: " + t.getMessage());
        }
    }

    private static void setLongField(Object obj, String fieldName, long value) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.setLong(obj, value);
        } catch (Throwable ignored) {}
    }
}
