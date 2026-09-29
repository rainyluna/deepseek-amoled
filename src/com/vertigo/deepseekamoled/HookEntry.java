package com.vertigo.deepseekamoled;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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
    // Crisp off-white for text (0xFFF0F0F0)
    private static final long COLOR_WHITE_TEXT = 0xFFF0F0F000000000L;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + "Initializing DeepSeek AMOLED hook in " + lpparam.processName);

        final ClassLoader cl = lpparam.classLoader;

        // 1. Hook Activity to guarantee AMOLED window backgrounds & status bar
        hookWindow(cl);

        // 2. Hook DeepSeek palette and theme classes
        hookKnownClasses(cl);

        // 3. Hook dynamic Material 3 ColorScheme construction
        hookDynamicScheme(cl);
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

    private void hookKnownClasses(final ClassLoader cl) {
        // --- 1. Palette Color Constants: kj6 ---
        // Only dark background/surface fields: o, t, w, j
        hookPaletteClass(cl, "kj6", new String[]{"o", "t", "w", "j"});

        // --- 2. Fallback Material Scheme: qe9.b ---
        try {
            Class<?> qe9Class = XposedHelpers.findClassIfExists("qe9", cl);
            if (qe9Class != null) {
                patchQe9(qe9Class);
                try {
                    XposedHelpers.findAndHookMethod(qe9Class, "<clinit>", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            patchQe9((Class<?>) param.method.getDeclaringClass());
                        }
                    });
                } catch (Throwable ignored) {}
                XposedBridge.log(TAG + "Hooked qe9 fallback Material Scheme");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on qe9: " + t.getMessage());
        }

        // --- 3. Dark Theme Builder: u2b.t() ---
        try {
            Class<?> u2bClass = XposedHelpers.findClassIfExists("u2b", cl);
            if (u2bClass != null) {
                XposedHelpers.findAndHookMethod(u2bClass, "t", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object or2Instance = param.getResult();
                        if (or2Instance != null) {
                            patchOr2(or2Instance);
                        }
                    }
                });
                XposedBridge.log(TAG + "Hooked u2b.t() dark theme builder");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on u2b.t: " + t.getMessage());
        }
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

    private static void patchOr2(Object or2Instance) {
        if (or2Instance == null) return;
        try {
            // In or2:
            // d is kr2 (background and surface container tokens)
            // NEVER touch or2.a (lr2) because it contains text/content colors (LocalContentColor)!
            Field dField = or2Instance.getClass().getDeclaredField("d");
            dField.setAccessible(true);
            Object kr2Obj = dField.get(or2Instance);
            if (kr2Obj != null) {
                // In kr2:
                // a=j10, b=j11, c=j12 (background kj6.o), d=j9, e=j8, f=j9, g=j13 (surface kj6.t), h=j10, i=j8, j=j11
                // Patch background (c) and surface (g) to pure black
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

    private static void patchQe9(Class<?> qe9Class) {
        if (qe9Class == null) return;
        try {
            Field bField = qe9Class.getDeclaredField("b");
            bField.setAccessible(true);
            Object darkScheme = bField.get(null);
            if (darkScheme != null) {
                patchColorScheme(darkScheme);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error patching qe9.b: " + t.getMessage());
        }
    }

    private static void patchColorScheme(Object colorScheme) {
        if (colorScheme == null) return;
        // In Material 3 ColorScheme (q52 in DeepSeek):
        // n = background, p = surface, r = surfaceVariant,
        // C = surfaceBright, D = surfaceDim, E = surfaceContainer,
        // F = surfaceContainerHigh, G = surfaceContainerHighest,
        // H = surfaceContainerLow, I = surfaceContainerLowest
        // (NOTE: o is onBackground and q is onSurface - DO NOT MAKE THEM BLACK!)
        String[] surfaceFields = {"n", "p", "r", "C", "D", "E", "F", "G", "H", "I"};
        Class<?> cl = colorScheme.getClass();
        for (String fieldName : surfaceFields) {
            try {
                Field f = cl.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.setLong(colorScheme, COLOR_AMOLED_BLACK);
            } catch (Throwable ignored) {}
        }
        // Ensure onBackground (o) and onSurface (q) are crisp white text
        setLongField(colorScheme, "o", COLOR_WHITE_TEXT);
        setLongField(colorScheme, "q", COLOR_WHITE_TEXT);
    }

    /**
     * Dynamic scanner to identify and hook ColorScheme construction even across updates.
     */
    private void hookDynamicScheme(final ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass", String.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Class<?> loadedClass = (Class<?>) param.getResult();
                    if (loadedClass == null) return;

                    String name = loadedClass.getName();
                    if (name.startsWith("android.") || name.startsWith("java.") || name.startsWith("kotlin.")) {
                        return;
                    }

                    if ("kj6".equals(name)) {
                        patchStaticPalette(loadedClass, new String[]{"o", "t", "w", "j"});
                    } else if ("qe9".equals(name)) {
                        patchQe9(loadedClass);
                    }

                    // Check if class is Material 3 ColorScheme
                    try {
                        Method toStringMethod = loadedClass.getDeclaredMethod("toString");
                        if (toStringMethod != null && !Modifier.isAbstract(loadedClass.getModifiers())) {
                            for (Constructor<?> ctor : loadedClass.getDeclaredConstructors()) {
                                Class<?>[] params = ctor.getParameterTypes();
                                if (params.length >= 25 && params[0] == long.class) {
                                    XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                                        @Override
                                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                            patchColorScheme(param.thisObject);
                                        }
                                    });
                                    XposedBridge.log(TAG + "Dynamically hooked ColorScheme constructor: " + name);
                                    break;
                                }
                            }
                        }
                    } catch (NoSuchMethodException ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Dynamic scheme scanner note: " + t.getMessage());
        }
    }
}
