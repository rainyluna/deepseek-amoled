# Updating DeepSeek AMOLED for New App Versions

When the DeepSeek app updates, ProGuard/R8 obfuscation may change the class and field names. Follow this guide to remap the tokens and update the module in a few minutes.

---

## Step 1: Pull the New APK

With your phone connected via ADB:
```bash
# 1. Find APK path on device
adb shell pm path com.deepseek.chat

# 2. Pull base.apk
mkdir -p deepseek_analysis
adb pull <path_from_above>/base.apk deepseek_analysis/base.apk
```

---

## Step 2: Extract & Decompile

Extract the dex files:
```bash
unzip -o deepseek_analysis/base.apk "*.dex" -d deepseek_analysis/
```

Decompile `MainActivity` with JADX:
```bash
jadx --single-class "com.deepseek.chat.MainActivity" -d deepseek_analysis/decompiled deepseek_analysis/classes.dex
```

---

## Step 3: Identify the Obfuscated Mapping

1. **Locate Theme Entry Point**:
   - In `MainActivity.java`, look inside `onCreate()` for `ComponentActivity.setContent` (`d82.a(this, ...)`).
   - Find the Composable lambda passed to `setContent` (e.g. `zq5`).
   - Inside that lambda, trace the Compose theme wrapper (tagged with `"com.deepseek.chat.ui.theme.DeepSeekTheme"`).

2. **Locate Palette Class (`kj6`)**:
   - Find the method building the dark theme (previously `u2b.t()`).
   - Look for the class containing dark color constants `4279176975L` (`#0E0F0F`), `4280163870L` (`#1E1E1E`), `4280558628L` (`#232424`).
   - Note the new class name (e.g., `kj6` -> `new_name`) and field names (`o`, `t`, `w`, `j`).

3. **Locate Fallback Material 3 ColorScheme (`qe9`)**:
   - In `Theme.kt`, look for `"com.deepseek.chat.ui.theme.fallback.fallbackMaterialColorScheme"`.
   - Find the class holding `static final q52 a` (light) and `static final q52 b` (dark).
   - Note the new class name (e.g., `qe9` -> `new_name`).

4. **Verify Material 3 ColorScheme Class (`q52`)**:
   - Search for `"ColorScheme(primary="` in strings:
     ```bash
     strings deepseek_analysis/classes.dex | grep "ColorScheme(primary="
     ```
   - Check its `toString()` to verify surface field letters (`m` = background, `o` = surface, etc.).

---

## Step 4: Update HookEntry.java

Edit [`src/com/vertigo/deepseekamoled/HookEntry.java`](src/com/vertigo/deepseekamoled/HookEntry.java):
1. In `hookKnownClasses()`:
   - Update or add the new palette class to `hookPaletteClass`:
     ```java
     hookPaletteClass(cl, "NEW_PALETTE_CLASS", new String[]{"o", "t", "w", "j"});
     ```
   - Update `patchQe9` / `qe9Class` name if changed.
   - Update `u2b.t()` class/method name if changed.
2. In `hookDynamicScheme()`:
   - Add the new class names to the late-loading interceptor.

---

## Step 5: Bump Version & Build

1. In [`AndroidManifest.xml`](AndroidManifest.xml):
   ```xml
   android:versionCode="2"
   android:versionName="1.1"
   ```
2. Build and sign:
   ```bash
   ./build.sh
   ```
3. Install on phone and restart DeepSeek:
   ```bash
   adb install -r deepseek-amoled.apk
   adb shell am force-stop com.deepseek.chat
   adb shell am start -n com.deepseek.chat/.MainActivity
   ```

---

## Step 6: Verify AMOLED Black Pixels

Capture a screenshot and check pure black ratio with Python:
```bash
adb shell screencap -p /sdcard/check.png && adb pull /sdcard/check.png .
python3 -c "
from PIL import Image
import numpy as np
arr = np.array(Image.open('check.png'))
sub = arr[200:1500, 50:1000, :3]
black = (sub[:,:,0]==0)&(sub[:,:,1]==0)&(sub[:,:,2]==0)
print(f'Pure black: {np.mean(black)*100:.2f}%')
"
```
You should see $\ge 95\%$ pure black with crisp white text.
