# How to Build, Test & Use `FeuTimer` (Android TV + Wi-Fi Mobile Referee)

## Prerequisites
- **JDK 21:** `/Users/lx/.local/share/rea/java/temurin-21.0.13+9/Contents/Home`
- **Android SDK 35:** `/Users/lx/Library/Android/sdk`

## 0. Live Preview on Mac + Phone (`web/serve.cjs`)
```bash
/Users/lx/.local/node/bin/node /Users/lx/Documents/FeuTimer/web/serve.cjs
```
- **Android TV Display (Mac Browser):** `http://127.0.0.1:8788/` (locked to a canonical `960×540` viewport so nothing ever cuts off at 100% zoom or any window size).
- **Mobile Portrait Referee Console (Any Phone on Same Wi-Fi):** Scan the QR code shown at the bottom of **Placar** or visit `http://<YOUR_MAC_IP>:7777` on your phone!

## 1. Run Unit Tests & Android Lint
```bash
export JAVA_HOME=/Users/lx/.local/share/rea/java/temurin-21.0.13+9/Contents/Home
./gradlew testDebugUnitTest lintDebug
/Users/lx/.local/node/bin/node --test web/tests/timer-web.test.cjs
```

## 2. Build & Install Universal APK on Android TV / TV Box
```bash
export JAVA_HOME=/Users/lx/.local/share/rea/java/temurin-21.0.13+9/Contents/Home
./gradlew assembleDebug assembleRelease
```
Signed universal APK (`minSdk 23`, `targetSdk 35`, `LAUNCHER` + `LEANBACK_LAUNCHER`):
- `/Users/lx/Documents/FeuTimer/dist/FeuTimer-AndroidTV-v1.3.4.apk`

### Sideload via ADB or USB Drive
```bash
~/Library/Android/sdk/platform-tools/adb connect <TV_IP>:5555
~/Library/Android/sdk/platform-tools/adb install -r dist/FeuTimer-AndroidTV-v1.3.4.apk
```

## 3. Controlling the IBJJF Scoreboard Live from Your Phone
1. Open **🏆 Placar** on the TV (or simply open the URL on your phone — it automatically switches the TV onto the Scoreboard!).
2. Scan the QR code on the bottom bar of the TV or enter the displayed short URL (`<TV_IP>:7777`, e.g. `192.168.1.45:7777`) in Safari/Chrome on any phone on the same Wi-Fi.
3. From **one single portrait screen (zero scrolling)**:
   - Type **Athlete 1**, **Athlete 2**, and **Category / Belt** — every keystroke appears live on the TV with zero delay.
   - Tap `+2`, `+3`, `+4`, `+1`, `−1`, `−2`, `VANT ±1`, `PUN ±1`, or **tap any score number / the match clock** to type an exact value directly.
   - Tap **💾 Guardar Resultado** to save the fight (and optionally reset `0–0` for the next match in one tap).
   - Tap **📋** at top-right to view, share (WhatsApp / AirDrop / Clipboard), export (`CSV`), delete individual fights (`🗑`), or clear all (`🗑 Apagar Tudo`).

> The phone console adapts to rotation: portrait = stacked single screen; landscape = `BRANCO | clock + controls | VERMELHO` side by side. Both fit with zero scrolling (verified 320×568 up to 1180×820 with `web/tests/live-referee-layout-probe.js`).

## Showcase video (dev tool)
`tools/video` renders a narrated-by-app demo (TV simulator + phone console side by side, real voice/horn audio, music bed).
Start `node web/serve.cjs`, then `cd tools/video && npm i && npx playwright install chromium && node record.cjs` → `dist/FeuTimer-v1.3.4-showcase.mp4`.
Stage page: `web/showcase/index.html` (not shipped in the APK).
