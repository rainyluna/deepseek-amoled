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

## Step 3: Obfuscated Token Mapping History

| Token Category | v2.5.x (Legacy) | v2.6.0 (Current) | Description |
|---|---|---|---|
| **Palette Class** | `kj6` (`o`, `t`, `w`, `j`) | `rm7` (`o`, `t`, `x`, `j`, `r`, `h`, `i`) | Color constants (`4279176975L` = `#0E0F0F`, `4280163870L` = `#1E1E1E`, `4280558628L` = `#232424`, `4281545523L` = `#323233`) |
| **Fallback ColorScheme** | `qe9.b` | `hqa.b` | Static holder for fallback dark Material 3 `ColorScheme` |
| **Dark Theme Builder** | `u2b.t()` | `oxb.D()` | Method constructing the dark theme custom tokens (`fn3`) |
| **Custom Theme Tokens** | `or2` (field `d` = `kr2`) | `fn3` (field `d` = `cn3`) | Holds background (`c`), surface (`g`), and input container (`a`) |
| **Material 3 Scheme** | `q52` | `oz2` | M3 `ColorScheme` class (`m`=bg, `n`=onBg, `o`=surface, `p`=onSurface, `q`=surfaceVariant) |
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
