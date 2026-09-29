# DeepSeek AMOLED LSPosed Module

An Xposed / LSPosed module that transforms the official **DeepSeek Android App** (`com.deepseek.chat`) into a true, 100% pure AMOLED black (`#000000`) theme.

---

## Features

- **Pure AMOLED Black (`#000000`)**: Replaces the default dark-grey backgrounds (`#0F0F0F` / `#1E1E1E` / `#232424`).
- **High-Contrast White Typography**: Preserves and enhances text readability (`#F0F0F0`) for all message responses, math equations, code blocks, and user input.
- **Pure Black Window & System Bars**: Hooks `MainActivity` window to ensure status bar, navigation bar, and decor views are pitch black.
- **Jetpack Compose Native**: Hooks DeepSeek's custom design tokens (`or2`, `kr2`, `kj6`) and Material 3 `ColorScheme` (`q52`, `qe9`).

---

## Installation

1. Download the latest `deepseek-amoled.apk` from the [Releases](https://github.com/rainyluna/deepseek-amoled/releases) or build it manually.
2. Install the APK on your device:
   ```bash
   adb install -r deepseek-amoled.apk
   ```
3. Open **LSPosed Manager**, tap the module notification, and **Enable** the module.
4. Ensure **DeepSeek** (`com.deepseek.chat`) is selected in the scope.
5. Force-stop DeepSeek and restart:
   ```bash
   adb shell am force-stop com.deepseek.chat
   adb shell am start -n com.deepseek.chat/.MainActivity
   ```

---

## How It Works

DeepSeek Android is built with **Jetpack Compose** and Material 3:

1. **Design Tokens (`kj6`)**: Contains static long colors:
   - `kj6.o` (`0xFF0E0F0F`) - Main background
   - `kj6.t` (`0xFF1E1E1E`) - Surface background
   - `kj6.w` (`0xFF232424`) - Surface container / input bar
   - `kj6.j` (`0xFF282929`) - Elevated cards / bubbles
   The module intercepts these fields and patches them to `COLOR_AMOLED_BLACK` (`0xFF00000000000000L`).

2. **Theme Builder (`u2b.t()`)**:
   Constructs the dark theme `or2` instance. The module hooks `u2b.t()` and ensures the background and surface fields (`or2.d.c` and `or2.d.g`) are set to pure black while preserving text colors in `or2.a` (`lr2`).

3. **Material 3 ColorScheme (`q52`)**:
   Intercepts constructor and patches:
   - `n` (background) -> pure black
   - `p` (surface) -> pure black
   - `r` (surfaceVariant) -> pure black
   - `C`–`I` (surface containers, dim, bright) -> pure black
   - `o` (onBackground) & `q` (onSurface) -> crisp `#F0F0F0` white text.

4. **Dynamic Scheme Scanner**:
   Hooks `ClassLoader.loadClass` to identify any class matching `ColorScheme(primary=` constructor signature to survive minor updates automatically.

---

## Building from Source

### Prerequisites
- Android SDK (build-tools 34.0.0, android-34 platform)
- Java JDK 17
- Bash shell & standard Unix utilities (`zip`, `zipalign`, `apksigner`)

```bash
chmod +x build.sh
./build.sh
```

---

## Updating for New APK Releases

See [UPDATING.md](UPDATING.md) for full instructions on how to decompile, map new ProGuard/R8 class names, and rebuild when DeepSeek updates.
