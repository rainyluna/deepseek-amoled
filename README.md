# DeepSeek AMOLED

LSPosed module targeting the official DeepSeek Android client (`com.deepseek.chat`). Replaces default dark-grey surfaces (`#0F0F0F`, `#1E1E1E`, `#232424`) with solid `#000000` AMOLED black while maintaining text contrast and system bar integration.

## Technical Architecture

DeepSeek Android is built using Jetpack Compose and Material 3 design tokens:

1. **Design Tokens (`kj6` / `rm7`)**:
   - Patches static 64-bit Compose colors (`ULong`) for main background, surface containers, and elevated cards to `0xFF00000000000000L`.
2. **Theme Builder (`u2b.t()` / `hqa.t()`)**:
   - Intercepts theme construction to overwrite background and surface properties while keeping high-contrast typography (`#F0F0F0`) intact.
3. **Material 3 ColorScheme (`q52` / `oxb.D`)**:
   - Intercepts constructor parameters:
     - `background`, `surface`, `surfaceVariant`, and container tiers (`surfaceContainerLowest` through `surfaceContainerHighest`) set to pure black.
     - `onBackground` and `onSurface` set to `#F0F0F0`.
4. **Dynamic Scanner**:
   - Hooks `ClassLoader.loadClass` to identify Jetpack Compose `ColorScheme` constructors by parameter signature, enabling fallback compatibility across minor version bumps.
5. **Window System Bars**:
   - Hooks `MainActivity.onCreate` to enforce pure black status bar and navigation bar decor views.

## Prerequisites

- Android 8.0+ (API 26-36).
- Working LSPosed / Xposed framework.
- Java JDK 17.
- Android SDK Build-Tools (34.0.0+) and Android API 34 platform jar (`android.jar`).

## Building from Source

```bash
git clone https://github.com/rainyluna/deepseek-amoled.git
cd deepseek-amoled
chmod +x build.sh
./build.sh
```

### Build Pipeline
1. `javac` compiles standalone Xposed stubs in `stubs/`.
2. `javac` compiles `src/com/vertigo/deepseekamoled/HookEntry.java` against `android.jar` and compiled stubs.
3. `d8` converts classes into `classes.dex` targeting API 34.
4. `aapt2` compiles and links package resources and manifest.
5. `zip` packages DEX and assets into unaligned APK.
6. `zipalign` 4-byte aligns the package.
7. `apksigner` signs using debug RSA-2048 keys.
8. Output artifact: `deepseek-amoled.apk`.

## Installation

```bash
adb install -r deepseek-amoled.apk
```
Enable the module in LSPosed Manager, ensure `com.deepseek.chat` is in scope, then force-stop and launch DeepSeek:
```bash
adb shell am force-stop com.deepseek.chat
adb shell am start -n com.deepseek.chat/.MainActivity
```
