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
        // --- 1. Palette Color Constants ---
        // v2.6.0: rm7 (o=bg #0E0F0F, t=surface #1E1E1E, x=container #232424, r=input #323233, j, h, i)
        hookPaletteClass(cl, "rm7", new String[]{"o", "t", "x", "j", "r", "h", "i"});
        // legacy: kj6 (o, t, w, j)
        hookPaletteClass(cl, "kj6", new String[]{"o", "t", "w", "j"});

        // --- 2. Fallback Material Scheme ---
        // v2.6.0: hqa.b
        hookFallbackScheme(cl, "hqa", "b");
        // legacy: qe9.b
        hookFallbackScheme(cl, "qe9", "b");

        // --- 3. Dark Theme Builder ---
        // v2.6.0: oxb.D() returning fn3
        hookThemeBuilder(cl, "oxb", "D", true);
        // legacy: u2b.t() returning or2
        hookThemeBuilder(cl, "u2b", "t", false);

        // --- 4. Direct Constructor Hooks ---
        hookCn3Constructor(cl);
        hookColorSchemeClass(cl, "oz2");
        hookColorSchemeClass(cl, "q52");
    }

    private void hookCn3Constructor(final ClassLoader cl) {
        try {
            Class<?> cn3Class = XposedHelpers.findClassIfExists("cn3", cl);
            if (cn3Class != null) {
                for (Constructor<?> ctor : cn3Class.getDeclaredConstructors()) {
                    XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            setLongField(param.thisObject, "c", COLOR_AMOLED_BLACK);
                            setLongField(param.thisObject, "g", COLOR_AMOLED_BLACK);
                            setLongField(param.thisObject, "a", COLOR_AMOLED_BLACK);
                            setLongField(param.thisObject, "h", COLOR_AMOLED_BLACK);
                        }
                    });
                }
                XposedBridge.log(TAG + "Hooked cn3 constructor for AMOLED containers");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on cn3: " + t.getMessage());
        }
    }

    private void hookColorSchemeClass(final ClassLoader cl, final String className) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz != null) {
                for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                    XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            patchColorScheme(param.thisObject);
                        }
                    });
                }
                XposedBridge.log(TAG + "Hooked " + className + " constructor for AMOLED ColorScheme");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on " + className + ": " + t.getMessage());
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

    private void hookFallbackScheme(final ClassLoader cl, final String className, final String fieldName) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz != null) {
                patchFallbackStatic(clazz, fieldName);
                try {
                    XposedHelpers.findAndHookMethod(clazz, "<clinit>", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            patchFallbackStatic((Class<?>) param.method.getDeclaringClass(), fieldName);
                        }
                    });
                } catch (Throwable ignored) {}
                XposedBridge.log(TAG + "Hooked " + className + "." + fieldName + " fallback Material Scheme");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Note on " + className + ": " + t.getMessage());
        }
    }

    private static void patchFallbackStatic(Class<?> clazz, String fieldName) {
        if (clazz == null) return;
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object darkScheme = f.get(null);
            if (darkScheme != null) {
                patchColorScheme(darkScheme);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error patching " + clazz.getSimpleName() + "." + fieldName + ": " + t.getMessage());
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

    private static void patchFn3(Object fn3Instance) {
        if (fn3Instance == null) return;
        try {
            // In fn3 (v2.6.0):
            // d is cn3 (background and surface container tokens)
            // NEVER touch fn3.a or fn3.e because they contain text/content colors!
            Field dField = fn3Instance.getClass().getDeclaredField("d");
            dField.setAccessible(true);
            Object cn3Obj = dField.get(fn3Instance);
            if (cn3Obj != null) {
                // In cn3:
                // a=j10 (input bg), b=j11, c=j12 (background rm7.o), d=j9, e=j8, f=j9, g=j13 (surface rm7.t), h=j10, i=j8, j=j11
                setLongField(cn3Obj, "c", COLOR_AMOLED_BLACK);
                setLongField(cn3Obj, "g", COLOR_AMOLED_BLACK);
                setLongField(cn3Obj, "a", COLOR_AMOLED_BLACK);
                setLongField(cn3Obj, "h", COLOR_AMOLED_BLACK);
            }
            try {
                Field gField = fn3Instance.getClass().getDeclaredField("g");
                gField.setAccessible(true);
                Object dn3Obj = gField.get(fn3Instance);
                if (dn3Obj != null) {
                    setLongField(dn3Obj, "a", COLOR_AMOLED_BLACK);
                    setLongField(dn3Obj, "f", COLOR_AMOLED_BLACK);
                    setLongField(dn3Obj, "g", COLOR_AMOLED_BLACK);
                    setLongField(dn3Obj, "h", COLOR_AMOLED_BLACK);
                }
            } catch (Throwable ignored) {}
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

    private static void patchColorScheme(Object colorScheme) {
        if (colorScheme == null) return;
        String simpleName = colorScheme.getClass().getSimpleName();
        if ("oz2".equals(simpleName)) {
            // In oz2 (v2.6.0 Material 3 ColorScheme):
            // m = background, o = surface, q = surfaceVariant
            // C = surfaceBright, D = surfaceDim, E = surfaceContainer
            // F = surfaceContainerHigh, G = surfaceContainerHighest
            // H = surfaceContainerLow, I = surfaceContainerLowest
            String[] surfaceFields = {"m", "o", "q", "C", "D", "E", "F", "G", "H", "I"};
            for (String fieldName : surfaceFields) {
                setLongField(colorScheme, fieldName, COLOR_AMOLED_BLACK);
            }
            // n = onBackground, p = onSurface (crisp white text)
            setLongField(colorScheme, "n", COLOR_WHITE_TEXT);
            setLongField(colorScheme, "p", COLOR_WHITE_TEXT);
        } else {
            // In q52 (legacy Material 3 ColorScheme):
            // n = background, p = surface, r = surfaceVariant
            // C = surfaceBright, D = surfaceDim, E = surfaceContainer
            // F = surfaceContainerHigh, G = surfaceContainerHighest
            // H = surfaceContainerLow, I = surfaceContainerLowest
            String[] surfaceFields = {"n", "p", "r", "C", "D", "E", "F", "G", "H", "I"};
            for (String fieldName : surfaceFields) {
                setLongField(colorScheme, fieldName, COLOR_AMOLED_BLACK);
            }
            // o = onBackground, q = onSurface
            setLongField(colorScheme, "o", COLOR_WHITE_TEXT);
            setLongField(colorScheme, "q", COLOR_WHITE_TEXT);
        }
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

                    if ("rm7".equals(name)) {
                        patchStaticPalette(loadedClass, new String[]{"o", "t", "x", "j", "r", "h", "i"});
                    } else if ("kj6".equals(name)) {
                        patchStaticPalette(loadedClass, new String[]{"o", "t", "w", "j"});
                    } else if ("hqa".equals(name) || "qe9".equals(name)) {
                        patchFallbackStatic(loadedClass, "b");
                    } else if ("cn3".equals(name)) {
                        for (Constructor<?> ctor : loadedClass.getDeclaredConstructors()) {
                            XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    setLongField(param.thisObject, "c", COLOR_AMOLED_BLACK);
                                    setLongField(param.thisObject, "g", COLOR_AMOLED_BLACK);
                                    setLongField(param.thisObject, "a", COLOR_AMOLED_BLACK);
                                    setLongField(param.thisObject, "h", COLOR_AMOLED_BLACK);
                                }
                            });
                        }
                    } else if ("oz2".equals(name) || "q52".equals(name)) {
                        for (Constructor<?> ctor : loadedClass.getDeclaredConstructors()) {
                            XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    patchColorScheme(param.thisObject);
                                }
                            });
                        }
                    }

                    // Dynamic catch-all for any unknown ColorScheme constructor
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
