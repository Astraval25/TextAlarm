As of April 10, 2026, here’s the practical end-to-end flow to publish your Android app to Google Play.

1. Create a release keystore (one time)
```powershell
keytool -genkeypair -v `
  -keystore C:\keys\text-alarm-upload.jks `
  -alias upload `
  -keyalg RSA -keysize 2048 -validity 10000
```

2. Build release App Bundle (`.aab`)
```powershell
cd E:\workspace\BrightAlarm
.\gradlew.bat --stop
.\gradlew.bat clean
.\gradlew.bat bundleRelease
```
Output:
```powershell
ii .\app\build\outputs\bundle\release
```

If your release signing config is not set in Gradle yet, use:
```powershell
.\gradlew.bat bundleRelease `
  -Pandroid.injected.signing.store.file="C:\keys\text-alarm-upload.jks" `
  -Pandroid.injected.signing.store.password="YOUR_STORE_PASSWORD" `
  -Pandroid.injected.signing.key.alias="upload" `
  -Pandroid.injected.signing.key.password="YOUR_KEY_PASSWORD"
```


From your project root (`e:\workspace\TextAlarm`), run it like this:

```powershell
.\gradlew.bat installDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.astraval.brightalarm/.MainActivity
```

Prereqs:
- Android Studio + SDK installed
- An emulator running or Android phone connected (USB debugging on)

If you only want to build APK (no install/run):

```powershell
.\gradlew.bat assembleDebug
```

APK output:
`app\build\outputs\apk\debug\app-debug.apk`

If you want, I can also give you a quick “first-time setup checklist” (SDK path, device detection, common errors).