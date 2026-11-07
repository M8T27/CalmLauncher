# Calm Launcher

A minimalist Android home screen plus a guard service that makes your phone duller, darker at night, and harder to
mindlessly open. Plain Kotlin, no third-party libraries, Android 8.0+ (minSdk 26).

**Status: written but never compiled or run.** It was produced without an Android SDK, so expect to fix a few small
compile errors on the first build, and expect to tune behavior on a real phone.

## Build and install

1. Install Android Studio. Choose **Open** and select this folder. Let it sync (it may offer to upgrade Gradle/AGP; accept).
2. Plug in your phone (USB debugging on) and press Run, or build an APK from Build > Build APK(s).

## One-time setup (the app's Settings screen shows this as a checklist)

1. **Accessibility service**: Settings > Accessibility > Calm Launcher > turn on.
   On Android 13+ a sideloaded app is blocked from this until you open
   Settings > Apps > Calm Launcher > ⋮ > **Allow restricted settings**.
2. **Grayscale permission** (needs a computer, once):
   `adb shell pm grant com.calmphone android.permission.WRITE_SECURE_SETTINGS`
3. **Notifications**: allow, so the control panel shows in the shade.
4. **Device admin**: activate (adds one extra step before uninstalling).
5. **Home app**: set Calm Launcher as default. It is the home screen only; Android's lock screen and system
   navigation can't be replaced by an app.

Optional: paste a free Gemini API key (aistudio.google.com) in Settings for AI-written questions. Without one,
questions are generated on the phone.

## What it does

| Feature | How it works |
|---|---|
| Black and white screen | Switches Android's built-in monochrome color correction (needs the adb grant). |
| Softer colors | A pale gray wash used whenever color is on (momentary color, or grayscale off). An approximation of lower vibrance. |
| Extra night dimming | Black overlay beyond the system's lowest brightness. Strength is adjustable for day and night. |
| Blue-light filter | A gray screen can't be warmed, so choose at night: grayscale + deep dim (default), or the warm filter (turns color on at night). |
| Locked apps | Long-press an app on the home screen > Lock. Opening it asks 3 questions in a row. A wrong answer means a 30 s wait, then a fresh set. Unlock lasts 10 min (adjustable) or until the screen turns off. |
| Uninstall / switch-off guard | When Settings, the package installer or Play Store shows this app's name, you get a 5-question quiz first. Also, Settings itself and un-locking/un-hiding apps cost 3 questions. |
| Bedtime and wake | From bedtime until wake, everything except calling, messaging, contacts, Settings, and apps you allow is bounced home. The alarm clock, keyboard, call screens and launcher are never blocked. A wind-down hour before bedtime starts the night filters. |
| Break timer | Notification (or Break button) > pick minutes in steps of 5 > one question per 5 minutes. Locks are suspended, then return. Disabled at bedtime and capped so it can't run into bedtime. |
| Momentary color | "Color 5 min" on the notification or home screen. Daytime only. |
| Minimal home | Clock, search (engine of your choice), then sections: Talk, Stay informed, Read & learn (KOReader, Kobo...), Notes & utilities, Focus & look (Opal, Paperize). All apps below as plain names with black-and-white icons. Long-press to hide. |

## Honest limits

- **Opal**: Opal has no public API, so this can't truly "override" it. On break start it opens Opal so you can pause
  its block, and an experimental, off-by-default option tries to tap Opal's pause button. Untested.
- **Gemini**: the Gemini built into phones has no API for other apps. This uses the Gemini web API with your own key
  (stored unencrypted in the app's private storage). LLM answers can occasionally be wrong; local questions are exact.
- **Uninstall guard is a speed bump, not a wall.** It works by reading on-screen text, so Safe Mode, adb, or a
  factory reset get around it. It can also trigger when you simply browse a Settings list that shows the app's name.
- Lock/bedtime enforcement relies on the accessibility service staying enabled. Some phone brands kill background
  services aggressively; exclude the app from battery optimization.
- Switching the service off restores color, so the phone is never stuck gray.
